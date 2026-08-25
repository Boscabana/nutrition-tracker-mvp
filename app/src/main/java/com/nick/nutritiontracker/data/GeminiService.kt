package com.nick.nutritiontracker.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.UsageMetadata
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.serialization.json.Json
import com.google.firebase.ai.GenerativeModel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class GeminiResult<T>(
    val data: T?,
    val usage: UsageMetadata?
)

class GeminiService(private val config: ConfigManager) {
    private val json = Json { ignoreUnknownKeys = true }
    // Using Frankfurt region for EU compliance
    private val region = "europe-west3"

    private val ai = Firebase.ai(backend = GenerativeBackend.googleAI())
    private val modelCache = mutableMapOf<String, GenerativeModel>()
    private val cacheMutex = Mutex()

    private suspend fun getModel(modelName: String): GenerativeModel {
        val cacheKey = "$modelName-${config.maxOutputTokens}-${config.temperature}"
        return cacheMutex.withLock {
            modelCache.getOrPut(cacheKey) {
                ai.generativeModel(
                    modelName = modelName,
                    generationConfig = generationConfig {
                        maxOutputTokens = config.maxOutputTokens
                        temperature = config.temperature.toFloat()
                    }
                )
            }
        }
    }

    suspend fun estimateNutrition(bitmap: Bitmap, preferredModel: String? = null): GeminiResult<AiEstimationResult> {
        val scaledBitmap = scaleBitmap(bitmap)
        val modelToUse = preferredModel ?: config.geminiModelName
        
        Log.d("GeminiService", "Analyzing with Firebase AI Logic ($region) model: $modelToUse")
        return try {
            callGemini(scaledBitmap, modelToUse)
        } catch (e: Exception) {
            Log.e("GeminiService", "AI Logic model failed: $modelToUse", e)
            throw e
        }
    }

    suspend fun testModelAvailability(modelName: String): AiModelStatus {
        return try {
            val model = getModel(modelName)
            model.generateContent("Respond with OK").text?.isNotBlank()
            AiModelStatus(modelName, true)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: e.toString()
            Log.w("GeminiService", "Model $modelName check failed: $msg")
            AiModelStatus(modelName, false, msg)
        }
    }

    private suspend fun callGemini(bitmap: Bitmap, modelName: String): GeminiResult<AiEstimationResult> {
        val model = getModel(modelName)
        
        val prompt = """
            Analyse this image of a meal. 
            Identify the main dish and estimate its weight and nutritional values.
            Return ONLY a valid JSON object in the following format:
            {
              "name": "Dish Name",
              "kcal": 0.0,
              "protein": 0.0,
              "carbs": 0.0,
              "sugar": 0.0,
              "fat": 0.0,
              "saturatedFat": 0.0,
              "grams": 0.0,
              "confidence": 0.0
            }
            All values should be for the entire portion seen in the image. 
            If the image does not show food, return an empty object with zero values.
            Language: German (for the name field)
        """.trimIndent()

        val response = model.generateContent(
            content {
                image(bitmap)
                text(prompt)
            }
        )
        
        val responseText = response.text ?: ""
        Log.d("GeminiService", "Raw AI Logic Response ($modelName): ${responseText.take(100)}...")
        
        val cleanJson = extractJson(responseText)
        if (cleanJson.isBlank()) return GeminiResult(null, response.usageMetadata)
        
        return GeminiResult(json.decodeFromString<AiEstimationResult>(cleanJson), response.usageMetadata)
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxSize = config.imageResizePx
        var width = bitmap.width
        var height = bitmap.height

        val bitmapRatio = width.toFloat() / height.toFloat()
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        return if (start != -1 && end != -1) {
            text.substring(start, end + 1)
        } else {
            text
        }
    }

    suspend fun estimateGenericFood(foodName: String, categories: List<String>, isBrandSearch: Boolean = false, preferredModel: String? = null): GeminiResult<AiGenericFoodResult> {
        val modelToUse = preferredModel ?: config.geminiModelName
        val model = getModel(modelToUse)
        
        val brandPromptPart = if (isBrandSearch) {
            "This is a search for a specific branded product. If you identify the brand, include it in the 'brand' field and provide real-world commercial portion sizes (e.g., '1 Riegel', '1 Dose', '1 Becher') in the 'portions' array."
        } else {
            "This is a general food item. Use 'portions' ONLY for very standard sizes (e.g., '1 Stück')."
        }

        val prompt = """
            You are a nutrition expert. Analyze the food item: '$foodName'.
            $brandPromptPart
            
            Provide average nutritional values per 100g (or 100ml for liquids).
            Also select the most suitable category from this list: ${categories.joinToString(", ")}.
            
            IMPORTANT RULES:
            1. All values must be ABSOLUTE numbers. 
            2. DO NOT use ranges. Use '12.5'.
            3. Macro values must be grams per 100 units.
            4. If the item is a liquid, set 'baseUnit' to 'ml', otherwise 'g'.
            5. Select EXACTLY ONE category from the provided list. If none fits, use 'Sonstiges'.
            6. Language: German for 'name', 'brand', and portion names.
            
            Return ONLY a valid JSON object in the following format:
            {
              "name": "Lebensmittel Name",
              "brand": "Markenname oder null",
              "kcalPer100": 0.0,
              "proteinPer100": 0.0,
              "carbsPer100": 0.0,
              "sugarPer100": 0.0,
              "fatPer100": 0.0,
              "saturatedFatPer100": 0.0,
              "baseUnit": "g",
              "category": "Chosen Category",
              "portions": [
                { "name": "Portionsname", "grams": 0.0 }
              ]
            }
        """.trimIndent()

        return try {
            val response = model.generateContent(prompt)
            val responseText = response.text ?: ""
            val cleanJson = extractJson(responseText)
            if (cleanJson.isBlank()) GeminiResult(null, response.usageMetadata)
            else GeminiResult(json.decodeFromString<AiGenericFoodResult>(cleanJson), response.usageMetadata)
        } catch (e: Exception) {
            Log.e("GeminiService", "Generic food estimation failed", e)
            GeminiResult(null, null)
        }
    }

    suspend fun categorizeItem(itemName: String, categories: List<String>): String {
        val prompt = """
            Identify the most suitable category for the item '$itemName' from this list: ${categories.joinToString(", ")}.
            Return ONLY a valid JSON object in the following format:
            {
              "category": "Chosen Category"
            }
            If unsure or if no category fits, use 'Sonstiges'.
            Do NOT create new categories.
        """.trimIndent()

        return try {
            val model = getModel(config.geminiModelName)
            val response = model.generateContent(prompt)
            val cleanJson = extractJson(response.text ?: "")
            val result = json.parseToJsonElement(cleanJson).asJsonObject()
            result["category"]?.asString() ?: "Sonstiges"
        } catch (e: Exception) {
            Log.e("GeminiService", "Categorization failed for $itemName", e)
            "Sonstiges"
        }
    }
}

private fun kotlinx.serialization.json.JsonElement.asJsonObject() = this as? kotlinx.serialization.json.JsonObject ?: kotlinx.serialization.json.JsonObject(emptyMap())
private fun kotlinx.serialization.json.JsonElement?.asString() = this?.toString()?.replace("\"", "") ?: ""
