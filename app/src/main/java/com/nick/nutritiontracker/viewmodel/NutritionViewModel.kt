package com.nick.nutritiontracker.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import com.nick.nutritiontracker.data.FoodEntryEntity
import com.nick.nutritiontracker.data.FoodItemEntity
import com.nick.nutritiontracker.data.FoodPortionEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("nutrition_tracker", Context.MODE_PRIVATE)
    private val json = Json { 
        ignoreUnknownKeys = true 
        prettyPrint = true
    }

    val todayIso: String = LocalDate.now().toString()

    private var nextFoodId = 1L
    private var nextPortionId = 1L
    private var nextEntryId = 1L

    val foods = mutableStateListOf<FoodItemEntity>()
    val todayEntries = mutableStateListOf<FoodEntryEntity>()

    // Daily totals
    val todayTotalKcal by derivedStateOf { todayEntries.sumOf { it.kcal } }
    val todayTotalProtein by derivedStateOf { todayEntries.sumOf { it.protein } }
    val todayTotalComplexCarbs by derivedStateOf { todayEntries.sumOf { it.complexCarbs } }
    val todayTotalSugar by derivedStateOf { todayEntries.sumOf { it.sugar } }
    val todayTotalUnsaturatedFat by derivedStateOf { todayEntries.sumOf { it.unsaturatedFat } }
    val todayTotalSaturatedFat by derivedStateOf { todayEntries.sumOf { it.saturatedFat } }

    init {
        loadFoods()
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
            portions = listOf(
                FoodPortionEntity(0, "S", 43.0),
                FoodPortionEntity(0, "M", 53.0),
                FoodPortionEntity(0, "L", 63.0)
            )
        )
        addFood(
            name = "Proteinriegel",
            kcal = 375.0,
            protein = 50.0,
            carbs = 25.0,
            sugar = 5.0,
            fat = 9.0,
            saturatedFat = 4.0,
            portions = listOf(FoodPortionEntity(0, "Riegel", 40.0))
        )
        addFood(
            name = "Skyr natur",
            kcal = 63.0,
            protein = 11.0,
            carbs = 4.0,
            sugar = 4.0,
            fat = 0.2,
            saturatedFat = 0.1,
            portions = listOf(FoodPortionEntity(0, "Becher", 500.0))
        )
    }

    fun addFood(
        name: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        sugar: Double,
        fat: Double,
        saturatedFat: Double,
        portions: List<FoodPortionEntity>,
        barcode: String? = null
    ) {
        if (name.isBlank()) return
        
        val newFood = FoodItemEntity(
            id = 0,
            name = name.trim(),
            kcalPer100g = kcal,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            sugarPer100g = sugar,
            fatPer100g = fat,
            saturatedFatPer100g = saturatedFat,
            portions = portions,
            barcode = barcode?.trim()?.takeIf { it.isNotBlank() }
        )
        foods.add(newFood)
        recalculateIds()
        saveFoods()
    }

    fun updateFood(updatedFood: FoodItemEntity) {
        val index = foods.indexOfFirst { it.id == updatedFood.id }
        if (index != -1) {
            foods[index] = updatedFood
            recalculateIds()
            saveFoods()
            
            for (i in todayEntries.indices) {
                if (todayEntries[i].foodItemId == updatedFood.id) {
                    todayEntries[i] = todayEntries[i].copy(
                        name = updatedFood.name,
                        kcalPer100g = updatedFood.kcalPer100g,
                        proteinPer100g = updatedFood.proteinPer100g,
                        carbsPer100g = updatedFood.carbsPer100g,
                        sugarPer100g = updatedFood.sugarPer100g,
                        fatPer100g = updatedFood.fatPer100g,
                        saturatedFatPer100g = updatedFood.saturatedFatPer100g
                    )
                }
            }
        }
    }

    fun deleteFood(id: Long) {
        foods.removeAll { it.id == id }
        todayEntries.removeAll { it.foodItemId == id }
        saveFoods()
    }

    fun addEntry(food: FoodItemEntity, amount: Double, portion: FoodPortionEntity?, mealSlot: String) {
        val grams = if (portion != null) amount * portion.grams else amount
        if (grams <= 0.0) return
        
        val unitLabel = portion?.name ?: "g"
        
        val entry = FoodEntryEntity(
            id = nextEntryId++,
            dateIso = todayIso,
            mealSlot = mealSlot,
            amount = amount,
            unitLabel = unitLabel,
            grams = grams,
            foodItemId = food.id,
            name = food.name,
            kcalPer100g = food.kcalPer100g,
            proteinPer100g = food.proteinPer100g,
            carbsPer100g = food.carbsPer100g,
            sugarPer100g = food.sugarPer100g,
            fatPer100g = food.fatPer100g,
            saturatedFatPer100g = food.saturatedFatPer100g
        )
        todayEntries.add(0, entry)
    }

    fun updateEntry(updatedEntry: FoodEntryEntity) {
        val index = todayEntries.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            todayEntries[index] = updatedEntry
        }
    }

    fun deleteEntry(id: Long) {
        todayEntries.removeAll { it.id == id }
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

    fun recalculateIds() {
        var fId = 1L
        var pId = 1L
        
        val updatedList = foods.map { food ->
            val updatedPortions = food.portions.map { it.copy(id = pId++) }
            food.copy(id = fId++, portions = updatedPortions)
        }
        
        foods.clear()
        foods.addAll(updatedList)
        
        nextFoodId = fId
        nextPortionId = pId
    }
}
