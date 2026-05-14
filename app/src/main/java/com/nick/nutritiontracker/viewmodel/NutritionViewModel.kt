package com.nick.nutritiontracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nick.nutritiontracker.data.AppDatabase
import com.nick.nutritiontracker.data.FoodEntryEntity
import com.nick.nutritiontracker.data.FoodItemEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.get(application).foodDao()
    val todayIso: String = LocalDate.now().toString()

    val foods = dao.observeFoodItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val todayEntries = dao.observeEntriesForDay(todayIso).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addFood(name: String, kcal: Double, protein: Double, carbs: Double, fat: Double, portionName: String?, portionGrams: Double?, barcode: String? = null) = viewModelScope.launch {
        dao.insertFoodItem(
            FoodItemEntity(
                name = name.trim(), barcode = barcode?.trim()?.takeIf { it.isNotBlank() }, kcalPer100g = kcal,
                proteinPer100g = protein, carbsPer100g = carbs, fatPer100g = fat,
                defaultPortionName = portionName?.trim()?.takeIf { it.isNotBlank() },
                defaultPortionGrams = portionGrams?.takeIf { it > 0.0 }
            )
        )
    }

    fun addEntry(food: FoodItemEntity, amount: Double, unit: String, mealSlot: String) = viewModelScope.launch {
        val grams = if (unit == "portion") amount * (food.defaultPortionGrams ?: 0.0) else amount
        if (grams <= 0.0) return@launch
        dao.insertFoodEntry(FoodEntryEntity(foodItemId = food.id, dateIso = todayIso, mealSlot = mealSlot, amount = amount, unit = unit, grams = grams))
    }

    fun deleteEntry(id: Long) = viewModelScope.launch { dao.deleteEntry(id) }
}
