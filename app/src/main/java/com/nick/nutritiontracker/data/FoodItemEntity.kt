package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

import kotlin.math.abs

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
    fun isSimilarTo(other: FoodEntryEntity): Boolean {
        val otherBarcode = other.barcode?.trim() ?: ""
        val thisBarcode = barcode?.trim() ?: ""
        
        // Barcode-Match ist am stärksten (wenn vorhanden)
        if (otherBarcode.isNotEmpty() && thisBarcode.isNotEmpty() && otherBarcode == thisBarcode) return true
        
        val otherName = other.name.trim().lowercase()
        val thisName = name.trim().lowercase()
        val otherBrand = other.brand?.trim()?.lowercase() ?: ""
        val thisBrand = brand?.trim()?.lowercase() ?: ""

        // Namens-Match (Name & Marke müssen übereinstimmen)
        return thisName == otherName && thisBrand == otherBrand
    }

    private fun eq(a: Double, b: Double) = abs(a - b) < 0.01

    fun matchesDataOf(other: FoodEntryEntity): Boolean {
        if (!isSimilarTo(other)) return false
        
        return eq(kcalPer100g, other.kcalPer100g) &&
                eq(proteinPer100g, other.proteinPer100g) &&
                eq(carbsPer100g, other.carbsPer100g) &&
                eq(sugarPer100g, other.sugarPer100g) &&
                eq(fatPer100g, other.fatPer100g) &&
                eq(saturatedFatPer100g, other.saturatedFatPer100g) &&
                eq(alcoholPercent, other.alcoholPercent) &&
                baseUnit.trim().lowercase() == other.baseUnit.trim().lowercase() &&
                (category?.trim() ?: "") == (other.category?.trim() ?: "") &&
                (barcode?.trim() ?: "") == (other.barcode?.trim() ?: "") &&
                isGeneric == other.isGeneric
    }

    fun matchesIngredient(ing: MealIngredientEntity): Boolean {
        val other = FoodEntryEntity(
            name = ing.name, brand = ing.brand, kcalPer100g = ing.kcalPer100g,
            proteinPer100g = ing.proteinPer100g, carbsPer100g = ing.carbsPer100g,
            sugarPer100g = ing.sugarPer100g, fatPer100g = ing.fatPer100g,
            saturatedFatPer100g = ing.saturatedFatPer100g, alcoholPercent = ing.alcoholPercent,
            baseUnit = ing.baseUnit, category = ing.category, barcode = ing.barcode,
            isGeneric = ing.isGeneric
        )
        return matchesDataOf(other)
    }

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
