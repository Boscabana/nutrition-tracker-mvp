package com.nick.nutritiontracker.data

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.nick.nutritiontracker.R

class ConfigManager {
    private val remoteConfig: FirebaseRemoteConfig = Firebase.remoteConfig

    init {
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600 // 1 hour for production, can be 0 for debugging
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // Set default values
        val defaults = mapOf(
            "gemini_model_name" to "gemini-2.0-flash",
            "gemini_max_output_tokens" to 1000L,
            "gemini_temperature" to 0.7,
            "ai_image_resize_px" to 768L,
            "free_ai_limit_per_day" to 3L
        )
        remoteConfig.setDefaultsAsync(defaults)
        
        fetchAndActivate()
    }

    private fun fetchAndActivate() {
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("ConfigManager", "Config updated: ${task.result}")
            } else {
                Log.e("ConfigManager", "Config fetch failed")
            }
        }
    }

    // Cockpit Values
    val geminiModelName: String get() = remoteConfig.getString("gemini_model_name")
    val maxOutputTokens: Int get() = remoteConfig.getLong("gemini_max_output_tokens").toInt()
    val temperature: Double get() = remoteConfig.getDouble("gemini_temperature")
    val imageResizePx: Int get() = remoteConfig.getLong("ai_image_resize_px").toInt()
    val freeAiLimitPerDay: Int get() = remoteConfig.getLong("free_ai_limit_per_day").toInt()
}
