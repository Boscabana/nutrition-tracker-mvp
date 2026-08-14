package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class AiGenericFoodResult(
    val name: String,
    val kcalPer100: Double,
    val proteinPer100: Double,
    val carbsPer100: Double,
    val sugarPer100: Double,
    val fatPer100: Double,
    val saturatedFatPer100: Double,
    val baseUnit: String = "g", // "g" or "ml"
    val category: String? = null,
    val brand: String? = null,
    val portions: List<AiFoodPortion> = emptyList()
)

@Serializable
data class AiFoodPortion(
    val name: String,
    val grams: Double
)
