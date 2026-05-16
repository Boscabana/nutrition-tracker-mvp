package com.nick.nutritiontracker.data

import android.content.Context
import android.util.Log
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.URL

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

    suspend fun fetchProduct(barcode: String): FoodItemEntity? = withContext(Dispatchers.IO) {
        try {
            val url = "https://world.openfoodfacts.org/api/v2/product/$barcode.json"
            val responseText = URL(url).readText()
            val response = json.decodeFromString<OFFProductResponse>(responseText)
            
            val product = response.product ?: return@withContext null
            val nutriments = product.nutriments
            
            val protein = nutriments?.proteins ?: 0.0
            val carbs = nutriments?.carbs ?: 0.0
            val fat = nutriments?.fat ?: 0.0
            val sugar = nutriments?.sugars ?: 0.0
            val saturatedFat = nutriments?.saturatedFat ?: 0.0
            val alcohol = nutriments?.alcohol100g ?: nutriments?.alcohol ?: 0.0
            
            // Determine base unit (ml if it looks like a liquid)
            val unit = product.unit?.lowercase() ?: ""
            val baseUnit = if (unit.contains("ml") || unit.contains("l")) "ml" else "g"

            FoodItemEntity(
                id = 0, // New product
                name = product.productName ?: product.productNameDe ?: "Unbekanntes Produkt",
                kcalPer100g = calculateKcalPer100g(protein, carbs, fat, alcohol),
                proteinPer100g = protein,
                carbsPer100g = carbs,
                sugarPer100g = sugar,
                fatPer100g = fat,
                saturatedFatPer100g = saturatedFat,
                alcoholPercent = alcohol,
                baseUnit = baseUnit,
                barcode = barcode
            )
        } catch (e: Exception) {
            Log.e("BarcodeScannerService", "Error fetching product", e)
            null
        }
    }
}
