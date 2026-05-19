package com.nick.nutritiontracker.data

import android.util.Log
import android.content.Context
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class BarcodeScannerService(private val context: Context) {
    private val scanner = GmsBarcodeScanning.getClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun startScan(): String? {
        return try {
            val result = scanner.startScan().await()
            result.rawValue
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun getUrlText(url: String, timeout: Int = 8000): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeout
        connection.readTimeout = timeout
        try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchProduct(barcode: String): FoodItemEntity? = withContext(Dispatchers.IO) {
        try {
            val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
            val responseText = getUrlText(url)
            val response = json.decodeFromString<OFFProductResponse>(responseText)
            
            val product = response.product ?: return@withContext null
            mapToEntity(product, barcode)
        } catch (e: Exception) {
            Log.e("BarcodeScannerService", "Error fetching product", e)
            null
        }
    }

    suspend fun searchProducts(query: String): List<FoodItemEntity> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.length < 2) return@withContext emptyList()

        try {
            // 1. Check if it's a barcode search
            val barcodeResult = if (trimmedQuery.all { it.isDigit() } && trimmedQuery.length >= 8) {
                fetchProduct(trimmedQuery)
            } else null

            val encodedQuery = URLEncoder.encode(trimmedQuery, "UTF-8")
            // Search with higher limit to allow better local ranking
            val url = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encodedQuery&search_simple=1&action=process&json=1&page_size=50"
            val responseText = getUrlText(url, timeout = 10000)
            val response = json.decodeFromString<OFFProductResponse>(responseText)
            
            val products = response.products ?: emptyList()
            
            // 2. Rank results according to user request
            val rankedProducts = rankOffProducts(products, trimmedQuery)
            
            val mapped = rankedProducts.mapNotNull { product ->
                mapToEntity(product, product.id ?: "")
            }.toMutableList()

            // Ensure barcode result is first if it was found
            barcodeResult?.let { b ->
                val existingIndex = mapped.indexOfFirst { it.barcode?.equals(b.barcode, ignoreCase = true) == true }
                if (existingIndex != -1) {
                    mapped.removeAt(existingIndex)
                }
                mapped.add(0, b)
            }

            mapped.distinctBy { it.barcode ?: it.name }.take(30)
        } catch (e: Exception) {
            Log.e("BarcodeScannerService", "Error searching products", e)
            emptyList()
        }
    }

    private fun rankOffProducts(products: List<OFFProduct>, query: String): List<OFFProduct> {
        val q = query.lowercase().trim()
        val words = q.split("\\s+".toRegex()).filter { it.isNotBlank() }
        
        return products.map { p ->
            var score = 0
            val name = (p.productName ?: "").lowercase()
            val nameDe = (p.productNameDe ?: "").lowercase()
            val brands = (p.brands ?: "").lowercase()
            val code = (p.id ?: "").lowercase()

            // 1. Absolute Priority: Exact match on code (barcode)
            if (code == q) score += 5000
            
            // 2. High Priority: Exact match on name or nameDe
            if (name == q || nameDe == q) {
                score += 2000
            } else if (name.startsWith(q) || nameDe.startsWith(q)) {
                score += 1500
            } else if (name.contains(q) || nameDe.contains(q)) {
                score += 1000
            }
            
            // 3. Brand match
            if (brands.contains(q)) score += 500
            
            // 4. Iterative Word Matching (Split by spaces)
            if (words.size > 1) {
                val matchedWordsCount = words.count { word -> 
                    name.contains(word) || nameDe.contains(word) || brands.contains(word) || code.contains(word)
                }
                
                // Bonus for matching all words
                if (matchedWordsCount == words.size) {
                    score += 800
                }
                
                score += matchedWordsCount * 200
            }
            
            // 5. Code partial match (as requested for "robustness")
            if (code.contains(q)) score += 300

            p to score
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
    }

    private fun mapToEntity(product: OFFProduct, barcode: String): FoodItemEntity? {
        val nutriments = product.nutriments ?: return null
        
        val protein = nutriments.proteins ?: 0.0
        val carbs = nutriments.carbs ?: 0.0
        val fat = nutriments.fat ?: 0.0
        val sugar = nutriments.sugars ?: 0.0
        val saturatedFat = nutriments.saturatedFat ?: 0.0
        val alcohol = nutriments.alcohol100g ?: nutriments.alcohol ?: 0.0
        
        // Determine base unit (ml if it looks like a liquid)
        val unit = product.unit?.lowercase() ?: ""
        val baseUnit = if (unit.contains("ml") || unit.contains("l")) "ml" else "g"

        // Prefer German name if available
        val rawName = product.productNameDe ?: product.productName ?: "Unbekanntes Produkt"
        val brand = product.brands?.split(",")?.firstOrNull()?.trim()
        val displayName = if (!brand.isNullOrBlank() && !rawName.contains(brand, ignoreCase = true)) {
            "$brand $rawName"
        } else {
            rawName
        }

        return FoodItemEntity(
            id = 0,
            name = displayName,
            kcalPer100g = calculateKcalPer100g(protein, carbs, fat, alcohol),
            proteinPer100g = protein,
            carbsPer100g = carbs,
            sugarPer100g = sugar,
            fatPer100g = fat,
            saturatedFatPer100g = saturatedFat,
            alcoholPercent = alcohol,
            baseUnit = baseUnit,
            barcode = barcode.takeIf { it.isNotBlank() },
            brand = brand
        )
    }
}
