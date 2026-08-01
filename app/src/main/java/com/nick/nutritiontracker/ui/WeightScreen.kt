package com.nick.nutritiontracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nick.nutritiontracker.data.WeightEntry
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(vm: NutritionViewModel, profileVm: ProfileViewModel) {
    val weightHistory = vm.weightHistory.sortedByDescending { it.dateIso }
    val userProfile by profileVm.userProfile.collectAsState()
    
    var weightInput by remember { mutableStateOf(userProfile?.weightKg?.toString() ?: "") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Gewicht & Fortschritt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // Today's Entry Card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Heute wiegen", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("kg") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Button(onClick = {
                        val w = weightInput.replace(',', '.').toDoubleOrNull()
                        if (w != null && userProfile != null) {
                            vm.addWeightEntry(w, userProfile!!)
                            // Update profile with new weight AND metabolic factor calculation
                            val newFactor = vm.calculateMetabolicFactorForProfile(userProfile!!)
                            profileVm.updateProfile(userProfile!!.copy(
                                weightKg = w,
                                metabolicFactor = newFactor,
                                initialWeight = userProfile!!.initialWeight ?: w
                            ))
                        }
                    }) {
                        Text("Speichern")
                    }
                }
            }
        }

        // Summary Statistics
        if (weightHistory.isNotEmpty()) {
            val latest = weightHistory.first().weight
            val startWeight = userProfile?.initialWeight ?: weightHistory.last().weight
            val totalLoss = startWeight - latest
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard(Modifier.weight(1f), "Aktuell", "$latest kg", MaterialTheme.colorScheme.primary)
                StatCard(Modifier.weight(1f), "Verlust", "${"%.1f".format(totalLoss)} kg", Color(0xFF2E7D32))
            }
        }

        // Simple Progress Chart
        if (weightHistory.size >= 2) {
            Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                Box(Modifier.padding(16.dp)) {
                    WeightLineChart(weightHistory.reversed())
                }
            }
        }

        // History List
        Text("Historie", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(weightHistory) { entry ->
                val isVerified = vm.dayVerifications[entry.dateIso] ?: false
                val budget = if (isVerified) vm.calculateWeightBudgetGrams(entry.dateIso, userProfile!!) else 0.0

                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(LocalDate.parse(entry.dateIso).format(dateFormatter), style = MaterialTheme.typography.bodyMedium)
                            if (isVerified) {
                                Text(
                                    text = if (budget >= 0) "${budget.round0()}g Fett geschmolzen ✨" else "${(-budget).round0()}g Aufbau",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (budget >= 0) Color(0xFF2E7D32) else Color.Red
                                )
                            } else {
                                Text("Nicht verifiziert", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                        }
                        Text("${entry.weight} kg", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

private fun Double.round0(): String = "%.0f".format(this)

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun WeightLineChart(data: List<WeightEntry>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (data.isEmpty()) return@Canvas
        
        val maxW = data.maxOf { it.weight }
        val minW = data.minOf { it.weight }
        val range = (maxW - minW).coerceAtLeast(1.0)
        
        val width = size.width
        val height = size.height
        val stepX = width / (data.size - 1).coerceAtLeast(1)
        
        val points = data.mapIndexed { index, entry ->
            val x = index * stepX
            val y = height - ((entry.weight - minW) / range * height).toFloat()
            Offset(x, y)
        }
        
        val path = Path().apply {
            if (points.isNotEmpty()) {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
        }
        
        drawPath(path, primaryColor, style = Stroke(width = 3.dp.toPx()))
        
        points.forEach { 
            drawCircle(primaryColor, radius = 4.dp.toPx(), center = it)
        }
    }
}
