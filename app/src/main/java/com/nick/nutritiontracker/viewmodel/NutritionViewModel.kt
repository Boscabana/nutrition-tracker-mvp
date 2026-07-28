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
    
    var foodSearchQuery by mutableStateOf("")
    var selectedFoodCategory by mutableStateOf<String?>(null)
    
    var pendingRecipeImport by mutableStateOf<RecipeData?>(null)
    
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

    init {
        loadFoods()
        loadMeals()
        loadEntries()
        loadSteps()
        loadCategories()
        
        // --- DATA INTEGRITY CHECK ---
        val hasDuplicateIds = foods.size != foods.map { it.id }.distinct().size
        val hasMissingParentRefs = foods.any { it.parentId != null && foods.none { p -> p.id == it.parentId } }
        
        if (hasDuplicateIds || hasMissingParentRefs) {
            recalculateIds()
        }
        // ----------------------------

        if (foods.isEmpty()) {
            createDefaultFoods()
        }
        if (categories.isEmpty()) {
            createDefaultCategories()
        }
        syncStepsForSelectedDate()
        
        setupFirebaseSync()
    }

    fun syncStepsForSelectedDate() {
        viewModelScope.launch {
            if (healthConnectManager.isAvailable() && healthConnectManager.hasAllPermissions()) {
                val steps = healthConnectManager.getStepsForDate(selectedDate)
                if (steps != null) {
                    updateSteps(steps)
                }
            }
        }
    }

    private fun setupFirebaseSync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            firebaseManager.household.collectLatest { household ->
                if (household != null) {
                    Log.d("Firestore", "Household connected: ${household.id}, starting sync")
                    // Sync Planner
                    launch {
                        try {
                            firestoreRepository.getPlannedEntries(household.id).collect { entries ->
                                plannedEntries.clear()
                                plannedEntries.addAll(entries)
                                Log.d("Firestore", "Sync: ${entries.size} planned entries")
                            }
                        } catch (e: Exception) {
                            Log.e("Firestore", "Sync error: Planner", e)
                        }
                    }
                    // Sync Shopping List
                    launch {
                        try {
                            firestoreRepository.getShoppingList(household.id).collect { items ->
                                shoppingList.clear()
                                shoppingList.addAll(items)
                                Log.d("Firestore", "Sync: ${items.size} shopping items")
                            }
                        } catch (e: Exception) {
                            Log.e("Firestore", "Sync error: Shopping List", e)
                        }
                    }
                } else {
                    Log.d("Firestore", "No household connected, clearing data")
                    plannedEntries.clear()
                    shoppingList.clear()
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
        
        val entry = FoodEntryEntity(
            id = System.currentTimeMillis(),
            dateIso = date.toString(),
            mealSlot = mealSlot,
            amount = amount,
            unitLabel = portion?.name ?: food.baseUnit,
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
        
        viewModelScope.launch {
            try {
                firestoreRepository.addPlannedEntry(householdId, entry)
                Log.d("Firestore", "Planned entry added: ${entry.name}")
                
                if (autoAddToShoppingList) {
                    val item = ShoppingItem(
                        name = getGeneralName(food),
                        amount = amount,
                        unit = portion?.name ?: food.baseUnit,
                        isAutoGenerated = true,
                        category = food.category,
                        householdId = householdId,
                        sourceName = "Einzelne Zutat",
                        weightGrams = grams,
                        baseUnit = food.baseUnit,
                        isPantryItem = food.isPantryItem || (food.parentId?.let { pId -> foods.find { it.id == pId }?.isPantryItem } ?: false)
                    )
                    firestoreRepository.addShoppingItem(householdId, item)
                    Log.d("Firestore", "Shopping item added for entry: ${item.name} ($grams ${food.baseUnit})")
                }
            } catch (e: Exception) {
                Log.e("Firestore", "Error adding planned entry/shopping item", e)
            }
        }
    }

    fun addPlannedMeal(meal: MealEntity, mealSlot: String, date: LocalDate, servings: Double = 1.0, autoAddToShoppingList: Boolean = true) {
        val householdId = firebaseManager.household.value?.id ?: return
        
        val entry = FoodEntryEntity(
            id = System.currentTimeMillis(),
            dateIso = date.toString(),
            mealSlot = mealSlot,
            amount = servings,
            unitLabel = if (servings == 1.0) "Portion" else "Portionen",
            grams = (meal.ingredients.sumOf { it.grams } * (servings / meal.servings)).coerceAtLeast(0.0),
            foodItemId = -1,
            name = meal.name,
            kcalPer100g = 0.0,
            proteinPer100g = 0.0,
            carbsPer100g = 0.0,
            sugarPer100g = 0.0,
            fatPer100g = 0.0,
            saturatedFatPer100g = 0.0,
            isMeal = true,
            mealIngredients = meal.ingredients.map { 
                it.copy(
                    id = System.currentTimeMillis() + (Math.random() * 1000).toLong(),
                    grams = it.grams * (servings / meal.servings), 
                    amount = it.amount * (servings / meal.servings)
                ) 
            },
            isPlanned = true
        )
        
        viewModelScope.launch {
            try {
                firestoreRepository.addPlannedEntry(householdId, entry)
                Log.d("Firestore", "Planned meal added: ${meal.name}")

                if (autoAddToShoppingList) {
                    meal.ingredients.forEach { ing ->
                        val foodItem = foods.find { it.id == ing.foodItemId }
                        val isPantry = foodItem?.isPantryItem == true || 
                                (foodItem?.parentId?.let { pId -> foods.find { it.id == pId }?.isPantryItem } ?: false)
                        
                        val itemWeight = (ing.grams / meal.servings) * servings
                        val item = ShoppingItem(
                            name = getGeneralNameForIngredient(ing.foodItemId, ing.name),
                            amount = (ing.amount / meal.servings) * servings,
                            unit = ing.unitLabel,
                            isAutoGenerated = true,
                            householdId = householdId,
                            sourceName = meal.name,
                            weightGrams = itemWeight,
                            baseUnit = ing.baseUnit,
                            isPantryItem = isPantry
                        )
                        firestoreRepository.addShoppingItem(householdId, item)
                        Log.d("Firestore", "Shopping item added for meal ingredient: ${item.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e("Firestore", "Error adding planned meal", e)
            }
        }
    }

    fun deletePlannedEntry(entryId: Long) {
        val householdId = firebaseManager.household.value?.id ?: return
        viewModelScope.launch {
            firestoreRepository.deletePlannedEntry(householdId, entryId)
        }
    }

    fun updatePlannedEntry(updated: FoodEntryEntity) {
        val householdId = firebaseManager.household.value?.id ?: return
        viewModelScope.launch {
            try {
                firestoreRepository.addPlannedEntry(householdId, updated)
            } catch (e: Exception) {
                Log.e("Firestore", "Error updating planned entry", e)
            }
        }
    }

    fun addShoppingItem(name: String, amount: Double, unit: String) {
        val householdId = firebaseManager.household.value?.id ?: return
        val lowerUnit = unit.lowercase().trim()
        val isWeight = lowerUnit == "g" || lowerUnit == "ml"
        viewModelScope.launch {
            firestoreRepository.addShoppingItem(
                householdId, 
                ShoppingItem(
                    name = name, 
                    amount = amount, 
                    unit = unit, 
                    householdId = householdId,
                    weightGrams = if (isWeight) amount else 0.0,
                    baseUnit = if (lowerUnit == "ml") "ml" else "g"
                )
            )
        }
    }

    fun toggleShoppingItem(item: ShoppingItem) {
        val householdId = firebaseManager.household.value?.id ?: return
        Log.d("Firestore", "Toggling item: ${item.name}, current state: ${item.isChecked}")
        viewModelScope.launch {
            try {
                if (isShoppingListAggregated) {
                    val isWeightItem = item.weightGrams > 0
                    val relatedItems = shoppingList.filter { 
                        val nameMatch = it.name.equals(item.name, ignoreCase = true)
                        val match = if (isWeightItem) {
                            nameMatch && it.weightGrams > 0 && it.baseUnit.equals(item.baseUnit, ignoreCase = true)
                        } else {
                            nameMatch && it.unit.equals(item.unit, ignoreCase = true)
                        }
                        match && it.isChecked != !item.isChecked 
                    }
                    Log.d("Firestore", "Updating ${relatedItems.size} related items")
                    relatedItems.forEach {
                        firestoreRepository.updateShoppingItem(householdId, it.copy(isChecked = !item.isChecked))
                    }
                } else {
                    firestoreRepository.updateShoppingItem(householdId, item.copy(isChecked = !item.isChecked))
                }
            } catch (e: Exception) {
                Log.e("Firestore", "Error toggling item", e)
            }
        }
    }

    fun deleteShoppingItem(itemId: String) {
        val householdId = firebaseManager.household.value?.id ?: return
        Log.d("Firestore", "Deleting item: $itemId")
        viewModelScope.launch {
            try {
                firestoreRepository.deleteShoppingItem(householdId, itemId)
                Log.d("Firestore", "Delete success")
            } catch (e: Exception) {
                Log.e("Firestore", "Error deleting item", e)
            }
        }
    }

    private fun createDefaultCategories() {
        val defaults = listOf(
            "Milchprodukte", "Fleisch", "Süßigkeiten", "Getränke", "Obst", 
            "Gemüse", "Teigwaren", "Fertiggerichte", "Tiefkühlprodukte", 
            "Kühlregal", "Convenience"
        )
        categories.addAll(defaults)
        saveCategories()
    }

    private fun createDefaultFoods() {
        addFood(
            name = "Ei",
            kcal = 155.0,
            protein = 13.0,
            carbs = 1.1,
            sugar = 1.1,
            fat = 11.0,
            saturatedFat = 3.3,
            alcoholPercent = 0.0,
            baseUnit = "g",
            portions = listOf(
                FoodPortionEntity(0, "S", 43.0),
                FoodPortionEntity(0, "M", 53.0),
                FoodPortionEntity(0, "L", 63.0)
            ),
            packages = emptyList(),
            category = "Protein",
            isGeneric = true
        )
    }

    fun findFoodByBarcode(barcode: String): FoodItemEntity? {
        return foods.find { it.barcode?.equals(barcode, ignoreCase = true) == true }
    }

    fun addFood(
        name: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        sugar: Double,
        fat: Double,
        saturatedFat: Double,
        alcoholPercent: Double,
        baseUnit: String,
        portions: List<FoodPortionEntity>,
        packages: List<FoodPackageEntity>,
        barcode: String? = null,
        brand: String? = null,
        category: String? = null,
        isGeneric: Boolean = false,
        parentId: Long? = null,
        store: String? = null,
        isPantryItem: Boolean = false
    ): FoodItemEntity {
        val updatedPortions = portions.map { 
            if (it.id == 0L) it.copy(id = nextPortionId++) else it 
        }
        val updatedPackages = packages.mapIndexed { idx, pkg -> 
            if (pkg.id == 0L) pkg.copy(id = nextFoodId * 100 + idx) else pkg 
        }

        val newFood = FoodItemEntity(
            id = nextFoodId++,
            name = name.trim(),
            brand = brand?.trim()?.takeIf { it.isNotBlank() },
            kcalPer100g = kcal,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            sugarPer100g = sugar,
            fatPer100g = fat,
            saturatedFatPer100g = saturatedFat,
            alcoholPercent = alcoholPercent,
            baseUnit = baseUnit,
            portions = updatedPortions,
            packages = updatedPackages,
            barcode = barcode?.trim()?.takeIf { it.isNotBlank() },
            category = category?.trim()?.takeIf { it.isNotBlank() },
            isGeneric = isGeneric,
            parentId = parentId,
            store = store?.trim()?.takeIf { it.isNotBlank() },
            isPantryItem = isPantryItem
        )
        foods.add(newFood)
        saveFoods()
        return newFood
    }

    fun updateFood(updatedFood: FoodItemEntity) {
        val index = foods.indexOfFirst { it.id == updatedFood.id }
        if (index != -1) {
            val fixedPortions = updatedFood.portions.map { 
                if (it.id == 0L) it.copy(id = nextPortionId++) else it 
            }
            val fixedPackages = updatedFood.packages.mapIndexed { idx, pkg ->
                if (pkg.id == 0L) pkg.copy(id = updatedFood.id * 100 + idx) else pkg
            }
            val finalFood = updatedFood.copy(portions = fixedPortions, packages = fixedPackages)
            
            foods[index] = finalFood
            saveFoods()
            
            // Sync with entries
            for (i in allEntries.indices) {
                if (allEntries[i].foodItemId == finalFood.id) {
                    allEntries[i] = allEntries[i].copy(
                        name = finalFood.name,
                        brand = finalFood.brand,
                        kcalPer100g = finalFood.kcalPer100g,
                        proteinPer100g = finalFood.proteinPer100g,
                        carbsPer100g = finalFood.carbsPer100g,
                        sugarPer100g = finalFood.sugarPer100g,
                        fatPer100g = finalFood.fatPer100g,
                        saturatedFatPer100g = finalFood.saturatedFatPer100g,
                        alcoholPercent = finalFood.alcoholPercent,
                        baseUnit = finalFood.baseUnit,
                        store = finalFood.store
                    )
                }
            }
            saveEntries()

            // Sync with meals templates
            for (mIdx in meals.indices) {
                var mealChanged = false
                val updatedIngredients = meals[mIdx].ingredients.map { ing ->
                    if (ing.foodItemId == finalFood.id) {
                        mealChanged = true
                        ing.copy(
                            name = finalFood.name,
                            kcalPer100g = finalFood.kcalPer100g,
                            proteinPer100g = finalFood.proteinPer100g,
                            carbsPer100g = finalFood.carbsPer100g,
                            sugarPer100g = finalFood.sugarPer100g,
                            fatPer100g = finalFood.fatPer100g,
                            saturatedFatPer100g = finalFood.saturatedFatPer100g,
                            alcoholPercent = finalFood.alcoholPercent,
                            baseUnit = finalFood.baseUnit,
                            store = finalFood.store,
                            brand = finalFood.brand
                        )
                    } else ing
                }
                if (mealChanged) {
                    meals[mIdx] = meals[mIdx].copy(ingredients = updatedIngredients)
                }
            }
            saveMeals()
        }
    }

    fun deleteFood(id: Long) {
        foods.removeAll { it.id == id }
        
        for (i in foods.indices) {
            if (foods[i].parentId == id) {
                foods[i] = foods[i].copy(parentId = null)
            }
        }
        
        for (i in allEntries.indices) {
            if (allEntries[i].foodItemId == id) {
                allEntries[i] = allEntries[i].copy(foodItemId = -1L)
            }
        }
        
        // Note: Ingredients in meals are kept but will show as orphaned in UI
        saveFoods()
        saveMeals()
        saveEntries()
    }

    fun mergeFoods(targetParentId: Long, childIds: List<Long>) {
        childIds.forEach { id ->
            val idx = foods.indexOfFirst { it.id == id }
            if (idx != -1) {
                foods[idx] = foods[idx].copy(parentId = targetParentId, isGeneric = false)
            }
        }
        saveFoods()
    }

    fun promoteToGeneric(foodId: Long) {
        val idx = foods.indexOfFirst { it.id == foodId }
        if (idx != -1) {
            foods[idx] = foods[idx].copy(isGeneric = true, parentId = null)
            saveFoods()
        }
    }

    fun addCategory(name: String) {
        if (name.isNotBlank() && !categories.contains(name.trim())) {
            categories.add(name.trim())
            saveCategories()
        }
    }

    fun deleteCategory(name: String) {
        categories.remove(name)
        saveCategories()
    }

    fun updateCategory(oldName: String, newName: String) {
        val idx = categories.indexOf(oldName)
        if (idx != -1 && newName.isNotBlank() && !categories.contains(newName.trim())) {
            categories[idx] = newName.trim()
            saveCategories()
            
            foods.forEachIndexed { index, food ->
                if (food.category == oldName) {
                    foods[index] = food.copy(category = newName.trim())
                }
            }
            saveFoods()
        }
    }

    private fun saveCategories() {
        try {
            val data = json.encodeToString(categories.toList())
            prefs.edit().putString("categories_json", data).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadCategories() {
        val data = prefs.getString("categories_json", null)
        if (data != null) {
            try {
                val loaded = json.decodeFromString<List<String>>(data)
                categories.clear()
                categories.addAll(loaded)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun addMealTemplate(name: String, ingredients: List<MealIngredientEntity>, servings: Double = 1.0) {
        val newMeal = MealEntity(
            id = nextMealId++,
            name = name,
            ingredients = ingredients.mapIndexed { idx, it -> it.copy(id = System.currentTimeMillis() + idx) },
            servings = servings
        )
        meals.add(newMeal)
        saveMeals()
    }

    fun updateMealTemplate(updatedMeal: MealEntity) {
        val index = meals.indexOfFirst { it.id == updatedMeal.id }
        if (index != -1) {
            meals[index] = updatedMeal
            saveMeals()
        }
    }

    fun deleteMealTemplate(id: Long) {
        meals.removeAll { it.id == id }
        saveMeals()
    }

    fun addEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String) {
        val grams = if (portion != null) amount * portion.grams else amount
        if (grams <= 0.0) return
        
        val unitLabel = portion?.name ?: food.baseUnit
        
        val entry = FoodEntryEntity(
            id = nextEntryId++,
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
        allEntries.add(entry)
        saveEntries()
    }

    fun addMealEntry(meal: MealEntity, mealSlot: String, servings: Double = 1.0) {
        val entry = FoodEntryEntity(
            id = nextEntryId++,
            dateIso = selectedDate.toString(),
            mealSlot = mealSlot,
            amount = servings,
            unitLabel = if (servings == 1.0) "Portion" else "Portionen",
            grams = (meal.ingredients.sumOf { it.grams } * (servings / meal.servings)).coerceAtLeast(0.0),
            foodItemId = -1,
            name = meal.name,
            kcalPer100g = 0.0,
            proteinPer100g = 0.0,
            carbsPer100g = 0.0,
            sugarPer100g = 0.0,
            fatPer100g = 0.0,
            saturatedFatPer100g = 0.0,
            isMeal = true,
            mealIngredients = meal.ingredients.mapIndexed { idx, it -> 
                it.copy(
                    id = System.currentTimeMillis() + idx,
                    grams = it.grams * (servings / meal.servings), 
                    amount = it.amount * (servings / meal.servings)
                ) 
            }
        )
        allEntries.add(entry)
        saveEntries()
    }

    fun updateEntry(updatedEntry: FoodEntryEntity) {
        val index = allEntries.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            allEntries[index] = updatedEntry
            saveEntries()
        }
    }

    fun deleteEntry(id: Long) {
        allEntries.removeAll { it.id == id }
        saveEntries()
    }
    
    fun copyEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) {
        val targetDateIso = targetDate.toString()
        val entriesToCopy = allEntries.filter { it.id in entryIds }
        val newEntries = entriesToCopy.map { entry ->
            entry.copy(
                id = nextEntryId++,
                dateIso = targetDateIso
            )
        }
        allEntries.addAll(newEntries)
        saveEntries()
    }

    fun moveEntriesToDate(entryIds: Set<Long>, targetDate: LocalDate) {
        copyEntriesToDate(entryIds, targetDate)
        allEntries.removeAll { it.id in entryIds }
        saveEntries()
    }

    fun updateSteps(steps: Int) {
        dailySteps[selectedDate.toString()] = steps
        saveSteps()
    }

    fun saveFoods() {
        try {
            val data = json.encodeToString(foods.toList())
            prefs.edit().putString("foods_json", data).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadFoods() {
        val data = prefs.getString("foods_json", null)
        if (data != null) {
            try {
                val loaded = json.decodeFromString<List<FoodItemEntity>>(data)
                foods.clear()
                foods.addAll(loaded)
                nextFoodId = (foods.maxOfOrNull { it.id } ?: 0L) + 1
                nextPortionId = (foods.flatMap { it.portions }.maxOfOrNull { it.id } ?: 0L) + 1
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun saveMeals() {
        try {
            val data = json.encodeToString(meals.toList())
            prefs.edit().putString("meals_json", data).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun loadMeals() {
        val data = prefs.getString("meals_json", null)
        if (data != null) {
            try {
                val loaded = json.decodeFromString<List<MealEntity>>(data)
                meals.clear()
                meals.addAll(loaded)
                nextMealId = (meals.maxOfOrNull { it.id } ?: 0L) + 1
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun saveEntries() {
        try {
            val data = json.encodeToString(allEntries.toList())
            prefs.edit().putString("entries_json", data).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun getBackupJson(): String {
        val backup = BackupData(
            foods = foods.toList(),
            meals = meals.toList(),
            categories = categories.toList(),
            entries = allEntries.toList()
        )
        return json.encodeToString(backup)
    }

    fun getCatalogJson(): String {
        val catalog = BackupData(
            foods = foods.toList(),
            meals = meals.toList(),
            categories = categories.toList(),
            entries = emptyList() // No diary entries
        )
        return json.encodeToString(catalog)
    }

    fun getRecipeJson(meal: MealEntity): String {
        val relatedFoods = meal.ingredients.mapNotNull { ing -> 
            foods.find { it.id == ing.foodItemId } 
        }
        val recipe = RecipeData(meal = meal, relatedFoods = relatedFoods)
        return json.encodeToString(recipe)
    }

    fun importBackup(jsonString: String): Boolean {
        return try {
            val backup = json.decodeFromString<BackupData>(jsonString)
            var changed = false
            backup.categories.forEach { cat ->
                if (cat.isNotBlank() && !categories.contains(cat)) {
                    categories.add(cat)
                    changed = true
                }
            }
            backup.foods.forEach { importedFood ->
                val existing = foods.find { it.name == importedFood.name && it.barcode == importedFood.barcode }
                if (existing == null) {
                    foods.add(importedFood)
                    changed = true
                } else if (existing.category == null && importedFood.category != null) {
                    val idx = foods.indexOf(existing)
                    foods[idx] = existing.copy(category = importedFood.category)
                    changed = true
                }
                importedFood.category?.let { cat ->
                    if (cat.isNotBlank() && !categories.contains(cat)) {
                        categories.add(cat)
                        changed = true
                    }
                }
            }
            backup.meals.forEach { meal ->
                if (meals.none { it.name == meal.name }) {
                    meals.add(meal)
                    changed = true
                }
            }
            backup.entries.forEach { entry ->
                if (allEntries.none { it.dateIso == entry.dateIso && it.name == entry.name && it.mealSlot == entry.mealSlot && it.grams == entry.grams }) {
                    allEntries.add(entry)
                    changed = true
                }
            }
            if (changed) {
                recalculateIds()
                saveFoods()
                saveMeals()
                saveEntries()
                saveCategories()
            }
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    fun startRecipeImport(jsonString: String): Boolean {
        return try {
            val recipe = json.decodeFromString<RecipeData>(jsonString)
            pendingRecipeImport = recipe
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun resolveRecipeImport(supplement: Boolean) {
        val recipe = pendingRecipeImport ?: return
        var changed = false
        
        // 1. Resolve Foods
        recipe.relatedFoods.forEach { importedFood ->
            val existing = foods.find { it.name == importedFood.name && it.barcode == importedFood.barcode }
            if (existing == null) {
                foods.add(importedFood)
                changed = true
            } else if (supplement) {
                // Supplement: update existing if it lacks data
                if (existing.category == null && importedFood.category != null) {
                    val idx = foods.indexOf(existing)
                    foods[idx] = existing.copy(category = importedFood.category)
                    changed = true
                }
            }
        }

        // 2. Resolve Meal
        val existingMeal = meals.find { it.name == recipe.meal.name }
        if (existingMeal == null) {
            meals.add(recipe.meal)
            changed = true
        } else if (supplement) {
            // Replace if supplement is true
            val idx = meals.indexOf(existingMeal)
            meals[idx] = recipe.meal
            changed = true
        }

        if (changed) {
            recalculateIds()
            saveFoods()
            saveMeals()
        }
        pendingRecipeImport = null
    }

    fun loadEntries() {
        val data = prefs.getString("entries_json", null)
        if (data != null) {
            try {
                val loaded = json.decodeFromString<List<FoodEntryEntity>>(data)
                allEntries.clear()
                allEntries.addAll(loaded)
                nextEntryId = (allEntries.maxOfOrNull { it.id } ?: 0L) + 1
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    private fun saveSteps() {
        try {
            val data = json.encodeToString(dailySteps.toMap())
            prefs.edit().putString("steps_json", data).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }
    
    private fun loadSteps() {
        val data = prefs.getString("steps_json", null)
        if (data != null) {
            try {
                val loaded = json.decodeFromString<Map<String, Int>>(data)
                dailySteps.clear()
                dailySteps.putAll(loaded)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun recalculateIds() {
        var fId = 1L
        var pId = 1L
        var pkgId = 1L
        var ingId = 1L
        val oldToNewFoodIdMap = mutableMapOf<Long, Long>()
        val updatedFoods = foods.map { food ->
            val newId = fId++
            oldToNewFoodIdMap[food.id] = newId
            val updatedPortions = food.portions.map { it.copy(id = pId++) }
            val updatedPackages = food.packages.map { it.copy(id = pkgId++) }
            food.copy(id = newId, portions = updatedPortions, packages = updatedPackages)
        }
        val finalFoods = updatedFoods.map { food ->
            if (food.parentId != null) food.copy(parentId = oldToNewFoodIdMap[food.parentId]) else food
        }
        val updatedMeals = meals.map { meal ->
            val updatedIngredients = meal.ingredients.filter { ing -> 
                oldToNewFoodIdMap.containsKey(ing.foodItemId) 
            }.map { ing ->
                ing.copy(id = ingId++, foodItemId = oldToNewFoodIdMap[ing.foodItemId]!!)
            }
            meal.copy(ingredients = updatedIngredients)
        }
        val updatedEntries = allEntries.map { entry ->
            val entryFoodId = if (entry.foodItemId != -1L) oldToNewFoodIdMap[entry.foodItemId] ?: entry.foodItemId else -1L
            val updatedMealIngredients = entry.mealIngredients?.map { ing ->
                ing.copy(id = ingId++, foodItemId = oldToNewFoodIdMap[ing.foodItemId] ?: ing.foodItemId)
            }
            entry.copy(foodItemId = entryFoodId, mealIngredients = updatedMealIngredients)
        }
        foods.clear()
        foods.addAll(finalFoods)
        meals.clear()
        meals.addAll(updatedMeals)
        allEntries.clear()
        allEntries.addAll(updatedEntries)
        nextFoodId = fId
        nextPortionId = pId
        nextMealId = (meals.maxOfOrNull { it.id } ?: 0L) + 1
        nextEntryId = (allEntries.maxOfOrNull { it.id } ?: 0L) + 1
        saveFoods()
        saveMeals()
        saveEntries()
    }
}
