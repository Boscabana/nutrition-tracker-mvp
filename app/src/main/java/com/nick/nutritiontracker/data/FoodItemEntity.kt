package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class FoodItemEntity(
    val id: Long = 0,
    val name: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val sugarPer100g: Double,
    val fatPer100g: Double,
    val saturatedFatPer100g: Double,
    val defaultPortionName: String? = null,
    val defaultPortionGrams: Double? = null,
    val barcode: String? = null
) {
    // These are "calculated fields" that your UI uses
    val complexCarbsPer100g: Double get() = carbsPer100g - sugarPer100g
    val unsaturatedFatPer100g: Double get() = fatPer100g - saturatedFatPer100g
}