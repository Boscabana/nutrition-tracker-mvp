package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class FoodPortionEntity(
    val id: Long,
    val name: String,
    val grams: Double
)
