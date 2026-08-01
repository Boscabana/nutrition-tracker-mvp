package com.nick.nutritiontracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nick.nutritiontracker.NotificationHelper
import com.nick.nutritiontracker.ReminderManager
import com.nick.nutritiontracker.data.FoodEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val profileRepo = com.nick.nutritiontracker.data.ProfileRepository(context)
            CoroutineScope(Dispatchers.Main).launch {
                val profile = profileRepo.userProfileFlow.first()
                ReminderManager.scheduleReminders(context, profile)
            }
            return
        }

        val type = intent.getStringExtra("reminder_type")
        when (type) {
            "breakfast" -> {
                if (shouldShowBreakfastReminder(context)) {
                    NotificationHelper.showBreakfastReminder(context)
                }
            }
            "weigh_in" -> {
                NotificationHelper.showWeighInReminder(context)
            }
        }
    }

    private fun shouldShowBreakfastReminder(context: Context): Boolean {
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
