package com.nick.nutritiontracker.data

import kotlinx.serialization.Serializable

@Serializable
data class DailyActivity(
    val dateIso: String,
    val steps: Int = 0
) {
    /**
     * Berechnet die Aktivitätskalorien basierend auf der MET-Formel (Metabolic Equivalent of Task).
     * Formel: MET * Gewicht (kg) * Dauer (Stunden)
     *
     * MET für moderates Gehen = 3.5
     * Annahme: Ein Durchschnittstempo von 100 Schritten pro Minute.
     * Dauer (h) = (Schritte / 100) / 60
     */
    fun calculateCalories(weightKg: Double, heightM: Double): Double {
        return 0.55 * weightKg * steps * 0.415 * heightM / 1000
    }
}
