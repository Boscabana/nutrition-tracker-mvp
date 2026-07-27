package com.nick.nutritiontracker.data

import com.google.firebase.firestore.Exclude
import kotlinx.serialization.Serializable

@Serializable
data class MealIngredientEntity(
    val id: Long = 0,
    val foodItemId: Long = 0,
    val name: String = "",
    val amount: Double = 0.0,
    val unitLabel: String = "g",
    val grams: Double = 0.0,
    // Store nutrient info at the time of adding to the meal to avoid issues if FoodItem changes? 
    // Or just reference FoodItem? Usually better to keep a copy for historical entries, 
    // but for templates, referencing FoodItem is fine.
    // However, FoodEntryEntity stores a copy. Let's store a copy here too for simplicity 
    // when calculating meal totals.
    val kcalPer100g: Double = 0.0,
    val proteinPer100g: Double = 0.0,
    val carbsPer100g: Double = 0.0,
    val sugarPer100g: Double = 0.0,
    val fatPer100g: Double = 0.0,
    val saturatedFatPer100g: Double = 0.0,
    val alcoholPercent: Double = 0.0,
    val baseUnit: String = "g",
    val store: String? = null,
    val brand: String? = null
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

@Serializable
data class MealEntity(
    val id: Long = 0,
    val name: String,
    val ingredients: List<MealIngredientEntity> = emptyList(),
    val servings: Double = 1.0
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
