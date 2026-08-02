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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.WeightEntry
import com.nick.nutritiontracker.data.UserProfile
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(vm: NutritionViewModel, profileVm: ProfileViewModel) {
    val weightHistory = vm.weightHistory.sortedByDescending { it.dateIso }
    val userProfileState by profileVm.userProfile.collectAsState()
    val userProfile = userProfileState ?: return
    
    var weightInput by remember { mutableStateOf(userProfile.weightKg.toString()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Auswählen") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        item {
            Text("Gewicht & Fortschritt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // Weight Entry Card
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Wiege-Eintrag", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.Event, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(selectedDate.format(dateFormatter))
                        }
                    }
                    
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
                            if (w != null) {
                                vm.addWeightEntry(w, selectedDate.toString(), userProfile)
                                
                                val isMostRecent = weightHistory.isEmpty() || !selectedDate.isBefore(LocalDate.parse(weightHistory.maxOf { it.dateIso }))
                                
                                if (isMostRecent) {
                                    val newFactor = vm.calculateMetabolicFactorForProfile(userProfile)
                                    profileVm.updateProfile(userProfile.copy(
                                        weightKg = w,
                                        metabolicFactor = newFactor,
                                        initialWeight = userProfile.initialWeight ?: w
                                    ))
                                }
                            }
                        }) {
                            Text("Speichern")
                        }
                    }
                }
            }
        }

        // Summary Statistics
        if (weightHistory.isNotEmpty()) {
            val latest = weightHistory.first().weight
            val startWeight = userProfile.initialWeight ?: weightHistory.last().weight
            val diff = latest - startWeight
            val diffSign = if (diff > 0) "+" else ""
            
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(Modifier.weight(1f), "Aktuell", "$latest kg", MaterialTheme.colorScheme.primary)
                    StatCard(
                        Modifier.weight(1f), 
                        "Differenz", 
                        "$diffSign${"%.1f".format(diff)} kg", 
                        if (diff <= 0) Color(0xFF2E7D32) else Color.Red
                    )
                }
            }
        }

        // Progress Chart
        if (weightHistory.size >= 2) {
            item {
                Card(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    Box(Modifier.padding(16.dp)) {
                        WeightLineChart(weightHistory.reversed())
                    }
                }
            }
        }

        // History Section
        item {
            Text("Historie", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        
        items(weightHistory) { entry ->
            val isVerified = vm.dayVerifications[entry.dateIso] ?: false
            val budget = if (isVerified) vm.calculateWeightBudgetGrams(entry.dateIso, userProfile) else 0.0

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
        val range = (maxW - minW).coerceAtLeast(0.1)
        
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

private fun Double.round0(): String = "%.0f".format(this)
