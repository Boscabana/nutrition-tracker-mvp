package com.nick.nutritiontracker.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    private val _household = MutableStateFlow<Household?>(null)
    val household = _household.asStateFlow()

    init {
        auth.addAuthStateListener {
            _currentUser.value = it.currentUser
            if (it.currentUser != null) {
                loadHousehold(it.currentUser!!.uid)
            } else {
                _household.value = null
            }
        }
    }

    private fun loadHousehold(uid: String) {
        db.collection("households")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    _household.value = doc.toObject(Household::class.java)?.copy(id = doc.id)
                } else {
                    _household.value = null
                }
            }
    }

    suspend fun createHousehold(name: String): String {
        val user = auth.currentUser ?: throw Exception("Not logged in")
        val inviteCode = UUID.randomUUID().toString().take(6).uppercase()
        val newHousehold = Household(
            name = name,
            adminUid = user.uid,
            members = listOf(user.uid),
            inviteCode = inviteCode
        )
        val docRef = db.collection("households").add(newHousehold).await()
        return inviteCode
    }

    suspend fun joinHousehold(inviteCode: String) {
        val user = auth.currentUser ?: throw Exception("Not logged in")
        val snapshot = db.collection("households")
            .whereEqualTo("inviteCode", inviteCode.uppercase())
            .get()
            .await()
        
        if (!snapshot.isEmpty) {
            val doc = snapshot.documents.first()
            val members = doc.get("members") as? List<String> ?: emptyList()
            if (!members.contains(user.uid)) {
                doc.reference.update("members", members + user.uid).await()
            }
        } else {
            throw Exception("Invalid invite code")
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
