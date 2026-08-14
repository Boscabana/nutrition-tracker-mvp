package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FoodPortionEntity(
    var id: Long = 0,
    var name: String = "",
    var grams: Double = 0.0
)
