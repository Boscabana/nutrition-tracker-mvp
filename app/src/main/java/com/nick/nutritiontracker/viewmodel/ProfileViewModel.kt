package com.nick.nutritiontracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val repository: ProfileRepository,
    private val firebaseManager: FirebaseManager
) : ViewModel() {

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
