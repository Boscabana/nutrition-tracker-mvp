package com.nick.nutritiontracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.nick.nutritiontracker.receiver.ReminderReceiver
import java.util.Calendar

object ReminderManager {
    private const val BREAKFAST_REQUEST_CODE = 2001
    private const val WEIGH_IN_REQUEST_CODE = 2002

    fun scheduleReminders(context: Context, profile: com.nick.nutritiontracker.data.UserProfile) {
        if (profile.breakfastReminderEnabled) {
            scheduleDailyAlarm(context, profile.breakfastReminderTime, BREAKFAST_REQUEST_CODE, "breakfast")
        } else {
            cancelAlarm(context, BREAKFAST_REQUEST_CODE)
        }

        if (profile.weighInReminderEnabled) {
            scheduleDailyAlarm(context, profile.weighInReminderTime, WEIGH_IN_REQUEST_CODE, "weigh_in")
        } else {
            cancelAlarm(context, WEIGH_IN_REQUEST_CODE)
        }
    }

    private fun scheduleDailyAlarm(context: Context, time: String, requestCode: Int, type: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_type", type)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val parts = time.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: 10
        val minute = parts[1].toIntOrNull() ?: 0

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        // Use setInexactRepeating for battery efficiency or setAndAllowWhileIdle for more precision
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun cancelAlarm(context: Context, requestCode: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    // Legacy method for compatibility if needed elsewhere
    fun scheduleReminder(context: Context) {
        // This is now handled by scheduleReminders with profile data
    }
}
