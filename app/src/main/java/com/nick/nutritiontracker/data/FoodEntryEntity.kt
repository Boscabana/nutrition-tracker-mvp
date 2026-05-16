package com.nick.nutritiontracker.data

data class FoodEntryEntity(
    val id: Long,
    val dateIso: String,
    val mealSlot: String,
    val amount: Double,
    val unitLabel: String,
    val grams: Double,

    val foodItemId: Long,
    val name: String,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val sugarPer100g: Double,
    val fatPer100g: Double,
    val saturatedFatPer100g: Double,
    val alcoholPercent: Double = 0.0,
    val baseUnit: String = "g"
) {
    val kcal: Double get() = kcalPer100g * grams / 100.0
    val protein: Double get() = proteinPer100g * grams / 100.0
    val carbs: Double get() = carbsPer100g * grams / 100.0
    val sugar: Double get() = sugarPer100g * grams / 100.0
    val complexCarbs: Double get() =
        (carbsPer100g - sugarPer100g).coerceAtLeast(0.0) * grams / 100.0
    val fat: Double get() = fatPer100g * grams / 100.0
    val saturatedFat: Double get() = saturatedFatPer100g * grams / 100.0
    val unsaturatedFat: Double get() =
        (fatPer100g - saturatedFatPer100g).coerceAtLeast(0.0) * grams / 100.0

    fun displayAmount(): String = "${clean(amount)} $unitLabel"

    private fun clean(v: Double): String =
        if (v % 1.0 == 0.0) "%.0f".format(v) else "%.1f".format(v)
}
