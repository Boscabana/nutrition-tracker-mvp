package com.nick.nutritiontracker.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.nick.nutritiontracker.data.*
import android.graphics.Bitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nutrition_tracker", Context.MODE_PRIVATE)
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }

    val healthConnectManager = HealthConnectManager(application)
    val firebaseManager = FirebaseManager()
    private val firestoreRepository = FirestoreRepository()
    private var syncJob: Job? = null

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    fun selectDate(date: LocalDate) {
        selectedDate = date
        syncStepsForSelectedDate()
    }

    private var nextFoodId = 1L
    private var nextPortionId = 1L
    private var nextEntryId = 1L
    private var nextMealId = 1L

    val foods = mutableStateListOf<FoodItemEntity>()
    val meals = mutableStateListOf<MealEntity>()
    val allEntries = mutableStateListOf<FoodEntryEntity>()
    val categories = mutableStateListOf<String>()
    
    val plannedEntries = mutableStateListOf<FoodEntryEntity>()
    val shoppingList = mutableStateListOf<ShoppingItem>()
    var isShoppingListAggregated by mutableStateOf(false)
    var showPantryInShoppingList by mutableStateOf(false)
    var shoppingListSortByCategory by mutableStateOf(prefs.getBoolean("shopping_sort_category", true))
        private set

    fun updateShoppingListSort(sort: Boolean) {
        shoppingListSortByCategory = sort
        prefs.edit().putBoolean("shopping_sort_category", sort).apply()
    }
    
    var foodSearchQuery by mutableStateOf("")
    var selectedFoodCategory by mutableStateOf<String?>(null)
    
    var pendingRecipeImport by mutableStateOf<RecipeData?>(null)
    
    val weightHistory = mutableStateListOf<WeightEntry>()
    val dayVerifications = mutableStateMapOf<String, Boolean>()
    
    val inboxMessages = mutableStateListOf<InboxMessage>()
    val unreadInboxCount by derivedStateOf { inboxMessages.count { !it.isRead } }

    val householdMembers = mutableStateListOf<Map<String, String>>()
    
    var forceOnboardingOnStart by mutableStateOf(prefs.getBoolean("force_onboarding", false))
        private set
    
    var geminiApiKey by mutableStateOf(
        prefs.getString("gemini_api_key", null) ?: com.nick.nutritiontracker.BuildConfig.GEMINI_API_KEY
    )
        private set
    
    var selectedAiModel by mutableStateOf(prefs.getString("selected_ai_model", "gemini-3.6-flash") ?: "gemini-3.6-flash")
        private set

    fun updateSelectedAiModel(model: String) {
        selectedAiModel = model
        prefs.edit().putString("selected_ai_model", model).apply()
    }

    fun updateGeminiApiKey(key: String) {
        geminiApiKey = key
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    var isAnalyzingImage by mutableStateOf(false)
    var aiEstimationResult by mutableStateOf<AiEstimationResult?>(null)
    var aiErrorMessage by mutableStateOf<String?>(null)

    val availableAiModels = mutableStateListOf<AiModelStatus>()

    fun probeAiModels() {
        availableAiModels.clear()
        viewModelScope.launch {
            val service = GeminiService()
            val candidates = listOf("gemini-3.6-flash", "gemini-3.1-pro", "gemini-2.0-flash")
            candidates.forEach { model ->
                val status = service.testModelAvailability(model)
                availableAiModels.add(status)
            }
        }
    }

    fun analyzeMealImage(bitmap: Bitmap) {
        Log.d("NutritionViewModel", "Starting AI analysis with Vertex AI. Selected model: $selectedAiModel")
        
        isAnalyzingImage = true
        aiErrorMessage = null
        viewModelScope.launch {
            try {
                val service = GeminiService()
                val result = service.estimateNutrition(bitmap, selectedAiModel)
                if (result != null) {
                    aiEstimationResult = result
                } else {
                    aiErrorMessage = "Das Bild konnte nicht analysiert werden."
                }
            } catch (e: Exception) {
                Log.e("NutritionViewModel", "AI Analysis failed", e)
                val msg = e.toString().lowercase()
                aiErrorMessage = when {
                    msg.contains("404") || msg.contains("not found") -> 
                        "AI Modell nicht verfügbar. Bitte prüfe die API-Berechtigungen deines Keys im Google AI Studio."
                    msg.contains("403") -> "API Key ungültig oder keine Berechtigung für die AI-Modelle."
                    else -> "Fehler bei der Analyse: ${e.localizedMessage}"
                }
            } finally {
                isAnalyzingImage = false
            }
        }
    }

    fun setForceOnboarding(force: Boolean) {
        forceOnboardingOnStart = force
        prefs.edit().putBoolean("force_onboarding", force).apply()
    }
    
    val dailySteps = mutableStateMapOf<String, Int>()

    val todayEntries by derivedStateOf {
        allEntries.filter { it.dateIso == selectedDate.toString() }
            .sortedByDescending { it.id }
    }

    val todayTotalKcal by derivedStateOf { todayEntries.sumOf { it.kcal } }
    val todayTotalProtein by derivedStateOf { todayEntries.sumOf { it.protein } }
    val todayTotalComplexCarbs by derivedStateOf { todayEntries.sumOf { it.complexCarbs } }
    val todayTotalSugar by derivedStateOf { todayEntries.sumOf { it.sugar } }
    val todayTotalUnsaturatedFat by derivedStateOf { todayEntries.sumOf { it.unsaturatedFat } }
    val todayTotalSaturatedFat by derivedStateOf { todayEntries.sumOf { it.saturatedFat } }
    
    val todaySteps by derivedStateOf { dailySteps[selectedDate.toString()] ?: 0 }

    fun calculateWeightBudgetGrams(dateIso: String, userProfile: UserProfile): Double {
        val dateEntries = allEntries.filter { it.dateIso == dateIso }
        if (dateEntries.isEmpty()) return 0.0
        val intake = dateEntries.sumOf { it.kcal }
        val steps = dailySteps[dateIso] ?: 0
        val activity = DailyActivity(dateIso, steps)
        val activityKcal = activity.calculateCalories(userProfile.weightKg, userProfile.heightCm / 100.0)
        val actualDeficit = (userProfile.bmr + activityKcal) - intake
        val cappedDeficit = minOf(userProfile.goalIntensity.toDouble(), actualDeficit)
        return (cappedDeficit / 7000.0) * 1000.0
    }

    fun getDayStatusColor(dateIso: String, profile: UserProfile): Int {
        val entries = allEntries.filter { it.dateIso == dateIso }
        if (entries.isEmpty()) return 0xFFFF0000.toInt()
        val intake = entries.sumOf { it.kcal }
        val steps = dailySteps[dateIso] ?: 0
        val activity = DailyActivity(dateIso, steps)
        val activityKcal = activity.calculateCalories(profile.weightKg, profile.heightCm / 100.0)
        val bmrLimit = profile.bmr + activityKcal
        val deficitLimit = (profile.bmr - profile.goalIntensity) + activityKcal
        return when {
            intake <= deficitLimit -> 0xFF2196F3.toInt()
            intake <= bmrLimit -> 0xFF4CAF50.toInt()
            else -> 0xFFFFC107.toInt()
        }
    }

    init {
        loadFoods(); loadMeals(); loadEntries(); loadSteps(); loadCategories(); loadWeightHistory(); loadVerifications()
        if (foods.size != foods.map { it.id }.distinct().size || foods.any { it.parentId != null && foods.none { p -> p.id == it.parentId } }) {
            recalculateIds()
        }
        if (foods.isEmpty()) createDefaultFoods()
        if (categories.isEmpty()) createDefaultCategories()
        syncStepsForSelectedDate()
        setupFirebaseSync()
    }

    fun syncStepsForSelectedDate() {
        viewModelScope.launch {
            if (healthConnectManager.isAvailable() && healthConnectManager.hasAllPermissions()) {
                val steps = healthConnectManager.getStepsForDate(selectedDate)
                if (steps != null) updateSteps(steps)
            }
        }
    }

    private fun setupFirebaseSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            firebaseManager.household.collectLatest { household ->
                if (household != null) {
                    launch {
                        try {
                            val members = firestoreRepository.getHouseholdMembers(household.members)
                            householdMembers.clear()
                            householdMembers.addAll(members)
                        } catch (e: Exception) { Log.e("Firestore", "Error members", e) }
                    }
                    launch {
                        try {
                            firestoreRepository.getInboxMessages(firebaseManager.currentUser.value?.uid ?: "").collect { messages ->
                                inboxMessages.clear()
                                inboxMessages.addAll(messages)
                            }
                        } catch (e: Exception) { Log.e("Firestore", "Inbox error", e) }
                    }
                    launch {
                        try {
                            firestoreRepository.getPlannedEntries(household.id).collect { entries ->
                                plannedEntries.clear()
                                plannedEntries.addAll(entries)
                            }
                        } catch (e: Exception) { Log.e("Firestore", "Planner error", e) }
                    }
                    launch {
                        try {
                            firestoreRepository.getShoppingList(household.id).collect { items ->
                                shoppingList.clear()
                                shoppingList.addAll(items)
                            }
                        } catch (e: Exception) { Log.e("Firestore", "Shopping error", e) }
                    }
                } else {
                    plannedEntries.clear(); shoppingList.clear(); householdMembers.clear(); inboxMessages.clear()
                }
            }
        }
    }

    private fun getGeneralName(food: FoodItemEntity): String {
        val parent = food.parentId?.let { pId -> foods.find { it.id == pId } }
        return parent?.name ?: food.name
    }

    private fun getGeneralNameForIngredient(foodItemId: Long, fallbackName: String): String {
        val food = foods.find { it.id == foodItemId }
        val parent = food?.parentId?.let { pId -> foods.find { it.id == pId } }
        return parent?.name ?: food?.name ?: fallbackName
    }

    fun addPlannedEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String, date: LocalDate, autoAddToShoppingList: Boolean = true) {
        val householdId = firebaseManager.household.value?.id ?: return
        val grams = if (portion != null) amount * portion.grams else amount
        val entry = FoodEntryEntity(id = System.currentTimeMillis(), dateIso = date.toString(), mealSlot = mealSlot, amount = amount, unitLabel = portion?.name ?: food.baseUnit, grams = grams, foodItemId = food.id, name = food.name, brand = food.brand, kcalPer100g = food.kcalPer100g, proteinPer100g = food.proteinPer100g, carbsPer100g = food.carbsPer100g, sugarPer100g = food.sugarPer100g, fatPer100g = food.fatPer100g, saturatedFatPer100g = food.saturatedFatPer100g, alcoholPercent = food.alcoholPercent, baseUnit = food.baseUnit, store = food.store, isPlanned = true)
        viewModelScope.launch {
            try {
                firestoreRepository.addPlannedEntry(householdId, entry)
                if (autoAddToShoppingList) {
                    val item = ShoppingItem(name = getGeneralName(food), amount = amount, unit = portion?.name ?: food.baseUnit, isAutoGenerated = true, category = food.category, householdId = householdId, sourceName = "Einzelne Zutat", weightGrams = grams, baseUnit = food.baseUnit, isPantryItem = food.isPantryItem || (food.parentId?.let { pId -> foods.find { it.id == pId }?.isPantryItem } ?: false))
                    firestoreRepository.addShoppingItem(householdId, item)
                }
            } catch (e: Exception) { Log.e("Firestore", "Add planned error", e) }
        }
    }

    fun addPlannedMeal(meal: MealEntity, mealSlot: String, date: LocalDate, servings: Double = 1.0, autoAddToShoppingList: Boolean = true) {
        val householdId = firebaseManager.household.value?.id ?: return
        val entry = FoodEntryEntity(id = System.currentTimeMillis(), dateIso = date.toString(), mealSlot = mealSlot, amount = servings, unitLabel = if (servings == 1.0) "Portion" else "Portionen", grams = (meal.ingredients.sumOf { it.grams } * (servings / meal.servings)).coerceAtLeast(0.0), foodItemId = -1, name = meal.name, kcalPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, sugarPer100g = 0.0, fatPer100g = 0.0, saturatedFatPer100g = 0.0, isMeal = true, mealIngredients = meal.ingredients.map { it.copy(id = System.currentTimeMillis() + (Math.random() * 1000).toLong(), grams = it.grams * (servings / meal.servings), amount = it.amount * (servings / meal.servings)) }, isPlanned = true)
        viewModelScope.launch {
            try {
                firestoreRepository.addPlannedEntry(householdId, entry)
                if (autoAddToShoppingList) {
                    meal.ingredients.forEach { ing ->
                        val foodItem = foods.find { it.id == ing.foodItemId }
                        val isPantry = foodItem?.isPantryItem == true || (foodItem?.parentId?.let { pId -> foods.find { it.id == pId }?.isPantryItem } ?: false)
                        val itemWeight = (ing.grams / meal.servings) * servings
                        val item = ShoppingItem(name = getGeneralNameForIngredient(ing.foodItemId, ing.name), amount = (ing.amount / meal.servings) * servings, unit = ing.unitLabel, isAutoGenerated = true, category = foodItem?.category ?: (foodItem?.parentId?.let { pId -> foods.find { it.id == pId }?.category }), householdId = householdId, sourceName = meal.name, weightGrams = itemWeight, baseUnit = ing.baseUnit, isPantryItem = isPantry)
                        firestoreRepository.addShoppingItem(householdId, item)
                    }
                }
            } catch (e: Exception) { Log.e("Firestore", "Add meal error", e) }
        }
    }

    fun deletePlannedEntry(entryId: Long) {
        val householdId = firebaseManager.household.value?.id ?: return
        viewModelScope.launch { firestoreRepository.deletePlannedEntry(householdId, entryId) }
    }

    fun updatePlannedEntry(updated: FoodEntryEntity) {
        val householdId = firebaseManager.household.value?.id ?: return
        val entryToSave = if (updated.foodItemId == -1L && !updated.isMeal) { val saved = saveEntryAsFood(updated); updated.copy(foodItemId = saved.id) }
        else if (updated.isMeal) { val finalIngredients = updated.mealIngredients?.map { ing -> if (foods.none { it.id == ing.foodItemId }) { val saved = saveIngredientAsFood(ing); ing.copy(foodItemId = saved.id) } else ing }; updated.copy(mealIngredients = finalIngredients) }
        else updated
        viewModelScope.launch { try { firestoreRepository.addPlannedEntry(householdId, entryToSave) } catch (e: Exception) { Log.e("Firestore", "Update planner error", e) } }
    }

    fun addShoppingItem(name: String, amount: Double, unit: String) {
        val householdId = firebaseManager.household.value?.id ?: return
        val lowerUnit = unit.lowercase().trim()
        val isWeight = lowerUnit == "g" || lowerUnit == "ml"
        viewModelScope.launch { firestoreRepository.addShoppingItem(householdId, ShoppingItem(name = name, amount = amount, unit = unit, householdId = householdId, weightGrams = if (isWeight) amount else 0.0, baseUnit = if (lowerUnit == "ml") "ml" else "g")) }
    }

    fun toggleShoppingItem(item: ShoppingItem) {
        val householdId = firebaseManager.household.value?.id ?: return
        viewModelScope.launch {
            try {
                if (isShoppingListAggregated) {
                    val isWeightItem = item.weightGrams > 0
                    val relatedItems = shoppingList.filter { 
                        val nameMatch = it.name.equals(item.name, ignoreCase = true)
                        val match = if (isWeightItem) nameMatch && it.weightGrams > 0 && it.baseUnit.equals(item.baseUnit, ignoreCase = true)
                        else nameMatch && it.unit.equals(item.unit, ignoreCase = true)
                        match && it.isChecked != !item.isChecked 
                    }
                    relatedItems.forEach { firestoreRepository.updateShoppingItem(householdId, it.copy(isChecked = !item.isChecked)) }
                } else firestoreRepository.updateShoppingItem(householdId, item.copy(isChecked = !item.isChecked))
            } catch (e: Exception) { Log.e("Firestore", "Toggle error", e) }
        }
    }

    fun deleteShoppingItem(itemId: String) {
        val householdId = firebaseManager.household.value?.id ?: return
        viewModelScope.launch { try { firestoreRepository.deleteShoppingItem(householdId, itemId) } catch (e: Exception) { Log.e("Firestore", "Delete error", e) } }
    }

    private fun createDefaultCategories() {
        val defaults = listOf("Obst", "Gemüse", "Backwaren", "Kühlregal", "Fleisch", "Milchprodukte", "Protein", "Teigwaren", "Convenience", "Fertiggerichte", "Tiefkühlprodukte", "Süßigkeiten", "Getränke")
        categories.addAll(defaults); saveCategories()
    }

    private fun createDefaultFoods() {
        addFood(name = "Ei", kcal = 155.0, protein = 13.0, carbs = 1.1, sugar = 1.1, fat = 11.0, saturatedFat = 3.3, alcoholPercent = 0.0, baseUnit = "g", portions = listOf(FoodPortionEntity(0, "S", 43.0), FoodPortionEntity(0, "M", 53.0), FoodPortionEntity(0, "L", 63.0)), packages = emptyList(), category = "Protein", isGeneric = true)
    }

    fun findFoodByBarcode(barcode: String): FoodItemEntity? = foods.find { it.barcode?.equals(barcode, ignoreCase = true) == true }

    fun addFood(name: String, kcal: Double, protein: Double, carbs: Double, sugar: Double, fat: Double, saturatedFat: Double, alcoholPercent: Double, baseUnit: String, portions: List<FoodPortionEntity>, packages: List<FoodPackageEntity>, barcode: String? = null, brand: String? = null, category: String? = null, isGeneric: Boolean = false, parentId: Long? = null, store: String? = null, isPantryItem: Boolean = false): FoodItemEntity {
        val updatedPortions = portions.map { if (it.id == 0L) it.copy(id = nextPortionId++) else it }
        val updatedPackages = packages.mapIndexed { idx, pkg -> if (pkg.id == 0L) pkg.copy(id = nextFoodId * 100 + idx) else pkg }
        val newFood = FoodItemEntity(id = nextFoodId++, name = name.trim(), brand = brand?.trim()?.takeIf { it.isNotBlank() }, kcalPer100g = kcal, proteinPer100g = protein, carbsPer100g = carbs, sugarPer100g = sugar, fatPer100g = fat, saturatedFatPer100g = saturatedFat, alcoholPercent = alcoholPercent, baseUnit = baseUnit, portions = updatedPortions, packages = updatedPackages, barcode = barcode?.trim()?.takeIf { it.isNotBlank() }, category = category?.trim()?.takeIf { it.isNotBlank() }, isGeneric = isGeneric, parentId = parentId, store = store?.trim()?.takeIf { it.isNotBlank() }, isPantryItem = isPantryItem)
        foods.add(newFood); saveFoods(); return newFood
    }

    fun updateFood(updatedFood: FoodItemEntity) {
        val index = foods.indexOfFirst { it.id == updatedFood.id }
        if (index != -1) {
            val fixedPortions = updatedFood.portions.map { if (it.id == 0L) it.copy(id = nextPortionId++) else it }
            val fixedPackages = updatedFood.packages.mapIndexed { idx, pkg -> if (pkg.id == 0L) pkg.copy(id = updatedFood.id * 100 + idx) else pkg }
            val finalFood = updatedFood.copy(portions = fixedPortions, packages = fixedPackages)
            foods[index] = finalFood; saveFoods()
            for (i in allEntries.indices) { if (allEntries[i].foodItemId == finalFood.id) allEntries[i] = allEntries[i].copy(name = finalFood.name, brand = finalFood.brand, kcalPer100g = finalFood.kcalPer100g, proteinPer100g = finalFood.proteinPer100g, carbsPer100g = finalFood.carbsPer100g, sugarPer100g = finalFood.sugarPer100g, fatPer100g = finalFood.fatPer100g, saturatedFatPer100g = finalFood.saturatedFatPer100g, alcoholPercent = finalFood.alcoholPercent, baseUnit = finalFood.baseUnit, store = finalFood.store) }
            saveEntries()
            for (mIdx in meals.indices) {
                var changed = false
                val updatedIngredients = meals[mIdx].ingredients.map { ing -> if (ing.foodItemId == finalFood.id) { changed = true; ing.copy(name = finalFood.name, kcalPer100g = finalFood.kcalPer100g, proteinPer100g = finalFood.proteinPer100g, carbsPer100g = finalFood.carbsPer100g, sugarPer100g = finalFood.sugarPer100g, fatPer100g = finalFood.fatPer100g, saturatedFatPer100g = finalFood.saturatedFatPer100g, alcoholPercent = finalFood.alcoholPercent, baseUnit = finalFood.baseUnit, store = finalFood.store, brand = finalFood.brand) } else ing }
                if (changed) meals[mIdx] = meals[mIdx].copy(ingredients = updatedIngredients)
            }
            saveMeals()
            val householdId = firebaseManager.household.value?.id
            if (householdId != null) {
                viewModelScope.launch {
                    shoppingList.filter { !it.isChecked && it.name.equals(finalFood.name, ignoreCase = true) }.forEach { firestoreRepository.updateShoppingItem(householdId, it.copy(category = finalFood.category, isPantryItem = finalFood.isPantryItem)) }
                    plannedEntries.filter { it.foodItemId == finalFood.id || (it.isMeal && it.mealIngredients?.any { ing -> ing.foodItemId == finalFood.id } == true) }.forEach { entry ->
                        if (entry.isMeal) {
                            val updatedIngredients = entry.mealIngredients?.map { ing -> if (ing.foodItemId == finalFood.id) ing.copy(name = finalFood.name, kcalPer100g = finalFood.kcalPer100g, proteinPer100g = finalFood.proteinPer100g, carbsPer100g = finalFood.carbsPer100g, sugarPer100g = finalFood.sugarPer100g, fatPer100g = finalFood.fatPer100g, saturatedFatPer100g = finalFood.saturatedFatPer100g, alcoholPercent = finalFood.alcoholPercent, baseUnit = finalFood.baseUnit, store = finalFood.store, brand = finalFood.brand) else ing }
                            firestoreRepository.addPlannedEntry(householdId, entry.copy(mealIngredients = updatedIngredients))
                        } else firestoreRepository.addPlannedEntry(householdId, entry.copy(name = finalFood.name, brand = finalFood.brand, kcalPer100g = finalFood.kcalPer100g, proteinPer100g = finalFood.proteinPer100g, carbsPer100g = finalFood.carbsPer100g, sugarPer100g = finalFood.sugarPer100g, fatPer100g = finalFood.fatPer100g, saturatedFatPer100g = finalFood.saturatedFatPer100g, alcoholPercent = finalFood.alcoholPercent, baseUnit = finalFood.baseUnit, store = finalFood.store))
                    }
                }
            }
        }
    }

    fun deleteFood(id: Long) { foods.removeAll { it.id == id }; foods.forEachIndexed { i, f -> if (f.parentId == id) foods[i] = f.copy(parentId = null) }; allEntries.forEachIndexed { i, e -> if (e.foodItemId == id) allEntries[i] = e.copy(foodItemId = -1L) }; saveFoods(); saveMeals(); saveEntries() }
    fun mergeFoods(targetParentId: Long, childIds: List<Long>) { childIds.forEach { id -> val idx = foods.indexOfFirst { it.id == id }; if (idx != -1) foods[idx] = foods[idx].copy(parentId = targetParentId, isGeneric = false) }; saveFoods() }
    fun promoteToGeneric(foodId: Long) { val idx = foods.indexOfFirst { it.id == foodId }; if (idx != -1) { foods[idx] = foods[idx].copy(isGeneric = true, parentId = null); saveFoods() } }
    fun addCategory(name: String) { if (name.isNotBlank() && !categories.contains(name.trim())) { categories.add(name.trim()); saveCategories() } }
    fun deleteCategory(name: String) { categories.remove(name); saveCategories() }
    fun updateCategory(oldName: String, newName: String) {
        val idx = categories.indexOf(oldName); if (idx != -1 && newName.isNotBlank() && !categories.contains(newName.trim())) {
            categories[idx] = newName.trim(); saveCategories()
            foods.forEachIndexed { i, f -> if (f.category == oldName) foods[i] = f.copy(category = newName.trim()) }; saveFoods()
        }
    }

    fun saveIngredientAsFood(ingredient: MealIngredientEntity): FoodItemEntity = addFood(ingredient.name, ingredient.kcalPer100g, ingredient.proteinPer100g, ingredient.carbsPer100g, ingredient.sugarPer100g, ingredient.fatPer100g, ingredient.saturatedFatPer100g, ingredient.alcoholPercent, ingredient.baseUnit, emptyList(), emptyList(), brand = ingredient.brand, store = ingredient.store, isGeneric = false)
    fun saveEntryAsFood(entry: FoodEntryEntity): FoodItemEntity = addFood(entry.name, entry.kcalPer100g, entry.proteinPer100g, entry.carbsPer100g, entry.sugarPer100g, entry.fatPer100g, entry.saturatedFatPer100g, entry.alcoholPercent, entry.baseUnit, emptyList(), emptyList(), brand = entry.brand, store = entry.store, isGeneric = false)
    private fun saveCategories() { try { prefs.edit().putString("categories_json", json.encodeToString(categories.toList())).apply() } catch (e: Exception) {} }
    private fun loadCategories() { val data = prefs.getString("categories_json", null); if (data != null) try { val loaded = json.decodeFromString<List<String>>(data); categories.clear(); categories.addAll(loaded) } catch (e: Exception) {} }
    fun addMealTemplate(name: String, ingredients: List<MealIngredientEntity>, servings: Double = 1.0) { val finalIngredients = ingredients.mapIndexed { idx, ing -> if (foods.none { it.id == ing.foodItemId }) { val saved = saveIngredientAsFood(ing); ing.copy(id = System.currentTimeMillis() + idx, foodItemId = saved.id) } else ing.copy(id = System.currentTimeMillis() + idx) }; meals.add(MealEntity(id = nextMealId++, name = name, ingredients = finalIngredients, servings = servings)); saveMeals() }
    fun updateMealTemplate(updatedMeal: MealEntity) { val finalIngredients = updatedMeal.ingredients.mapIndexed { _, ing -> if (foods.none { it.id == ing.foodItemId }) { val saved = saveIngredientAsFood(ing); ing.copy(foodItemId = saved.id) } else ing }; val finalMeal = updatedMeal.copy(ingredients = finalIngredients); val idx = meals.indexOfFirst { it.id == finalMeal.id }; if (idx != -1) { meals[idx] = finalMeal; saveMeals() } }
    fun deleteMealTemplate(id: Long) { meals.removeAll { it.id == id }; saveMeals() }
    fun addEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String) { val grams = if (portion != null) amount * portion.grams else amount; if (grams > 0.0) { allEntries.add(FoodEntryEntity(id = nextEntryId++, dateIso = selectedDate.toString(), mealSlot = mealSlot, amount = amount, unitLabel = portion?.name ?: food.baseUnit, grams = grams, foodItemId = food.id, name = food.name, brand = food.brand, kcalPer100g = food.kcalPer100g, proteinPer100g = food.proteinPer100g, carbsPer100g = food.carbsPer100g, sugarPer100g = food.sugarPer100g, fatPer100g = food.fatPer100g, saturatedFatPer100g = food.saturatedFatPer100g, alcoholPercent = food.alcoholPercent, baseUnit = food.baseUnit, store = food.store)); saveEntries() } }
    fun addMealEntry(meal: MealEntity, mealSlot: String, servings: Double = 1.0) { allEntries.add(FoodEntryEntity(id = nextEntryId++, dateIso = selectedDate.toString(), mealSlot = mealSlot, amount = servings, unitLabel = if (servings == 1.0) "Portion" else "Portionen", grams = (meal.ingredients.sumOf { it.grams } * (servings / meal.servings)).coerceAtLeast(0.0), foodItemId = -1, name = meal.name, kcalPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, sugarPer100g = 0.0, fatPer100g = 0.0, saturatedFatPer100g = 0.0, isMeal = true, mealIngredients = meal.ingredients.mapIndexed { idx, it -> it.copy(id = System.currentTimeMillis() + idx, grams = it.grams * (servings / meal.servings), amount = it.amount * (servings / meal.servings)) })); saveEntries() }
    fun updateEntry(updatedEntry: FoodEntryEntity) { val entryToSave = if (updatedEntry.foodItemId == -1L && !updatedEntry.isMeal) { val saved = saveEntryAsFood(updatedEntry); updatedEntry.copy(foodItemId = saved.id) } else if (updatedEntry.isMeal) { val finalIngredients = updatedEntry.mealIngredients?.map { ing -> if (foods.none { it.id == ing.foodItemId }) { val saved = saveIngredientAsFood(ing); ing.copy(foodItemId = saved.id) } else ing }; updatedEntry.copy(mealIngredients = finalIngredients) } else updatedEntry; val idx = allEntries.indexOfFirst { it.id == entryToSave.id }; if (idx != -1) { allEntries[idx] = entryToSave; saveEntries() } }
    fun deleteEntry(id: Long) { allEntries.removeAll { it.id == id }; saveEntries() }
    fun copyEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) { val newEntries = allEntries.filter { it.id in entryIds }.map { it.copy(id = nextEntryId++, dateIso = targetDate.toString()) }; allEntries.addAll(newEntries); saveEntries() }
    fun moveEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) { copyEntriesToDate(entryIds, targetDate); allEntries.removeAll { it.id in entryIds }; saveEntries() }
    fun updateSteps(steps: Int) { dailySteps[selectedDate.toString()] = steps; saveSteps() }
    fun addWeightEntry(weight: Double, dateIso: String, profile: UserProfile) { val entry = WeightEntry(dateIso, weight); val existingIdx = weightHistory.indexOfFirst { it.dateIso == dateIso }; if (existingIdx != -1) weightHistory[existingIdx] = entry else weightHistory.add(entry); saveWeightHistory(); calculateMetabolicFactorForProfile(profile) }
    fun calculateMetabolicFactorForProfile(profile: UserProfile): Double { if (weightHistory.size < 3) return profile.metabolicFactor; val sorted = weightHistory.sortedBy { it.dateIso }; val actualLossKg = sorted.first().weight - sorted.last().weight; if (actualLossKg <= 0) return profile.metabolicFactor; var predictedLossG = 0.0; var curr = LocalDate.parse(sorted.first().dateIso); val end = LocalDate.parse(sorted.last().dateIso); while (!curr.isAfter(end)) { if (dayVerifications[curr.toString()] == true) predictedLossG += calculateWeightBudgetGrams(curr.toString(), profile); curr = curr.plusDays(1) }; val predictedLossKg = predictedLossG / 1000.0; return if (predictedLossKg <= 0.1) profile.metabolicFactor else actualLossKg / predictedLossKg }
    fun verifyDay(dateIso: String, isComplete: Boolean) { dayVerifications[dateIso] = isComplete; saveVerifications() }
    private fun saveWeightHistory() { try { prefs.edit().putString("weight_history_json", json.encodeToString(weightHistory.toList())).apply() } catch (e: Exception) {} }
    private fun loadWeightHistory() { val data = prefs.getString("weight_history_json", null); if (data != null) try { weightHistory.addAll(json.decodeFromString<List<WeightEntry>>(data)) } catch (e: Exception) {} }
    private fun saveVerifications() { try { prefs.edit().putString("verifications_json", json.encodeToString(dayVerifications.toMap())).apply() } catch (e: Exception) {} }
    private fun loadVerifications() { val data = prefs.getString("verifications_json", null); if (data != null) try { dayVerifications.putAll(json.decodeFromString<Map<String, Boolean>>(data)) } catch (e: Exception) {} }
    fun saveFoods() { try { prefs.edit().putString("foods_json", json.encodeToString(foods.toList())).apply() } catch (e: Exception) {} }
    private fun loadFoods() { val data = prefs.getString("foods_json", null); if (data != null) try { val loaded = json.decodeFromString<List<FoodItemEntity>>(data); foods.clear(); foods.addAll(loaded); nextFoodId = (foods.maxOfOrNull { it.id } ?: 0L) + 1; nextPortionId = (foods.flatMap { it.portions }.maxOfOrNull { it.id } ?: 0L) + 1 } catch (e: Exception) {} }
    fun saveMeals() { try { prefs.edit().putString("meals_json", json.encodeToString(meals.toList())).apply() } catch (e: Exception) {} }
    private fun loadMeals() { val data = prefs.getString("meals_json", null); if (data != null) try { val loaded = json.decodeFromString<List<MealEntity>>(data); meals.clear(); meals.addAll(loaded); nextMealId = (meals.maxOfOrNull { it.id } ?: 0L) + 1 } catch (e: Exception) {} }
    fun saveEntries() { try { prefs.edit().putString("entries_json", json.encodeToString(allEntries.toList())).apply() } catch (e: Exception) {} }
    fun loadEntries() { val data = prefs.getString("entries_json", null); if (data != null) try { val loaded = json.decodeFromString<List<FoodEntryEntity>>(data); allEntries.clear(); allEntries.addAll(loaded); nextEntryId = (allEntries.maxOfOrNull { it.id } ?: 0L) + 1 } catch (e: Exception) {} }
    private fun saveSteps() { try { prefs.edit().putString("steps_json", json.encodeToString(dailySteps.toMap())).apply() } catch (e: Exception) {} }
    private fun loadSteps() { val data = prefs.getString("steps_json", null); if (data != null) try { val loaded = json.decodeFromString<Map<String, Int>>(data); dailySteps.putAll(loaded) } catch (e: Exception) {} }
    fun getBackupJson(): String = json.encodeToString(BackupData(foods.toList(), meals.toList(), categories.toList(), allEntries.toList()))
    fun getCatalogJson(): String = json.encodeToString(BackupData(foods.toList(), meals.toList(), categories.toList(), emptyList()))
    fun getRecipeJson(meal: MealEntity): String = json.encodeToString(RecipeData(meal, meal.ingredients.mapNotNull { ing -> foods.find { it.id == ing.foodItemId } }))
    fun importBackup(s: String): Boolean = try { val b = json.decodeFromString<BackupData>(s); b.categories.forEach { if (it.isNotBlank() && !categories.contains(it)) categories.add(it) }; b.foods.forEach { imp -> if (foods.none { it.name == imp.name && it.barcode == imp.barcode }) foods.add(imp) }; b.meals.forEach { m -> if (meals.none { it.name == m.name }) meals.add(m) }; b.entries.forEach { e -> if (allEntries.none { it.dateIso == e.dateIso && it.name == e.name && it.mealSlot == e.mealSlot }) allEntries.add(e) }; recalculateIds(); true } catch (e: Exception) { false }
    fun startRecipeImport(s: String): Boolean = try { pendingRecipeImport = json.decodeFromString<RecipeData>(s); true } catch (e: Exception) { false }
    fun resolveRecipeImport(supplement: Boolean) { val r = pendingRecipeImport ?: return; r.relatedFoods.forEach { imp -> if (foods.none { it.name == imp.name && it.barcode == imp.barcode }) foods.add(imp) else if (supplement && foods.find { it.name == imp.name }?.category == null) foods[foods.indexOfFirst { it.name == imp.name }] = foods.find { it.name == imp.name }!!.copy(category = imp.category) }; if (meals.none { it.name == r.meal.name }) meals.add(r.meal) else if (supplement) meals[meals.indexOfFirst { it.name == r.meal.name }] = r.meal; recalculateIds(); pendingRecipeImport = null }
    fun deleteInboxMessage(messageId: String) { val uid = firebaseManager.currentUser.value?.uid ?: return; viewModelScope.launch { try { firestoreRepository.deleteInboxMessage(uid, messageId) } catch (e: Exception) { Log.e("Firestore", "Error deleting message", e) } } }
    fun markMessageAsRead(messageId: String) { val uid = firebaseManager.currentUser.value?.uid ?: return; viewModelScope.launch { try { firestoreRepository.markMessageAsRead(uid, messageId) } catch (e: Exception) { Log.e("Firestore", "Error marking as read", e) } } }
    fun sendRecipeToUser(targetUid: String, meal: MealEntity) { val currentUser = firebaseManager.currentUser.value ?: return; viewModelScope.launch { try { val name = firebaseManager.getUserName(currentUser.uid); val jsonPayload = getRecipeJson(meal); val message = InboxMessage(fromUid = currentUser.uid, fromName = name, type = MessageType.RECIPE, payloadJson = jsonPayload); firestoreRepository.sendInboxMessage(targetUid, message) } catch (e: Exception) { Log.e("Firestore", "Error sending recipe", e) } } }
    fun sendFoodToUser(targetUid: String, food: FoodItemEntity) { val currentUser = firebaseManager.currentUser.value ?: return; viewModelScope.launch { try { val name = firebaseManager.getUserName(currentUser.uid); val jsonPayload = json.encodeToString(food); val message = InboxMessage(fromUid = currentUser.uid, fromName = name, type = MessageType.FOOD, payloadJson = jsonPayload); firestoreRepository.sendInboxMessage(targetUid, message) } catch (e: Exception) { Log.e("Firestore", "Error sending food", e) } } }
    fun recalculateIds() {
        var fId = 1L; var pId = 1L; var pkgId = 1L; var ingId = 1L
        val foodMap = mutableMapOf<Long, Long>()
        val upFoods = foods.map { f -> val nid = fId++; foodMap[f.id] = nid; f.copy(id = nid, portions = f.portions.map { it.copy(id = pId++) }, packages = f.packages.map { it.copy(id = pkgId++) }) }
        val finalFoods = upFoods.map { f -> if (f.parentId != null) f.copy(parentId = foodMap[f.parentId]) else f }
        val upMeals = meals.map { m -> m.copy(ingredients = m.ingredients.filter { foodMap.containsKey(it.foodItemId) }.map { it.copy(id = ingId++, foodItemId = foodMap[it.foodItemId]!!) }) }
        val upEntries = allEntries.map { e -> e.copy(foodItemId = if (e.foodItemId != -1L) foodMap[e.foodItemId] ?: e.foodItemId else -1L, mealIngredients = e.mealIngredients?.map { it.copy(id = ingId++, foodItemId = foodMap[it.foodItemId] ?: it.foodItemId) }) }
        foods.clear(); foods.addAll(finalFoods); meals.clear(); meals.addAll(upMeals); allEntries.clear(); allEntries.addAll(upEntries)
        nextFoodId = fId; nextPortionId = pId; nextMealId = (meals.maxOfOrNull { it.id } ?: 0L) + 1; nextEntryId = (allEntries.maxOfOrNull { it.id } ?: 0L) + 1; saveFoods(); saveMeals(); saveEntries()
    }
}
