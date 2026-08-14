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
import com.nick.nutritiontracker.data.ExerciseSessionInfo

@Composable
fun MacroProgressSection(
    userProfile: UserProfile,
    currentKcal: Double,
    currentProtein: Double,
    currentComplexCarbs: Double,
    currentSugar: Double,
    currentUnsaturatedFat: Double,
    currentSaturatedFat: Double,
    activityKcal: Double,
    weightBudgetGrams: Double,
    steps: Int = 0,
    stepKcal: Double = 0.0,
    exerciseSessions: List<ExerciseSessionInfo> = emptyList()
) {
    val totalBudget = userProfile.calorieBudget + activityKcal

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tagesbudget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                // Weight Budget Badge
                Surface(
                    color = if (weightBudgetGrams >= 0) Color(0xFF2E7D32).copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = if (weightBudgetGrams >= 0) "${weightBudgetGrams.round0()}g geschmolzen 🔥" else "${(-weightBudgetGrams).round0()}g Aufbau ⚠️",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (weightBudgetGrams >= 0) Color(0xFF2E7D32) else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Calories Summary with Activity
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Basis: ${userProfile.calorieBudget} kcal", style = MaterialTheme.typography.bodySmall)
                    Text("Bonus: +${activityKcal.round0()} kcal", style = MaterialTheme.typography.bodySmall, color = Color(0xFF2E7D32))
                }
                BudgetSummary(
                    label = "Gesamt Budget",
                    current = currentKcal,
                    target = totalBudget,
                    unit = "kcal",
                    showRemaining = true
                )
            }

            // Activity Breakdown List
            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                // 1. Steps (Custom Formula)
                if (steps > 0) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Schritte ($steps) 🚶‍♂️",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "+${stepKcal.round0()} kcal",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                // 2. Exercise Sessions
                exerciseSessions.forEach { session ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${session.type} (${session.durationMinutes} min) 🔥",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        val displayKcal = session.calories ?: 0.0
                        Text(
                            text = if (displayKcal > 0) "+${displayKcal.round0()} kcal" else "In Gesamt inkl.",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (displayKcal > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline
                        )
                    }
                }
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
