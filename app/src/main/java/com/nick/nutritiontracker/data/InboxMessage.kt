package com.nick.nutritiontracker.data

import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageType {
    RECIPE, FOOD
}

@Serializable
data class InboxMessage(
    val id: String = "",
    val fromUid: String = "",
    val fromName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.RECIPE,
    val payloadJson: String = "",
    @get:PropertyName("isRead") @set:PropertyName("isRead")
    var isRead: Boolean = false
)
