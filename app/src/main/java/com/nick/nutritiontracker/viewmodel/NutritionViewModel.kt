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
import com.nick.nutritiontracker.data.*
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
    
    var foodSearchQuery by mutableStateOf("")
    var selectedFoodCategory by mutableStateOf<String?>(null)
    
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
        store: String? = null
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
            store = store?.trim()?.takeIf { it.isNotBlank() }
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
            ingredients = ingredients,
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

    fun addMealEntry(meal: MealEntity, mealSlot: String) {
        val entry = FoodEntryEntity(
            id = nextEntryId++,
            dateIso = selectedDate.toString(),
            mealSlot = mealSlot,
            amount = 1.0,
            unitLabel = "Meal",
            grams = (meal.ingredients.sumOf { it.grams } / meal.servings).coerceAtLeast(0.0),
            foodItemId = -1,
            name = meal.name,
            kcalPer100g = 0.0,
            proteinPer100g = 0.0,
            carbsPer100g = 0.0,
            sugarPer100g = 0.0,
            fatPer100g = 0.0,
            saturatedFatPer100g = 0.0,
            isMeal = true,
            mealIngredients = meal.ingredients.map { it.copy(grams = it.grams / meal.servings, amount = it.amount / meal.servings) }
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
                ing.copy(foodItemId = oldToNewFoodIdMap[ing.foodItemId]!!)
            }
            meal.copy(ingredients = updatedIngredients)
        }
        val updatedEntries = allEntries.map { entry ->
            val entryFoodId = if (entry.foodItemId != -1L) oldToNewFoodIdMap[entry.foodItemId] ?: entry.foodItemId else -1L
            val updatedMealIngredients = entry.mealIngredients?.map { ing ->
                ing.copy(foodItemId = oldToNewFoodIdMap[ing.foodItemId] ?: ing.foodItemId)
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
