package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val foods: List<FoodItemEntity> = emptyList(),
    val meals: List<MealEntity> = emptyList()
)
