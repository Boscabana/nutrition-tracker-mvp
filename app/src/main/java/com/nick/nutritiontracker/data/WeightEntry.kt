package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class WeightEntry(
    val dateIso: String = "",
    val weight: Double = 0.0
) {
    constructor() : this("", 0.0)
}

@Keep
@Serializable
data class DayVerification(
    val dateIso: String = "",
    val isComplete: Boolean = false
) {
    constructor() : this("", false)
}
