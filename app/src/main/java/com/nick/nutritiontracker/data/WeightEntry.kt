package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class WeightEntry(
    val dateIso: String = "",
    val weight: Double = 0.0
)

@Serializable
data class DayVerification(
    val dateIso: String = "",
    val isComplete: Boolean = false
)
