package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FoodItemEntity(
    var id: Long = 0,
    var name: String = "",
    var kcalPer100g: Double = 0.0,
    var proteinPer100g: Double = 0.0,
    var carbsPer100g: Double = 0.0,
    var sugarPer100g: Double = 0.0,
    var fatPer100g: Double = 0.0,
    var saturatedFatPer100g: Double = 0.0,
    var alcoholPercent: Double = 0.0,
    var baseUnit: String = "g",
    var portions: List<FoodPortionEntity> = emptyList(),
    var packages: List<FoodPackageEntity> = emptyList(),
    var barcode: String? = null,
    var brand: String? = null,
    var category: String? = null,
    @get:PropertyName("isGeneric") @set:PropertyName("isGeneric")
    var isGeneric: Boolean = false,
    var parentId: Long? = null,
    var store: String? = null,
    @get:PropertyName("isPantryItem") @set:PropertyName("isPantryItem")
    var isPantryItem: Boolean = false,
    var lastModified: Long = System.currentTimeMillis()
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
