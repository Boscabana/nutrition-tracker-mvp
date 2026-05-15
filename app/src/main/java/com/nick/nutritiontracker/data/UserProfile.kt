package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val firstName: String = "",
    val weightKg: Double = 70.0,
    val heightCm: Int = 175,
    val goalDescription: String = "Gewicht halten",
    val calorieBudget: Int = 2000,
    val proteinPercent: Int = 20,
    val complexCarbsPercent: Int = 40,
    val sugarPercent: Int = 10,
    val unsaturatedFatPercent: Int = 20,
    val saturatedFatPercent: Int = 10
) {
    val totalPercent: Int get() = proteinPercent + complexCarbsPercent + sugarPercent + unsaturatedFatPercent + saturatedFatPercent
    val isPercentValid: Boolean get() = totalPercent == 100

    // Goals in grams
    val proteinGoalGrams: Double get() = (calorieBudget * (proteinPercent / 100.0)) / 4.0
    val complexCarbsGoalGrams: Double get() = (calorieBudget * (complexCarbsPercent / 100.0)) / 4.0
    val sugarGoalGrams: Double get() = (calorieBudget * (sugarPercent / 100.0)) / 4.0
    val unsaturatedFatGoalGrams: Double get() = (calorieBudget * (unsaturatedFatPercent / 100.0)) / 9.0
    val saturatedFatGoalGrams: Double get() = (calorieBudget * (saturatedFatPercent / 100.0)) / 9.0
    
    // Derived total carbs and total fat goals
    val totalCarbsGoalGrams: Double get() = complexCarbsGoalGrams + sugarGoalGrams
    val totalFatGoalGrams: Double get() = unsaturatedFatGoalGrams + saturatedFatGoalGrams
}
