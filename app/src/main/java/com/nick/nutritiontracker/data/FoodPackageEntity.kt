package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class FoodPackageEntity(
    val id: Long = 0,
    val name: String = "",
    val quantity: Double = 0.0,
    val unit: String = "g"
)
