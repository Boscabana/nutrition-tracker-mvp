package com.nick.nutritiontracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OFFProductResponse(
    val product: OFFProduct? = null,
    val products: List<OFFProduct>? = null,
    val status: Int? = null,
    val count: Int? = null
)

@Serializable
data class OFFProduct(
    val id: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_de") val productNameDe: String? = null,
    val brands: String? = null,
    val nutriments: OFFNutriments? = null,
    @SerialName("serving_quantity_unit") val unit: String? = null
)

@Serializable
data class OFFNutriments(
    @SerialName("proteins_100g") val proteins: Double? = null,
    @SerialName("carbohydrates_100g") val carbs: Double? = null,
    @SerialName("sugars_100g") val sugars: Double? = null,
    @SerialName("fat_100g") val fat: Double? = null,
    @SerialName("saturated-fat_100g") val saturatedFat: Double? = null,
    @SerialName("alcohol_100g") val alcohol100g: Double? = null,
    val alcohol: Double? = null
)
