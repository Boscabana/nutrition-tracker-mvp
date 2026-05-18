package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
enum class Gender {
    MALE, FEMALE
}

@Serializable
data class UserProfile(
    val firstName: String = "",
    val age: Int = 31,
    val weightKg: Double = 84.0,
    val heightCm: Int = 175,
    val gender: Gender = Gender.MALE,
    val goalDescription: String = "Gewicht halten",
    val calorieBudget: Int = 2000,
    val proteinPercent: Int = 20,
    val complexCarbsPercent: Int = 40,
    val sugarPercent: Int = 10,
    val unsaturatedFatPercent: Int = 20,
    val saturatedFatPercent: Int = 10,
    val activityLevel: Double = 1.2 // PAL factor
) {
    val totalPercent: Int get() = proteinPercent + complexCarbsPercent + sugarPercent + unsaturatedFatPercent + saturatedFatPercent
    val isPercentValid: Boolean get() = totalPercent == 100

    /**
     * Grundumsatz (BMR) nach der Harris-Benedict-Formel (revidiert von Roza und Shizgal, 1984).
     */
    val bmr: Double get() {
        return if (gender == Gender.MALE) {
            88.362 + (13.397 * weightKg) + (4.799 * heightCm) - (5.677 * age)
        } else {
            447.593 + (9.247 * weightKg) + (3.098 * heightCm) - (4.330 * age)
        }
    }

    // TDEE (Total Daily Energy Expenditure) without extra activity
    val tdee: Double get() = bmr * activityLevel

    // Goals in grams based on calorieBudget
    val proteinGoalGrams: Double get() = (calorieBudget * (proteinPercent / 100.0)) / 4.0
    val complexCarbsGoalGrams: Double get() = (calorieBudget * (complexCarbsPercent / 100.0)) / 4.0
    val sugarGoalGrams: Double get() = (calorieBudget * (sugarPercent / 100.0)) / 4.0
    val unsaturatedFatGoalGrams: Double get() = (calorieBudget * (unsaturatedFatPercent / 100.0)) / 9.0
    val saturatedFatGoalGrams: Double get() = (calorieBudget * (saturatedFatPercent / 100.0)) / 9.0
    
    // Derived total carbs and total fat goals
    val totalCarbsGoalGrams: Double get() = complexCarbsGoalGrams + sugarGoalGrams
    val totalFatGoalGrams: Double get() = unsaturatedFatGoalGrams + saturatedFatGoalGrams
}
