package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class FoodPortionEntity(
    val id: Long = 0,
    val name: String = "",
    val grams: Double = 0.0
)
