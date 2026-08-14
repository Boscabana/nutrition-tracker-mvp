package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import com.google.firebase.firestore.Exclude
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
    var brand: String? = null
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
}

@Keep
@Serializable
data class MealEntity(
    var id: Long = 0,
    var name: String = "",
    var ingredients: List<MealIngredientEntity> = emptyList(),
    var servings: Double = 1.0,
    var tags: List<String> = emptyList(),
    var imageUrl: String? = null,
    var lastModified: Long = System.currentTimeMillis()
) {
    @get:Exclude
    val totalKcal: Double get() = ingredients.sumOf { it.kcal }
    @get:Exclude
    val totalProtein: Double get() = ingredients.sumOf { it.protein }
    @get:Exclude
    val totalCarbs: Double get() = ingredients.sumOf { it.carbs }
    @get:Exclude
    val totalFat: Double get() = ingredients.sumOf { it.fat }

    @get:Exclude
    val totalWeight: Double get() = ingredients.sumOf { it.grams }

    @get:Exclude
    val kcalPerServing: Double get() = if (servings > 0) totalKcal / servings else totalKcal
}
