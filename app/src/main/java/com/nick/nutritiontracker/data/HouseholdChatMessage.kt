package com.nick.nutritiontracker.data

import androidx.annotation.Keep
import com.google.firebase.firestore.IgnoreExtraProperties
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class ChatMessageType {
    TEXT,
    RECIPE,
    FOOD
}

@Keep
@Serializable
@IgnoreExtraProperties
data class HouseholdChatMessage(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: ChatMessageType = ChatMessageType.TEXT,
    val payloadJson: String? = null
) {
    constructor() : this("")
}
