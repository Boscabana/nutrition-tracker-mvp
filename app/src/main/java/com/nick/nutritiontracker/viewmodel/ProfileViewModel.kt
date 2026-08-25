package com.nick.nutritiontracker.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nick.nutritiontracker.data.BillingManager
import com.nick.nutritiontracker.data.FirebaseManager
import com.nick.nutritiontracker.data.ProfileRepository
import com.nick.nutritiontracker.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    context: Context,
    private val repository: ProfileRepository,
    private val firebaseManager: FirebaseManager
) : ViewModel() {

    private val billingManager = BillingManager(context, viewModelScope) { isPremium ->
        setPremiumStatus(isPremium)
    }

    val premiumProduct = billingManager.premiumProduct

    fun purchasePremium(activity: Activity) {
        billingManager.purchasePremium(activity)
    }

    private fun setPremiumStatus(isPremium: Boolean) {
        viewModelScope.launch {
            userProfile.value?.let { current ->
                // Only update if we gain premium.
                // If we lose it according to BillingManager, we DON'T automatically overwrite
                // to allow manual overrides/gifts in Firestore to persist.
                if (isPremium && !current.premium) {
                    val updated = current.copy(premium = true)
                    repository.saveProfile(updated)
                    firebaseManager.syncProfileToFirestore(updated)
                }
            }
        }
    }

    val userProfile: StateFlow<UserProfile?> = repository.userProfileFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _authError = MutableStateFlow<String?>(null)
    val authError = _authError.asStateFlow()

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
            // Mirror to Firestore if logged in
            firebaseManager.syncProfileToFirestore(profile)
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authError.value = null
            try {
                firebaseManager.signInWithEmail(email, password)
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Login fehlgeschlagen"
            }
        }
    }

    fun signUp(email: String, password: String, firstName: String) {
        viewModelScope.launch {
            _authError.value = null
            try {
                firebaseManager.registerWithEmail(email, password, firstName)
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Registrierung fehlgeschlagen"
            }
        }
    }

    fun signOut() {
        firebaseManager.signOut()
    }
}
