package com.nick.nutritiontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nick.nutritiontracker.data.ProfileRepository
import com.nick.nutritiontracker.ui.NutritionApp
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel

class MainActivity : ComponentActivity() {
    private val nutritionVm by viewModels<NutritionViewModel>()
    
    private val profileVm by viewModels<ProfileViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfileViewModel(ProfileRepository(applicationContext)) as T
                }
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NutritionApp(nutritionVm, profileVm) }
    }
}
