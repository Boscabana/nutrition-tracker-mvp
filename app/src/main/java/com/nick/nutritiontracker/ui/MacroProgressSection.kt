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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.UserProfile
import com.nick.nutritiontracker.data.DailyActivity

@Composable
fun MacroProgressSection(
    userProfile: UserProfile,
    currentKcal: Double,
    currentProtein: Double,
    currentComplexCarbs: Double,
    currentSugar: Double,
    currentUnsaturatedFat: Double,
    currentSaturatedFat: Double,
    steps: Int
) {
    // Activity calories calculation based on MET formula
    val activity = DailyActivity("", steps)
    val activityKcal = activity.calculateCalories(userProfile.weightKg, userProfile.heightCm / 100.0)
    
    val totalBudget = userProfile.calorieBudget + activityKcal

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Tagesbudget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Calories Summary with Activity
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Grundbudget", style = MaterialTheme.typography.bodyMedium)
                    Text("${userProfile.calorieBudget} kcal")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aktivität ($steps Schritte)", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF2E7D32))
                    Text("+${activityKcal.round0()} kcal", color = Color(0xFF2E7D32))
                }
                HorizontalDivider(thickness = 0.5.dp)
                BudgetSummary(
                    label = "Gesamt Kalorien",
                    current = currentKcal,
                    target = totalBudget,
                    unit = "kcal",
                    showRemaining = true
                )
            }

            // Calorie Progress Bar (Thicker, App-Purple/Primary)
            MacroProgressBar(
                label = "Kalorien Fortschritt",
                current = currentKcal,
                target = totalBudget,
                color = MaterialTheme.colorScheme.primary,
                height = 14.dp,
                unit = "kcal"
            )

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

            // Macro Progress bars
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
    val remaining = (target - current)
    val remainingText = if (remaining >= 0) "${remaining.round0()} $unit übrig" else "${(-remaining).round0()} $unit drüber"
    val remainingColor = if (remaining >= 0) MaterialTheme.colorScheme.primary else Color.Red

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Bold)
            Text("${current.round0()} / ${target.round0()} $unit")
        }
        if (showRemaining) {
            Text(remainingText, style = MaterialTheme.typography.labelSmall, color = remainingColor)
        }
    }
}

@Composable
private fun MacroProgressBar(
    label: String, 
    current: Double, 
    target: Double, 
    color: Color,
    height: Dp = 8.dp,
    unit: String = "g"
) {
    val progress = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    val isOver = current > target && target > 0

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("${current.round0()} / ${target.round0()} $unit", style = MaterialTheme.typography.labelSmall)
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(height / 2))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(height / 2))
            )
        }
        
        if (isOver) {
            Text(
                "+${(current - target).round0()} $unit über Ziel", 
                style = MaterialTheme.typography.labelSmall, 
                color = Color.Red,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun Double.round0(): String = "%.0f".format(this)
