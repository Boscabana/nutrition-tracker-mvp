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
import com.nick.nutritiontracker.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nutrition_tracker", Context.MODE_PRIVATE)
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    fun selectDate(date: LocalDate) {
        selectedDate = date
    }

    private var nextFoodId = 1L
    private var nextPortionId = 1L
    private var nextEntryId = 1L

    val foods = mutableStateListOf<FoodItemEntity>()
    val allEntries = mutableStateListOf<FoodEntryEntity>()
    
    // Step tracking: Date ISO -> Steps
    val dailySteps = mutableStateMapOf<String, Int>()

    val todayEntries by derivedStateOf {
        allEntries.filter { it.dateIso == selectedDate.toString() }
            .sortedByDescending { it.id }
    }

    val availableDates by derivedStateOf {
        (allEntries.map { LocalDate.parse(it.dateIso) } + dailySteps.keys.map { LocalDate.parse(it) } + LocalDate.now() + LocalDate.now().plusDays(1))
            .distinct()
            .sortedDescending()
    }

    // Daily totals for the selected date
    val todayTotalKcal by derivedStateOf { todayEntries.sumOf { it.kcal } }
    val todayTotalProtein by derivedStateOf { todayEntries.sumOf { it.protein } }
    val todayTotalComplexCarbs by derivedStateOf { todayEntries.sumOf { it.complexCarbs } }
    val todayTotalSugar by derivedStateOf { todayEntries.sumOf { it.sugar } }
    val todayTotalUnsaturatedFat by derivedStateOf { todayEntries.sumOf { it.unsaturatedFat } }
    val todayTotalSaturatedFat by derivedStateOf { todayEntries.sumOf { it.saturatedFat } }
    
    val todaySteps by derivedStateOf { dailySteps[selectedDate.toString()] ?: 0 }

    init {
        loadFoods()
        loadEntries()
        loadSteps()
        if (foods.isEmpty()) {
            createDefaultFoods()
        } else {
            recalculateIds()
        }
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
            packages = emptyList()
        )
        addFood(
            name = "Skyr natur",
            kcal = 63.0,
            protein = 11.0,
            carbs = 4.0,
            sugar = 4.0,
            fat = 0.2,
            saturatedFat = 0.1,
            alcoholPercent = 0.0,
            baseUnit = "g",
            portions = listOf(FoodPortionEntity(0, "Becher", 500.0)),
            packages = listOf(FoodPackageEntity(0, "Becher", 500.0, "g"))
        )
    }

    fun findFoodByBarcode(barcode: String): FoodItemEntity? {
        return foods.find { it.barcode == barcode }
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
        brand: String? = null
    ): FoodItemEntity {
        val newFood = FoodItemEntity(
            id = 0,
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
            portions = portions,
            packages = packages,
            barcode = barcode?.trim()?.takeIf { it.isNotBlank() }
        )
        foods.add(newFood)
        recalculateIds()
        saveFoods()
        return foods.last { it.name == newFood.name && (barcode == null || it.barcode == barcode) }
    }

    fun updateFood(updatedFood: FoodItemEntity) {
        val index = foods.indexOfFirst { it.id == updatedFood.id }
        if (index != -1) {
            foods[index] = updatedFood
            recalculateIds()
            saveFoods()
            
            // Sync current entries snapshots
            for (i in allEntries.indices) {
                if (allEntries[i].foodItemId == updatedFood.id) {
                    allEntries[i] = allEntries[i].copy(
                        name = updatedFood.name,
                        brand = updatedFood.brand,
                        kcalPer100g = updatedFood.kcalPer100g,
                        proteinPer100g = updatedFood.proteinPer100g,
                        carbsPer100g = updatedFood.carbsPer100g,
                        sugarPer100g = updatedFood.sugarPer100g,
                        fatPer100g = updatedFood.fatPer100g,
                        saturatedFatPer100g = updatedFood.saturatedFatPer100g,
                        alcoholPercent = updatedFood.alcoholPercent,
                        baseUnit = updatedFood.baseUnit
                    )
                }
            }
            saveEntries()
        }
    }

    fun deleteFood(id: Long) {
        foods.removeAll { it.id == id }
        allEntries.removeAll { it.foodItemId == id }
        saveFoods()
        saveEntries()
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
            baseUnit = food.baseUnit
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
    
    fun updateSteps(steps: Int) {
        dailySteps[selectedDate.toString()] = steps
        saveSteps()
    }

    fun saveFoods() {
        try {
            val data = json.encodeToString(foods.toList())
            prefs.edit().putString("foods_json", data).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFoods() {
        val data = prefs.getString("foods_json", null)
        if (data != null) {
            try {
                val loaded = json.decodeFromString<List<FoodItemEntity>>(data)
                foods.clear()
                foods.addAll(loaded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveEntries() {
        try {
            val data = json.encodeToString(allEntries.toList())
            prefs.edit().putString("entries_json", data).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadEntries() {
        val data = prefs.getString("entries_json", null)
        if (data != null) {
            try {
                val loaded = json.decodeFromString<List<FoodEntryEntity>>(data)
                allEntries.clear()
                allEntries.addAll(loaded)
                nextEntryId = (allEntries.maxOfOrNull { it.id } ?: 0L) + 1
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun saveSteps() {
        try {
            val data = json.encodeToString(dailySteps.toMap())
            prefs.edit().putString("steps_json", data).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadSteps() {
        val data = prefs.getString("steps_json", null)
        if (data != null) {
            try {
                val loaded = json.decodeFromString<Map<String, Int>>(data)
                dailySteps.clear()
                dailySteps.putAll(loaded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun recalculateIds() {
        var fId = 1L
        var pId = 1L
        var pkgId = 1L
        
        val updatedList = foods.map { food ->
            val updatedPortions = food.portions.map { it.copy(id = pId++) }
            val updatedPackages = food.packages.map { it.copy(id = pkgId++) }
            food.copy(id = fId++, portions = updatedPortions, packages = updatedPackages)
        }
        
        foods.clear()
        foods.addAll(updatedList)
        
        nextFoodId = fId
        nextPortionId = pId
    }
}
