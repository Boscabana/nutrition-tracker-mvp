package com.nick.nutritiontracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_items ORDER BY name ASC")
    fun observeFoodItems(): Flow<List<FoodItemEntity>>

    @Insert
    suspend fun insertFoodItem(item: FoodItemEntity): Long

    @Insert
    suspend fun insertFoodEntry(entry: FoodEntryEntity): Long

    @Query("DELETE FROM food_entries WHERE id = :entryId")
    suspend fun deleteEntry(entryId: Long)

    @Transaction
    @Query("""
        SELECT e.id AS entryId, e.dateIso, e.mealSlot, e.amount, e.unit, e.grams,
               f.id AS foodItemId, f.name, f.kcalPer100g, f.proteinPer100g, f.carbsPer100g, f.fatPer100g
        FROM food_entries e
        INNER JOIN food_items f ON f.id = e.foodItemId
        WHERE e.dateIso = :dateIso
        ORDER BY e.id DESC
    """)
    fun observeEntriesForDay(dateIso: String): Flow<List<FoodEntryWithFood>>
}
