package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PlannedMealPoolEntity(
    var id: String = "",
    var mealName: String = "",
    var mealIngredients: List<MealIngredientEntity> = emptyList(),
    var plannedPortions: Double = 0.0,
    var remainingPortions: Double = 0.0,
    var imageUrl: String? = null,
    var tags: List<String> = emptyList(),
    var createdAt: Long = System.currentTimeMillis(),
    var createdByUid: String = "",
    var createdByName: String = "",
    @get:PropertyName("isFrozen") @set:PropertyName("isFrozen")
    var isFrozen: Boolean = false
) {
    val isFinished: Boolean get() = remainingPortions <= 0
}
