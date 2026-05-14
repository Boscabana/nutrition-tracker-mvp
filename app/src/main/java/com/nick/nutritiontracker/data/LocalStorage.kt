package com.nick.nutritiontracker.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class FoodItem(
    val id: String,
    val name: String,
    val calories: Int,
    val protein: Float
)

class LocalStorage(context: Context) {
    private val file = File(context.filesDir, "nutrition_data.json")

    // Fixed the configuration syntax here
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun saveFoodItems(items: List<FoodItem>) {
        try {
            val content = json.encodeToString(items)
            file.writeText(content)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFoodItems(): List<FoodItem> {
        return if (file.exists()) {
            try {
                val fileContent = file.readText()
                json.decodeFromString<List<FoodItem>>(fileContent)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        } else {
            emptyList()
        }
    }
}