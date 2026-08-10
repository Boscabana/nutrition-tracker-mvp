package com.nick.nutritiontracker.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser = _currentUser.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile = _userProfile.asStateFlow()

    private val _household = MutableStateFlow<Household?>(null)
    val household = _household.asStateFlow()

    private var profileListener: ListenerRegistration? = null
    private var householdListener: ListenerRegistration? = null

    init {
        auth.addAuthStateListener {
            _currentUser.value = it.currentUser
            if (it.currentUser != null) {
                observeUserProfile(it.currentUser!!.uid)
            } else {
                cleanupListeners()
                _userProfile.value = null
                _household.value = null
            }
        }
    }

    private fun observeUserProfile(uid: String) {
        profileListener?.remove()
        profileListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirebaseManager", "Error observing profile", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    try {
                        val data = snapshot.data
                        if (data != null) {
                            // Extract isPremium manually to be safe
                            val cloudPremium = snapshot.getBoolean("isPremium") ?: 
                                             snapshot.getBoolean("premium") ?: 
                                             (data["isPremium"] as? Boolean) ?: false
                            
                            // Map manually if toObject fails
                            val profile = snapshot.toObject(UserProfile::class.java) ?: UserProfile(
                                firstName = data["firstName"] as? String ?: "",
                                age = (data["age"] as? Long)?.toInt() ?: 30,
                                weightKg = (data["weightKg"] as? Double) ?: 70.0,
                                heightCm = (data["heightCm"] as? Long)?.toInt() ?: 175,
                                gender = Gender.valueOf(data["gender"] as? String ?: "MALE"),
                                goal = UserGoal.valueOf(data["goal"] as? String ?: "MAINTAIN"),
                                setupCompleted = data["setupCompleted"] as? Boolean ?: false
                            )
                            
                            profile.premium = cloudPremium
                            _userProfile.value = profile
                            Log.d("FirebaseManager", "Profile fixed sync for $uid: isPremium=$cloudPremium")
                            
                            // Reactively start or stop household observation
                            if (cloudPremium) {
                                if (householdListener == null) {
                                    startObservingHousehold(uid)
                                }
                            } else {
                                householdListener?.remove()
                                householdListener = null
                                _household.value = null
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FirebaseManager", "CRITICAL Error mapping profile", e)
                    }
                }
            }
    }

    private fun startObservingHousehold(uid: String) {
        householdListener?.remove()
        householdListener = db.collection("households")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && !snapshot.isEmpty) {
                    val doc = snapshot.documents.first()
                    val h = doc.toObject(Household::class.java)?.copy(id = doc.id)
                    Log.d("FirebaseManager", "Household found: ${h?.name} (ID: ${h?.id})")
                    _household.value = h
                } else {
                    Log.d("FirebaseManager", "No household found for members array containing uid")
                    _household.value = null
                }
            }
    }

    private fun cleanupListeners() {
        profileListener?.remove()
        profileListener = null
        householdListener?.remove()
        householdListener = null
    }

    suspend fun registerWithEmail(email: String, password: String, firstName: String) {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user?.let { user ->
            val initialProfile = UserProfile(firstName = firstName, premium = false)
            db.collection("users").document(user.uid).set(initialProfile).await()
        }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
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

    suspend fun syncProfileToFirestore(profile: UserProfile) {
        val user = auth.currentUser ?: return
        db.collection("users").document(user.uid).set(profile).await()
    }

    suspend fun getUserName(uid: String): String {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            doc.getString("firstName") ?: "Unbekannter Nutzer"
        } catch (e: Exception) {
            "Unbekannter Nutzer"
        }
    }
}
