package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class Household(
    val id: String = "",
    val name: String = "",
    val adminUid: String = "",
    val members: List<String> = emptyList(), // List of UIDs
    val inviteCode: String = ""
)
