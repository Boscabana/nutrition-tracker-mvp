package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class FoodEntryEntity(
    val id: Long,
    val dateIso: String,
    val mealSlot: String,
    val amount: Double,
    val unitLabel: String,
    val grams: Double,

    val foodItemId: Long,
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val sugarPer100g: Double,
    val fatPer100g: Double,
    val saturatedFatPer100g: Double,
    val alcoholPercent: Double = 0.0,
    val baseUnit: String = "g",
    val store: String? = null,
    
    // New fields for meal support
    val isMeal: Boolean = false,
    val mealIngredients: List<MealIngredientEntity>? = null
) {
    val kcal: Double get() = if (isMeal) mealIngredients?.sumOf { it.kcal } ?: 0.0 else kcalPer100g * grams / 100.0
    val protein: Double get() = if (isMeal) mealIngredients?.sumOf { it.protein } ?: 0.0 else proteinPer100g * grams / 100.0
    val carbs: Double get() = if (isMeal) mealIngredients?.sumOf { it.carbs } ?: 0.0 else carbsPer100g * grams / 100.0
    val sugar: Double get() = if (isMeal) mealIngredients?.sumOf { it.sugar } ?: 0.0 else sugarPer100g * grams / 100.0
    val complexCarbs: Double get() = if (isMeal) {
        mealIngredients?.sumOf { (it.carbsPer100g - it.sugarPer100g).coerceAtLeast(0.0) * it.grams / 100.0 } ?: 0.0
    } else {
        (carbsPer100g - sugarPer100g).coerceAtLeast(0.0) * grams / 100.0
    }
    val fat: Double get() = if (isMeal) mealIngredients?.sumOf { it.fat } ?: 0.0 else fatPer100g * grams / 100.0
    val saturatedFat: Double get() = if (isMeal) mealIngredients?.sumOf { it.saturatedFat } ?: 0.0 else saturatedFatPer100g * grams / 100.0
    val unsaturatedFat: Double get() = if (isMeal) {
        mealIngredients?.sumOf { (it.fatPer100g - it.saturatedFatPer100g).coerceAtLeast(0.0) * it.grams / 100.0 } ?: 0.0
    } else {
        (fatPer100g - saturatedFatPer100g).coerceAtLeast(0.0) * grams / 100.0
    }

    fun displayAmount(): String = if (isMeal) "Meal" else "${clean(amount)} $unitLabel"

    private fun clean(v: Double): String =
        if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)
}
