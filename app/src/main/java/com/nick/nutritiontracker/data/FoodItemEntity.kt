package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class FoodItemEntity(
    val id: Long = 0,
    val name: String = "",
    val kcalPer100g: Double = 0.0,
    val proteinPer100g: Double = 0.0,
    val carbsPer100g: Double = 0.0,
    val sugarPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    val saturatedFatPer100g: Double = 0.0,
    val alcoholPercent: Double = 0.0,
    val baseUnit: String = "g",
    val portions: List<FoodPortionEntity> = emptyList(),
    val packages: List<FoodPackageEntity> = emptyList(),
    val barcode: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val isGeneric: Boolean = false,
    val parentId: Long? = null,
    val store: String? = null
) {
    val complexCarbsPer100g: Double
        get() = (carbsPer100g - sugarPer100g).coerceAtLeast(0.0)

    val unsaturatedFatPer100g: Double
        get() = (fatPer100g - saturatedFatPer100g).coerceAtLeast(0.0)

    val defaultPortion: FoodPortionEntity?
        get() = portions.firstOrNull()

    fun getAllPortions(parent: FoodItemEntity? = null): List<FoodPortionEntity> {
        val parentPortions = parent?.portions ?: emptyList()
        // Combine but prefer own if name matches? Usually names are unique like "Stück" or "Beutel"
        return (parentPortions + portions).distinctBy { it.name }
    }
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
