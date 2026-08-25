package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class MealIngredientEntity(
    var id: Long = 0,
    var foodItemId: Long = 0,
    var name: String = "",
    var amount: Double = 0.0,
    var unitLabel: String = "g",
    var grams: Double = 0.0,
    var kcalPer100g: Double = 0.0,
    var proteinPer100g: Double = 0.0,
    var carbsPer100g: Double = 0.0,
    var sugarPer100g: Double = 0.0,
    var fatPer100g: Double = 0.0,
    var saturatedFatPer100g: Double = 0.0,
    var alcoholPercent: Double = 0.0,
    var baseUnit: String = "g",
    var store: String? = null,
    var brand: String? = null,
    var category: String? = null,
    var barcode: String? = null,
    @get:PropertyName("isGeneric") @set:PropertyName("isGeneric")
    var isGeneric: Boolean = false
) {
    @get:Exclude
    val kcal: Double get() = kcalPer100g * grams / 100.0
    @get:Exclude
    val protein: Double get() = proteinPer100g * grams / 100.0
    @get:Exclude
    val carbs: Double get() = carbsPer100g * grams / 100.0
    @get:Exclude
    val sugar: Double get() = sugarPer100g * grams / 100.0
    @get:Exclude
    val fat: Double get() = fatPer100g * grams / 100.0
    @get:Exclude
    val saturatedFat: Double get() = saturatedFatPer100g * grams / 100.0
    @get:Exclude
    val complexCarbs: Double get() = (carbsPer100g - sugarPer100g).coerceAtLeast(0.0) * grams / 100.0
    @get:Exclude
    val unsaturatedFat: Double get() = (fatPer100g - saturatedFatPer100g).coerceAtLeast(0.0) * grams / 100.0

    fun displayAmount(): String = displayAmountFor(amount, grams)

    fun displayAmountFor(amt: Double, g: Double): String {
        val amountStr = if (amt % 1.0 == 0.0) "%.0f".format(amt) else "%.1f".format(amt)
        val gramsStr = if (g % 1.0 == 0.0) "%.0f".format(g) else "%.1f".format(g)
        return if (unitLabel == "g" || unitLabel == "ml") {
            "$gramsStr $unitLabel"
        } else {
            "$gramsStr$baseUnit ($amountStr $unitLabel)"
        }
    }
}

@Keep
@Serializable
@IgnoreExtraProperties
data class MealEntity(
    var id: Long = 0,
    var name: String = "",
    var ingredients: List<MealIngredientEntity> = emptyList(),
    var servings: Double = 1.0,
    var tags: List<String> = emptyList(),
    var imageUrl: String? = null,
    var lastModified: Long = System.currentTimeMillis()
) {
    constructor() : this(0)

    @get:Exclude
    val totalKcal: Double get() = ingredients.sumOf { it.kcal }
    @get:Exclude
    val totalProtein: Double get() = ingredients.sumOf { it.protein }
    @get:Exclude
    val totalCarbs: Double get() = ingredients.sumOf { it.carbs }
    @get:Exclude
    val totalFat: Double get() = ingredients.sumOf { it.fat }
    @get:Exclude
    val totalSugar: Double get() = ingredients.sumOf { it.sugar }
    @get:Exclude
    val totalSaturatedFat: Double get() = ingredients.sumOf { it.saturatedFat }
    @get:Exclude
    val totalComplexCarbs: Double get() = ingredients.sumOf { it.complexCarbs }
    @get:Exclude
    val totalUnsaturatedFat: Double get() = ingredients.sumOf { it.unsaturatedFat }

    @get:Exclude
    val totalWeight: Double get() = ingredients.sumOf { it.grams }

    @get:Exclude
    val kcalPerServing: Double get() = if (servings > 0) totalKcal / servings else totalKcal
}
