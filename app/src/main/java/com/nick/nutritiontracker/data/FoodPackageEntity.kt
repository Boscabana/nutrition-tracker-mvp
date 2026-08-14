package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FoodPackageEntity(
    var id: Long = 0,
    var name: String = "",
    var quantity: Double = 0.0,
    var unit: String = "g"
)
