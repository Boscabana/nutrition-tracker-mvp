package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class DailyActivity(
    val dateIso: String,
    val steps: Int = 0,
    val activeCalories: Double? = null
) {
    /**
     * Berechnet die Aktivitätskalorien. 
     * Wenn [activeCalories] vorhanden ist (aus Health Connect/Samsung Health), wird dieser Wert bevorzugt.
     * Ansonsten erfolgt eine Schätzung basierend auf den Schritten.
     */
    fun calculateCalories(weightKg: Double, heightM: Double): Double {
        if (activeCalories != null && activeCalories > 0) {
            return activeCalories
        }
        
        // Fallback: Schätzung basierend auf Schritten (MET-Formel)
        return 0.55 * weightKg * steps * 0.415 * heightM / 1000
    }
}
