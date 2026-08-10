package com.nick.nutritiontracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nick.nutritiontracker.data.ProfileRepository
import com.nick.nutritiontracker.ui.NutritionApp
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel

class MainActivity : FragmentActivity() {
    private val nutritionVm by viewModels<NutritionViewModel>()
    
    private val profileVm by viewModels<ProfileViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfileViewModel(
                        ProfileRepository(applicationContext),
                        nutritionVm.firebaseManager
                    ) as T
                }
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialisiere Benachrichtigungen
        NotificationHelper.createNotificationChannel(this)
        
        requestNotificationPermission()

        setContent { NutritionApp(nutritionVm, profileVm) }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }
    }
}
