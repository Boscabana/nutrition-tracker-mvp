package com.nick.nutritiontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.nick.nutritiontracker.ui.NutritionApp
import com.nick.nutritiontracker.viewmodel.NutritionViewModel

class MainActivity : ComponentActivity() {
    private val vm by viewModels<NutritionViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NutritionApp(vm) }
    }
}
