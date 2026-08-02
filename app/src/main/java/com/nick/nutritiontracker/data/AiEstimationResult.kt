package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class AiEstimationResult(
    val name: String = "",
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val sugar: Double = 0.0,
    val fat: Double = 0.0,
    val saturatedFat: Double = 0.0,
    val grams: Double = 100.0,
    val confidence: Double = 0.0
)
