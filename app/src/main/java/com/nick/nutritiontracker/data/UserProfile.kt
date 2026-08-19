package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName
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
enum class DietaryPreference {
    NONE, VEGETARIAN, VEGAN, PALEO, KETO, LOW_CARB
}

@Keep
@Serializable
data class UserProfile(
    var firstName: String = "",
    var age: Int = 30,
    var weightKg: Double = 70.0,
    var heightCm: Int = 175,
    var gender: Gender = Gender.MALE,
    var goal: UserGoal = UserGoal.MAINTAIN,
    var goalIntensity: Int = 500, // Deficit or surplus
    var dietaryPreference: DietaryPreference = DietaryPreference.NONE,
    var setupCompleted: Boolean = false,
    
    // Macro percentages (kept for now, will be automated later)
    var proteinPercent: Int = 20,
    var complexCarbsPercent: Int = 40,
    var sugarPercent: Int = 10,
    var unsaturatedFatPercent: Int = 20,
    var saturatedFatPercent: Int = 10,
    var stepGoal: Int = 10000,

    var weighInReminderEnabled: Boolean = false,
    var weighInReminderTime: String = "07:00",
    var breakfastReminderEnabled: Boolean = false,
    var breakfastReminderTime: String = "09:00",
    
    var initialWeight: Double? = null,
    var metabolicFactor: Double = 1.0,
    @get:PropertyName("isPremium")
    @set:PropertyName("isPremium")
    var premium: Boolean = false,
    var aiImagesGeneratedToday: Int = 0,
    var lastAiImageDate: String = "",
    var fcmToken: String? = null
) {
    @get:com.google.firebase.firestore.Exclude
    val isPremium: Boolean get() = premium
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
