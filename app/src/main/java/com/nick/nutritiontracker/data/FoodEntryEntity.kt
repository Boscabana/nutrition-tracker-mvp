package com.nick.nutritiontracker.data

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

@Serializable
data class FoodEntryEntity(
    val id: Long = 0,
    val dateIso: String = "",
    val mealSlot: String = "Snack",
    val amount: Double = 0.0,
    val unitLabel: String = "g",
    val grams: Double = 0.0,

    val foodItemId: Long = -1,
    val name: String = "",
    val brand: String? = null,
    val kcalPer100g: Double = 0.0,
    val proteinPer100g: Double = 0.0,
    val carbsPer100g: Double = 0.0,
    val sugarPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    val saturatedFatPer100g: Double = 0.0,
    val alcoholPercent: Double = 0.0,
    val baseUnit: String = "g",
    val store: String? = null,
    
    // New fields for meal support
    @get:PropertyName("isMeal") @set:PropertyName("isMeal")
    var isMeal: Boolean = false,
    val mealIngredients: List<MealIngredientEntity>? = null,
    @get:PropertyName("isPlanned") @set:PropertyName("isPlanned")
    var isPlanned: Boolean = false
) {
    @get:Exclude
    val kcal: Double get() = if (isMeal) mealIngredients?.sumOf { it.kcal } ?: 0.0 else kcalPer100g * grams / 100.0
    @get:Exclude
    val protein: Double get() = if (isMeal) mealIngredients?.sumOf { it.protein } ?: 0.0 else proteinPer100g * grams / 100.0
    @get:Exclude
    val carbs: Double get() = if (isMeal) mealIngredients?.sumOf { it.carbs } ?: 0.0 else carbsPer100g * grams / 100.0
    @get:Exclude
    val sugar: Double get() = if (isMeal) mealIngredients?.sumOf { it.sugar } ?: 0.0 else sugarPer100g * grams / 100.0
    @get:Exclude
    val complexCarbs: Double get() = if (isMeal) {
        mealIngredients?.sumOf { (it.carbsPer100g - it.sugarPer100g).coerceAtLeast(0.0) * it.grams / 100.0 } ?: 0.0
    } else {
        (carbsPer100g - sugarPer100g).coerceAtLeast(0.0) * grams / 100.0
    }
    @get:Exclude
    val fat: Double get() = if (isMeal) mealIngredients?.sumOf { it.fat } ?: 0.0 else fatPer100g * grams / 100.0
    @get:Exclude
    val saturatedFat: Double get() = if (isMeal) mealIngredients?.sumOf { it.saturatedFat } ?: 0.0 else saturatedFatPer100g * grams / 100.0
    @get:Exclude
    val unsaturatedFat: Double get() = if (isMeal) {
        mealIngredients?.sumOf { (it.fatPer100g - it.saturatedFatPer100g).coerceAtLeast(0.0) * it.grams / 100.0 } ?: 0.0
    } else {
        (fatPer100g - saturatedFatPer100g).coerceAtLeast(0.0) * grams / 100.0
    }

    fun displayAmount(): String = if (isMeal) "Meal" else "${clean(amount)} $unitLabel"

    private fun clean(v: Double): String =
        if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)
}
