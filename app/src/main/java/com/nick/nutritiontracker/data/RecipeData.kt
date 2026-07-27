package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class RecipeData(
    val meal: MealEntity,
    val relatedFoods: List<FoodItemEntity>
)
