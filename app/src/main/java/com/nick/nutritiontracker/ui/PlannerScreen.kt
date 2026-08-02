package com.nick.nutritiontracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.BarcodeScannerService
import com.nick.nutritiontracker.data.FoodEntryEntity
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(vm: NutritionViewModel) {
    var plannerDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    val plannedEntries = vm.plannedEntries.filter { it.dateIso == plannerDate.toString() }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN) }
    
    val household by vm.firebaseManager.household.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scannerService = remember { BarcodeScannerService(context) }
    
    var entryToEdit by remember { mutableStateOf<FoodEntryEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (household == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bitte erstelle einen Haushalt im Profil, um den Planer zu nutzen.")
        }
        return
    }

    if (entryToEdit != null) {
        val currentEntry = entryToEdit!!
        if (currentEntry.isMeal) {
            EditMealEntryDialog(
                entry = currentEntry,
                vm = vm,
                onDismiss = { entryToEdit = null },
                onSave = { updated ->
                    vm.updatePlannedEntry(updated)
                    entryToEdit = null
                }
            )
        } else {
            EditEntryDialog(
                entry = currentEntry,
                vm = vm,
                onDismiss = { entryToEdit = null },
                onSave = { updated ->
                    vm.updatePlannedEntry(updated)
                    entryToEdit = null
                }
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { plannerDate = plannerDate.minusDays(1) }) {
                    Icon(Icons.Default.ChevronLeft, null)
                }
                Text(plannerDate.format(dateFormatter), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = { plannerDate = plannerDate.plusDays(1) }) {
                    Icon(Icons.Default.ChevronRight, null)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            AddEntryCard(
                foods = vm.foods,
                meals = vm.meals,
                onAddEntry = { food, amount, portion, mealSlot ->
                    vm.addPlannedEntry(food, amount, portion, mealSlot, plannerDate)
                },
                onAddMeal = { meal, mealSlot, servings ->
                    vm.addPlannedMeal(meal, mealSlot, plannerDate, servings)
                },
                onScanRequest = {
                    scope.launch {
                        val barcode = scannerService.startScan()
                        if (barcode != null) {
                            val food = scannerService.fetchProduct(barcode)
                            if (food != null) {
                                vm.addPlannedEntry(food, 100.0, null, "Snack", plannerDate)
                            }
                        }
                    }
                },
                onSearchRequest = { query -> scannerService.searchProducts(query) },
                onCaptureRequested = { /* Handle capture */ },
                vm = vm,
                snackbarHostState = snackbarHostState
            )

            LazyColumn(
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(plannedEntries, key = { it.id }) { entry ->
                    SwipeActionContainer(
                        onDeleteRequest = { vm.deletePlannedEntry(entry.id) },
                        onEditRequest = { entryToEdit = entry }
                    ) {
                        PlannedEntryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
fun PlannedEntryRow(entry: FoodEntryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontWeight = FontWeight.Bold)
                Text("${entry.mealSlot} · ${entry.displayAmount()}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
