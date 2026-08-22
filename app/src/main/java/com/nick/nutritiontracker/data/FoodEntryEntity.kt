package com.nick.nutritiontracker.data

import com.google.firebase.firestore.Exclude
import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class FoodEntryEntity(
    var id: Long = 0,
    var dateIso: String = "",
    var mealSlot: String = "Snack",
    var amount: Double = 0.0,
    var unitLabel: String = "g",
    var grams: Double = 0.0,

    var foodItemId: Long = -1,
    var name: String = "",
    var brand: String? = null,
    var kcalPer100g: Double = 0.0,
    var proteinPer100g: Double = 0.0,
    var carbsPer100g: Double = 0.0,
    var sugarPer100g: Double = 0.0,
    var fatPer100g: Double = 0.0,
    var saturatedFatPer100g: Double = 0.0,
    var alcoholPercent: Double = 0.0,
    var baseUnit: String = "g",
    var store: String? = null,
    var category: String? = null,
    var barcode: String? = null,
    @get:PropertyName("isGeneric") @set:PropertyName("isGeneric")
    var isGeneric: Boolean = false,
    
    // New fields for meal support
    @get:PropertyName("isMeal") @set:PropertyName("isMeal")
    var isMeal: Boolean = false,
    var mealIngredients: List<MealIngredientEntity>? = null,
    @get:PropertyName("isPlanned") @set:PropertyName("isPlanned")
    var isPlanned: Boolean = false,
    var imageUrl: String? = null,
    var tags: List<String> = emptyList(),
    
    // Tracking for shared plans and pool
    var poolItemId: String? = null,
    var originPlannedEntryId: Long? = null,
    @get:PropertyName("isFromFreezer") @set:PropertyName("isFromFreezer")
    var isFromFreezer: Boolean = false,

    var plannedByUid: String? = null,
    var plannedByName: String? = null,
    var lastModifiedByUid: String? = null,
    var lastModifiedByName: String? = null
) {
    @get:Exclude
    val kcal: Double get() = if (isMeal) mealIngredients?.sumOf { it.kcal } ?: 0.0 else kcalPer100g * grams / 100.0
    @get:Exclude
    val protein: Double get() = if (isMeal) mealIngredients?.sumOf { it.protein } ?: 0.0 else proteinPer100g * grams / 100.0
    @get:Exclude
    val carbs: Double get() = if (isMeal) mealIngredients?.sumOf { it.carbs } ?: 0.0 else carbsPer100g * grams / 100.0
    @get:Exclude
    val sugar: Double get() = if (isMeal) mealIngredients?.sumOf { it.sugar } ?: 0.0 else sugarPer100g * grams / 100.0
    @get:Exclude
    val complexCarbs: Double get() = if (isMeal) {
        mealIngredients?.sumOf { (it.carbsPer100g - it.sugarPer100g).coerceAtLeast(0.0) * it.grams / 100.0 } ?: 0.0
    } else {
        (carbsPer100g - sugarPer100g).coerceAtLeast(0.0) * grams / 100.0
    }
    @get:Exclude
    val fat: Double get() = if (isMeal) mealIngredients?.sumOf { it.fat } ?: 0.0 else fatPer100g * grams / 100.0
    @get:Exclude
    val saturatedFat: Double get() = if (isMeal) mealIngredients?.sumOf { it.saturatedFat } ?: 0.0 else saturatedFatPer100g * grams / 100.0
    @get:Exclude
    val unsaturatedFat: Double get() = if (isMeal) {
        mealIngredients?.sumOf { (it.fatPer100g - it.saturatedFatPer100g).coerceAtLeast(0.0) * it.grams / 100.0 } ?: 0.0
    } else {
        (fatPer100g - saturatedFatPer100g).coerceAtLeast(0.0) * grams / 100.0
    }

    fun displayAmount(): String {
        val effectiveGrams = if (isMeal) mealIngredients?.sumOf { it.grams } ?: 0.0 else grams
        val gramsStr = clean(effectiveGrams)
        return if (unitLabel == "g" || unitLabel == "ml") {
            "$gramsStr $unitLabel"
        } else {
            "$gramsStr$baseUnit (${clean(amount)} $unitLabel)"
        }
    }

    private fun clean(v: Double): String =
        if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)
}
