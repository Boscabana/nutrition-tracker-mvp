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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.WeightEntry
import com.nick.nutritiontracker.data.UserProfile
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(vm: NutritionViewModel, profileVm: ProfileViewModel) {
    val weightHistory = vm.weightHistory.sortedByDescending { it.dateIso }
    val userProfileState by profileVm.userProfile.collectAsState()
    val userProfile = userProfileState ?: return
    
    var weightInput by remember { mutableStateOf(userProfile.weightKg.toString()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    var chartStartDate by remember { 
        mutableStateOf(
            if (weightHistory.isEmpty()) LocalDate.now().minusDays(30)
            else {
                val oldestDate = LocalDate.parse(weightHistory.last().dateIso)
                if (oldestDate.isAfter(LocalDate.now().minusDays(30))) oldestDate
                else LocalDate.now().minusDays(30)
            }
        )
    }
    var showChartDatePicker by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<WeightEntry?>(null) }
    
    val filteredHistory = remember(weightHistory, chartStartDate) {
        weightHistory.filter { !LocalDate.parse(it.dateIso).isBefore(chartStartDate) }
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN) }

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
    
    if (showChartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = chartStartDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showChartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        chartStartDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    }
                    showChartDatePicker = false
                }) { Text("Auswählen") }
            },
            dismissButton = {
                TextButton(onClick = { showChartDatePicker = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    if (editingEntry != null) {
        var editValue by remember { mutableStateOf(editingEntry!!.weight.toString()) }
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text("Gewicht bearbeiten") },
            text = {
                OutlinedTextField(
                    value = editValue,
                    onValueChange = { editValue = it },
                    label = { Text("kg") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editValue.replace(',', '.').toDoubleOrNull()?.let { w ->
                        vm.addWeightEntry(w, editingEntry!!.dateIso, userProfile)
                    }
                    editingEntry = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) { Text("Abbrechen") }
            }
        )
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
        if (filteredHistory.isNotEmpty()) {
            val latest = filteredHistory.first().weight
            val startWeight = filteredHistory.last().weight
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
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Verlauf", fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showChartDatePicker = true }) {
                            Icon(Icons.Default.FilterList, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Ab: ${chartStartDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))}")
                        }
                    }
                    
                    if (filteredHistory.size >= 2) {
                        Box(Modifier.fillMaxWidth().height(180.dp)) {
                            WeightLineChart(
                                data = filteredHistory.reversed(),
                                startWeight = filteredHistory.last().weight
                            )
                        }
                    } else {
                        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                            Text("Nicht genug Daten im Zeitraum", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
        }

        // History Section
        item {
            Text("Historie", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        
        items(weightHistory, key = { it.dateIso }) { entry ->
            val isVerified = vm.dayVerifications[entry.dateIso] ?: false
            val budget = if (isVerified) vm.calculateWeightBudgetGrams(entry.dateIso, userProfile) else 0.0

            SwipeActionContainer(
                onDeleteRequest = { vm.deleteWeightEntry(entry.dateIso) },
                onEditRequest = { editingEntry = entry },
                key = entry.dateIso
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
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
                        Text(
                            text = "${entry.weight} kg", 
                            fontWeight = FontWeight.Bold, 
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.widthIn(min = 80.dp),
                            textAlign = TextAlign.End
                        )
                    }
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
private fun WeightLineChart(
    data: List<WeightEntry>,
    startWeight: Double
) {
    if (data.isEmpty()) return
    
    val oldestDate = LocalDate.parse(data.first().dateIso)
    val newestDate = LocalDate.parse(data.last().dateIso)
    val totalDays = ChronoUnit.DAYS.between(oldestDate, newestDate).coerceAtLeast(1)
    
    val primaryColor = MaterialTheme.colorScheme.primary
    
    var selectedEntry by remember { mutableStateOf<WeightEntry?>(null) }
    var touchPos by remember { mutableStateOf<Offset?>(null) }

    Box(Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(data) {
                detectTapGestures(
                    onPress = { offset ->
                        touchPos = offset
                        tryAwaitRelease()
                        touchPos = null
                        selectedEntry = null
                    },
                    onTap = { offset -> touchPos = offset }
                )
            }
            .pointerInput(data) {
                detectDragGestures(
                    onDragStart = { offset -> touchPos = offset },
                    onDragEnd = { touchPos = null; selectedEntry = null },
                    onDragCancel = { touchPos = null; selectedEntry = null },
                    onDrag = { change, _ ->
                        touchPos = change.position
                        change.consume()
                    }
                )
            }
        ) {
            val maxW = data.maxOf { it.weight }
            val minW = data.minOf { it.weight }
            val range = (maxW - minW).coerceAtLeast(0.1)
            
            val width = size.width
            val height = size.height
            
            val points = data.map { entry ->
                val entryDate = LocalDate.parse(entry.dateIso)
                val daysSinceStart = ChronoUnit.DAYS.between(oldestDate, entryDate)
                val x = (daysSinceStart.toFloat() / totalDays) * width
                val y = height - ((entry.weight - minW) / range * height).toFloat()
                Offset(x, y) to entry
            }
            
            // Draw path
            val path = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points.first().first.x, points.first().first.y)
                    points.drop(1).forEach { lineTo(it.first.x, it.first.y) }
                }
            }
            drawPath(path, primaryColor.copy(alpha = 0.5f), style = Stroke(width = 2.dp.toPx()))
            
            // Draw points
            points.forEach { (offset, _) ->
                drawCircle(primaryColor, radius = 3.dp.toPx(), center = offset)
            }
            
            // Interaction
            touchPos?.let { pos ->
                val closest = points.minByOrNull { abs(it.first.x - pos.x) }
                closest?.let { (offset, entry) ->
                    selectedEntry = entry
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = Offset(offset.x, 0f),
                        end = Offset(offset.x, height),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawCircle(primaryColor, radius = 6.dp.toPx(), center = offset)
                }
            }
        }
        
        // Tooltip Overlay
        selectedEntry?.let { entry ->
            val diff = entry.weight - startWeight
            val diffSign = if (diff > 0) "+" else ""
            
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                tonalElevation = 4.dp
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(LocalDate.parse(entry.dateIso).toString(), style = MaterialTheme.typography.labelSmall)
                    Text("${entry.weight} kg", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "$diffSign${"%.1f".format(diff)} kg", 
                        style = MaterialTheme.typography.labelSmall,
                        color = if (diff <= 0) Color(0xFF2E7D32) else Color.Red
                    )
                }
            }
        }
    }
}

private fun Double.round0(): String = "%.0f".format(this)
