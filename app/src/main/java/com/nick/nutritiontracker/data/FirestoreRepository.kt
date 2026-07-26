package com.nick.nutritiontracker.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository {
    private val db = FirebaseFirestore.getInstance()

    fun getPlannedEntries(householdId: String): Flow<List<FoodEntryEntity>> = callbackFlow {
        val subscription = db.collection("households")
            .document(householdId)
            .collection("planner")
            .orderBy("dateIso", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("Firestore", "Error listening to planner", error)
                    close(error)
                    return@addSnapshotListener
                }
                val entries = snapshot?.documents?.mapNotNull { it.toObject(FoodEntryEntity::class.java) } ?: emptyList()
                Log.d("Firestore", "Loaded ${entries.size} planned entries")
                trySend(entries)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun addPlannedEntry(householdId: String, entry: FoodEntryEntity) {
        db.collection("households")
            .document(householdId)
            .collection("planner")
            .document(entry.id.toString())
            .set(entry)
            .await()
        Log.d("Firestore", "Success: Planned entry written to Cloud")
    }

    suspend fun deletePlannedEntry(householdId: String, entryId: Long) {
        db.collection("households")
            .document(householdId)
            .collection("planner")
            .document(entryId.toString())
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
        db.collection("households")
            .document(householdId)
            .collection("shopping_list")
            .add(item)
            .await()
        Log.d("Firestore", "Success: Shopping item written to Cloud")
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
}
