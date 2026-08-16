package com.nick.nutritiontracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
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
        
        handleIntent(intent)
        
        // Initialisiere Benachrichtigungen
        NotificationHelper.createNotificationChannel(this)
        
        requestNotificationPermission()

        setContent {
            LaunchedEffect(nutritionVm.shouldCloseApp) {
                if (nutritionVm.shouldCloseApp) {
                    nutritionVm.shouldCloseApp = false
                    nutritionVm.isQuickScanRunning = false
                    finish()
                }
            }
            NutritionApp(nutritionVm, profileVm)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.nick.nutritiontracker.ACTION_QUICK_SCAN") {
            nutritionVm.triggerScan(isQuickScan = true)
        }
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
