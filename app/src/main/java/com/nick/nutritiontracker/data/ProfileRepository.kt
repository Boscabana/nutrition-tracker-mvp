package com.nick.nutritiontracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile")

class ProfileRepository(private val context: Context) {

    private object PreferencesKeys {
        val FIRST_NAME = stringPreferencesKey("first_name")
        val AGE = intPreferencesKey("age")
        val GENDER = stringPreferencesKey("gender")
        val WEIGHT = doublePreferencesKey("weight")
        val HEIGHT = intPreferencesKey("height")
        val GOAL = stringPreferencesKey("goal")
        val GOAL_INTENSITY = intPreferencesKey("goal_intensity")
        val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        
        val PROTEIN_PCT = intPreferencesKey("protein_percent")
        val COMPLEX_CARBS_PCT = intPreferencesKey("complex_carbs_percent")
        val SUGAR_PCT = intPreferencesKey("sugar_percent")
        val UNSATURATED_FAT_PCT = intPreferencesKey("unsaturated_fat_percent")
        val SATURATED_FAT_PCT = intPreferencesKey("saturated_fat_percent")
    }

    val userProfileFlow: Flow<UserProfile> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserProfile(
                firstName = preferences[PreferencesKeys.FIRST_NAME] ?: "",
                age = preferences[PreferencesKeys.AGE] ?: 30,
                gender = Gender.valueOf(preferences[PreferencesKeys.GENDER] ?: Gender.MALE.name),
                weightKg = preferences[PreferencesKeys.WEIGHT] ?: 70.0,
                heightCm = preferences[PreferencesKeys.HEIGHT] ?: 175,
                goal = UserGoal.valueOf(preferences[PreferencesKeys.GOAL] ?: UserGoal.MAINTAIN.name),
                goalIntensity = preferences[PreferencesKeys.GOAL_INTENSITY] ?: 500,
                setupCompleted = preferences[PreferencesKeys.SETUP_COMPLETED] ?: false,
                proteinPercent = preferences[PreferencesKeys.PROTEIN_PCT] ?: 20,
                complexCarbsPercent = preferences[PreferencesKeys.COMPLEX_CARBS_PCT] ?: 40,
                sugarPercent = preferences[PreferencesKeys.SUGAR_PCT] ?: 10,
                unsaturatedFatPercent = preferences[PreferencesKeys.UNSATURATED_FAT_PCT] ?: 20,
                saturatedFatPercent = preferences[PreferencesKeys.SATURATED_FAT_PCT] ?: 10
            )
        }

    suspend fun saveProfile(profile: UserProfile) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.FIRST_NAME] = profile.firstName
            preferences[PreferencesKeys.AGE] = profile.age
            preferences[PreferencesKeys.GENDER] = profile.gender.name
            preferences[PreferencesKeys.WEIGHT] = profile.weightKg
            preferences[PreferencesKeys.HEIGHT] = profile.heightCm
            preferences[PreferencesKeys.GOAL] = profile.goal.name
            preferences[PreferencesKeys.GOAL_INTENSITY] = profile.goalIntensity
            preferences[PreferencesKeys.SETUP_COMPLETED] = profile.setupCompleted
            preferences[PreferencesKeys.PROTEIN_PCT] = profile.proteinPercent
            preferences[PreferencesKeys.COMPLEX_CARBS_PCT] = profile.complexCarbsPercent
            preferences[PreferencesKeys.SUGAR_PCT] = profile.sugarPercent
            preferences[PreferencesKeys.UNSATURATED_FAT_PCT] = profile.unsaturatedFatPercent
            preferences[PreferencesKeys.SATURATED_FAT_PCT] = profile.saturatedFatPercent
        }
    }
}
