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

    private suspend fun <T> executeWithFallback(
        preferredModel: String?,
        block: suspend (modelName: String) -> T
    ): T {
        val primary = preferredModel ?: config.geminiModelName.ifBlank { "gemini-2.5-flash" }
        return try {
            block(primary)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            Log.w("GeminiService", "Primary model $primary failed: $msg. Trying fallback gemini-2.5-flash...", e)
            if (primary != "gemini-2.5-flash") {
                try {
                    block("gemini-2.5-flash")
                } catch (e2: Exception) {
                    Log.w("GeminiService", "Fallback gemini-2.5-flash failed, trying gemini-3.8-flash...", e2)
                    block("gemini-3.8-flash")
                }
            } else {
                block("gemini-3.8-flash")
            }
        }
    }

    suspend fun estimateNutrition(bitmap: Bitmap, preferredModel: String? = null): GeminiResult<AiEstimationResult> {
        val scaledBitmap = scaleBitmap(bitmap)
        return executeWithFallback(preferredModel) { modelToUse ->
            Log.d("GeminiService", "Analyzing with Firebase AI Logic ($region) model: $modelToUse")
            callGemini(scaledBitmap, modelToUse)
        }
    }

    suspend fun estimateNutritionFromText(
        promptText: String,
        weightGrams: Double? = null,
        preferredModel: String? = null
    ): GeminiResult<AiEstimationResult> {
        return executeWithFallback(preferredModel) { modelToUse ->
            Log.d("GeminiService", "Analyzing text with Firebase AI Logic ($region) model: $modelToUse")
            callGeminiText(promptText, weightGrams, modelToUse)
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

    private suspend fun callGeminiText(
        promptText: String,
        weightGrams: Double?,
        modelName: String
    ): GeminiResult<AiEstimationResult> {
        val model = getModel(modelName)
        
        val weightInstruction = if (weightGrams != null && weightGrams > 0) {
            "The portion weight is specified by the user as EXACTLY $weightGrams grams. Calculate and scale all nutritional values specifically for this portion of $weightGrams grams."
        } else {
            "Estimate a realistic standard portion weight in grams for this food item and calculate total nutritional values for that portion."
        }

        val prompt = """
            Analyse this food item / meal description: "$promptText".
            $weightInstruction
            
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
            All values should be for the entire portion weight ("grams").
            Language: German (for the name field)
        """.trimIndent()

        val response = model.generateContent(prompt)
        val responseText = response.text ?: ""
        Log.d("GeminiService", "Raw AI Logic Text Response ($modelName): ${responseText.take(100)}...")
        
        val cleanJson = extractJson(responseText)
        if (cleanJson.isBlank()) return GeminiResult(null, response.usageMetadata)
        
        val estimation = json.decodeFromString<AiEstimationResult>(cleanJson)
        val finalEstimation = if (weightGrams != null && weightGrams > 0 && (estimation.grams <= 0 || estimation.grams != weightGrams)) {
            estimation.copy(grams = weightGrams)
        } else {
            estimation
        }

        return GeminiResult(finalEstimation, response.usageMetadata)
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

    suspend fun estimatePortionWeights(foodName: String, portionNames: List<String>, preferredModel: String? = null): GeminiResult<Map<String, Double>> {
        return executeWithFallback(preferredModel) { modelToUse ->
            val model = getModel(modelToUse)
            val prompt = """
                You are a nutrition expert. 
                I have a food item named '$foodName'.
                I want to know the average weight in grams for the following portion descriptions: ${portionNames.joinToString(", ")}.
                
                IMPORTANT RULES:
                1. Provide ONLY a valid JSON object.
                2. The keys in the JSON object must be EXACTLY the requested portion names.
                3. The values must be ABSOLUTE numbers in grams.
                4. DO NOT use ranges. Use an average/typical value, e.g. 120.0.
                
                Return ONLY a valid JSON object in the following format:
                {
                  "${portionNames.firstOrNull() ?: "Portion"}": 0.0
                }
            """.trimIndent()

            val response = model.generateContent(prompt)
            val responseText = response.text ?: ""
            val cleanJson = extractJson(responseText)
            if (cleanJson.isBlank()) {
                GeminiResult(null, response.usageMetadata)
            } else {
                val jsonObject = json.parseToJsonElement(cleanJson).asJsonObject()
                val weightMap = jsonObject.entries.associate { (k, v) ->
                    k to (v.toString().replace("\"", "").toDoubleOrNull() ?: 0.0)
                }
                GeminiResult(weightMap, response.usageMetadata)
            }
        }
    }

    suspend fun estimateGenericFood(foodName: String, categories: List<String>, isBrandSearch: Boolean = false, preferredModel: String? = null): GeminiResult<AiGenericFoodResult> {
        return executeWithFallback(preferredModel) { modelToUse ->
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

            val response = model.generateContent(prompt)
            val responseText = response.text ?: ""
            val cleanJson = extractJson(responseText)
            if (cleanJson.isBlank()) GeminiResult(null, response.usageMetadata)
            else GeminiResult(json.decodeFromString<AiGenericFoodResult>(cleanJson), response.usageMetadata)
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
