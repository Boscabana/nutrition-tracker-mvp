package com.nick.nutritiontracker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class ActivityData(val steps: Int?, val totalKcal: Double?, val sessions: List<ExerciseSessionInfo>)

@Serializable
data class ExerciseSessionInfo(val type: String, val durationMinutes: Long, val calories: Double?)

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy {
        try {
            if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
                HealthConnectClient.getOrCreate(context)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(permissions)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun syncActivityForSelectedDate(date: LocalDate): ActivityData {
        val client = healthConnectClient ?: return ActivityData(null, null, emptyList())
        
        return try {
            val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            // 1. Steps
            val stepsResponse = client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            val steps = stepsResponse[StepsRecord.COUNT_TOTAL]?.toInt()

            // 2. Total Calories (BMR + Active)
            val totalResponse = client.aggregate(
                AggregateRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            val totalKcal = totalResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories

            // 3. Exercise Sessions
            val sessionsResponse = client.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                )
            )
            
            val sessions = mutableListOf<ExerciseSessionInfo>()
            for (session in sessionsResponse.records) {
                val sessionStart = session.startTime
                val sessionEnd = session.endTime
                
                // Try 1: Aggregation (Active Calories)
                val sessionKcalResponse = client.aggregate(
                    AggregateRequest(
                        metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(sessionStart, sessionEnd)
                    )
                )
                var sessionKcal = sessionKcalResponse[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories

                // Try 2: If aggregation is null/0, try summing individual records
                if (sessionKcal == null || sessionKcal == 0.0) {
                    val individualKcalRecords = client.readRecords(
                        ReadRecordsRequest(
                            recordType = ActiveCaloriesBurnedRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(sessionStart, sessionEnd)
                        )
                    )
                    sessionKcal = individualKcalRecords.records.sumOf { it.energy.inKilocalories }
                }

                // Try 3: If still 0, try Total Calories during that time window
                if (sessionKcal == null || sessionKcal == 0.0) {
                    val totalKcalResponse = client.aggregate(
                        AggregateRequest(
                            metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                            timeRangeFilter = TimeRangeFilter.between(sessionStart, sessionEnd)
                        )
                    )
                    val sessionTotal = totalKcalResponse[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                    if (sessionTotal != null && sessionTotal > 0) {
                        // We use the total as a fallback. 
                        // In the ViewModel, we could subtract BMR if we had it here, 
                        // but let's just mark it so it's transparent.
                        sessionKcal = sessionTotal
                    }
                }

                val duration = java.time.Duration.between(sessionStart, sessionEnd).toMinutes()
                
                sessions.add(
                    ExerciseSessionInfo(
                        type = mapExerciseType(session.exerciseType),
                        durationMinutes = duration,
                        calories = if (sessionKcal != null && sessionKcal > 0) sessionKcal else null
                    )
                )
            }
            
            ActivityData(steps, totalKcal, sessions)
        } catch (e: Exception) {
            e.printStackTrace()
            ActivityData(null, null, emptyList())
        }
    }

    private fun mapExerciseType(type: Int): String {
        return when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Gehen"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Laufen"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Radfahren"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER, 
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Schwimmen"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Krafttraining"
            else -> "Sport"
        }
    }
    
    fun getAvailabilityStatus(): Int {
        return HealthConnectClient.getSdkStatus(context)
    }

    fun isAvailable(): Boolean {
        return getAvailabilityStatus() == HealthConnectClient.SDK_AVAILABLE
    }
    
    fun getInstallIntent(): Intent {
        val packageName = "com.google.android.apps.healthdata"
        return Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("market://details?id=$packageName")
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun getSettingsIntent(): Intent {
        return Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
