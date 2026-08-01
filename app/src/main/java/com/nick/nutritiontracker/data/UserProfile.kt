package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
enum class Gender {
    MALE, FEMALE
}

@Serializable
enum class UserGoal {
    LOSE_WEIGHT, MAINTAIN, BUILD_MUSCLE
}

@Serializable
data class UserProfile(
    val firstName: String = "",
    val age: Int = 30,
    val weightKg: Double = 70.0,
    val heightCm: Int = 175,
    val gender: Gender = Gender.MALE,
    val goal: UserGoal = UserGoal.MAINTAIN,
    val goalIntensity: Int = 500, // Deficit or surplus
    val setupCompleted: Boolean = false,
    
    // Macro percentages (kept for now, will be automated later)
    val proteinPercent: Int = 20,
    val complexCarbsPercent: Int = 40,
    val sugarPercent: Int = 10,
    val unsaturatedFatPercent: Int = 20,
    val saturatedFatPercent: Int = 10,
    val stepGoal: Int = 10000,

    val weighInReminderEnabled: Boolean = false,
    val weighInReminderTime: String = "07:00",
    val breakfastReminderEnabled: Boolean = false,
    val breakfastReminderTime: String = "09:00"
) {
    val totalPercent: Int get() = proteinPercent + complexCarbsPercent + sugarPercent + unsaturatedFatPercent + saturatedFatPercent
    val isPercentValid: Boolean get() = totalPercent == 100

    /**
     * Grundumsatz (BMR) nach der Mifflin-St Jeor Formel.
     */
    val bmr: Double get() {
        return if (gender == Gender.MALE) {
            (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) + 5.0
        } else {
            (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) - 161.0
        }
    }

    /**
     * Das berechnete Kalorienbudget basierend auf dem Grundumsatz und dem Ziel.
     * Aktivitätskalorien sind hier NICHT enthalten.
     */
    val calorieBudget: Int get() {
        val adjustment = when (goal) {
            UserGoal.LOSE_WEIGHT -> -goalIntensity
            UserGoal.MAINTAIN -> 0
            UserGoal.BUILD_MUSCLE -> goalIntensity
        }
        return (bmr + adjustment).coerceAtLeast(1200.0).toInt()
    }

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
