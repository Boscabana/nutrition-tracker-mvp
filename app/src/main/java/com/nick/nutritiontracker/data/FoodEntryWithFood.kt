package com.nick.nutritiontracker.data

data class FoodEntryWithFood(
    val entryId: Long,
    val dateIso: String,
    val mealSlot: String,
    val amount: Double,
    val unit: String,
    val grams: Double,
    val foodItemId: Long,
    val name: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double
) {
    val kcal: Double get() = kcalPer100g * grams / 100.0
    val protein: Double get() = proteinPer100g * grams / 100.0
    val carbs: Double get() = carbsPer100g * grams / 100.0
    val fat: Double get() = fatPer100g * grams / 100.0
}
