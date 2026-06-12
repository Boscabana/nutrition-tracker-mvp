package com.nick.nutritiontracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nick.nutritiontracker.NotificationHelper
import com.nick.nutritiontracker.ReminderManager
import com.nick.nutritiontracker.data.FoodEntryEntity
import kotlinx.serialization.json.Json
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Nach Neustart des Geräts den Alarm wieder planen
            ReminderManager.scheduleReminder(context)
            return
        }

        // Prüfen, ob für heute bereits ein Frühstück eingetragen wurde
        if (shouldShowReminder(context)) {
            NotificationHelper.showBreakfastReminder(context)
        }
    }

    private fun shouldShowReminder(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nutrition_tracker", Context.MODE_PRIVATE)
        val data = prefs.getString("entries_json", null) ?: return true
        
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val entries = json.decodeFromString<List<FoodEntryEntity>>(data)
            val today = LocalDate.now().toString()
            
            // Prüfen, ob ein Eintrag für "Frühstück" am heutigen Tag existiert
            entries.none { it.dateIso == today && it.mealSlot == "Frühstück" }
        } catch (e: Exception) {
            true // Im Zweifelsfall lieber erinnern
        }
    }
}
