package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class AiModelStatus(
    val modelName: String,
    val isAvailable: Boolean,
    val errorMessage: String? = null
)
