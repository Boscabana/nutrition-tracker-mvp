package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class MealIngredientEntity(
    val foodItemId: Long,
    val name: String,
    val amount: Double,
    val unitLabel: String,
    val grams: Double,
    // Store nutrient info at the time of adding to the meal to avoid issues if FoodItem changes? 
    // Or just reference FoodItem? Usually better to keep a copy for historical entries, 
    // but for templates, referencing FoodItem is fine.
    // However, FoodEntryEntity stores a copy. Let's store a copy here too for simplicity 
    // when calculating meal totals.
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val sugarPer100g: Double,
    val fatPer100g: Double,
    val saturatedFatPer100g: Double,
    val alcoholPercent: Double = 0.0,
    val baseUnit: String = "g"
) {
    val kcal: Double get() = kcalPer100g * grams / 100.0
    val protein: Double get() = proteinPer100g * grams / 100.0
    val carbs: Double get() = carbsPer100g * grams / 100.0
    val sugar: Double get() = sugarPer100g * grams / 100.0
    val fat: Double get() = fatPer100g * grams / 100.0
    val saturatedFat: Double get() = saturatedFatPer100g * grams / 100.0
}

@Serializable
data class MealEntity(
    val id: Long = 0,
    val name: String,
    val ingredients: List<MealIngredientEntity> = emptyList(),
    val servings: Double = 1.0
) {
    val totalKcal: Double get() = ingredients.sumOf { it.kcal }
    val totalProtein: Double get() = ingredients.sumOf { it.protein }
    val totalCarbs: Double get() = ingredients.sumOf { it.carbs }
    val totalFat: Double get() = ingredients.sumOf { it.fat }

    val kcalPerServing: Double get() = if (servings > 0) totalKcal / servings else totalKcal
}
