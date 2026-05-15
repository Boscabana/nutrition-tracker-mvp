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
        val WEIGHT = doublePreferencesKey("weight")
        val HEIGHT = intPreferencesKey("height")
        val GOAL_DESC = stringPreferencesKey("goal_description")
        val CALORIE_BUDGET = intPreferencesKey("calorie_budget")
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
                weightKg = preferences[PreferencesKeys.WEIGHT] ?: 70.0,
                heightCm = preferences[PreferencesKeys.HEIGHT] ?: 175,
                goalDescription = preferences[PreferencesKeys.GOAL_DESC] ?: "Gewicht halten",
                calorieBudget = preferences[PreferencesKeys.CALORIE_BUDGET] ?: 2000,
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
            preferences[PreferencesKeys.WEIGHT] = profile.weightKg
            preferences[PreferencesKeys.HEIGHT] = profile.heightCm
            preferences[PreferencesKeys.GOAL_DESC] = profile.goalDescription
            preferences[PreferencesKeys.CALORIE_BUDGET] = profile.calorieBudget
            preferences[PreferencesKeys.PROTEIN_PCT] = profile.proteinPercent
            preferences[PreferencesKeys.COMPLEX_CARBS_PCT] = profile.complexCarbsPercent
            preferences[PreferencesKeys.SUGAR_PCT] = profile.sugarPercent
            preferences[PreferencesKeys.UNSATURATED_FAT_PCT] = profile.unsaturatedFatPercent
            preferences[PreferencesKeys.SATURATED_FAT_PCT] = profile.saturatedFatPercent
        }
    }
}
