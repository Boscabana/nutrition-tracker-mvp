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
    val alcoholPercent: Double = 0.0,
    val baseUnit: String = "g",
    val portions: List<FoodPortionEntity> = emptyList(),
    val packages: List<FoodPackageEntity> = emptyList(),
    val barcode: String? = null
) {
    val complexCarbsPer100g: Double
        get() = (carbsPer100g - sugarPer100g).coerceAtLeast(0.0)

    val unsaturatedFatPer100g: Double
        get() = (fatPer100g - saturatedFatPer100g).coerceAtLeast(0.0)

    val defaultPortion: FoodPortionEntity?
        get() = portions.firstOrNull()
}

fun calculateKcalPer100g(
    protein: Double,
    carbs: Double,
    fat: Double,
    alcoholPercent: Double
): Double {
    return protein * 4.1 +
            carbs * 4.1 +
            fat * 9.3 +
            alcoholPercent * 5.523
}
