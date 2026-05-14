package com.nick.nutritiontracker.data

data class FoodEntryEntity(
    val id: Long = 0,
    val foodItemId: Long,
    val dateIso: String,
    val mealSlot: String,
    val amount: Double,
    val unit: String,
    val grams: Double
)
