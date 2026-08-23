package com.nick.nutritiontracker.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun uploadMealImage(uid: String, localPath: String): String? {
        return try {
            val file = File(localPath)
            if (!file.exists()) return null
            
            val storageRef = storage.reference.child("users/$uid/meal_images/${file.name}")
            storageRef.putFile(android.net.Uri.fromFile(file)).await()
            storageRef.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("Firestore", "Image upload failed", e)
            null
        }
    }


    fun getPlannedMealPool(householdId: String): Flow<List<PlannedMealPoolEntity>> = callbackFlow {
        val subscription = db.collection("households")
            .document(householdId)
            .collection("meal_pool")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error listening to meal pool", error)
                    close(error)
                    return@addSnapshotListener
                }
                launch(Dispatchers.Default) {
                    val pool = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(PlannedMealPoolEntity::class.java)?.copy(id = doc.id)
                    } ?: emptyList()
                    trySend(pool)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addPlannedMealToPool(householdId: String, poolEntry: PlannedMealPoolEntity) {
        db.collection("households")
            .document(householdId)
            .collection("meal_pool")
            .document(poolEntry.id)
            .set(poolEntry)
            .await()
    }

    suspend fun updatePlannedMealInPool(householdId: String, poolEntry: PlannedMealPoolEntity) {
        db.collection("households")
            .document(householdId)
            .collection("meal_pool")
            .document(poolEntry.id)
            .set(poolEntry)
            .await()
    }

    suspend fun deletePlannedMealFromPool(householdId: String, poolId: String) {
        db.collection("households")
            .document(householdId)
            .collection("meal_pool")
            .document(poolId)
            .delete()
            .await()
    }


    fun getShoppingList(householdId: String): Flow<List<ShoppingItem>> = callbackFlow {
        val subscription = db.collection("households")
            .document(householdId)
            .collection("shopping_list")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error listening to shopping list", error)
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(ShoppingItem::class.java)?.copy(id = it.id) } ?: emptyList()
                Log.d("Firestore", "Loaded ${items.size} shopping items")
                trySend(items)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addShoppingItem(householdId: String, item: ShoppingItem) {
        val finalId = if (item.id.isBlank()) UUID.randomUUID().toString() else item.id
        db.collection("households")
            .document(householdId)
            .collection("shopping_list")
            .document(finalId)
            .set(item.copy(id = finalId))
            .await()
        Log.d("Firestore", "Success: Shopping item written to Cloud with ID $finalId")
    }

    suspend fun updateShoppingItem(householdId: String, item: ShoppingItem) {
        db.collection("households")
            .document(householdId)
            .collection("shopping_list")
            .document(item.id)
            .set(item)
            .await()
    }

    suspend fun deleteShoppingItem(householdId: String, itemId: String) {
        db.collection("households")
            .document(householdId)
            .collection("shopping_list")
            .document(itemId)
            .delete()
            .await()
    }

    // --- PERSONAL DATA (Foods, Meals, Diary & Private Planner) ---

    fun getPersonalPlannedEntries(uid: String): Flow<List<FoodEntryEntity>> = callbackFlow {
        val subscription = db.collection("users")
            .document(uid)
            .collection("personal_planner")
            .orderBy("dateIso", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                launch(Dispatchers.Default) {
                    val entries = snapshot?.documents?.mapNotNull { doc ->
                        val entry = doc.toObject(FoodEntryEntity::class.java)
                        if (entry != null) {
                            entry.id = doc.id.toLongOrNull() ?: 0L
                            entry.isMeal = doc.getBoolean("isMeal") ?: entry.isMeal
                            entry.isPlanned = doc.getBoolean("isPlanned") ?: entry.isPlanned
                            entry.isGeneric = doc.getBoolean("isGeneric") ?: doc.getBoolean("generic") ?: entry.isGeneric
                        }
                        entry
                    } ?: emptyList()
                    trySend(entries)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun savePersonalPlannedEntry(uid: String, entry: FoodEntryEntity) {
        db.collection("users")
            .document(uid)
            .collection("personal_planner")
            .document(entry.id.toString())
            .set(entry)
            .await()
    }

    suspend fun deletePersonalPlannedEntry(uid: String, entryId: Long) {
        db.collection("users")
            .document(uid)
            .collection("personal_planner")
            .document(entryId.toString())
            .delete()
            .await()
    }

    fun getPersonalFoods(uid: String): Flow<List<FoodItemEntity>> = callbackFlow {
        val subscription = db.collection("users")
            .document(uid)
            .collection("personal_foods")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                
                // Offload mapping to background thread to avoid OOM/Jank on Main
                launch(Dispatchers.Default) {
                    val foods = snapshot?.documents?.mapNotNull { doc ->
                        val food = doc.toObject(FoodItemEntity::class.java)
                        if (food != null) {
                            val isGenericCloud = doc.getBoolean("isGeneric") ?: doc.getBoolean("generic") ?: food.isGeneric
                            val isPantryCloud = doc.getBoolean("isPantryItem") ?: doc.getBoolean("pantryItem") ?: food.isPantryItem
                            food.isGeneric = isGenericCloud
                            food.isPantryItem = isPantryCloud
                        }
                        food
                    } ?: emptyList()
                    trySend(foods)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun savePersonalFood(uid: String, food: FoodItemEntity) {
        db.collection("users")
            .document(uid)
            .collection("personal_foods")
            .document(food.id.toString())
            .set(food)
            .await()
    }

    fun getPersonalMeals(uid: String): Flow<List<MealEntity>> = callbackFlow {
        val subscription = db.collection("users")
            .document(uid)
            .collection("personal_meals")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                launch(Dispatchers.Default) {
                    val meals = snapshot?.documents?.mapNotNull { it.toObject(MealEntity::class.java) } ?: emptyList()
                    trySend(meals)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun savePersonalMeal(uid: String, meal: MealEntity) {
        db.collection("users")
            .document(uid)
            .collection("personal_meals")
            .document(meal.id.toString())
            .set(meal)
            .await()
    }

    suspend fun deletePersonalFood(uid: String, foodId: Long) {
        db.collection("users").document(uid).collection("personal_foods").document(foodId.toString()).delete().await()
    }

    suspend fun deletePersonalMeal(uid: String, mealId: Long) {
        db.collection("users").document(uid).collection("personal_meals").document(mealId.toString()).delete().await()
    }

    // --- PERSONAL ENTRIES (Diary) ---

    fun getPersonalEntries(uid: String): Flow<List<FoodEntryEntity>> = callbackFlow {
        val subscription = db.collection("users")
            .document(uid)
            .collection("personal_entries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                launch(Dispatchers.Default) {
                    val entries = snapshot?.documents?.mapNotNull { doc ->
                        val entry = doc.toObject(FoodEntryEntity::class.java)
                        if (entry != null) {
                            entry.isMeal = doc.getBoolean("isMeal") ?: entry.isMeal
                            entry.isPlanned = doc.getBoolean("isPlanned") ?: entry.isPlanned
                            entry.isGeneric = doc.getBoolean("isGeneric") ?: doc.getBoolean("generic") ?: entry.isGeneric
                        }
                        entry
                    } ?: emptyList()
                    trySend(entries)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun savePersonalEntry(uid: String, entry: FoodEntryEntity) {
        db.collection("users")
            .document(uid)
            .collection("personal_entries")
            .document(entry.id.toString())
            .set(entry)
            .await()
    }

    suspend fun deletePersonalEntry(uid: String, entryId: Long) {
        db.collection("users")
            .document(uid)
            .collection("personal_entries")
            .document(entryId.toString())
            .delete()
            .await()
    }

    // --- WEIGHT HISTORY ---

    fun getWeightHistory(uid: String): Flow<List<WeightEntry>> = callbackFlow {
        val subscription = db.collection("users")
            .document(uid)
            .collection("weight_history")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                launch(Dispatchers.Default) {
                    val entries = snapshot?.documents?.mapNotNull { it.toObject(WeightEntry::class.java) } ?: emptyList()
                    trySend(entries)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun saveWeightEntry(uid: String, entry: WeightEntry) {
        db.collection("users")
            .document(uid)
            .collection("weight_history")
            .document(entry.dateIso)
            .set(entry)
            .await()
    }

    suspend fun deleteWeightEntry(uid: String, dateIso: String) {
        db.collection("users")
            .document(uid)
            .collection("weight_history")
            .document(dateIso)
            .delete()
            .await()
    }

    // --- SHARED DATA (Planner & Shopping List) ---

    fun getInboxMessages(uid: String): Flow<List<InboxMessage>> = callbackFlow {
        val subscription = db.collection("users")
            .document(uid)
            .collection("inbox")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                launch(Dispatchers.Default) {
                    val messages = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(InboxMessage::class.java)?.copy(id = doc.id)
                    } ?: emptyList<InboxMessage>()
                    trySend(messages)
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun sendInboxMessage(toUid: String, message: InboxMessage) {
        db.collection("users")
            .document(toUid)
            .collection("inbox")
            .add(message)
            .await()
    }

    suspend fun markMessageAsRead(uid: String, messageId: String) {
        db.collection("users")
            .document(uid)
            .collection("inbox")
            .document(messageId)
            .update("isRead", true)
            .await()
    }

    suspend fun deleteInboxMessage(uid: String, messageId: String) {
        db.collection("users")
            .document(uid)
            .collection("inbox")
            .document(messageId)
            .delete()
            .await()
    }

    suspend fun getHouseholdMembers(uids: List<String>): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        for (uid in uids) {
            val doc = db.collection("users").document(uid).get().await()
            val name = doc.getString("firstName") ?: "Unbekannt"
            results.add(mapOf("uid" to uid, "name" to name))
        }
        return results
    }
}
