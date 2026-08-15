package com.nick.nutritiontracker.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nick.nutritiontracker.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nutrition_tracker", Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val healthConnectManager = HealthConnectManager(application)
    val firebaseManager = FirebaseManager()
    private val firestoreRepository = FirestoreRepository()
    private val profileRepository = ProfileRepository(application)
    private var syncJob: Job? = null

    // --- State Properties ---
    var selectedDate: LocalDate by mutableStateOf(LocalDate.now())
        private set

    val foods = mutableStateListOf<FoodItemEntity>()
    val meals = mutableStateListOf<MealEntity>()
    val allEntries = mutableStateListOf<FoodEntryEntity>()
    val categories = mutableStateListOf<String>()
    val plannedEntries = mutableStateListOf<FoodEntryEntity>()
    val shoppingList = mutableStateListOf<ShoppingItem>()
    val weightHistory = mutableStateListOf<WeightEntry>()
    val dayVerifications = mutableStateMapOf<String, Boolean>()
    val inboxMessages = mutableStateListOf<InboxMessage>()
    val unreadInboxCount by derivedStateOf { inboxMessages.count { !it.isRead } }
    val householdMembers = mutableStateListOf<Map<String, String>>()

    val dailySteps = mutableStateMapOf<String, Int>()
    val dailyTotalCalories = mutableStateMapOf<String, Double>()
    val dailyExerciseSessions = mutableStateMapOf<String, List<ExerciseSessionInfo>>()

    private var userProfile by mutableStateOf(UserProfile())
    private var isRepairing = false

    // --- Mandatory Properties for NutritionApp.kt ---
    val todayEntries: List<FoodEntryEntity>
        get() = allEntries.filter { it.dateIso == selectedDate.toString() }

    val todayTotalKcal: Double
        get() = todayEntries.sumOf { it.kcal }

    val todayTotalProtein: Double
        get() = todayEntries.sumOf { it.protein }

    val todayTotalComplexCarbs: Double
        get() = todayEntries.sumOf { it.complexCarbs }

    val todayTotalSugar: Double
        get() = todayEntries.sumOf { it.sugar }

    val todayTotalUnsaturatedFat: Double
        get() = todayEntries.sumOf { it.unsaturatedFat }

    val todayTotalSaturatedFat: Double
        get() = todayEntries.sumOf { it.saturatedFat }

    val todaySteps: Int
        get() = dailySteps[selectedDate.toString()] ?: 0

    val todayStepKcal: Double
        get() = calculateStepKcal(selectedDate.toString(), userProfile)

    val todayActivityKcal: Double
        get() = getActivityKcal(selectedDate.toString(), userProfile)

    // UI States
    var foodSearchQuery by mutableStateOf("")
    var selectedFoodCategory by mutableStateOf<String?>(null)
    var pendingRecipeImport by mutableStateOf<RecipeData?>(null)
    var isShoppingListAggregated by mutableStateOf(value = false)
    var showPantryInShoppingList by mutableStateOf(value = false)
    var shoppingListSortByCategory by mutableStateOf(prefs.getBoolean("shopping_sort_category", true))

    var forceOnboardingOnStart by mutableStateOf(prefs.getBoolean("force_onboarding", false))
    var geminiApiKey by mutableStateOf(prefs.getString("gemini_api_key", null) ?: "")
    var biometricEnabled by mutableStateOf(prefs.getBoolean("biometric_enabled", false))
    var isAppUnlocked by mutableStateOf(false)
    val availableAiModels = mutableStateListOf<AiModelStatus>()

    private var nextFoodId = 1L
    private var nextEntryId = 1L
    private var nextMealId = 1L

    var isAnalyzingImage by mutableStateOf(false)
    var aiEstimationResult by mutableStateOf<AiEstimationResult?>(null)
    var aiErrorMessage by mutableStateOf<String?>(null)
    var selectedAiModel by mutableStateOf(prefs.getString("selected_ai_model", "gemini-3.6-flash") ?: "gemini-3.6-flash")

    init {
        loadLocalState()
        if (foods.isEmpty()) createDefaultFoods()
        if (categories.isEmpty()) createDefaultCategories()
        syncActivityForSelectedDate()
        setupFirebaseSync()

        viewModelScope.launch {
            profileRepository.userProfileFlow.collect { profile ->
                userProfile = profile
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        syncActivityForSelectedDate()
    }

    fun unlockApp() {
        isAppUnlocked = true
    }

    fun updateBiometricEnabled(enabled: Boolean) {
        biometricEnabled = enabled
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
    }

    fun updateSelectedAiModel(model: String) {
        selectedAiModel = model
        prefs.edit().putString("selected_ai_model", model).apply()
    }

    fun updateGeminiApiKey(key: String) {
        geminiApiKey = key
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun setForceOnboarding(force: Boolean) {
        forceOnboardingOnStart = force
        prefs.edit().putBoolean("force_onboarding", force).apply()
    }

    fun updateShoppingListSort(sort: Boolean) {
        shoppingListSortByCategory = sort
        prefs.edit().putBoolean("shopping_sort_category", sort).apply()
    }

    fun syncActivityForSelectedDate() {
        viewModelScope.launch {
            if (healthConnectManager.isAvailable() && healthConnectManager.hasAllPermissions()) {
                val data = healthConnectManager.syncActivityForSelectedDate(selectedDate)
                updateActivity(data.steps, data.totalKcal, data.sessions)
            }
        }
    }

    // --- Sync Logic ---
    private fun setupFirebaseSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            firebaseManager.currentUser.collectLatest { user ->
                if (user != null) {
                    coroutineScope {
                        launch {
                            firestoreRepository.getPersonalFoods(user.uid).collect { cloudFoods ->
                                syncList(foods, cloudFoods) { it.id }
                                updateNextIds()
                                saveFoods()
                            }
                        }
                        launch {
                            firestoreRepository.getPersonalMeals(user.uid).collect { cloudMeals ->
                                syncList(meals, cloudMeals) { it.id }
                                updateNextIds()
                                saveMeals()
                            }
                        }
                        launch {
                            firestoreRepository.getPersonalEntries(user.uid).collect { cloudEntries ->
                                syncList(allEntries, cloudEntries) { it.id }
                                updateNextIds()
                                saveEntries()
                            }
                        }
                        launch {
                            firestoreRepository.getWeightHistory(user.uid).collect { cloudWeight ->
                                weightHistory.clear()
                                weightHistory.addAll(cloudWeight.sortedByDescending { it.dateIso })
                                saveWeightHistory()
                            }
                        }
                        launch {
                            firestoreRepository.getInboxMessages(user.uid).collect { cloudMessages ->
                                inboxMessages.clear()
                                inboxMessages.addAll(cloudMessages)
                            }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            firebaseManager.household.collectLatest { household ->
                if (household != null) {
                    coroutineScope {
                        launch {
                            firestoreRepository.getPlannedEntries(household.id).collect { cloudPlanned ->
                                syncList(plannedEntries, cloudPlanned) { it.id }
                                updateNextIds()
                                savePlannedEntries()
                            }
                        }
                        launch {
                            firestoreRepository.getShoppingList(household.id).collect { cloudShopping ->
                                syncList(shoppingList, cloudShopping) { it.id }
                                saveShoppingList()
                            }
                        }
                        launch {
                            val members = firestoreRepository.getHouseholdMembers(household.members)
                            householdMembers.clear()
                            householdMembers.addAll(members)
                        }
                    }
                }
            }
        }
    }

    private fun <T, K> syncList(localList: MutableList<T>, cloudList: List<T>, idSelector: (T) -> K) {
        if (cloudList.isEmpty()) {
            if (localList.isNotEmpty()) localList.clear()
            return
        }

        // Use a set of IDs for efficient removal check
        val cloudIds = cloudList.mapTo(HashSet()) { idSelector(it) }
        localList.removeAll { idSelector(it) !in cloudIds }
        cloudIds.clear() // Clear to help GC

        // Build a temporary index map for O(1) lookups during sync
        val localIndexMap = mutableMapOf<K, Int>()
        localList.forEachIndexed { index, item ->
            localIndexMap[idSelector(item)] = index
        }
        
        cloudList.forEach { cloudItem ->
            val id = idSelector(cloudItem)
            val index = localIndexMap[id]
            if (index != null) {
                if (localList[index] != cloudItem) {
                    localList[index] = cloudItem
                }
            } else {
                localList.add(cloudItem)
            }
        }
        localIndexMap.clear() // Clear to help GC
    }

    private suspend fun repairMealIngredients(uid: String) = withContext(Dispatchers.IO) {
        if (foods.isEmpty()) return@withContext
        val foodById = foods.associateBy { it.id }
        val foodByName = foods.associateBy { "${it.name}|${it.brand}" }

        val currentMeals = meals.toList()
        for (meal in currentMeals) {
            var changed = false
            val repairedIngredients = meal.ingredients.map { ing ->
                if (ing.kcalPer100g == 0.0) {
                    val food = foodById[ing.foodItemId] ?: foodByName["${ing.name}|${ing.brand}"]
                    if (food != null) {
                        val repaired = ing.copy(
                            foodItemId = food.id,
                            kcalPer100g = food.kcalPer100g,
                            proteinPer100g = food.proteinPer100g,
                            carbsPer100g = food.carbsPer100g,
                            sugarPer100g = food.sugarPer100g,
                            fatPer100g = food.fatPer100g,
                            saturatedFatPer100g = food.saturatedFatPer100g,
                            baseUnit = food.baseUnit,
                        )
                        if (repaired != ing) {
                            changed = true
                            repaired
                        } else ing
                    } else ing
                } else ing
            }
            if (changed) {
                val updatedMeal = meal.copy(ingredients = repairedIngredients, lastModified = System.currentTimeMillis())
                withContext(Dispatchers.Main) {
                    val idx = meals.indexOfFirst { it.id == meal.id }
                    if (idx != -1) meals[idx] = updatedMeal
                }
                firestoreRepository.savePersonalMeal(uid, updatedMeal)
                delay(100) // Sequentialize and throttle writes
            }
            yield()
        }
    }

    private suspend fun repairPlannedEntryIngredients(householdId: String) = withContext(Dispatchers.IO) {
        if (foods.isEmpty()) return@withContext
        val foodById = foods.associateBy { it.id }
        val foodByName = foods.associateBy { "${it.name}|${it.brand}" }

        val currentPlanned = plannedEntries.toList()
        for (entry in currentPlanned) {
            val ingredients = entry.mealIngredients
            if (entry.isMeal && (ingredients != null)) {
                var changed = false
                val repairedIngredients = ingredients.map { ing ->
                    if (ing.kcalPer100g == 0.0) {
                        val food = foodById[ing.foodItemId] ?: foodByName["${ing.name}|${ing.brand}"]
                        if (food != null) {
                            val repaired = ing.copy(
                                foodItemId = food.id,
                                kcalPer100g = food.kcalPer100g,
                                proteinPer100g = food.proteinPer100g,
                                carbsPer100g = food.carbsPer100g,
                                sugarPer100g = food.sugarPer100g,
                                fatPer100g = food.fatPer100g,
                                saturatedFatPer100g = food.saturatedFatPer100g,
                                baseUnit = food.baseUnit
                            )
                            if (repaired != ing) {
                                changed = true
                                repaired
                            } else ing
                        } else ing
                    } else ing
                }
                if (changed) {
                    val updatedEntry = entry.copy(mealIngredients = repairedIngredients)
                    withContext(Dispatchers.Main) {
                        val idx = plannedEntries.indexOfFirst { it.id == entry.id }
                        if (idx != -1) plannedEntries[idx] = updatedEntry
                    }
                    firestoreRepository.addPlannedEntry(householdId, updatedEntry)
                    delay(100) // Sequentialize and throttle writes
                }
            }
            yield()
        }
    }

    fun triggerAutoRepair() {
        if (isRepairing) return
        val user = firebaseManager.currentUser.value ?: return
        val householdId = firebaseManager.household.value?.id
        viewModelScope.launch {
            isRepairing = true
            try {
                repairMealIngredients(user.uid)
                if (householdId != null) repairPlannedEntryIngredients(householdId)
                saveMeals()
                savePlannedEntries()
            } catch (e: Exception) {
                Log.e("NutritionViewModel", "Auto repair failed", e)
            } finally {
                isRepairing = false
            }
        }
    }

    fun findFoodByBarcode(barcode: String): FoodItemEntity? = foods.find { it.barcode == barcode }

    // --- Calorie Calculation ---
    fun calculateStepKcal(dateIso: String, profile: UserProfile): Double {
        val steps = dailySteps[dateIso] ?: 0
        val heightM = profile.heightCm / 100.0
        return 0.55 * profile.weightKg * steps * 0.415 * heightM / 1000.0
    }

    fun getActivityKcal(dateIso: String, profile: UserProfile): Double {
        val stepKcal = calculateStepKcal(dateIso, profile)
        val sessions = dailyExerciseSessions[dateIso] ?: emptyList()
        val workoutKcal = sessions.sumOf { it.calories ?: 0.0 }
        return stepKcal + workoutKcal
    }

    fun calculateWeightBudgetGrams(dateIso: String, userProfile: UserProfile): Double {
        val dateEntries = allEntries.filter { it.dateIso == dateIso }
        if (dateEntries.isEmpty()) return 0.0
        val intake = dateEntries.sumOf { it.kcal }
        val activityKcal = getActivityKcal(dateIso, userProfile)
        val actualDeficit = (userProfile.bmr + activityKcal) - intake
        val targetDeficit = userProfile.goalIntensity.toDouble()
        val deficitToUse = if (userProfile.goal == UserGoal.LOSE_WEIGHT) minOf(targetDeficit, actualDeficit) else actualDeficit
        return (deficitToUse / 7000.0) * 1000.0
    }

    fun getDayStatusColor(dateIso: String, profile: UserProfile): Int {
        val entries = allEntries.filter { it.dateIso == dateIso }
        if (entries.isEmpty()) return 0xFF9E9E9E.toInt()
        val intake = entries.sumOf { it.kcal }
        val activityKcal = getActivityKcal(dateIso, profile)
        val bmrLimit = profile.bmr + activityKcal
        val deficitLimit = (profile.bmr - profile.goalIntensity) + activityKcal
        return when {
            intake <= deficitLimit -> 0xFF2196F3.toInt()
            intake <= bmrLimit -> 0xFF4CAF50.toInt()
            else -> 0xFFFFC107.toInt()
        }
    }

    // --- AI Integration ---
    fun analyzeMealImage(bitmap: Bitmap, isUserPremium: Boolean) {
        if (!isUserPremium) {
            aiErrorMessage = "Premium erforderlich."
            return
        }
        isAnalyzingImage = true
        aiErrorMessage = null
        aiEstimationResult = null
        viewModelScope.launch {
            try {
                val service = GeminiService()
                val result = service.estimateNutrition(bitmap, selectedAiModel)
                aiEstimationResult = result
            } catch (e: Exception) {
                Log.e("NutritionViewModel", "AI analysis failed", e)
                aiErrorMessage = "Fehler: ${e.localizedMessage}"
            } finally {
                isAnalyzingImage = false
            }
        }
    }

    var isAnalyzingGenericFood by mutableStateOf(false)
    var aiGenericFoodError by mutableStateOf<String?>(null)

    suspend fun estimateGenericMacros(name: String, isBrandSearch: Boolean = false): AiGenericFoodResult? {
        isAnalyzingGenericFood = true
        aiGenericFoodError = null
        return try {
            val service = GeminiService()
            val result = service.estimateGenericFood(name, categories.toList(), isBrandSearch, selectedAiModel)
            if (result == null) {
                aiGenericFoodError = "Keine Daten gefunden."
            }
            result
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "AI generic macros failed", e)
            aiGenericFoodError = "Fehler: ${e.localizedMessage}"
            null
        } finally {
            isAnalyzingGenericFood = false
        }
    }

    fun probeAiModels() {
        viewModelScope.launch {
            availableAiModels.clear()
            val service = GeminiService()
            val models = listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-3.6-flash")
            models.forEach { model ->
                availableAiModels.add(service.testModelAvailability(model))
            }
        }
    }

    // --- Data Management ---
    fun addFood(
        name: String, kcal: Double, protein: Double, carbs: Double, sugar: Double,
        fat: Double, saturatedFat: Double, alcoholPercent: Double, baseUnit: String,
        portions: List<FoodPortionEntity>, packages: List<FoodPackageEntity>,
        barcode: String? = null, brand: String? = null, category: String? = null,
        isGeneric: Boolean = false, parentId: Long? = null, store: String? = null,
        isPantryItem: Boolean = false
    ): FoodItemEntity {
        val user = firebaseManager.currentUser.value
        val newFood = FoodItemEntity(
            id = nextFoodId++,
            name = name,
            kcalPer100g = kcal,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            sugarPer100g = sugar,
            fatPer100g = fat,
            saturatedFatPer100g = saturatedFat,
            alcoholPercent = alcoholPercent,
            baseUnit = baseUnit,
            portions = portions,
            packages = packages,
            barcode = barcode,
            brand = brand,
            category = category,
            isGeneric = isGeneric,
            parentId = parentId,
            store = store,
            isPantryItem = isPantryItem,
            lastModified = System.currentTimeMillis()
        )
        foods.add(newFood)
        saveFoods()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalFood(user.uid, newFood) }
        }
        return newFood
    }

    fun updateFood(updatedFood: FoodItemEntity) {
        val user = firebaseManager.currentUser.value
        val index = foods.indexOfFirst { it.id == updatedFood.id }
        if (index != -1) {
            val updated = updatedFood.copy(lastModified = System.currentTimeMillis())
            foods[index] = updated
            saveFoods()
            if (user != null) {
                viewModelScope.launch { firestoreRepository.savePersonalFood(user.uid, updated) }
            }
        }
    }

    fun deleteFood(id: Long) {
        val user = firebaseManager.currentUser.value
        foods.removeAll { it.id == id }
        saveFoods()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deletePersonalFood(user.uid, id) }
        }
    }

    fun mergeFoods(targetParentId: Long, childIds: List<Long>) {
        val user = firebaseManager.currentUser.value
        childIds.forEach { childId ->
            allEntries.forEachIndexed { index, entry ->
                if (entry.foodItemId == childId) {
                    val updated = entry.copy(foodItemId = targetParentId)
                    allEntries[index] = updated
                    if (user != null) {
                        viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, updated) }
                    }
                }
            }
            deleteFood(childId)
        }
        saveEntries()
    }

    fun promoteToGeneric(foodId: Long) {
        val food = foods.find { it.id == foodId } ?: return
        updateFood(food.copy(isGeneric = true))
    }

    fun addMealTemplate(name: String, ingredients: List<MealIngredientEntity>, servings: Double, tags: List<String>, imageUrl: String?) {
        val user = firebaseManager.currentUser.value
        val newMeal = MealEntity(
            id = nextMealId++,
            name = name,
            ingredients = ingredients,
            servings = servings,
            tags = tags,
            imageUrl = imageUrl,
            lastModified = System.currentTimeMillis()
        )
        meals.add(newMeal)
        saveMeals()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalMeal(user.uid, newMeal) }
        }
    }

    fun updateMealTemplate(updatedMeal: MealEntity) {
        val user = firebaseManager.currentUser.value
        val index = meals.indexOfFirst { it.id == updatedMeal.id }
        if (index != -1) {
            val updated = updatedMeal.copy(lastModified = System.currentTimeMillis())
            meals[index] = updated
            saveMeals()
            if (user != null) {
                viewModelScope.launch { firestoreRepository.savePersonalMeal(user.uid, updated) }
            }
        }
    }

    fun deleteMealTemplate(id: Long) {
        val user = firebaseManager.currentUser.value
        meals.removeAll { it.id == id }
        saveMeals()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deletePersonalMeal(user.uid, id) }
        }
    }

    fun addEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String, pkg: FoodPackageEntity? = null) {
        val grams = when {
            pkg != null -> amount * pkg.quantity
            portion != null -> amount * portion.grams
            else -> amount
        }
        val unitLabel = pkg?.name ?: portion?.name ?: food.baseUnit

        val entry = FoodEntryEntity(
            dateIso = selectedDate.toString(),
            mealSlot = mealSlot,
            amount = amount,
            unitLabel = unitLabel,
            grams = grams,
            foodItemId = food.id,
            name = food.name,
            brand = food.brand,
            kcalPer100g = food.kcalPer100g,
            proteinPer100g = food.proteinPer100g,
            carbsPer100g = food.carbsPer100g,
            sugarPer100g = food.sugarPer100g,
            fatPer100g = food.fatPer100g,
            saturatedFatPer100g = food.saturatedFatPer100g,
            alcoholPercent = food.alcoholPercent,
            baseUnit = food.baseUnit,
            store = food.store
        )
        addEntry(entry)
    }

    fun addEntry(entry: FoodEntryEntity) {
        val user = firebaseManager.currentUser.value
        // Highly unique ID using current time nanos + large random to prevent collisions in fast loops
        val newEntry = entry.copy(id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000))
        allEntries.add(newEntry)
        saveEntries()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, newEntry) }
        }
    }

    fun addMealEntry(meal: MealEntity, mealSlot: String, servings: Double = 1.0) {
        val ratio = servings / meal.servings
        val adjustedIngredients = meal.ingredients.map { ing ->
            ing.copy(
                id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
                amount = ing.amount * ratio,
                grams = ing.grams * ratio
            )
        }
        val entry = FoodEntryEntity(
            id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
            dateIso = selectedDate.toString(),
            mealSlot = mealSlot,
            name = meal.name,
            isMeal = true,
            mealIngredients = adjustedIngredients,
            amount = servings,
            unitLabel = "Portion(en)",
            imageUrl = meal.imageUrl,
            tags = meal.tags
        )
        addEntry(entry)
    }

    fun updateEntry(updatedEntry: FoodEntryEntity) {
        val user = firebaseManager.currentUser.value
        val index = allEntries.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            allEntries[index] = updatedEntry
            saveEntries()
            if (user != null) {
                viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, updatedEntry) }
            }
        }
    }

    fun deleteEntry(id: Long) {
        val user = firebaseManager.currentUser.value
        allEntries.removeAll { it.id == id }
        saveEntries()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deletePersonalEntry(user.uid, id) }
        }
    }

    fun copyEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) {
        val entriesToCopy = allEntries.filter { it.id in entryIds }
        entriesToCopy.forEach { entry ->
            addEntry(entry.copy(id = 0, dateIso = targetDate.toString()))
        }
    }

    fun moveEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) {
        val user = firebaseManager.currentUser.value
        allEntries.forEachIndexed { index, entry ->
            if (entry.id in entryIds) {
                val updated = entry.copy(dateIso = targetDate.toString())
                allEntries[index] = updated
                if (user != null) {
                    viewModelScope.launch { firestoreRepository.savePersonalEntry(user.uid, updated) }
                }
            }
        }
        saveEntries()
    }

    fun verifyDay(dateIso: String, isComplete: Boolean) {
        dayVerifications[dateIso] = isComplete
        saveVerifications()
    }

    // --- Planner ---
    private fun getDayLabel(date: LocalDate): String {
        return when (date) {
            LocalDate.now() -> "Heute"
            LocalDate.now().plusDays(1) -> "Morgen"
            else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.GERMAN)
        }
    }

    fun addPlannedEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String, date: LocalDate, pkg: FoodPackageEntity? = null) {
        val grams = when {
            pkg != null -> amount * pkg.quantity
            portion != null -> amount * portion.grams
            else -> amount
        }
        val unitLabel = pkg?.name ?: portion?.name ?: food.baseUnit
        val dayLabel = getDayLabel(date)

        val entry = FoodEntryEntity(
            id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
            dateIso = date.toString(),
            mealSlot = mealSlot,
            amount = amount,
            unitLabel = unitLabel,
            grams = grams,
            foodItemId = food.id,
            name = food.name,
            brand = food.brand,
            kcalPer100g = food.kcalPer100g,
            proteinPer100g = food.proteinPer100g,
            carbsPer100g = food.carbsPer100g,
            sugarPer100g = food.sugarPer100g,
            fatPer100g = food.fatPer100g,
            saturatedFatPer100g = food.saturatedFatPer100g,
            alcoholPercent = food.alcoholPercent,
            baseUnit = food.baseUnit,
            store = food.store,
            isPlanned = true
        )
        addPlannedEntry(entry)
        internalAddToShoppingList(food, amount, unitLabel, pkg, sourceName = "Einzelartikel ($dayLabel)")
    }

    fun addPlannedEntry(entry: FoodEntryEntity) {
        val householdId = firebaseManager.household.value?.id
        // Highly unique ID using current time nanos + large random to prevent collisions in fast loops
        val newEntry = if (entry.id == 0L) {
            entry.copy(id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000))
        } else entry
        
        plannedEntries.add(newEntry)
        savePlannedEntries()
        if (householdId != null) {
            viewModelScope.launch { firestoreRepository.addPlannedEntry(householdId, newEntry) }
        }
    }

    fun addPlannedMeal(meal: MealEntity, mealSlot: String, date: LocalDate, servings: Double = 1.0) {
        val householdId = firebaseManager.household.value?.id
        val ratio = servings / meal.servings
        val dayLabel = getDayLabel(date)
        val source = "${meal.name} ($dayLabel)"

        val adjustedIngredients = meal.ingredients.map { ing ->
            ing.copy(
                id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
                amount = ing.amount * ratio,
                grams = ing.grams * ratio
            )
        }
        val entry = FoodEntryEntity(
            id = (System.currentTimeMillis() * 1000) + Random.nextLong(1000000),
            dateIso = date.toString(),
            mealSlot = mealSlot,
            name = meal.name,
            isMeal = true,
            mealIngredients = adjustedIngredients,
            amount = servings,
            unitLabel = "Portion(en)",
            imageUrl = meal.imageUrl,
            tags = meal.tags,
            isPlanned = true
        )
        addPlannedEntry(entry)

        meal.ingredients.forEach { ing ->
            val food = foods.find { it.id == ing.foodItemId }
            // Nur hinzufügen, wenn es kein Vorratsartikel ist
            if (food?.isPantryItem != true) {
                internalAddToShoppingList(food ?: FoodItemEntity(name = ing.name), ing.amount * ratio, ing.unitLabel, sourceName = source)
            }
        }
    }

    fun updatePlannedEntry(updatedEntry: FoodEntryEntity) {
        val householdId = firebaseManager.household.value?.id
        val index = plannedEntries.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            plannedEntries[index] = updatedEntry
            savePlannedEntries()
            if (householdId != null) {
                viewModelScope.launch { firestoreRepository.addPlannedEntry(householdId, updatedEntry) }
            }
        }
    }

    fun deletePlannedEntry(id: Long) {
        val householdId = firebaseManager.household.value?.id
        plannedEntries.removeAll { it.id == id }
        savePlannedEntries()
        if (householdId != null) {
            viewModelScope.launch { firestoreRepository.deletePlannedEntry(householdId, id) }
        }
    }

    // --- Shopping List ---
    fun toggleShoppingItem(item: ShoppingItem) {
        val householdId = firebaseManager.household.value?.id
        val index = shoppingList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            val updated = item.copy(isChecked = !item.isChecked)
            shoppingList[index] = updated
            saveShoppingList()
            if (householdId != null) {
                viewModelScope.launch { firestoreRepository.updateShoppingItem(householdId, updated) }
            }
        }
    }

    fun deleteShoppingItem(id: String) {
        val householdId = firebaseManager.household.value?.id
        shoppingList.removeAll { it.id == id }
        saveShoppingList()
        if (householdId != null) {
            viewModelScope.launch { firestoreRepository.deleteShoppingItem(householdId, id) }
        }
    }

    private fun getGeneralName(food: FoodItemEntity): String {
        return food.name.split(",").first().trim()
    }

    private fun internalAddToShoppingList(food: FoodItemEntity, amount: Double, unit: String, pkg: FoodPackageEntity? = null, sourceName: String? = null) {
        val householdId = firebaseManager.household.value?.id ?: return
        val name = getGeneralName(food)
        
        val addedWeight = when {
            pkg != null -> amount * pkg.quantity
            unit == food.baseUnit -> amount
            else -> 0.0 
        }

        // Wir suchen NUR dann nach einem existierenden Item, wenn Name, Einheit UND Quelle 
        // exakt gleich sind. Das verhindert, dass Zutaten aus Mahlzeiten in "Manuell" verschwinden.
        val existingIndex = shoppingList.indexOfFirst { 
            it.name.equals(name, ignoreCase = true) && 
            it.unit == unit && 
            !it.isChecked && 
            it.isPantryItem == food.isPantryItem &&
            it.sourceName == (sourceName ?: "Manuell hinzugefügt")
        }
        
        if (existingIndex != -1) {
            val existing = shoppingList[existingIndex]
            val updated = existing.copy(
                amount = existing.amount + amount,
                weightGrams = existing.weightGrams + addedWeight
            )
            shoppingList[existingIndex] = updated
            viewModelScope.launch { firestoreRepository.updateShoppingItem(householdId, updated) }
        } else {
            val newItem = ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = name,
                amount = amount,
                unit = unit,
                category = food.category,
                householdId = householdId,
                isAutoGenerated = true,
                isPantryItem = food.isPantryItem,
                baseUnit = food.baseUnit,
                weightGrams = addedWeight,
                sourceName = sourceName ?: "Manuell hinzugefügt"
            )
            shoppingList.add(newItem)
            viewModelScope.launch { firestoreRepository.addShoppingItem(householdId, newItem) }
        }
        saveShoppingList()
    }

    fun addShoppingItem(name: String, amount: Double, unit: String, category: String?, isPremium: Boolean) {
        val householdId = firebaseManager.household.value?.id ?: ""
        val newItem = ShoppingItem(
            id = UUID.randomUUID().toString(),
            name = name,
            amount = amount,
            unit = unit,
            category = category,
            householdId = householdId,
            sourceName = "Manuell hinzugefügt"
        )
        shoppingList.add(newItem)
        saveShoppingList()
        if (householdId.isNotEmpty()) {
            viewModelScope.launch { firestoreRepository.addShoppingItem(householdId, newItem) }
        }
    }

    fun updateShoppingItem(updatedItem: ShoppingItem) {
        val householdId = firebaseManager.household.value?.id
        val index = shoppingList.indexOfFirst { it.id == updatedItem.id }
        if (index != -1) {
            shoppingList[index] = updatedItem
            saveShoppingList()
            if (householdId != null) {
                viewModelScope.launch { firestoreRepository.updateShoppingItem(householdId, updatedItem) }
            }
        }
    }

    suspend fun suggestCategory(name: String, isPremium: Boolean): String? {
        if (!isPremium) return foods.find { it.name.equals(name, ignoreCase = true) }?.category
        return try {
            GeminiService().categorizeItem(name, categories)
        } catch (e: Exception) {
            null
        }
    }

    // --- Weight Management ---
    fun addWeightEntry(weight: Double, dateIso: String, profile: UserProfile) {
        val user = firebaseManager.currentUser.value
        val newEntry = WeightEntry(dateIso, weight)
        weightHistory.removeAll { it.dateIso == dateIso }
        weightHistory.add(newEntry)
        weightHistory.sortByDescending { it.dateIso }
        saveWeightHistory()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.saveWeightEntry(user.uid, newEntry) }
        }
    }

    fun deleteWeightEntry(dateIso: String) {
        val user = firebaseManager.currentUser.value
        weightHistory.removeAll { it.dateIso == dateIso }
        saveWeightHistory()
        if (user != null) {
            viewModelScope.launch { firestoreRepository.deleteWeightEntry(user.uid, dateIso) }
        }
    }

    fun calculateMetabolicFactorForProfile(profile: UserProfile): Double {
        if (weightHistory.size < 2) return profile.metabolicFactor
        
        val sorted = weightHistory.sortedBy { it.dateIso }
        val latest = sorted.last()
        val first = sorted.first()
        
        val days = ChronoUnit.DAYS.between(LocalDate.parse(first.dateIso), LocalDate.parse(latest.dateIso))
        if (days < 7) return profile.metabolicFactor
        
        val actualLossKg = first.weight - latest.weight
        val actualDeficit = actualLossKg * 7000.0
        
        var totalTheoreticalDeficit = 0.0
        var daysWithEntries = 0
        
        val startDate = LocalDate.parse(first.dateIso)
        val endDate = LocalDate.parse(latest.dateIso)
        
        var curr = startDate
        while (!curr.isAfter(endDate)) {
            val dateIso = curr.toString()
            val entries = allEntries.filter { it.dateIso == dateIso }
            if (entries.isNotEmpty()) {
                val intake = entries.sumOf { it.kcal }
                val activity = getActivityKcal(dateIso, profile)
                totalTheoreticalDeficit += (profile.bmr + activity) - intake
                daysWithEntries++
            }
            curr = curr.plusDays(1)
        }
        
        if (daysWithEntries < 5 || totalTheoreticalDeficit == 0.0) return profile.metabolicFactor
        
        val factor = actualDeficit / totalTheoreticalDeficit
        return factor.coerceIn(0.5, 2.0)
    }

    // --- Category Management ---
    fun addCategory(category: String) {
        if (!categories.contains(category)) {
            categories.add(category)
            saveCategories()
        }
    }

    fun updateCategory(old: String, new: String) {
        val index = categories.indexOf(old)
        if (index != -1) {
            categories[index] = new
            foods.forEachIndexed { fIndex, food ->
                if (food.category == old) {
                    val updated = food.copy(category = new)
                    foods[fIndex] = updated
                    updateFood(updated)
                }
            }
            saveCategories()
        }
    }

    fun deleteCategory(category: String) {
        categories.remove(category)
        saveCategories()
    }

    // --- Social / Inbox ---
    fun updateActivity(steps: Int?, totalKcal: Double?, sessions: List<ExerciseSessionInfo>? = null) {
        val dateStr = selectedDate.toString()
        steps?.let { dailySteps[dateStr] = it }
        totalKcal?.let { dailyTotalCalories[dateStr] = it }
        sessions?.let { dailyExerciseSessions[dateStr] = it }
        saveActivity()
    }

    fun markMessageAsRead(messageId: String) {
        val user = firebaseManager.currentUser.value ?: return
        viewModelScope.launch { firestoreRepository.markMessageAsRead(user.uid, messageId) }
    }

    fun deleteInboxMessage(messageId: String) {
        val user = firebaseManager.currentUser.value ?: return
        viewModelScope.launch { firestoreRepository.deleteInboxMessage(user.uid, messageId) }
    }

    fun sendFoodToUser(targetUid: String, food: FoodItemEntity) {
        val user = firebaseManager.currentUser.value ?: return
        val message = InboxMessage(
            fromUid = user.uid,
            fromName = userProfile.firstName,
            type = MessageType.FOOD,
            payloadJson = json.encodeToString(food),
            timestamp = System.currentTimeMillis()
        )
        viewModelScope.launch { firestoreRepository.sendInboxMessage(targetUid, message) }
    }

    fun sendRecipeToUser(targetUid: String, meal: MealEntity) {
        val user = firebaseManager.currentUser.value ?: return
        viewModelScope.launch {
            var finalMeal = meal
            val imageUrl = meal.imageUrl
            if (imageUrl != null && imageUrl.startsWith("/")) { // Local path
                val cloudUrl = firestoreRepository.uploadMealImage(user.uid, imageUrl)
                if (cloudUrl != null) {
                    finalMeal = meal.copy(imageUrl = cloudUrl)
                }
            }
            val message = InboxMessage(
                fromUid = user.uid,
                fromName = userProfile.firstName,
                type = MessageType.RECIPE,
                payloadJson = json.encodeToString(finalMeal),
                timestamp = System.currentTimeMillis()
            )
            firestoreRepository.sendInboxMessage(targetUid, message)
        }
    }

    fun getRecipeJson(meal: MealEntity): String = json.encodeToString(meal)

    fun startRecipeImport(jsonStr: String): Boolean {
        return try {
            pendingRecipeImport = json.decodeFromString<RecipeData>(jsonStr)
            true
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "Recipe import failed", e)
            false
        }
    }

    fun resolveRecipeImport(supplement: Boolean) {
        val importData = pendingRecipeImport ?: return
        viewModelScope.launch {
            importData.relatedFoods.forEach { food ->
                val existing = foods.find { it.id == food.id }
                if (existing == null) {
                    addFood(
                        name = food.name, kcal = food.kcalPer100g, protein = food.proteinPer100g,
                        carbs = food.carbsPer100g, sugar = food.sugarPer100g, fat = food.fatPer100g,
                        saturatedFat = food.saturatedFatPer100g, alcoholPercent = food.alcoholPercent,
                        baseUnit = food.baseUnit, portions = food.portions, packages = food.packages,
                        barcode = food.barcode, brand = food.brand, category = food.category,
                        isGeneric = food.isGeneric, store = food.store
                    )
                } else if (supplement) {
                    updateFood(food)
                }
            }
            val existingMeal = meals.find { it.id == importData.meal.id }
            if (existingMeal == null) {
                addMealTemplate(
                    importData.meal.name, importData.meal.ingredients,
                    importData.meal.servings, importData.meal.tags, importData.meal.imageUrl
                )
            } else if (supplement) {
                updateMealTemplate(importData.meal)
            }
            pendingRecipeImport = null
        }
    }

    fun forceMigrationToCloud() {
        val user = firebaseManager.currentUser.value ?: return
        val householdId = firebaseManager.household.value?.id
        viewModelScope.launch {
            foods.forEach { firestoreRepository.savePersonalFood(user.uid, it) }
            meals.forEach { firestoreRepository.savePersonalMeal(user.uid, it) }
            allEntries.forEach { firestoreRepository.savePersonalEntry(user.uid, it) }
            weightHistory.forEach { firestoreRepository.saveWeightEntry(user.uid, it) }
            if (householdId != null) {
                plannedEntries.forEach { firestoreRepository.addPlannedEntry(householdId, it) }
                shoppingList.forEach { firestoreRepository.addShoppingItem(householdId, it) }
            }
        }
    }

    fun saveImageLocally(uri: android.net.Uri): String? {
        return try {
            val contentResolver = getApplication<Application>().contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(getApplication<Application>().filesDir, "meal_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "Failed to save image locally", e)
            null
        }
    }

    // --- Persistence & Backup ---
    fun importBackup(jsonStr: String): Boolean {
        return try {
            val data = json.decodeFromString<BackupData>(jsonStr)
            foods.clear(); foods.addAll(data.foods)
            meals.clear(); meals.addAll(data.meals)
            allEntries.clear(); allEntries.addAll(data.entries)
            categories.clear(); categories.addAll(data.categories)
            saveFoods(); saveMeals(); saveEntries(); saveCategories()
            updateNextIds()
            true
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "Import failed", e)
            false
        }
    }

    fun getBackupJson(): String {
        val data = BackupData(
            foods = foods.toList(),
            meals = meals.toList(),
            categories = categories.toList(),
            entries = allEntries.toList()
        )
        return json.encodeToString(data)
    }

    fun getCatalogJson(): String {
        val data = BackupData(
            foods = foods.toList(),
            meals = meals.toList(),
            categories = categories.toList()
        )
        return json.encodeToString(data)
    }

    private fun saveFoods() = viewModelScope.launch(Dispatchers.IO) {
        val data = foods.toList()
        prefs.edit().putString("foods", json.encodeToString(data)).apply()
    }

    private fun saveMeals() = viewModelScope.launch(Dispatchers.IO) {
        val data = meals.toList()
        prefs.edit().putString("meals", json.encodeToString(data)).apply()
    }

    private fun saveEntries() = viewModelScope.launch(Dispatchers.IO) {
        val data = allEntries.toList()
        prefs.edit().putString("entries", json.encodeToString(data)).apply()
    }

    private fun saveCategories() = viewModelScope.launch(Dispatchers.IO) {
        val data = categories.toList()
        prefs.edit().putString("categories", json.encodeToString(data)).apply()
    }

    private fun savePlannedEntries() = viewModelScope.launch(Dispatchers.IO) {
        val data = plannedEntries.toList()
        prefs.edit().putString("planned_entries", json.encodeToString(data)).apply()
    }

    private fun saveShoppingList() = viewModelScope.launch(Dispatchers.IO) {
        val data = shoppingList.toList()
        prefs.edit().putString("shopping_list", json.encodeToString(data)).apply()
    }

    private fun saveWeightHistory() = viewModelScope.launch(Dispatchers.IO) {
        val data = weightHistory.toList()
        prefs.edit().putString("weight_history", json.encodeToString(data)).apply()
    }

    private fun saveVerifications() = viewModelScope.launch(Dispatchers.IO) {
        val data = dayVerifications.toMap()
        prefs.edit().putString("verifications", json.encodeToString(data)).apply()
    }

    private fun saveActivity() = viewModelScope.launch(Dispatchers.IO) {
        val steps = dailySteps.toMap()
        val cals = dailyTotalCalories.toMap()
        val sessions = dailyExerciseSessions.toMap()
        prefs.edit().let {
            it.putString("daily_steps", json.encodeToString(steps))
            it.putString("daily_total_kcal", json.encodeToString(cals))
            it.putString("daily_sessions", json.encodeToString(sessions))
            it.apply()
        }
    }

    private fun loadLocalState() {
        try {
            prefs.getString("foods", null)?.let { foods.addAll(json.decodeFromString<List<FoodItemEntity>>(it)) }
            prefs.getString("meals", null)?.let { meals.addAll(json.decodeFromString<List<MealEntity>>(it)) }
            prefs.getString("entries", null)?.let { allEntries.addAll(json.decodeFromString<List<FoodEntryEntity>>(it)) }
            prefs.getString("categories", null)?.let { categories.addAll(json.decodeFromString<List<String>>(it)) }
            prefs.getString("planned_entries", null)?.let { plannedEntries.addAll(json.decodeFromString<List<FoodEntryEntity>>(it)) }
            prefs.getString("shopping_list", null)?.let { shoppingList.addAll(json.decodeFromString<List<ShoppingItem>>(it)) }
            prefs.getString("weight_history", null)?.let { weightHistory.addAll(json.decodeFromString<List<WeightEntry>>(it)) }
            prefs.getString("verifications", null)?.let { dayVerifications.putAll(json.decodeFromString<Map<String, Boolean>>(it)) }
            prefs.getString("daily_steps", null)?.let { dailySteps.putAll(json.decodeFromString<Map<String, Int>>(it)) }
            prefs.getString("daily_total_kcal", null)?.let { dailyTotalCalories.putAll(json.decodeFromString<Map<String, Double>>(it)) }
            prefs.getString("daily_sessions", null)?.let { dailyExerciseSessions.putAll(json.decodeFromString<Map<String, List<ExerciseSessionInfo>>>(it)) }
            updateNextIds()
        } catch (e: Exception) {
            Log.e("NutritionViewModel", "Error loading local state", e)
        }
    }

    private fun updateNextIds() {
        if (foods.isNotEmpty()) nextFoodId = (foods.maxOf { it.id }) + 1
        if (meals.isNotEmpty()) nextMealId = (meals.maxOf { it.id }) + 1
        if (allEntries.isNotEmpty()) nextEntryId = (allEntries.maxOf { it.id }) + 1
    }

    fun repairAndWipeLocalCache() {
        if (isRepairing) return
        foods.clear(); meals.clear(); allEntries.clear(); plannedEntries.clear(); shoppingList.clear()
        saveFoods(); saveMeals(); saveEntries(); savePlannedEntries(); saveShoppingList()
        setupFirebaseSync()
        triggerAutoRepair()
    }

    fun forceDeepRepair() {
        triggerAutoRepair()
    }

    private fun createDefaultFoods() {
        addFood("Apfel", 52.0, 0.3, 14.0, 10.0, 0.2, 0.0, 0.0, "g", listOf(FoodPortionEntity(0, "Stück", 150.0)), emptyList(), category = "Obst")
    }

    private fun createDefaultCategories() {
        categories.addAll(listOf("Obst", "Gemüse", "Fleisch", "Milchprodukte", "Getreide", "Sonstiges"))
        saveCategories()
    }
}
