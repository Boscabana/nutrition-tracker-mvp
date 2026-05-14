package com.nick.nutritiontracker.data

data class FoodItemEntity(
    val id: Long = 0,
    val name: String,
    val barcode: String? = null,
    val brand: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val sugarPer100g: Double,
    val fatPer100g: Double,
    val saturatedFatPer100g: Double,
    val defaultPortionName: String? = null,
    val defaultPortionGrams: Double? = null
) {
    val complexCarbsPer100g: Double get() = (carbsPer100g - sugarPer100g).coerceAtLeast(0.0)
    val unsaturatedFatPer100g: Double get() = (fatPer100g - saturatedFatPer100g).coerceAtLeast(0.0)
}
