package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class FoodPackageEntity(
    val id: Long,
    val name: String,
    val quantity: Double,
    val unit: String
)
