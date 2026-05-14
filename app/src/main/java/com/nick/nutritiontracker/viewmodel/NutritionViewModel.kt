package com.nick.nutritiontracker.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.nick.nutritiontracker.data.FoodEntryWithFood
import java.time.LocalDate

class NutritionViewModel : ViewModel() {
    val todayIso: String = LocalDate.now().toString()

    private var nextFoodId = 1L
    private var nextEntryId = 1L

    val foods = mutableStateListOf<FoodItemEntity>()
    val todayEntries = mutableStateListOf<FoodEntryWithFood>()

    init {
        addFood("Ei M", 155.0, 13.0, 1.1, 1.1, 11.0, 3.3, "Stück", 53.0)
        addFood("Proteinriegel 40g", 375.0, 50.0, 25.0, 5.0, 9.0, 4.0, "Riegel", 40.0)
        addFood("Skyr natur", 63.0, 11.0, 4.0, 4.0, 0.2, 0.1, "Becher", 500.0)
    }

    fun addFood(
        name: String,
        kcal: Double,
        protein: Double,
        carbs: Double,
        sugar: Double,
        fat: Double,
        saturatedFat: Double,
        portionName: String?,
        portionGrams: Double?,
        barcode: String? = null
    ) {
        if (name.isBlank()) return
        foods.add(
            FoodItemEntity(
                id = nextFoodId++,
                name = name.trim(),
                barcode = barcode?.trim()?.takeIf { it.isNotBlank() },
                kcalPer100g = kcal,
                proteinPer100g = protein,
                carbsPer100g = carbs,
                sugarPer100g = sugar.coerceAtMost(carbs).coerceAtLeast(0.0),
                fatPer100g = fat,
                saturatedFatPer100g = saturatedFat.coerceAtMost(fat).coerceAtLeast(0.0),
                defaultPortionName = portionName?.trim()?.takeIf { it.isNotBlank() },
                defaultPortionGrams = portionGrams?.takeIf { it > 0.0 }
            )
        )
    }

    fun addEntry(food: FoodItemEntity, amount: Double, unit: String, mealSlot: String) {
        val grams = if (unit == "portion") amount * (food.defaultPortionGrams ?: 0.0) else amount
        if (grams <= 0.0) return
        val entry = FoodEntryEntity(
            id = nextEntryId++,
            foodItemId = food.id,
            dateIso = todayIso,
            mealSlot = mealSlot,
            amount = amount,
            unit = unit,
            grams = grams
        )
        todayEntries.add(
            0,
            FoodEntryWithFood(
                entryId = entry.id,
                dateIso = entry.dateIso,
                mealSlot = entry.mealSlot,
                amount = entry.amount,
                unit = entry.unit,
                grams = entry.grams,
                foodItemId = food.id,
                name = food.name,
                defaultPortionName = food.defaultPortionName,
                kcalPer100g = food.kcalPer100g,
                proteinPer100g = food.proteinPer100g,
                carbsPer100g = food.carbsPer100g,
                sugarPer100g = food.sugarPer100g,
                fatPer100g = food.fatPer100g,
                saturatedFatPer100g = food.saturatedFatPer100g
            )
        )
    }

    fun deleteEntry(id: Long) {
        todayEntries.removeAll { it.entryId == id }
    }
}
