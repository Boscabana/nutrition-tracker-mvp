package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class CommunityTopic(
    val name: String,
    val tag: String,
    val description: String,
    val icon: String
)

val OFFICIAL_COMMUNITIES = listOf(
    CommunityTopic("🌱 Veganes Kochen", "Vegan", "Rein pflanzliche Rezepte voller Geschmack & Nährstoffe", "🌱"),
    CommunityTopic("🥩 High Protein & Fitness", "High Protein", "Eiweißreiche Mahlzeiten für Muskelaufbau & Sättigung", "🥩"),
    CommunityTopic("🥑 Low Carb & Keto", "Low Carb", "Kohlenhydratarme Gerichte für optimale Fettverbrennung", "🥑"),
    CommunityTopic("🍱 Meal Prep & Schnelle Küche", "Meal Prep", "Perfekt zum Vorkochen & schnelle Alltagsgerichte", "🍱"),
    CommunityTopic("🍰 Gesundes Backen & Desserts", "Gesundes Backen", "Zuckerarme & nährstoffreiche Backideen & Desserts", "🍰"),
    CommunityTopic("🥗 Balanced & Healthy", "Balanced", "Ausgewogene Alltagsküche für Wohlbefinden & Energie", "🥗")
)

@Keep
@Serializable
@IgnoreExtraProperties
data class CommunityPost(
    val id: String = "",
    val authorUid: String = "official_app_editor",
    val authorName: String = "NutritionTracker Redaktion",
    val authorAvatarUrl: String? = null,
    val topic: String = "🌱 Veganes Kochen",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val likedByUids: List<String> = emptyList(),
    val payloadType: String = "RECIPE", // "RECIPE" or "FOOD"
    val payloadJson: String? = null,
    val isOfficial: Boolean = true
) {
    constructor() : this("")
}
