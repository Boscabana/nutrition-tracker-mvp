package com.nick.nutritiontracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
import com.nick.nutritiontracker.data.FoodEntryEntity
import com.nick.nutritiontracker.data.FoodItemEntity
import com.nick.nutritiontracker.data.MealEntity
import com.nick.nutritiontracker.viewmodel.PlanMatchStatus
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
    var poolItemToTake by remember { mutableStateOf<com.nick.nutritiontracker.data.PlannedMealPoolEntity?>(null) }
    var poolItemToDelete by remember { mutableStateOf<com.nick.nutritiontracker.data.PlannedMealPoolEntity?>(null) }
    var poolItemToEdit by remember { mutableStateOf<com.nick.nutritiontracker.data.PlannedMealPoolEntity?>(null) }
    var showPoolAddDialog by remember { mutableStateOf(false) }
    var showFreezerAddDialog by remember { mutableStateOf(false) }
    var showFreezerDialog by remember { mutableStateOf(false) }
    var isAddingToFreezer by remember { mutableStateOf(false) }

    var draggedEntry by remember { mutableStateOf<FoodEntryEntity?>(null) }
    var showPortionMoveDialog by remember { mutableStateOf<Pair<FoodEntryEntity, LocalDate>?>(null) }

    if (showPortionMoveDialog != null) {
        val (entry, targetDate) = showPortionMoveDialog!!
        PortionMoveDialog(
            entry = entry,
            onDismiss = { showPortionMoveDialog = null },
            onConfirm = { portions ->
                vm.movePlannedEntry(entry.id, targetDate, portions)
                showPortionMoveDialog = null
            }
        )
    }

    if (showFreezerDialog) {
        FreezerDialog(
            pool = vm.plannedMealPool.filter { it.isFrozen },
            vm = vm,
            onDismiss = { showFreezerDialog = false },
            onAddClick = { 
                showFreezerAddDialog = true
                showFreezerDialog = false
            },
            onTakeClick = { 
                poolItemToTake = it
                showFreezerDialog = false 
            },
            onEditClick = { poolItemToEdit = it },
            onDeleteClick = { poolItemToDelete = it }
        )
    }

    if (showFreezerAddDialog) {
        PlannedItemPickerBottomSheet(
            onDismiss = { showFreezerAddDialog = false },
            onMealSelected = { meal ->
                selectedMealForPlanning = meal
                // We'll use the existing AddPoolMealDialog but we need to know it's for freezer
                // Let's add a flag or a separate state
                showFreezerAddDialog = false
            },
            onFoodSelected = { /* Maybe only meals for freezer for now */ },
            vm = vm,
            title = "In den Gefrierschrank"
        )
    }

    if (poolItemToDelete != null) {
        AlertDialog(
            onDismissRequest = { poolItemToDelete = null },
            title = { Text("Pool-Eintrag löschen") },
            text = { Text("Möchtest du auch die zugehörigen Artikel von der Einkaufsliste entfernen?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePoolItem(poolItemToDelete!!.id, deleteFromShoppingList = true)
                    poolItemToDelete = null
                }) { Text("Ja, alles löschen", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.deletePoolItem(poolItemToDelete!!.id, deleteFromShoppingList = false)
                    poolItemToDelete = null
                }) { Text("Nein, nur Pool") }
            }
        )
    }

    if (poolItemToTake != null) {
        TakeFromPoolDialog(
            poolItem = poolItemToTake!!,
            days = days,
            onDismiss = { poolItemToTake = null },
            onConfirm = { date, servings, addToDiary ->
                vm.takeFromPool(poolItemToTake!!, date, servings, addToDiary)
                poolItemToTake = null
            }
        )
    }

    if (showPoolAddDialog) {
        PlannedItemPickerBottomSheet(
            onDismiss = { showPoolAddDialog = false },
            onMealSelected = { meal ->
                selectedMealForPlanning = meal
                isAddingToFreezer = false
                showPoolAddDialog = false
            },
            onFoodSelected = { food ->
                selectedFoodForPlanning = food
                showPoolAddDialog = false
            },
            vm = vm,
            title = "Zum Pool hinzufügen"
        )
    }

    if (showFreezerAddDialog) {
        PlannedItemPickerBottomSheet(
            onDismiss = { 
                showFreezerAddDialog = false
                showFreezerDialog = true
            },
            onMealSelected = { meal ->
                selectedMealForPlanning = meal
                isAddingToFreezer = true
                showFreezerAddDialog = false
            },
            onFoodSelected = { },
            vm = vm,
            title = "In den Gefrierschrank"
        )
    }

    // Special case for pool addition when a meal was picked from the bottom sheet
    if (selectedMealForPlanning != null && foodPlanningDate == null) {
        AddPoolMealDialog(
            meal = selectedMealForPlanning!!,
            isFreezer = isAddingToFreezer,
            onDismiss = { 
                if (isAddingToFreezer) showFreezerDialog = true
                selectedMealForPlanning = null 
            },
            onConfirm = { portions ->
                vm.addMealToPool(selectedMealForPlanning!!, portions, isFrozen = isAddingToFreezer)
                if (isAddingToFreezer) showFreezerDialog = true
                selectedMealForPlanning = null
            }
        )
    }

    if (poolItemToEdit != null) {
        val mappedMeal = MealEntity(
            name = poolItemToEdit!!.mealName,
            ingredients = poolItemToEdit!!.mealIngredients,
            imageUrl = poolItemToEdit!!.imageUrl,
            tags = poolItemToEdit!!.tags,
            servings = poolItemToEdit!!.plannedPortions
        )
        MealEditDialog(
            meal = mappedMeal,
            vm = vm,
            onDismiss = { poolItemToEdit = null },
            onSave = { name, ingredients, servings, tags, imageUrl ->
                vm.updatePoolItem(poolItemToEdit!!.copy(
                    mealName = name,
                    mealIngredients = ingredients,
                    plannedPortions = servings,
                    tags = tags,
                    imageUrl = imageUrl
                ))
                poolItemToEdit = null
            }
        )
    }

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
                title = { Text("Wochenplaner", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showFreezerDialog = true }) {
                        Icon(Icons.Default.AcUnit, "Gefrierschrank", tint = Color(0xFF1976D2))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                MealPoolSection(
                    pool = vm.plannedMealPool.filter { !it.isFrozen },
                    vm = vm,
                    onAddClick = { showPoolAddDialog = true },
                    onTakeClick = { poolItemToTake = it },
                    onEditClick = { poolItemToEdit = it },
                    onDeleteClick = { poolItemToDelete = it }
                )
            }

            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Text("Tagesplanung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(days) { date ->
                val isDropTarget = draggedEntry != null && draggedEntry?.dateIso != date.toString()
                
                Box(modifier = Modifier.fillMaxWidth().then(
                    if (isDropTarget) {
                        Modifier.clickable {
                            showPortionMoveDialog = draggedEntry!! to date
                            draggedEntry = null
                        }
                    } else Modifier
                )) {
                    DayPlannerCard(
                        date = date,
                        entries = vm.plannedEntries.filter { it.dateIso == date.toString() },
                        userProfile = userProfile,
                        onAddClick = { showPickerForDate = date },
                        onEntryClick = { entry ->
                            if (draggedEntry == null) {
                                entryToEdit = entry
                            } else {
                                if (draggedEntry?.dateIso != date.toString()) {
                                    showPortionMoveDialog = draggedEntry!! to date
                                    draggedEntry = null
                                } else {
                                    draggedEntry = null
                                }
                            }
                        },
                        onDeleteEntry = { entryToDelete = it },
                        vm = vm,
                        draggedEntryId = draggedEntry?.id,
                        onLongClickEntry = { entry ->
                            draggedEntry = if (draggedEntry?.id == entry.id) null else entry
                        }
                    )
                    
                    if (isDropTarget) {
                        Surface(
                            modifier = Modifier.matchParentSize(),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Hierher verschieben", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DayPlannerCard(
    date: LocalDate,
    entries: List<FoodEntryEntity>,
    userProfile: com.nick.nutritiontracker.data.UserProfile,
    onAddClick: () -> Unit,
    onEntryClick: (FoodEntryEntity) -> Unit,
    onDeleteEntry: (FoodEntryEntity) -> Unit,
    vm: NutritionViewModel,
    draggedEntryId: Long? = null,
    onLongClickEntry: (FoodEntryEntity) -> Unit = {}
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
            
            // Calorie progress bar
            val totalPlannedKcal = remember(entries) { entries.sumOf { it.kcal } }
            val activityKcal = vm.getActivityKcal(date.toString(), userProfile)
            val totalBudget = userProfile.calorieBudget + activityKcal
            
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                LinearProgressIndicator(
                    progress = { (totalPlannedKcal / totalBudget.toDouble()).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = if (totalPlannedKcal > totalBudget) Color(0xFFF44336) else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${totalPlannedKcal.toInt()} / ${totalBudget.toInt()} kcal geplant",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (totalPlannedKcal > totalBudget) Color(0xFFF44336) else MaterialTheme.colorScheme.outline
                    )
                    
                    val remaining = (totalBudget - totalPlannedKcal).toInt()
                    if (remaining >= 0) {
                        Text(
                            "Noch $remaining kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "${-remaining} kcal drüber",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFF44336)
                        )
                    }
                }
            }

            if (entries.isEmpty()) {
                Text("Noch nichts geplant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                entries.forEach { entry ->
                    val isDragged = draggedEntryId == entry.id
                    
                    Box(modifier = Modifier.fillMaxWidth().then(
                        if (isDragged) Modifier.graphicsLayer { alpha = 0.5f; scaleX = 0.95f; scaleY = 0.95f }
                        else Modifier
                    )) {
                        SwipeActionContainer(
                            onDeleteRequest = { onDeleteEntry(entry) },
                            onEditRequest = { onEntryClick(entry) },
                            key = entry.id
                        ) {
                            val status = vm.getMatchStatus(entry)
                            
                            val onImport: () -> Unit = {
                                if (entry.isMeal) {
                                    vm.importPlannedMealToLibrary(entry)
                                } else {
                                    vm.importPlannedEntryToLibrary(entry, replaceExisting = true)
                                }
                            }
                            
                            Box(modifier = Modifier.combinedClickable(
                                onClick = { onEntryClick(entry) },
                                onLongClick = { onLongClickEntry(entry) }
                            )) {
                                PlannedEntryRow(entry, status, onImportClick = onImport)
                            }
                        }
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
    vm: NutritionViewModel,
    title: String = "Element planen"
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
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            
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
fun PlannedEntryRow(entry: FoodEntryEntity, status: PlanMatchStatus = PlanMatchStatus.EXACT, onImportClick: () -> Unit = {}) {
    val isMissing = status == PlanMatchStatus.MISSING
    val isDivergent = status == PlanMatchStatus.DIVERGENT
    val isTemplateMissing = status == PlanMatchStatus.TEMPLATE_MISSING
    val isExact = status == PlanMatchStatus.EXACT

    Surface(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp, 
            color = when {
                isMissing -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                isDivergent -> Color(0xFFFFC107).copy(alpha = 0.4f)
                isTemplateMissing -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
            }
        )
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when {
                    entry.isFromFreezer -> Color(0xFFE3F2FD).copy(alpha = 0.5f)
                    isTemplateMissing -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                    entry.isMeal -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.1f)
                    isMissing -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                    isDivergent -> Color(0xFFFFE082).copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                },
                modifier = Modifier.size(36.dp)
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
                            imageVector = when {
                                entry.isFromFreezer -> Icons.Default.AcUnit
                                isMissing -> Icons.Default.CloudDownload
                                isDivergent -> Icons.Default.SyncProblem
                                isTemplateMissing -> Icons.Default.AutoAwesome
                                entry.isMeal -> Icons.Default.Restaurant
                                else -> Icons.Default.Fastfood
                            }, 
                            contentDescription = null, 
                            modifier = Modifier.size(18.dp),
                            tint = when {
                                entry.isFromFreezer -> Color(0xFF1976D2)
                                isMissing -> MaterialTheme.colorScheme.error
                                isDivergent -> Color(0xFFFFA000)
                                isTemplateMissing -> MaterialTheme.colorScheme.tertiary
                                entry.isMeal -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.secondary
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.name, 
                        fontWeight = FontWeight.Bold, 
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    if (!isExact) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = if (isMissing) Icons.Default.CloudDownload else Icons.Default.Sync,
                            contentDescription = "Sync",
                            modifier = Modifier.size(12.dp).clickable { onImportClick() },
                            tint = if (isMissing) MaterialTheme.colorScheme.error else Color(0xFFFFA000)
                        )
                    }
                }
                Text(
                    text = "${entry.mealSlot} · ${entry.displayAmount()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
            Text("${entry.kcal.toInt()} kcal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
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

@Composable
fun MealPoolSection(
    pool: List<com.nick.nutritiontracker.data.PlannedMealPoolEntity>,
    vm: NutritionViewModel,
    onAddClick: () -> Unit,
    onTakeClick: (com.nick.nutritiontracker.data.PlannedMealPoolEntity) -> Unit,
    onEditClick: (com.nick.nutritiontracker.data.PlannedMealPoolEntity) -> Unit,
    onDeleteClick: (com.nick.nutritiontracker.data.PlannedMealPoolEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SoupKitchen, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text("Mahlzeiten-Pool", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                }
            }

            if (pool.isEmpty()) {
                Text(
                    "Noch keine Mahlzeiten im Pool. Plane hier Mahlzeiten für die Woche, die dann in die Einkaufsliste kommen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                pool.forEach { item ->
                    key(item.id) {
                        SwipeActionContainer(
                            onDeleteRequest = { onDeleteClick(item) },
                            onEditRequest = { onEditClick(item) },
                            key = item.id
                        ) {
                            PoolItemRow(item, onTakeClick, onEditClick, onDeleteClick, vm)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
fun FreezerDialog(
    pool: List<com.nick.nutritiontracker.data.PlannedMealPoolEntity>,
    vm: NutritionViewModel,
    onDismiss: () -> Unit,
    onAddClick: () -> Unit,
    onTakeClick: (com.nick.nutritiontracker.data.PlannedMealPoolEntity) -> Unit,
    onEditClick: (com.nick.nutritiontracker.data.PlannedMealPoolEntity) -> Unit,
    onDeleteClick: (com.nick.nutritiontracker.data.PlannedMealPoolEntity) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AcUnit, null, tint = Color(0xFF1976D2))
                    Spacer(Modifier.width(8.dp))
                    Text("Gefrierschrank", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF1565C0), modifier = Modifier.weight(1f))
                    IconButton(onClick = onAddClick) { Icon(Icons.Default.AddCircle, null, tint = Color(0xFF1976D2)) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                
                Text("Hier liegen deine bereits gekochten Portionen. Sie erscheinen nicht auf der Einkaufsliste.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                
                Spacer(Modifier.height(16.dp))
                
                if (pool.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Dein Gefrierschrank ist leer.", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pool) { item ->
                            key(item.id) {
                                SwipeActionContainer(
                                    onDeleteRequest = { onDeleteClick(item) },
                                    onEditRequest = { onEditClick(item) },
                                    key = item.id
                                ) {
                                    PoolItemRow(item, onTakeClick, onEditClick, onDeleteClick, vm, isFrozen = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PoolItemRow(
    item: com.nick.nutritiontracker.data.PlannedMealPoolEntity,
    onTakeClick: (com.nick.nutritiontracker.data.PlannedMealPoolEntity) -> Unit,
    onEditClick: (com.nick.nutritiontracker.data.PlannedMealPoolEntity) -> Unit,
    onDeleteClick: (com.nick.nutritiontracker.data.PlannedMealPoolEntity) -> Unit,
    vm: NutritionViewModel,
    isFinished: Boolean = false,
    isFrozen: Boolean = false
) {
    val tempEntry = remember(item) {
        FoodEntryEntity(
            name = item.mealName,
            isMeal = true,
            mealIngredients = item.mealIngredients,
            amount = item.plannedPortions
        )
    }
    val status = vm.getMatchStatus(tempEntry)
    val isExact = status == PlanMatchStatus.EXACT

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFrozen -> Color(0xFFF0F8FF)
            isFinished -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(32.dp)) {
                if (!item.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (isFrozen) Icons.Default.AcUnit else Icons.Default.Restaurant, 
                        contentDescription = null, 
                        modifier = Modifier.padding(4.dp), 
                        tint = if (isFrozen) Color(0xFF1976D2) else MaterialTheme.colorScheme.outline
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.mealName, 
                        fontWeight = FontWeight.SemiBold, 
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        color = if (isFinished) MaterialTheme.colorScheme.outline else Color.Unspecified
                    )
                    if (!isExact && !isFinished && !isFrozen) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = if (status == PlanMatchStatus.MISSING) Icons.Default.CloudDownload else Icons.Default.Sync,
                            contentDescription = "Sync nötig",
                            modifier = Modifier.size(14.dp).clickable { onEditClick(item) },
                            tint = if (status == PlanMatchStatus.MISSING) MaterialTheme.colorScheme.error else Color(0xFFFFA000)
                        )
                    }
                }
                Text(
                    "Gesamt geplant: ${item.plannedPortions.roundString()}", 
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isFrozen -> Color(0xFF1976D2)
                        isFinished -> MaterialTheme.colorScheme.outline
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                )
            }
            
            if (!isFinished) {
                IconButton(onClick = { onTakeClick(item) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.CalendarToday, "Auf Tag verteilen", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = { onDeleteClick(item) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, "Löschen", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddPoolMealDialog(
    meal: MealEntity,
    isFreezer: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var portions by remember { mutableStateOf("4") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isFreezer) "In den Gefrierschrank" else "In den Pool legen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (isFreezer) "Wie viele Portionen '${meal.name}' hast du eingefroren?" else "Wie viele Portionen '${meal.name}' möchtest du für diese Woche einplanen?")
                AutoSelectTextField(
                    value = portions,
                    onValueChange = { portions = it },
                    label = { Text("Portionen (Gesamt)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(portions.num()) })
                )
                if (!isFreezer) {
                    Text(
                        "Die Zutaten für $portions Portionen werden automatisch auf die Einkaufsliste gesetzt.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(portions.num()) }) { Text(if (isFreezer) "Einfrieren" else "Einplanen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TakeFromPoolDialog(
    poolItem: com.nick.nutritiontracker.data.PlannedMealPoolEntity,
    days: List<LocalDate>,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, Double, Boolean) -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var servings by remember { mutableStateOf("1") }
    var addToDiary by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Portion nehmen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("An welchem Tag möchtest du '${poolItem.mealName}' essen?")
                
                // Simple date picker row
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    days.forEach { date ->
                        FilterChip(
                            selected = selectedDate == date,
                            onClick = { selectedDate = date },
                            label = { Text(date.format(DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN))) }
                        )
                    }
                }

                AutoSelectTextField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = { Text("Portionen") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = addToDiary, onCheckedChange = { addToDiary = it })
                    Text("Direkt ins Tagebuch eintragen", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedDate, servings.num(), addToDiary) }) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun PortionMoveDialog(
    entry: FoodEntryEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    val maxPortions = remember(entry.amount) { entry.amount.toInt().coerceAtLeast(1) }
    var portions by remember { mutableStateOf(maxPortions.toDouble()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Portionen verschieben") },
        text = {
            Column {
                Text(entry.name, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text("Wie viele ganze Portionen möchtest du verschieben?")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${portions.toInt()} Portion(en)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = portions.toFloat(),
                    onValueChange = { portions = kotlin.math.round(it).toDouble() },
                    valueRange = 1f..maxPortions.toFloat(),
                    steps = (maxPortions - 2).coerceAtLeast(0)
                )
                if (entry.amount < 1.0) {
                    Text(
                        "Hinweis: Der Eintrag hat weniger als eine ganze Portion. Er wird komplett verschoben.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                // Wenn der Original-Eintrag < 1 war, verschieben wir den Rest komplett
                val toMove = if (entry.amount < 1.0) entry.amount else portions
                onConfirm(toMove) 
            }) { Text("Verschieben") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun String.num(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.round0(): String = "%.0f".format(this)
private fun Double.roundString(): String = toString().replace(".0", "")
