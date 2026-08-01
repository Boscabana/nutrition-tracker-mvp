package com.nick.nutritiontracker.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.nick.nutritiontracker.data.*
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ProteinGreen = Color(0xFF2E7D32)
private val CarbOrange = Color(0xFFFF9800)
private val SugarRed = Color(0xFFD32F2F)
private val SaturatedGrey = Color(0xFF757575)
private val UnsaturatedYellow = Color(0xFFFBC02D)
private val ActionEditYellow = Color(0xFFFFC107) 
private val ActionDeleteRed = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionApp(vm: NutritionViewModel, profileVm: ProfileViewModel) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    val foods = vm.foods
    val entries = vm.todayEntries
    val userProfile by profileVm.userProfile.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN) }
    var showCalendar by remember { mutableStateOf(false) }

    if (showCalendar) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = vm.selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showCalendar = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.selectDate(date)
                    }
                    showCalendar = false
                }) { Text("Auswählen") }
            },
            dismissButton = {
                TextButton(onClick = { showCalendar = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val selectedEntryIds = remember { mutableStateOf(setOf<Long>()) }
    val selectedFoodIds = remember { mutableStateOf(setOf<Long>()) }
    val selectedMealIds = remember { mutableStateOf(setOf<Long>()) }

    val isDiarySelection = selectedEntryIds.value.isNotEmpty()
    val isFoodSelection = selectedFoodIds.value.isNotEmpty()
    val isMealSelection = selectedMealIds.value.isNotEmpty()
    
    val isSelectionMode = isDiarySelection || isFoodSelection || isMealSelection

    if (isSelectionMode) {
        BackHandler { 
            selectedEntryIds.value = emptySet() 
            selectedFoodIds.value = emptySet()
            selectedMealIds.value = emptySet()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(vm.healthConnectManager.permissions)) {
            vm.syncStepsForSelectedDate()
            scope.launch { snackbarHostState.showSnackbar("Berechtigung erteilt!") }
        }
    }

    if (vm.pendingRecipeImport != null) {
        val recipe = vm.pendingRecipeImport!!
        AlertDialog(
            onDismissRequest = { vm.pendingRecipeImport = null },
            title = { Text("Rezept importieren") },
            text = { Text("Möchtest du '${recipe.meal.name}' zu deinen Mahlzeiten hinzufügen? Falls Artikel oder Mahlzeit bereits existieren, wie soll verfahren werden?") },
            confirmButton = {
                Row {
                    TextButton(onClick = { vm.resolveRecipeImport(supplement = true) }) { Text("Ergänzen/Ersetzen") }
                    TextButton(onClick = { vm.resolveRecipeImport(supplement = false) }) { Text("Nur Fehlendes") }
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.pendingRecipeImport = null }) { Text("Abbrechen") }
            }
        )
    }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                if (isSelectionMode) {
                    val selectionSize = selectedEntryIds.value.size + selectedFoodIds.value.size + selectedMealIds.value.size
                    TopAppBar(
                        title = { Text("$selectionSize ausgewählt") },
                        navigationIcon = {
                            IconButton(onClick = { 
                                selectedEntryIds.value = emptySet() 
                                selectedFoodIds.value = emptySet()
                                selectedMealIds.value = emptySet()
                            }) {
                                Icon(Icons.Default.Close, "Abbrechen")
                            }
                        },
                        actions = {
                            if (isDiarySelection) {
                                DiarySelectionActions(vm, selectedEntryIds, scope, snackbarHostState, dateFormatter)
                            }
                            if (isFoodSelection) {
                                FoodSelectionActions(vm, selectedFoodIds, scope, snackbarHostState)
                            }
                            if (isMealSelection) {
                                MealSelectionActions(vm, selectedMealIds, scope, snackbarHostState)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                } else {
                    TopAppBar(
                        title = {
                            if (tab == 0) {
                                TextButton(
                                    onClick = { showCalendar = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                ) {
                                    val label = when (vm.selectedDate) {
                                        LocalDate.now() -> "Heute"
                                        LocalDate.now().plusDays(1) -> "Morgen"
                                        else -> vm.selectedDate.format(dateFormatter)
                                    }
                                    Text(label, style = MaterialTheme.typography.titleLarge)
                                    Icon(Icons.Default.Event, null, modifier = Modifier.padding(start = 4.dp))
                                }
                            } else {
                                Text(
                                    when (tab) {
                                        1 -> "Artikel"
                                        2 -> "Mahlzeiten"
                                        4 -> "Planer"
                                        5 -> "Einkaufsliste"
                                        else -> "Profil & Ziele"
                                    }
                                )
                            }
                        },
                        actions = {
                            if (tab == 0) {
                                IconButton(onClick = {
                                    scope.launch {
                                        val status = vm.healthConnectManager.getAvailabilityStatus()
                                        when (status) {
                                            HealthConnectClient.SDK_AVAILABLE -> {
                                                if (vm.healthConnectManager.hasAllPermissions()) {
                                                    vm.syncStepsForSelectedDate()
                                                    snackbarHostState.showSnackbar("Schritte aktualisiert")
                                                } else {
                                                    permissionLauncher.launch(vm.healthConnectManager.permissions)
                                                }
                                            }
                                            else -> {
                                                vm.syncStepsForSelectedDate()
                                                snackbarHostState.showSnackbar("Health Connect nicht verfügbar")
                                            }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync steps")
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("Tagebuch") },
                        icon = { Icon(Icons.Default.Today, null) }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("Artikel") },
                        icon = { Icon(Icons.Default.Restaurant, null) }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        label = { Text("Mahlzeiten") },
                        icon = { Icon(Icons.Default.SoupKitchen, null) }
                    )
                    NavigationBarItem(
                        selected = tab == 4,
                        onClick = { tab = 4 },
                        label = { Text("Planer") },
                        icon = { Icon(Icons.Default.Event, null) }
                    )
                    NavigationBarItem(
                        selected = tab == 5,
                        onClick = { tab = 5 },
                        label = { Text("Einkaufen") },
                        icon = { Icon(Icons.Default.ShoppingCart, null) }
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        label = { Text("Profil") },
                        icon = { Icon(Icons.Default.Person, null) }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> TodayScreen(userProfile, foods, entries, vm, snackbarHostState, selectedEntryIds, selectedFoodIds, selectedMealIds)
                    1 -> FoodsScreen(vm, snackbarHostState, selectedFoodIds)
                    2 -> MealsScreen(vm, snackbarHostState, selectedMealIds)
                    3 -> ProfileScreen(profileVm, vm)
                    4 -> PlannerScreen(vm)
                    5 -> ShoppingListScreen(vm)
                }
            }
        }
    }
}

@Composable
fun SwipeActionContainer(
    onDeleteRequest: () -> Unit,
    onEditRequest: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val maxSwipePx = with(LocalDensity.current) { 80.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
    ) {
        val swipeValue = offsetX.value
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when {
                        swipeValue > 1f -> ActionEditYellow
                        swipeValue < -1f -> ActionDeleteRed
                        else -> Color.Transparent
                    }
                )
        ) {
            if (swipeValue > 20f) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Bearbeiten",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 24.dp)
                )
            }
            if (swipeValue < -20f) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 24.dp)
                )
            }
        }

        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .then(if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                scope.launch {
                                    offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-maxSwipePx, maxSwipePx))
                                }
                                change.consume()
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (offsetX.value > maxSwipePx * 0.6f) {
                                        offsetX.animateTo(0f)
                                        onEditRequest()
                                    } else if (offsetX.value < -maxSwipePx * 0.6f) {
                                        offsetX.animateTo(0f)
                                        onDeleteRequest()
                                    } else {
                                        offsetX.animateTo(0f)
                                    }
                                }
                            }
                        )
                    }
                } else Modifier),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp
        ) {
            content()
        }
    }
}

@Composable
private fun TodayScreen(
    userProfile: UserProfile,
    foods: List<FoodItemEntity>,
    entries: List<FoodEntryEntity>,
    vm: NutritionViewModel,
    snackbarHostState: SnackbarHostState,
    selectedEntryIds: MutableState<Set<Long>>,
    selectedFoodIds: MutableState<Set<Long>>,
    selectedMealIds: MutableState<Set<Long>>
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scannerService = remember { BarcodeScannerService(context) }

    var entryToDelete by remember { mutableStateOf<Long?>(null) }
    var entryToEdit by remember { mutableStateOf<FoodEntryEntity?>(null) }
    var scannedFood by remember { mutableStateOf<FoodItemEntity?>(null) }
    var duplicateFood by remember { mutableStateOf<FoodItemEntity?>(null) }
    var showStepDialog by remember { mutableStateOf(false) }
    var foodToCapture by remember { mutableStateOf<FoodItemEntity?>(null) }
    var askToCaptureFood by remember { mutableStateOf<FoodItemEntity?>(null) }

    val mealSlots = listOf("Frühstück", "Mittag", "Abend", "Snack")
    val expandedStates = remember { mutableStateMapOf<String, Boolean>().apply { mealSlots.forEach { put(it, true) } } }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Eintrag löschen") },
            text = { Text("Möchtest du diesen Eintrag wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    val id = entryToDelete
                    if (id != null) {
                        vm.deleteEntry(id)
                        entryToDelete = null
                    }
                }) { Text("Löschen", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Abbrechen") }
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
                    vm.updateEntry(updated)
                    entryToEdit = null
                }
            )
        } else {
            EditEntryDialog(
                entry = currentEntry,
                vm = vm,
                onDismiss = { entryToEdit = null },
                onSave = { updated ->
                    vm.updateEntry(updated)
                    entryToEdit = null
                }
            )
        }
    }

    if (scannedFood != null) {
        val food = scannedFood!!
        AddAmountDialog(
            food = food,
            vm = vm,
            onDismiss = { scannedFood = null },
            onConfirm = { amount, portion, mealSlot ->
                vm.addEntry(food, amount, portion, mealSlot)
                scannedFood = null
            }
        )
    }

    if (duplicateFood != null) {
        val food = duplicateFood!!
        AlertDialog(
            onDismissRequest = { duplicateFood = null },
            title = { Text("Artikel bereits vorhanden") },
            text = { Text("Ein Artikel mit dem Barcode '${food.barcode}' ist bereits als '${food.name}' gespeichert. Möchtest du den vorhandenen Artikel verwenden?") },
            confirmButton = {
                Button(onClick = {
                    scannedFood = food
                    duplicateFood = null
                }) { Text("Verwenden") }
            },
            dismissButton = {
                TextButton(onClick = {
                    duplicateFood = null
                }) { Text("Abbrechen") }
            }
        )
    }

    if (askToCaptureFood != null) {
        val food = askToCaptureFood!!
        AlertDialog(
            onDismissRequest = { askToCaptureFood = null },
            title = { Text("Neuer Artikel") },
            text = { Text("Möchtest du '${food.name}' dauerhaft in deinen Artikeln speichern (mit Portionen etc.) oder nur für diesen Eintrag verwenden?") },
            confirmButton = {
                Button(onClick = {
                    foodToCapture = food
                    askToCaptureFood = null
                }) { Text("Dauerhaft speichern") }
            },
            dismissButton = {
                TextButton(onClick = {
                    scannedFood = food
                    askToCaptureFood = null
                }) { Text("Nur verwenden") }
            }
        )
    }

    if (foodToCapture != null) {
        FoodEditDialog(
            food = foodToCapture,
            vm = vm,
            onDismiss = { foodToCapture = null },
            onSave = { newFood ->
                val saved = vm.addFood(
                    newFood.name, newFood.kcalPer100g, newFood.proteinPer100g,
                    newFood.carbsPer100g, newFood.sugarPer100g, newFood.fatPer100g,
                    newFood.saturatedFatPer100g, newFood.alcoholPercent, newFood.baseUnit,
                    newFood.portions, newFood.packages, newFood.barcode, newFood.brand,
                    newFood.category,
                    isGeneric = newFood.isGeneric,
                    parentId = newFood.parentId,
                    store = newFood.store
                )
                foodToCapture = null
                scannedFood = saved
            }
        )
    }
    
    if (showStepDialog) {
        StepInputDialog(
            initialSteps = vm.todaySteps,
            onDismiss = { showStepDialog = false },
            onConfirm = { 
                vm.updateSteps(it)
                showStepDialog = false
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
    ) {
        item {
            Box(Modifier.clickable { showStepDialog = true }) {
                MacroProgressSection(
                    userProfile = userProfile,
                    currentKcal = vm.todayTotalKcal,
                    currentProtein = vm.todayTotalProtein,
                    currentComplexCarbs = vm.todayTotalComplexCarbs,
                    currentSugar = vm.todayTotalSugar,
                    currentUnsaturatedFat = vm.todayTotalUnsaturatedFat,
                    currentSaturatedFat = vm.todayTotalSaturatedFat,
                    steps = vm.todaySteps
                )
            }
        }
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    MacroLegendRow()
                }
            }
        }
        item { 
            AddEntryCard(
                foods = foods, 
                meals = vm.meals,
                onAddEntry = { food, amount, portion, mealSlot ->
                    var finalFood = food
                    if (food.id == 0L) {
                        val existing = food.barcode?.let { vm.findFoodByBarcode(it) }
                        if (existing != null) {
                            finalFood = existing
                        } else {
                            finalFood = vm.addFood(
                                food.name, food.kcalPer100g, food.proteinPer100g,
                                food.carbsPer100g, food.sugarPer100g, food.fatPer100g,
                                food.saturatedFatPer100g, food.alcoholPercent, food.baseUnit,
                                food.portions, food.packages, food.barcode, food.brand, food.category,
                                isGeneric = food.isGeneric,
                                parentId = food.parentId,
                                store = food.store
                            )
                        }
                    }
                    vm.addEntry(finalFood, amount, portion, mealSlot)
                },
                onAddMeal = { meal, mealSlot, servings ->
                    vm.addMealEntry(meal, mealSlot, servings)
                },
                onScanRequest = {
                    scope.launch {
                        val barcode = scannerService.startScan()
                        if (barcode != null) {
                            val existing = vm.findFoodByBarcode(barcode)
                            if (existing != null) {
                                duplicateFood = existing
                            } else {
                                val fetched = scannerService.fetchProduct(barcode)
                                if (fetched != null) {
                                    askToCaptureFood = fetched
                                } else {
                                    snackbarHostState.showSnackbar("Produkt nicht gefunden. Bitte manuell erfassen.")
                                    foodToCapture = FoodItemEntity(name = "", kcalPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, sugarPer100g = 0.0, fatPer100g = 0.0, saturatedFatPer100g = 0.0, barcode = barcode)
                                }
                            }
                        }
                    }
                },
                onSearchRequest = { query -> scannerService.searchProducts(query) },
                onCaptureRequested = { food -> askToCaptureFood = food },
                vm = vm
            ) 
        }

        mealSlots.forEach { slot ->
            val slotEntries = entries.filter { it.mealSlot == slot }
            
            item(key = "header_$slot") {
                MealGroupHeader(
                    title = slot,
                    entries = slotEntries,
                    isExpanded = expandedStates[slot] ?: true,
                    onToggle = { expandedStates[slot] = !(expandedStates[slot] ?: true) }
                )
            }

            if (expandedStates[slot] ?: true) {
                items(slotEntries, key = { it.id }) { entry ->
                    val isSelected = selectedEntryIds.value.contains(entry.id)
                    SwipeActionContainer(
                        onDeleteRequest = { entryToDelete = entry.id },
                        onEditRequest = { entryToEdit = entry },
                        enabled = selectedEntryIds.value.isEmpty()
                    ) {
                        CompactEntryRow(
                            entry = entry,
                            isSelected = isSelected,
                            onToggleSelection = {
                                if (selectedEntryIds.value.contains(entry.id)) {
                                    selectedEntryIds.value -= entry.id
                                } else {
                                    selectedEntryIds.value += entry.id
                                }
                            },
                            isSelectionMode = selectedEntryIds.value.isNotEmpty()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepInputDialog(
    initialSteps: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var steps by remember { mutableStateOf(initialSteps.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schritte erfassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wie viele Schritte hast du heute gemacht?")
                AutoSelectTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    label = { Text("Schritte") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(steps.toIntOrNull() ?: 0) })
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(steps.toIntOrNull() ?: 0) }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun MealGroupHeader(
    title: String,
    entries: List<FoodEntryEntity>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val totalKcal = entries.sumOf { it.kcal }
    val totalProtein = entries.sumOf { it.protein }
    val totalComplexCarbs = entries.sumOf { it.complexCarbs }
    val totalSugar = entries.sumOf { it.sugar }
    val totalSaturatedFat = entries.sumOf { it.saturatedFat }
    val totalUnsaturatedFat = entries.sumOf { it.unsaturatedFat }

    val rotation by animateFloatAsState(if (isExpanded) 0f else -90f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }
        ) {
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.rotate(rotation).size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${totalKcal.round0()} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MacroNumber(totalProtein, ProteinGreen)
            Separator()
            MacroNumber(totalComplexCarbs, CarbOrange)
            MacroNumber(totalSugar, SugarRed)
            Separator()
            MacroNumber(totalUnsaturatedFat, UnsaturatedYellow)
            MacroNumber(totalSaturatedFat, SaturatedGrey)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddAmountDialog(
    food: FoodItemEntity,
    vm: NutritionViewModel,
    onDismiss: () -> Unit,
    onConfirm: (Double, FoodPortionEntity?, String) -> Unit
) {
    val parent = food.parentId?.let { pId -> vm.foods.find { it.id == pId } }
    val allPortions = food.getAllPortions(parent)
    val firstPortion = allPortions.firstOrNull()
    var amount by remember { mutableStateOf(if (firstPortion != null) "1" else "100") }
    var selectedPortion by remember { mutableStateOf<FoodPortionEntity?>(firstPortion) }
    var mealSlot by remember { mutableStateOf("Snack") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(food.name)
                if (!food.isGeneric && !food.brand.isNullOrBlank()) {
                    Text(food.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoSelectTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Menge") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(amount.num(), selectedPortion, mealSlot) })
                )
                Text("Einheit", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPortion == null,
                        onClick = { 
                            val oldGrams = if (selectedPortion != null) amount.num() * selectedPortion!!.grams else amount.num()
                            selectedPortion = null 
                            amount = oldGrams.roundString()
                        },
                        label = { Text(food.baseUnit) }
                    )
                    allPortions.forEach { portion ->
                        FilterChip(
                            selected = selectedPortion == portion,
                            onClick = { 
                                val oldGrams = if (selectedPortion != null) amount.num() * selectedPortion!!.grams else amount.num()
                                selectedPortion = portion
                                amount = (oldGrams / portion.grams).roundString()
                            },
                            label = { Text("${portion.name} (${portion.grams.roundString()}${food.baseUnit})") }
                        )
                    }
                }
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
                onConfirm(amount.num(), selectedPortion, mealSlot)
            }) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryCard(
    foods: List<FoodItemEntity>,
    meals: List<MealEntity>,
    onAddEntry: (FoodItemEntity, Double, FoodPortionEntity?, String) -> Unit,
    onAddMeal: (MealEntity, String, Double) -> Unit,
    onScanRequest: () -> Unit,
    onSearchRequest: suspend (String) -> List<FoodItemEntity>,
    onCaptureRequested: (FoodItemEntity) -> Unit,
    vm: NutritionViewModel
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Any>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    var selectedFood by remember { mutableStateOf<FoodItemEntity?>(null) }
    var selectedMeal by remember { mutableStateOf<MealEntity?>(null) }
    var showMealSlotDialog by remember { mutableStateOf(false) }

    if (selectedFood != null) {
        AddAmountDialog(
            food = selectedFood!!,
            vm = vm,
            onDismiss = { selectedFood = null },
            onConfirm = { amount, portion, mealSlot ->
                onAddEntry(selectedFood!!, amount, portion, mealSlot)
                selectedFood = null
                query = ""
                results = emptyList()
            }
        )
    }

    if (showMealSlotDialog && selectedMeal != null) {
        var slot by remember { mutableStateOf("Snack") }
        var servings by remember { mutableStateOf("1") }
        var weightByPortion by remember { mutableStateOf(selectedMeal!!.totalWeight / selectedMeal!!.servings) }
        var entryMode by remember { mutableIntStateOf(0) } // 0: Portions, 1: Grams
        var weightInput by remember { mutableStateOf(weightByPortion.round0()) }
        
        AlertDialog(
            onDismissRequest = { showMealSlotDialog = false },
            title = { Text("Mahlzeit hinzufügen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Details für '${selectedMeal!!.name}' festlegen:")
                    
                    TabRow(selectedTabIndex = entryMode) {
                        Tab(selected = entryMode == 0, onClick = { entryMode = 0 }) { Text("Portionen") }
                        Tab(selected = entryMode == 1, onClick = { entryMode = 1 }) { Text("Gramm (g)") }
                    }

                    if (entryMode == 0) {
                        AutoSelectTextField(
                            value = servings,
                            onValueChange = { 
                                servings = it 
                                weightInput = (it.num() * weightByPortion).round0()
                            },
                            label = { Text("Anzahl Portionen") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                        )
                    } else {
                        AutoSelectTextField(
                            value = weightInput,
                            onValueChange = { 
                                weightInput = it
                                servings = (it.num() / weightByPortion).roundString()
                            },
                            label = { Text("Gesamtgewicht (g)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                        )
                    }

                    Text("Mahlzeit-Slot", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Frühstück", "Mittag", "Abend", "Snack").forEach { s ->
                            FilterChip(
                                selected = slot == s,
                                onClick = { slot = s },
                                label = { Text(s) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddMeal(selectedMeal!!, slot, servings.num())
                    showMealSlotDialog = false
                    selectedMeal = null
                    query = ""
                    results = emptyList()
                }) { Text("Hinzufügen") }
            },
            dismissButton = {
                TextButton(onClick = { showMealSlotDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            expanded = false
            return@LaunchedEffect
        }
        
        isSearching = true
        // Local search
        val localFoods = foods.filter { 
            it.name.contains(query, ignoreCase = true) || 
            it.brand?.contains(query, ignoreCase = true) == true ||
            it.barcode?.contains(query) == true
        }.sortedByDescending { it.isGeneric }.take(10)
        
        val localMeals = meals.filter { it.name.contains(query, ignoreCase = true) }.take(5)
        
        results = localFoods + localMeals
        expanded = results.isNotEmpty()
        
        // Remote search
        delay(500) // Debounce
        val remote = onSearchRequest(query)
        val filteredRemote = remote.filter { r -> foods.none { it.barcode == r.barcode && it.barcode != null } }
        
        results = (localFoods + localMeals + filteredRemote).distinctBy { 
            if (it is FoodItemEntity) it.barcode ?: it.name else (it as MealEntity).name 
        }
        expanded = results.isNotEmpty()
        isSearching = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Suchen oder Barcode...") },
                        modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { 
                                    query = ""
                                    expanded = false
                                }) { Icon(Icons.Default.Clear, null) }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )
                    
                    FilledIconButton(
                        onClick = onScanRequest,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, "Barcode scannen")
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    properties = PopupProperties(focusable = false),
                    offset = DpOffset(0.dp, (-340).dp), // Force upward
                    modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 300.dp)
                ) {
                    results.forEach { item ->
                        DropdownMenuItem(
                            text = { 
                                val (data, color) = when (item) {
                                    is FoodItemEntity -> {
                                        if (item.isGeneric) {
                                            Triple(Icons.Default.Inventory2, item.name, "Basis-Zutat") to MaterialTheme.colorScheme.primary
                                        } else {
                                            Triple(Icons.Default.Restaurant, item.name, item.brand ?: "Markenprodukt") to MaterialTheme.colorScheme.secondary
                                        }
                                    }
                                    is MealEntity -> Triple(Icons.Default.SoupKitchen, item.name, "Mahlzeit") to MaterialTheme.colorScheme.tertiary
                                    else -> Triple(Icons.AutoMirrored.Filled.Help, "Unbekannt", "") to Color.Gray
                                }
                                val (icon, title, subtitle) = data
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                                    Column {
                                        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        if (subtitle.isNotEmpty()) {
                                            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        }
                                    }
                                }
                            },
                            onClick = {
                                if (item is FoodItemEntity) {
                                    selectedFood = item
                                } else if (item is MealEntity) {
                                    selectedMeal = item
                                    showMealSlotDialog = true
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }
        }
    }
}

@Composable
fun EditEntryDialog(
    entry: FoodEntryEntity,
    vm: NutritionViewModel,
    onDismiss: () -> Unit,
    onSave: (FoodEntryEntity) -> Unit
) {
    val foods = vm.foods
    val food = foods.find { it.id == entry.foodItemId }
    val parent = food?.parentId?.let { pId -> foods.find { it.id == pId } }
    val allPortions = food?.getAllPortions(parent) ?: emptyList()
    
    var amount by remember { mutableStateOf(entry.amount.roundString()) }
    var selectedPortion by remember {
        mutableStateOf(allPortions.find { it.name == entry.unitLabel })
    }
    var mealSlot by remember { mutableStateOf(entry.mealSlot) }
    val isOrphaned = food == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Eintrag bearbeiten")
                    Text(entry.name, style = MaterialTheme.typography.labelSmall, color = if (isOrphaned) Color.Red else MaterialTheme.colorScheme.outline)
                }
                if (isOrphaned) {
                    Icon(Icons.Default.Save, "Wird automatisch gespeichert", tint = ProteinGreen, modifier = Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isOrphaned) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ProteinGreen.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = ProteinGreen, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Dieser Artikel wird beim Speichern automatisch in deine Bibliothek übernommen.", style = MaterialTheme.typography.labelSmall, color = ProteinGreen)
                        }
                    }
                }

                AutoSelectTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Menge") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
                )
                
                if (allPortions.isNotEmpty() || !isOrphaned) {
                    Text("Einheit", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedPortion == null,
                            onClick = { 
                                val oldGrams = if (selectedPortion != null) amount.num() * selectedPortion!!.grams else amount.num()
                                selectedPortion = null
                                amount = oldGrams.roundString()
                            },
                            label = { Text(food?.baseUnit ?: "g") }
                        )
                        allPortions.forEach { portion ->
                            FilterChip(
                                selected = selectedPortion == portion,
                                onClick = { 
                                    val oldGrams = if (selectedPortion != null) amount.num() * selectedPortion!!.grams else amount.num()
                                    selectedPortion = portion
                                    amount = (oldGrams / portion.grams).roundString()
                                },
                                label = { Text("${portion.name} (${portion.grams.roundString()}${food?.baseUnit ?: "g"})") }
                            )
                        }
                    }
                }

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
            TextButton(onClick = {
                val numAmount = amount.num()
                val grams = if (selectedPortion != null) numAmount * selectedPortion!!.grams else if (food != null) numAmount else entry.grams
                onSave(
                    entry.copy(
                        amount = numAmount,
                        unitLabel = selectedPortion?.name ?: food?.baseUnit ?: entry.unitLabel,
                        grams = if (isOrphaned && selectedPortion == null) (numAmount * (entry.grams / entry.amount.coerceAtLeast(1.0))) else grams,
                        mealSlot = mealSlot
                    )
                )
            }) { Text("Speichern") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("Abbrechen") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditMealEntryDialog(
    entry: FoodEntryEntity,
    vm: NutritionViewModel,
    onDismiss: () -> Unit,
    onSave: (FoodEntryEntity) -> Unit
) {
    val foods = vm.foods
    var mealSlot by remember(entry.id) { mutableStateOf(entry.mealSlot) }
    val ingredients = remember(entry.id) { 
        mutableStateListOf<MealIngredientEntity>().apply { 
            entry.mealIngredients?.let { addAll(it) } 
        } 
    }
    
    // servings calculation based on weight
    val totalWeight = ingredients.sumOf { it.grams }
    // We need to know the original meal template to know the weight per portion
    // but entry already has amount (servings).
    // Let's assume entry.amount is the number of servings.
    var entryMode by remember { mutableIntStateOf(0) }
    var servings by remember(entry.id) { mutableStateOf(entry.amount.roundString()) }
    var weightInput by remember(entry.id) { mutableStateOf(totalWeight.round0()) }
    val weightPerPortion = if (entry.amount > 0) totalWeight / entry.amount else totalWeight

    var searchQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    val filteredFoods = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else foods.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(5)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Mahlzeit abwandeln", style = MaterialTheme.typography.headlineSmall)
                Text(entry.name, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                
                TabRow(selectedTabIndex = entryMode) {
                    Tab(selected = entryMode == 0, onClick = { entryMode = 0 }) { Text("Portionen") }
                    Tab(selected = entryMode == 1, onClick = { entryMode = 1 }) { Text("Gramm (g)") }
                }

                if (entryMode == 0) {
                    AutoSelectTextField(
                        value = servings,
                        onValueChange = { 
                            servings = it 
                            weightInput = (it.num() * weightPerPortion).round0()
                        },
                        label = { Text("Anzahl Portionen") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                } else {
                    AutoSelectTextField(
                        value = weightInput,
                        onValueChange = { 
                            weightInput = it
                            servings = (it.num() / weightPerPortion).roundString()
                        },
                        label = { Text("Gesamtgewicht (g)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                }

                Spacer(Modifier.height(8.dp))
                
                Text("Mahlzeit-Slot", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Frühstück", "Mittag", "Abend", "Snack").forEach { slot ->
                        FilterChip(
                            selected = mealSlot == slot,
                            onClick = { mealSlot = slot },
                            label = { Text(slot) }
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Text("Zutaten anpassen:", style = MaterialTheme.typography.labelMedium)
                
                Box {
                    AutoSelectTextField(
                        value = searchQuery,
                        onValueChange = { 
                            searchQuery = it
                            expanded = true
                        },
                        label = { Text("Zutat hinzufügen...") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { Icon(Icons.Default.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { expanded = true })
                    )
                    DropdownMenu(
                        expanded = expanded && filteredFoods.isNotEmpty(),
                        onDismissRequest = { expanded = false },
                        properties = PopupProperties(focusable = false),
                        offset = DpOffset(0.dp, (-300).dp) // Force upward
                    ) {
                        filteredFoods.forEach { food ->
                            DropdownMenuItem(
                                text = { Text(food.name) },
                                onClick = {
                                    val firstPortion = food.portions.firstOrNull()
                                    ingredients.add(
                                        MealIngredientEntity(
                                            id = System.currentTimeMillis() + ingredients.size,
                                            foodItemId = food.id,
                                            name = food.name,
                                            amount = if (firstPortion != null) 1.0 else 100.0,
                                            unitLabel = firstPortion?.name ?: food.baseUnit,
                                            grams = if (firstPortion != null) firstPortion.grams else 100.0,
                                            kcalPer100g = food.kcalPer100g,
                                            proteinPer100g = food.proteinPer100g,
                                            carbsPer100g = food.carbsPer100g,
                                            sugarPer100g = food.sugarPer100g,
                                            fatPer100g = food.fatPer100g,
                                            saturatedFatPer100g = food.saturatedFatPer100g,
                                            alcoholPercent = food.alcoholPercent,
                                            baseUnit = food.baseUnit,
                                            store = food.store,
                                            brand = food.brand
                                        )
                                    )
                                    searchQuery = ""
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                    items(ingredients, key = { it.id }) { ingredient ->
                        IngredientAdjustRow(
                            ingredient = ingredient,
                            vm = vm,
                            onUpdate = { updated ->
                                val idx = ingredients.indexOfFirst { it.id == ingredient.id }
                                if (idx != -1) ingredients[idx] = updated
                            },
                            onRemove = { ingredients.removeAll { it.id == ingredient.id } }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(onClick = {
                        val finalServings = servings.num()
                        val scale = if (entry.amount > 0) finalServings / entry.amount else 1.0
                        onSave(entry.copy(
                            amount = finalServings, 
                            unitLabel = if (finalServings == 1.0) "Portion" else "Portionen",
                            mealSlot = mealSlot, 
                            mealIngredients = ingredients.map { it.copy(grams = it.grams * scale, amount = it.amount * scale) }
                        ))
                    }) { Text("Speichern") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IngredientAdjustRow(
    ingredient: MealIngredientEntity,
    vm: NutritionViewModel,
    onUpdate: (MealIngredientEntity) -> Unit,
    onRemove: () -> Unit
) {
    val foods = vm.foods
    val food = foods.find { it.id == ingredient.foodItemId }
    val parent = food?.parentId?.let { pId -> foods.find { it.id == pId } }
    val allPortions = food?.getAllPortions(parent) ?: emptyList()
    
    var amountText by remember(ingredient.amount, ingredient.unitLabel) { 
        mutableStateOf(ingredient.amount.roundString()) 
    }
    val selectedPortion = allPortions.find { it.name == ingredient.unitLabel }

    val relatives = remember(food, foods) {
        val root = if (food?.isGeneric == true) food else parent
        val effectiveRoot = root ?: foods.find { it.isGeneric && it.name == ingredient.name }
        
        if (effectiveRoot != null) {
            (listOf(effectiveRoot) + foods.filter { it.parentId == effectiveRoot.id }).filter { it.id != food?.id }
        } else {
            foods.filter { it.isGeneric }
        }
    }
    var showSwapMenu by remember { mutableStateOf(false) }
    val isOrphaned = food == null

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = if (isOrphaned) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)) 
                 else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ingredient.name, 
                            fontWeight = FontWeight.Bold, 
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOrphaned) Color.Red else Color.Unspecified
                        )
                        if (isOrphaned) {
                            Icon(
                                imageVector = Icons.Default.Save, 
                                contentDescription = "Wird automatisch gespeichert", 
                                tint = ProteinGreen, 
                                modifier = Modifier.size(14.dp).padding(start = 4.dp)
                            )
                        }
                        
                        if (relatives.isNotEmpty()) {
                            Box {
                                IconButton(
                                    onClick = { showSwapMenu = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOrphaned) Icons.Default.FindReplace else Icons.Default.SwapHoriz, 
                                        contentDescription = "Zutat tauschen", 
                                        modifier = Modifier.size(16.dp), 
                                        tint = if (isOrphaned) Color.Red else MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(expanded = showSwapMenu, onDismissRequest = { showSwapMenu = false }) {
                                    if (isOrphaned) {
                                        DropdownMenuItem(
                                            text = { Text("Original gelöscht! Bitte Ersatz wählen:", style = MaterialTheme.typography.labelSmall, color = Color.Red) },
                                            onClick = {},
                                            enabled = false
                                        )
                                    }
                                    relatives.take(15).forEach { alt ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (alt.isGeneric) {
                                                        Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(alt.name + " (Basis)")
                                                    } else {
                                                        Icon(Icons.AutoMirrored.Filled.Label, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("${alt.brand ?: "Unbekannt"} @ ${alt.store ?: "Unbekannt"}")
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onUpdate(ingredient.copy(
                                                    foodItemId = alt.id,
                                                    name = alt.name,
                                                    kcalPer100g = alt.kcalPer100g,
                                                    proteinPer100g = alt.proteinPer100g,
                                                    carbsPer100g = alt.carbsPer100g,
                                                    sugarPer100g = alt.sugarPer100g,
                                                    fatPer100g = alt.fatPer100g,
                                                    saturatedFatPer100g = alt.saturatedFatPer100g,
                                                    alcoholPercent = alt.alcoholPercent,
                                                    baseUnit = alt.baseUnit,
                                                    store = alt.store,
                                                    brand = alt.brand
                                                ))
                                                showSwapMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (isOrphaned) {
                        Text("Wird automatisch gespeichert", style = MaterialTheme.typography.labelSmall, color = ProteinGreen)
                    } else {
                        food?.let { f ->
                            if (!f.brand.isNullOrBlank() || !f.store.isNullOrBlank()) {
                                Text(
                                    text = "${f.brand ?: ""} @ ${f.store ?: ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoSelectTextField(
                    value = amountText,
                    onValueChange = { 
                        amountText = it
                        val amt = it.num()
                        val grams = if (selectedPortion != null) amt * selectedPortion.grams else amt
                        onUpdate(ingredient.copy(amount = amt, grams = grams))
                    },
                    label = { Text("Menge") },
                    modifier = Modifier.weight(0.3f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
                )
                
                Column(Modifier.weight(0.7f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedPortion == null,
                            onClick = { 
                                val oldGrams = if (selectedPortion != null) amountText.num() * selectedPortion.grams else amountText.num()
                                amountText = oldGrams.roundString()
                                onUpdate(ingredient.copy(amount = oldGrams, unitLabel = food?.baseUnit ?: "g", grams = oldGrams))
                            },
                            label = { Text(food?.baseUnit ?: "g") }
                        )
                        allPortions.forEach { portion ->
                            FilterChip(
                                selected = selectedPortion == portion,
                                onClick = {
                                    val oldGrams = if (selectedPortion != null) amountText.num() * selectedPortion.grams else amountText.num()
                                    val newAmt = oldGrams / portion.grams
                                    amountText = newAmt.roundString()
                                    onUpdate(ingredient.copy(amount = newAmt, unitLabel = portion.name, grams = oldGrams))
                                },
                                label = { Text(portion.name) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactEntryRow(
    entry: FoodEntryEntity,
    isSelected: Boolean = false,
    onToggleSelection: () -> Unit = {},
    isSelectionMode: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { 
                    if (isSelectionMode) {
                        onToggleSelection()
                    } else if (entry.isMeal) {
                        isExpanded = !isExpanded 
                    }
                },
                onLongClick = onToggleSelection
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSelectionMode) {
                    Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.isMeal) {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.SoupKitchen, 
                                null, 
                                modifier = Modifier.size(14.dp), 
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(entry.name, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    if (!entry.brand.isNullOrBlank() || !entry.store.isNullOrBlank()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!entry.brand.isNullOrBlank()) {
                                Text(entry.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                            }
                            if (!entry.store.isNullOrBlank()) {
                                Text("@ ${entry.store}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), maxLines = 1)
                            }
                        }
                    }
                    Text(
                        text = if (entry.isMeal) "${entry.amount.roundString()} ${entry.unitLabel} (${entry.grams.round0()}g)" else entry.displayAmount(),
                        style = MaterialTheme.typography.labelSmall, 
                        maxLines = 1
                    )
                }
                Text(entry.kcal.round0(), modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold)
                MacroNumber(entry.protein, ProteinGreen)
                Separator()
                MacroNumber(entry.complexCarbs, CarbOrange)
                MacroNumber(entry.sugar, SugarRed)
                Separator()
                MacroNumber(entry.unsaturatedFat, UnsaturatedYellow)
                MacroNumber(entry.saturatedFat, SaturatedGrey)
            }
            
            if (isExpanded && entry.isMeal && entry.mealIngredients != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 10.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    entry.mealIngredients.forEach { ing ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${ing.amount.roundString()} ${ing.unitLabel} ${ing.name}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${ing.kcal.round0()} kcal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Text("P:${ing.protein.round0()}", style = MaterialTheme.typography.labelSmall, color = ProteinGreen)
                                Text("C:${ing.carbs.round0()}", style = MaterialTheme.typography.labelSmall, color = CarbOrange)
                                Text("F:${ing.fat.round0()}", style = MaterialTheme.typography.labelSmall, color = UnsaturatedYellow)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroLegendRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LegendDot(ProteinGreen, "Protein")
        LegendDot(CarbOrange, "KH")
        LegendDot(SugarRed, "Zucker")
        LegendDot(UnsaturatedYellow, "unges. Fett")
        LegendDot(SaturatedGrey, "ges. Fett")
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MacroNumber(value: Double, color: Color) {
    Text(value.round0(), color = color, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 28.dp))
}

@Composable
private fun Separator() {
    Text("|", color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Composable
fun FoodsScreen(
    vm: NutritionViewModel,
    snackbarHostState: SnackbarHostState,
    selectedFoodIds: MutableState<Set<Long>>
) {
    val foods = vm.foods
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var foodToDelete by remember { mutableStateOf<Long?>(null) }
    var foodToEdit by remember { mutableStateOf<FoodItemEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showPantry by remember { mutableStateOf(false) }

    if (showPantry) {
        PantryScreen(vm, onDismiss = { showPantry = false })
    }

    val searchQuery = vm.foodSearchQuery
    val selectedCategory = vm.selectedFoodCategory
    val categories = vm.categories.sorted()

    val filteredFoods by remember(searchQuery, selectedCategory) {
        derivedStateOf {
            foods.filter { food ->
                val matchesSearch = food.name.contains(searchQuery, ignoreCase = true) || (food.brand?.contains(searchQuery, ignoreCase = true) == true)
                val matchesCategory = selectedCategory == null || food.category == selectedCategory
                matchesSearch && matchesCategory
            }
        }
    }

    val expandedStates = remember { mutableStateMapOf<Long, Boolean>() }
    val isSelectionMode = selectedFoodIds.value.isNotEmpty()

    if (foodToDelete != null) {
        val affectedMeals = vm.meals.filter { it.ingredients.any { ing -> ing.foodItemId == foodToDelete } }
        AlertDialog(
            onDismissRequest = { foodToDelete = null },
            title = { Text("Lebensmittel löschen") },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Möchtest du dieses Lebensmittel wirklich löschen? Es wird aus deinem Bestand entfernt.")
                    if (affectedMeals.isNotEmpty()) {
                        Text(
                            "Achtung: Dieser Artikel wird in folgenden Mahlzeiten verwendet und dort als 'gelöscht' markiert:",
                            color = Color.Red,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        affectedMeals.forEach { meal ->
                            Text("• ${meal.name}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = foodToDelete
                    if (id != null) {
                        vm.deleteFood(id)
                        foodToDelete = null
                    }
                }) { Text("Löschen", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { foodToDelete = null }) { Text("Abbrechen") }
            }
        )
    }

    if (foodToEdit != null && !showAddDialog) {
        FoodEditDialog(
            food = foodToEdit!!,
            vm = vm,
            onDismiss = { foodToEdit = null },
            onSave = { updated ->
                vm.updateFood(updated)
                foodToEdit = null
            }
        )
    }

    if (showAddDialog) {
        FoodEditDialog(
            food = foodToEdit, 
            vm = vm,
            onDismiss = { 
                showAddDialog = false
                foodToEdit = null
            },
            onSave = { newFood ->
                if (newFood.id == 0L) {
                    vm.addFood(
                        newFood.name, newFood.kcalPer100g, newFood.proteinPer100g,
                        newFood.carbsPer100g, newFood.sugarPer100g, newFood.fatPer100g,
                        newFood.saturatedFatPer100g, newFood.alcoholPercent, newFood.baseUnit,
                        newFood.portions, newFood.packages, newFood.barcode, newFood.brand, newFood.category,
                        isGeneric = newFood.isGeneric,
                        parentId = newFood.parentId,
                        store = newFood.store
                    )
                } else {
                    vm.updateFood(newFood)
                }
                showAddDialog = false
                foodToEdit = null
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { 
                        foodToEdit = FoodItemEntity(isGeneric = true)
                        showAddDialog = true 
                    }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Inventory2, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Basis")
                }

                Button(
                    onClick = { 
                        foodToEdit = FoodItemEntity(isGeneric = false)
                        showAddDialog = true 
                    }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Label, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Marke")
                }

                Button(
                    onClick = { showPantry = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                ) {
                    Icon(Icons.Default.Kitchen, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Vorrat")
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { vm.foodSearchQuery = it },
                label = { Text("Suchen...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { vm.foodSearchQuery = "" }) { Icon(Icons.Default.Clear, null) } }
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { vm.selectedFoodCategory = null },
                        label = { Text("Alle") }
                    )
                }
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { vm.selectedFoodCategory = if (selectedCategory == cat) null else cat },
                        label = { Text(cat) }
                    )
                }
            }
        }

        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            val genericItems = filteredFoods.filter { it.isGeneric }
            val specificItemsNoParent = filteredFoods.filter { !it.isGeneric && it.parentId == null }
            val specificItemsWithParent = foods.filter { !it.isGeneric && it.parentId != null }
            
            genericItems.forEach { genericFood ->
                item(key = "gen_${genericFood.id}") {
                    FoodItemRow(
                        food = genericFood,
                        vm = vm,
                        isExpanded = expandedStates[genericFood.id] ?: false,
                        onExpandToggle = { 
                            if (isSelectionMode) {
                                val current = selectedFoodIds.value
                                selectedFoodIds.value = if (current.contains(genericFood.id)) current - genericFood.id else current + genericFood.id
                            } else {
                                expandedStates[genericFood.id] = !(expandedStates[genericFood.id] ?: false) 
                            }
                        },
                        onDelete = { foodToDelete = genericFood.id },
                        onEdit = { foodToEdit = genericFood; showAddDialog = true },
                        onToggleSelection = {
                            val current = selectedFoodIds.value
                            selectedFoodIds.value = if (current.contains(genericFood.id)) current - genericFood.id else current + genericFood.id
                        },
                        isSelected = selectedFoodIds.value.contains(genericFood.id),
                        isSelectionMode = isSelectionMode
                    )
                }
                
                val children = specificItemsWithParent.filter { it.parentId == genericFood.id }
                if (expandedStates[genericFood.id] == true || searchQuery.isNotBlank()) {
                    items(children, key = { "child_${it.id}" }) { child ->
                        Box(Modifier.padding(start = if (isSelectionMode) 0.dp else 24.dp)) {
                            FoodItemRow(
                                food = child,
                                vm = vm,
                                isExpanded = expandedStates[child.id] ?: false,
                                onExpandToggle = { 
                                    if (isSelectionMode) {
                                        val current = selectedFoodIds.value
                                        selectedFoodIds.value = if (current.contains(child.id)) current - child.id else current + child.id
                                    } else {
                                        expandedStates[child.id] = !(expandedStates[child.id] ?: false) 
                                    }
                                },
                                onDelete = { foodToDelete = child.id },
                                onEdit = { foodToEdit = child; showAddDialog = true },
                                isChild = true,
                                onToggleSelection = {
                                    val current = selectedFoodIds.value
                                    selectedFoodIds.value = if (current.contains(child.id)) current - child.id else current + child.id
                                },
                                isSelected = selectedFoodIds.value.contains(child.id),
                                isSelectionMode = isSelectionMode
                            )
                        }
                    }
                }
            }
            
            if (specificItemsNoParent.isNotEmpty()) {
                item { 
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Andere Artikel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                }
                items(specificItemsNoParent, key = { it.id }) { food ->
                    FoodItemRow(
                        food = food,
                        vm = vm,
                        isExpanded = expandedStates[food.id] ?: false,
                        onExpandToggle = { 
                            if (isSelectionMode) {
                                val current = selectedFoodIds.value
                                selectedFoodIds.value = if (current.contains(food.id)) current - food.id else current + food.id
                            } else {
                                expandedStates[food.id] = !(expandedStates[food.id] ?: false) 
                            }
                        },
                        onDelete = { foodToDelete = food.id },
                        onEdit = { foodToEdit = food; showAddDialog = true },
                        onToggleSelection = {
                            val current = selectedFoodIds.value
                            selectedFoodIds.value = if (current.contains(food.id)) current - food.id else current + food.id
                        },
                        isSelected = selectedFoodIds.value.contains(food.id),
                        isSelectionMode = isSelectionMode
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FoodItemRow(
    food: FoodItemEntity,
    vm: NutritionViewModel,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggleSelection: () -> Unit,
    isChild: Boolean = false,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false
) {
    SwipeActionContainer(
        onDeleteRequest = onDelete,
        onEditRequest = onEdit,
        enabled = !isSelectionMode
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onExpandToggle,
                    onLongClick = onToggleSelection
                ),
            colors = if (isSelected) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else if (food.isGeneric) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            } else if (isChild) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f))
            } else {
                CardDefaults.cardColors()
            }
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
                        Spacer(Modifier.width(8.dp))
                    }

                    if (food.isGeneric) {
                        Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
                    } else if (isChild) {
                        Icon(Icons.Default.SubdirectoryArrowRight, null, modifier = Modifier.size(16.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                    
                    Text(
                        text = food.name, 
                        fontWeight = if (food.isGeneric) FontWeight.ExtraBold else FontWeight.Bold, 
                        modifier = Modifier.weight(1f),
                        style = if (food.isGeneric) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
                    )
                    
                    if (!isExpanded && !food.category.isNullOrBlank()) {
                        SuggestionChip(onClick = {}, label = { Text(food.category, style = MaterialTheme.typography.labelSmall) })
                    }
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
                
                AnimatedVisibility(visible = isExpanded) {
                    Column(Modifier.padding(top = 8.dp)) {
                        if (!food.brand.isNullOrBlank() || !food.store.isNullOrBlank()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!food.brand.isNullOrBlank()) {
                                    Text(food.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                if (!food.store.isNullOrBlank()) {
                                    Text("@ ${food.store}", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                                }
                            }
                        }
                        Text("${food.kcalPer100g.round0()} kcal / 100 ${food.baseUnit}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MacroNumber(food.proteinPer100g, ProteinGreen)
                            Separator()
                            MacroNumber(food.complexCarbsPer100g, CarbOrange)
                            MacroNumber(food.sugarPer100g, SugarRed)
                            Separator()
                            MacroNumber(food.unsaturatedFatPer100g, UnsaturatedYellow)
                            MacroNumber(food.saturatedFatPer100g, SaturatedGrey)
                        }
                        if (food.alcoholPercent > 0) {
                            Text("Alkohol: ${food.alcoholPercent.round1()}%", style = MaterialTheme.typography.labelSmall, color = Color.Magenta)
                        }
                        if (food.portions.isNotEmpty()) {
                            Text("Portionen: " + food.portions.joinToString(" · ") { "${it.name} (${it.grams.round0()}${food.baseUnit})" }, style = MaterialTheme.typography.labelSmall)
                        }
                        if (food.packages.isNotEmpty()) {
                            Text("Packungen: " + food.packages.joinToString(" · ") { "${it.name} ${it.quantity.round0()} ${it.unit}" }, style = MaterialTheme.typography.labelSmall)
                        }
                        if (!food.barcode.isNullOrBlank()) {
                            Text("EAN: ${food.barcode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditDialog(
    food: FoodItemEntity?,
    vm: NutritionViewModel,
    onDismiss: () -> Unit,
    onSave: (FoodItemEntity) -> Unit
) {
    var name by remember { mutableStateOf(food?.name ?: "") }
    var brand by remember { mutableStateOf(food?.brand ?: "") }
    var category by remember { mutableStateOf(food?.category ?: "") }
    var protein by remember { mutableStateOf(food?.proteinPer100g?.toString()?.replace(".0", "") ?: "") }
    var carbs by remember { mutableStateOf(food?.carbsPer100g?.toString()?.replace(".0", "") ?: "") }
    var sugar by remember { mutableStateOf(food?.sugarPer100g?.toString()?.replace(".0", "") ?: "") }
    var fat by remember { mutableStateOf(food?.fatPer100g?.toString()?.replace(".0", "") ?: "") }
    var saturatedFat by remember { mutableStateOf(food?.saturatedFatPer100g?.toString()?.replace(".0", "") ?: "") }
    var alcohol by remember { mutableStateOf(food?.alcoholPercent?.toString()?.replace(".0", "") ?: "0") }
    var baseUnit by remember { mutableStateOf(food?.baseUnit ?: "g") }
    var barcode by remember { mutableStateOf(food?.barcode ?: "") }
    var isGeneric by remember { mutableStateOf(food?.isGeneric ?: false) }
    var parentId by remember { mutableStateOf(food?.parentId) }
    var store by remember { mutableStateOf(food?.store ?: "") }
    var isPantryItem by remember { mutableStateOf(food?.isPantryItem ?: false) }
    
    var unitExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var parentExpanded by remember { mutableStateOf(false) }

    val genericFoods by remember(food?.id) { 
        derivedStateOf { vm.foods.filter { it.isGeneric && it.id != food?.id } } 
    }
    val parentFood = genericFoods.find { it.id == parentId }

    val kcal = calculateKcalPer100g(protein.num(), carbs.num(), fat.num(), alcohol.num())

    val portions = remember {
        mutableStateListOf<PortionInputState>().apply {
            food?.portions?.forEach { add(PortionInputState(it.name, it.grams.toString().replace(".0", ""))) }
        }
    }

    val packages = remember {
        mutableStateListOf<PackageInputState>().apply {
            food?.packages?.forEach { add(PackageInputState(it.name, it.quantity.toString().replace(".0", ""), it.unit)) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
        ) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = when {
                        parentId != null && parentFood != null -> "Neue Variante von ${parentFood.name}"
                        food?.isGeneric == true -> if (food.id == 0L) "Neue Basis-Zutat" else "Basis-Zutat bearbeiten"
                        else -> if (food == null || food.id == 0L) "Neues Markenprodukt" else "Markenprodukt bearbeiten"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                AutoSelectTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())

                if (!isGeneric) {
                    ExposedDropdownMenuBox(
                        expanded = parentExpanded,
                        onExpandedChange = { parentExpanded = !parentExpanded }
                    ) {
                        OutlinedTextField(
                            value = parentFood?.name ?: "Keine Basis-Zutat gewählt",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Gehört zu Basis-Zutat (z.B. Apfel)") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = parentExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = if (parentId == null) OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Red, focusedBorderColor = Color.Red) else OutlinedTextFieldDefaults.colors()
                        )
                        ExposedDropdownMenu(
                            expanded = parentExpanded,
                            onDismissRequest = { parentExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Keine Verknüpfung") },
                                onClick = {
                                    parentId = null
                                    parentExpanded = false
                                }
                            )
                            genericFoods.forEach { gen ->
                                DropdownMenuItem(
                                    text = { Text(gen.name) },
                                    onClick = {
                                        parentId = gen.id
                                        parentExpanded = false
                                        if (name.isBlank()) name = gen.name
                                        if (category.isBlank()) category = gen.category ?: ""
                                    }
                                )
                            }
                        }
                    }

                    AutoSelectTextField(brand, { brand = it }, label = { Text("Marke (z.B. ja!, Bio-Zentrale)") }, modifier = Modifier.fillMaxWidth())
                    AutoSelectTextField(store, { store = it }, label = { Text("Laden / Supermarkt") }, modifier = Modifier.fillMaxWidth())
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Vorratsartikel (wird in Liste ausgeblendet)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = isPantryItem, onCheckedChange = { isPantryItem = it })
                }
                
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        readOnly = true,
                        label = { Text("Kategorie") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        vm.categories.sorted().forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    category = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                        if (vm.categories.isEmpty()) {
                            DropdownMenuItem(text = { Text("Keine Kategorien definiert") }, onClick = { }, enabled = false)
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = !unitExpanded }
                ) {
                    OutlinedTextField(
                        value = if (baseUnit == "g") "Gramm (g)" else "Milliliter (ml)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Basiseinheit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = unitExpanded,
                        onDismissRequest = { unitExpanded = false }
                    ) {
                        DropdownMenuItem(text = { Text("Gramm (g)") }, onClick = { baseUnit = "g"; unitExpanded = false })
                        DropdownMenuItem(text = { Text("Milliliter (ml)") }, onClick = { baseUnit = "ml"; unitExpanded = false })
                    }
                }

                OutlinedTextField(
                    value = kcal.round0(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Kalorien pro 100 $baseUnit (berechnet)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.LightGray.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.LightGray.copy(alpha = 0.1f)
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(
                        fat, { fat = it }, 
                        label = { Text("Fett ges.") }, 
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    AutoSelectTextField(
                        saturatedFat, { saturatedFat = it }, 
                        label = { Text("davon ges.") }, 
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(
                        carbs, { carbs = it }, 
                        label = { Text("KH ges.") }, 
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    AutoSelectTextField(
                        sugar, { sugar = it }, 
                        label = { Text("davon Zucker") }, 
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(
                        protein, { protein = it }, 
                        label = { Text("Protein") }, 
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    AutoSelectTextField(
                        alcohol, { alcohol = it }, 
                        label = { Text("Alc.-%") }, 
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                }

                AutoSelectTextField(
                    barcode, { barcode = it }, 
                    label = { Text("Barcode") }, 
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                )

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                if (parentId != null && parentFood != null) {
                    Text("Standard-Portionen (geerbt von ${parentFood.name})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    parentFood.portions.forEach { p ->
                        Text("• ${p.name}: ${p.grams.round0()} $baseUnit", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Text(if (isGeneric) "Allgemeine Portionen (z.B. Stück, Teller)" else "Zusätzliche Portionen", fontWeight = FontWeight.Bold)
                portions.forEachIndexed { index, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AutoSelectTextField(p.name, { p.name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                        AutoSelectTextField(p.grams, { p.grams = it }, label = { Text(baseUnit) }, modifier = Modifier.weight(0.6f))
                        IconButton(onClick = { portions.removeAt(index) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    }
                }
                TextButton(onClick = { portions.add(PortionInputState("", "")) }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Portion hinzufügen")
                }

                if (!isGeneric) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Produktspezifische Packungen (z.B. 250g Beutel)", fontWeight = FontWeight.Bold)
                    packages.forEachIndexed { index, pkg ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AutoSelectTextField(pkg.name, { pkg.name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                            AutoSelectTextField(pkg.quantity, { pkg.quantity = it }, label = { Text("Menge") }, modifier = Modifier.weight(0.7f))
                            AutoSelectTextField(pkg.unit, { pkg.unit = it }, label = { Text("Einh.") }, modifier = Modifier.weight(0.7f))
                            IconButton(onClick = { packages.removeAt(index) }) { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                        }
                    }
                    TextButton(onClick = { packages.add(PackageInputState("", "", baseUnit)) }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Packungsgröße hinzufügen")
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(enabled = name.isNotBlank(), onClick = {
                        onSave(
                            FoodItemEntity(
                                id = food?.id ?: 0,
                                name = name,
                                brand = brand.takeIf { it.isNotBlank() },
                                category = category.takeIf { it.isNotBlank() },
                                kcalPer100g = kcal,
                                proteinPer100g = protein.num(),
                                carbsPer100g = carbs.num(),
                                sugarPer100g = sugar.num(),
                                fatPer100g = fat.num(),
                                saturatedFatPer100g = saturatedFat.num(),
                                alcoholPercent = alcohol.num(),
                                baseUnit = baseUnit,
                                barcode = barcode.takeIf { it.isNotBlank() },
                                portions = portions.map { FoodPortionEntity(0, it.name, it.grams.num()) },
                                packages = packages.map { FoodPackageEntity(0, it.name, it.quantity.num(), it.unit) },
                                isGeneric = isGeneric,
                                parentId = if (!isGeneric) parentId else null,
                                store = if (!isGeneric) store.takeIf { it.isNotBlank() } else null,
                                isPantryItem = isPantryItem
                            )
                        )
                    }) { Text("Speichern") }
                }
            }
        }
    }
}

@Composable
fun PantryScreen(vm: NutritionViewModel, onDismiss: () -> Unit) {
    val pantryItems = remember(vm.foods) { derivedStateOf { vm.foods.filter { it.isPantryItem } } }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Kitchen, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Vorratsschrank", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }
                
                Text("Artikel, die immer da sind und im Planer nicht automatisch auf die Einkaufsliste kommen (außer aktiviert).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                
                Spacer(Modifier.height(16.dp))
                
                if (pantryItems.value.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Keine Vorratsartikel markiert.", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pantryItems.value) { food ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(food.name, fontWeight = FontWeight.Bold)
                                        if (!food.brand.isNullOrBlank()) Text(food.brand, style = MaterialTheme.typography.labelSmall)
                                    }
                                    IconButton(onClick = { 
                                        vm.updateFood(food.copy(isPantryItem = false))
                                    }) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red)
                                    }
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
fun MergeFoodsDialog(
    selectedIds: Set<Long>,
    foods: List<FoodItemEntity>,
    onDismiss: () -> Unit,
    onMerge: (Long) -> Unit
) {
    val genericFoods = foods.filter { it.isGeneric }
    var selectedParentId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${selectedIds.size} Artikel zusammenführen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Wähle einen allgemeinen Basis-Artikel aus, unter dem die markierten Produkte gruppiert werden sollen:")
                
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(genericFoods) { gen ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedParentId = gen.id },
                            color = if (selectedParentId == gen.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(gen.name)
                            }
                        }
                    }
                }
                
                if (genericFoods.isEmpty()) {
                    Text("Keine allgemeinen Artikel vorhanden. Erstelle erst eine Basis-Zutat (z.B. 'Pasta').", color = Color.Red)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = selectedParentId != null,
                onClick = { selectedParentId?.let { onMerge(it) } }
            ) { Text("Zusammenführen") }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) { Text("Abbrechen") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DiarySelectionActions(
    vm: NutritionViewModel,
    selectedEntryIds: MutableState<Set<Long>>,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    dateFormatter: java.time.format.DateTimeFormatter
) {
    var showDateDialog by remember { mutableStateOf(false) }
    var operationType by remember { mutableStateOf("copy") }
    var showCreateMealDialog by remember { mutableStateOf(false) }
    var mealFromSelection by remember { mutableStateOf<MealEntity?>(null) }

    if (showDateDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = vm.selectedDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                        if (operationType == "copy") {
                            vm.copyEntriesToDate(selectedEntryIds.value, date)
                            scope.launch { snackbarHostState.showSnackbar("Kopiert nach ${date.format(dateFormatter)}") }
                        } else {
                            vm.moveEntriesToDate(selectedEntryIds.value, date)
                            scope.launch { snackbarHostState.showSnackbar("Verschoben nach ${date.format(dateFormatter)}") }
                        }
                    }
                    selectedEntryIds.value = emptySet()
                    showDateDialog = false
                }) { Text("Auswählen") }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) { Text("Abbrechen") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showCreateMealDialog && mealFromSelection != null) {
        MealEditDialog(
            meal = mealFromSelection,
            vm = vm,
            onDismiss = { 
                showCreateMealDialog = false
                mealFromSelection = null
            },
            onSave = { name, ingredients, servings ->
                vm.addMealTemplate(name, ingredients, servings)
                selectedEntryIds.value = emptySet()
                showCreateMealDialog = false
                mealFromSelection = null
                scope.launch { snackbarHostState.showSnackbar("Mahlzeit '$name' erstellt") }
            }
        )
    }

    IconButton(onClick = { 
        val selectedEntries = vm.allEntries.filter { it.id in selectedEntryIds.value }
        val flattenedIngredients = selectedEntries.flatMap { entry ->
            if (entry.isMeal) {
                entry.mealIngredients ?: emptyList()
            } else {
                listOf(MealIngredientEntity(
                    foodItemId = entry.foodItemId,
                    name = entry.name,
                    amount = entry.amount,
                    unitLabel = entry.unitLabel,
                    grams = entry.grams,
                    kcalPer100g = entry.kcalPer100g,
                    proteinPer100g = entry.proteinPer100g,
                    carbsPer100g = entry.carbsPer100g,
                    sugarPer100g = entry.sugarPer100g,
                    fatPer100g = entry.fatPer100g,
                    saturatedFatPer100g = entry.saturatedFatPer100g,
                    alcoholPercent = entry.alcoholPercent,
                    baseUnit = entry.baseUnit,
                    store = entry.store,
                    brand = entry.brand
                ))
            }
        }.mapIndexed { idx, ing -> ing.copy(id = System.currentTimeMillis() + idx) }

        mealFromSelection = MealEntity(
            name = "",
            ingredients = flattenedIngredients,
            servings = 1.0
        )
        showCreateMealDialog = true
    }) {
        Icon(Icons.Default.SoupKitchen, "Mahlzeit aus Auswahl erstellen")
    }

    IconButton(onClick = { 
        operationType = "copy"
        showDateDialog = true 
    }) {
        Icon(Icons.Default.ContentCopy, "Kopieren")
    }
    IconButton(onClick = { 
        operationType = "move"
        showDateDialog = true 
    }) {
        Icon(Icons.AutoMirrored.Filled.DriveFileMove, "Verschieben")
    }
    IconButton(onClick = {
        selectedEntryIds.value.forEach { vm.deleteEntry(it) }
        selectedEntryIds.value = emptySet()
        scope.launch { snackbarHostState.showSnackbar("Einträge gelöscht") }
    }) {
        Icon(Icons.Default.Delete, "Löschen")
    }
}

@Composable
private fun FoodSelectionActions(
    vm: NutritionViewModel,
    selectedFoodIds: MutableState<Set<Long>>,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    var showMergeDialog by remember { mutableStateOf(false) }

    if (showMergeDialog) {
        MergeFoodsDialog(
            selectedIds = selectedFoodIds.value,
            foods = vm.foods,
            onDismiss = { showMergeDialog = false },
            onMerge = { targetParentId ->
                vm.mergeFoods(targetParentId, selectedFoodIds.value.toList())
                selectedFoodIds.value = emptySet()
                showMergeDialog = false
            }
        )
    }

    if (selectedFoodIds.value.size == 1) {
        val foodId = selectedFoodIds.value.first()
        val food = vm.foods.find { it.id == foodId }
        if (food != null && !food.isGeneric) {
            IconButton(onClick = {
                vm.promoteToGeneric(foodId)
                selectedFoodIds.value = emptySet()
                scope.launch { snackbarHostState.showSnackbar("${food.name} hochgestuft") }
            }) {
                Icon(Icons.Default.Upgrade, "Hochstufen")
            }
        }
    }

    IconButton(onClick = { showMergeDialog = true }) {
        Icon(Icons.Default.Merge, "Zusammenführen")
    }

    IconButton(onClick = {
        selectedFoodIds.value.forEach { vm.deleteFood(it) }
        selectedFoodIds.value = emptySet()
        scope.launch { snackbarHostState.showSnackbar("Artikel gelöscht") }
    }) {
        Icon(Icons.Default.Delete, "Löschen")
    }
}

@Composable
private fun MealSelectionActions(
    vm: NutritionViewModel,
    selectedMealIds: MutableState<Set<Long>>,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current

    if (selectedMealIds.value.size == 1) {
        val mealId = selectedMealIds.value.first()
        val meal = vm.meals.find { it.id == mealId }
        if (meal != null) {
            IconButton(onClick = {
                try {
                    val json = vm.getRecipeJson(meal)
                    val file = java.io.File(context.cacheDir, "recipe_${meal.name.replace(" ", "_")}.json")
                    file.writeText(json)
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_SUBJECT, "Rezept: ${meal.name}")
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(intent, "Rezept teilen"))
                    selectedMealIds.value = emptySet()
                } catch (e: Exception) {
                    scope.launch { snackbarHostState.showSnackbar("Teilen fehlgeschlagen: ${e.localizedMessage}") }
                }
            }) {
                Icon(Icons.Default.Share, "Teilen")
            }
        }
    }

    IconButton(onClick = {
        selectedMealIds.value.forEach { vm.deleteMealTemplate(it) }
        selectedMealIds.value = emptySet()
        scope.launch { snackbarHostState.showSnackbar("Mahlzeiten gelöscht") }
    }) {
        Icon(Icons.Default.Delete, "Löschen")
    }
}


class PortionInputState(nameInitial: String, gramsInitial: String) {
    var name by mutableStateOf(nameInitial)
    var grams by mutableStateOf(gramsInitial)
}

class PackageInputState(nameInitial: String, quantityInitial: String, unitInitial: String) {
    var name by mutableStateOf(nameInitial)
    var quantity by mutableStateOf(quantityInitial)
    var unit by mutableStateOf(unitInitial)
}

private fun String.num(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.round0(): String = "%.0f".format(this)
private fun Double.round1(): String = "%.1f".format(this)
private fun Double.roundString(): String = if (this % 1.0 == 0.0) "%.0f".format(this) else "%.1f".format(this)
