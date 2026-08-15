package com.nick.nutritiontracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nick.nutritiontracker.data.FoodEntryEntity
import com.nick.nutritiontracker.data.FoodItemEntity
import com.nick.nutritiontracker.data.MealEntity
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(vm: NutritionViewModel, @Suppress("UNUSED_PARAMETER") userProfile: com.nick.nutritiontracker.data.UserProfile) {
    val household by vm.firebaseManager.household.collectAsState()
    val days = remember { (0..13).map { LocalDate.now().plusDays(it.toLong()) } }
    
    var showPickerForDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedFoodForPlanning by remember { mutableStateOf<FoodItemEntity?>(null) }
    var selectedMealForPlanning by remember { mutableStateOf<MealEntity?>(null) }
    var foodPlanningDate by remember { mutableStateOf<LocalDate?>(null) }
    var entryToEdit by remember { mutableStateOf<FoodEntryEntity?>(null) }
    var entryToDelete by remember { mutableStateOf<FoodEntryEntity?>(null) }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Eintrag löschen") },
            text = { Text("Möchtest du auch die zugehörigen Einträge auf der Einkaufsliste löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePlannedEntry(entryToDelete!!.id, deleteFromShoppingList = true)
                    entryToDelete = null
                }) { Text("Ja, alles löschen", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deletePlannedEntry(entryToDelete!!.id, deleteFromShoppingList = false)
                    entryToDelete = null
                }) { Text("Nein, nur Planer") }
            }
        )
    }

    if (household == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bitte erstelle einen Haushalt im Profil, um den Planer zu nutzen.")
        }
        return
    }

    if (selectedFoodForPlanning != null && foodPlanningDate != null) {
        AddAmountDialog(
            food = selectedFoodForPlanning!!,
            vm = vm,
            onDismiss = { 
                selectedFoodForPlanning = null
                foodPlanningDate = null
            },
            onConfirm = { amount, portion, pkg, mealSlot ->
                vm.addPlannedEntry(selectedFoodForPlanning!!, amount, portion, mealSlot, foodPlanningDate!!, pkg = pkg)
                selectedFoodForPlanning = null
                foodPlanningDate = null
            }
        )
    }

    if (selectedMealForPlanning != null && foodPlanningDate != null) {
        AddMealAmountDialog(
            meal = selectedMealForPlanning!!,
            onDismiss = { 
                selectedMealForPlanning = null
                foodPlanningDate = null
            },
            onConfirm = { servings, mealSlot ->
                vm.addPlannedMeal(selectedMealForPlanning!!, mealSlot, foodPlanningDate!!, servings)
                selectedMealForPlanning = null
                foodPlanningDate = null
            }
        )
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Wochenplaner", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(days) { date ->
                DayPlannerCard(
                    date = date,
                    entries = vm.plannedEntries.filter { it.dateIso == date.toString() },
                    onAddClick = { showPickerForDate = date },
                    onEntryClick = { entryToEdit = it },
                    onDeleteEntry = { entryToDelete = it }
                )
            }
        }
    }

    if (showPickerForDate != null) {
        PlannedItemPickerBottomSheet(
            onDismiss = { showPickerForDate = null },
            onMealSelected = { meal ->
                selectedMealForPlanning = meal
                foodPlanningDate = showPickerForDate
                showPickerForDate = null
            },
            onFoodSelected = { food ->
                selectedFoodForPlanning = food
                foodPlanningDate = showPickerForDate
                showPickerForDate = null
            },
            vm = vm
        )
    }
}

@Composable
fun DayPlannerCard(
    date: LocalDate,
    entries: List<FoodEntryEntity>,
    onAddClick: () -> Unit,
    onEntryClick: (FoodEntryEntity) -> Unit,
    onDeleteEntry: (FoodEntryEntity) -> Unit
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN) }
    val isToday = date == LocalDate.now()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                 else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isToday) "Heute, ${date.format(DateTimeFormatter.ofPattern("d. MMM"))}" else date.format(dateFormatter),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Unspecified
                )
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            if (entries.isEmpty()) {
                Text("Noch nichts geplant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                entries.forEach { entry ->
                    SwipeActionContainer(
                        onDeleteRequest = { onDeleteEntry(entry) },
                        onEditRequest = { onEntryClick(entry) },
                        key = entry.id
                    ) {
                        PlannedEntryRow(entry)
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannedItemPickerBottomSheet(
    onDismiss: () -> Unit,
    onMealSelected: (MealEntity) -> Unit,
    onFoodSelected: (FoodItemEntity) -> Unit,
    vm: NutritionViewModel
) {
    val meals = vm.meals
    val foods = vm.foods
    var searchQuery by remember { mutableStateOf("") }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var tabIndex by remember { mutableIntStateOf(0) }
    
    val allTags = remember(meals) {
        meals.flatMap { it.tags }.distinct().sorted()
    }
    
    val filteredMeals = remember(searchQuery, selectedTags, meals) {
        meals.filter { meal ->
            (selectedTags.isEmpty() || meal.tags.any { it in selectedTags }) &&
            (searchQuery.isBlank() || meal.name.contains(searchQuery, ignoreCase = true) || meal.tags.any { it.contains(searchQuery, ignoreCase = true) })
        }
    }

    val filteredFoods = remember(searchQuery, foods) {
        if (searchQuery.isBlank()) emptyList()
        else foods.filter { it.name.contains(searchQuery, ignoreCase = true) || it.brand?.contains(searchQuery, ignoreCase = true) == true }.take(20)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).fillMaxHeight(0.85f)) {
            Text("Element planen", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            
            Spacer(Modifier.height(12.dp))
            
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) { Text("Mahlzeiten") }
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) { Text("Artikel") }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Suchen...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(Modifier.height(12.dp))
            
            if (tabIndex == 0) {
                if (allTags.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTags.isEmpty(),
                            onClick = { selectedTags = emptySet() },
                            label = { Text("Alle") }
                        )
                        allTags.forEach { tag ->
                            FilterChip(
                                selected = tag in selectedTags,
                                onClick = { 
                                    selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
                LazyColumn(Modifier.weight(1f)) {
                    items(filteredMeals) { meal ->
                        ListItem(
                            headlineContent = { Text(meal.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { 
                                Column {
                                    Text("${meal.kcalPerServing.toInt()} kcal")
                                    if (meal.tags.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            meal.tags.take(3).forEach { tag ->
                                                SuggestionChip(
                                                    onClick = {},
                                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            leadingContent = {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (!meal.imageUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = meal.imageUrl,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onMealSelected(meal) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(filteredFoods) { food ->
                        ListItem(
                            headlineContent = { Text(food.name, fontWeight = FontWeight.SemiBold) },
                            supportingContent = { 
                                Text("${food.brand ?: "Marke unbekannt"} · ${food.kcalPer100g.toInt()} kcal/100${food.baseUnit}")
                            },
                            leadingContent = {
                                Icon(if (food.isGeneric) Icons.Default.Inventory2 else Icons.Default.Restaurant, null, tint = MaterialTheme.colorScheme.secondary)
                            },
                            modifier = Modifier.clickable { onFoodSelected(food) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                    if (filteredFoods.isEmpty() && searchQuery.length > 1) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Keine Artikel gefunden", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PlannedEntryRow(entry: FoodEntryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (entry.isMeal) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f) 
                        else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!entry.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = entry.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            if (entry.isMeal) Icons.Default.Restaurant else Icons.Default.Fastfood, 
                            null, 
                            modifier = Modifier.size(20.dp),
                            tint = if (entry.isMeal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                if (!entry.brand.isNullOrBlank() || !entry.store.isNullOrBlank()) {
                    Text(
                        text = (entry.brand ?: "") + (if (!entry.store.isNullOrBlank()) " @ ${entry.store}" else ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${entry.mealSlot} · ${entry.displayAmount()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (entry.tags.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        entry.tags.take(2).forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            ) {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.8),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }
            Text("${entry.kcal.toInt()} kcal", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddMealAmountDialog(
    meal: MealEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("1") }
    var mealSlot by remember { mutableStateOf("Mittag") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(meal.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoSelectTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Portionen") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(amount.num(), mealSlot) })
                )
                Text("Mahlzeit", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Frühstück", "Mittag", "Abend", "Snack").forEach { slot ->
                        FilterChip(
                            selected = mealSlot == slot,
                            onClick = { mealSlot = slot },
                            label = { Text(slot) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(amount.num(), mealSlot)
            }) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun String.num(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
