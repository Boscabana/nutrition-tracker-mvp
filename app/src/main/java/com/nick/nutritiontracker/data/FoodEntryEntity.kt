package com.nick.nutritiontracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "food_entries",
    foreignKeys = [ForeignKey(
        entity = FoodItemEntity::class,
        parentColumns = ["id"],
        childColumns = ["foodItemId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("foodItemId")]
)
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodItemId: Long,
    val dateIso: String,
    val mealSlot: String,
    val amount: Double,
    val unit: String,
    val grams: Double
)
