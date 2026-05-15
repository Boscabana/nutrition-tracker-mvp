package com.nick.nutritiontracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.UserProfile

@Composable
fun MacroProgressSection(
    userProfile: UserProfile,
    currentKcal: Double,
    currentProtein: Double,
    currentComplexCarbs: Double,
    currentSugar: Double,
    currentUnsaturatedFat: Double,
    currentSaturatedFat: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tagesbudget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Calories
            BudgetSummary(
                label = "Kalorien",
                current = currentKcal,
                target = userProfile.calorieBudget.toDouble(),
                unit = "kcal",
                showRemaining = true
            )

            // Progress bars
            MacroProgressBar("Protein", currentProtein, userProfile.proteinGoalGrams, Color(0xFF2E7D32))
            MacroProgressBar("Komplexe KH", currentComplexCarbs, userProfile.complexCarbsGoalGrams, Color(0xFFFF9800))
            MacroProgressBar("Zucker", currentSugar, userProfile.sugarGoalGrams, Color(0xFFD32F2F))
            MacroProgressBar("Ungesättigte Fette", currentUnsaturatedFat, userProfile.unsaturatedFatGoalGrams, Color(0xFFFBC02D))
            MacroProgressBar("Gesättigte Fette", currentSaturatedFat, userProfile.saturatedFatGoalGrams, Color(0xFF757575))
        }
    }
}

@Composable
private fun BudgetSummary(label: String, current: Double, target: Double, unit: String, showRemaining: Boolean) {
    val remaining = (target - current).coerceAtLeast(0.0)
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text("${current.round0()} / ${target.round0()} $unit")
        }
        if (showRemaining) {
            Text("${remaining.round0()} $unit übrig", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MacroProgressBar(label: String, current: Double, target: Double, color: Color) {
    val progress = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    val isOver = current > target && target > 0

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${current.round0()} / ${target.round0()} g", style = MaterialTheme.typography.labelSmall)
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(4.dp))
            )
        }
        
        if (isOver) {
            Text(
                "+${(current - target).round0()} g über Ziel", 
                style = MaterialTheme.typography.labelSmall, 
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun Double.round0(): String = "%.0f".format(this)
