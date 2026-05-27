package com.nick.nutritiontracker.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
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
import java.time.LocalDate
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
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val foods = vm.foods
    val entries = vm.todayEntries
    val userProfile by profileVm.userProfile.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN) }
    var dateMenuExpanded by remember { mutableStateOf(false) }

    // Health Connect Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(vm.healthConnectManager.permissions)) {
            vm.syncStepsForSelectedDate()
            scope.launch { snackbarHostState.showSnackbar("Berechtigung erteilt!") }
        }
    }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        if (tab == 0) {
                            Box {
                                TextButton(
                                    onClick = { dateMenuExpanded = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                ) {
                                    val label = when (vm.selectedDate) {
                                        LocalDate.now() -> "Heute"
                                        LocalDate.now().plusDays(1) -> "Morgen"
                                        else -> vm.selectedDate.format(dateFormatter)
                                    }
                                    Text(label, style = MaterialTheme.typography.titleLarge)
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(
                                    expanded = dateMenuExpanded,
                                    onDismissRequest = { dateMenuExpanded = false }
                                ) {
                                    vm.availableDates.forEach { date ->
                                        val label = when (date) {
                                            LocalDate.now() -> "Heute"
                                            LocalDate.now().plusDays(1) -> "Morgen"
                                            else -> date.format(dateFormatter)
                                        }
                                        DropdownMenuItem(
                                            text = { 
                                                Text(label) 
                                            },
                                            onClick = {
                                                vm.selectDate(date)
                                                dateMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                when (tab) {
                                    1 -> "Lebensmittel"
                                    2 -> "Mahlzeiten"
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
                                                snackbarHostState.showSnackbar(
                                                    "Berechtigung erforderlich",
                                                    actionLabel = "Einstellungen"
                                                ).also { result ->
                                                    if (result == SnackbarResult.ActionPerformed) {
                                                        context.startActivity(vm.healthConnectManager.getSettingsIntent())
                                                    } else {
                                                        // Fallback: Dialog versuchen
                                                        permissionLauncher.launch(vm.healthConnectManager.permissions)
                                                    }
                                                }
                                            }
                                        }
                                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                                            snackbarHostState.showSnackbar(
                                                "Health Connect Update erforderlich",
                                                actionLabel = "Update"
                                            ).also { result ->
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    context.startActivity(vm.healthConnectManager.getInstallIntent())
                                                }
                                            }
                                        }
                                        else -> {
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
                    0 -> TodayScreen(userProfile, foods, entries, vm, snackbarHostState)
                    1 -> FoodsScreen(foods, vm::addFood, vm::deleteFood, vm::updateFood, snackbarHostState)
                    2 -> MealsScreen(vm, snackbarHostState)
                    3 -> ProfileScreen(profileVm)
                }
            }
        }
    }
}

@Composable
fun SwipeActionContainer(
    onDeleteRequest: () -> Unit,
    onEditRequest: () -> Unit,
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
                .pointerInput(Unit) {
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
                },
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
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scannerService = remember { BarcodeScannerService(context) }

    var entryToDelete by remember { mutableStateOf<Long?>(null) }
    var entryToEdit by remember { mutableStateOf<FoodEntryEntity?>(null) }
    var scannedFood by remember { mutableStateOf<FoodItemEntity?>(null) }
    var showStepDialog by remember { mutableStateOf(false) }

    val mealSlots = listOf("Frühstück", "Mittag", "Abend", "Snack")
    val expandedStates = remember { mutableStateMapOf<String, Boolean>().apply { mealSlots.forEach { put(it, true) } } }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Eintrag löschen") },
            text = { Text("Möchtest du diesen Eintrag wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteEntry(entryToDelete!!)
                    entryToDelete = null
                }) { Text("Löschen", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) { Text("Abbrechen") }
            }
        )
    }

    if (entryToEdit != null) {
        if (entryToEdit!!.isMeal) {
            EditMealEntryDialog(
                entry = entryToEdit!!,
                foods = foods,
                onDismiss = { entryToEdit = null },
                onSave = { updated ->
                    vm.updateEntry(updated)
                    entryToEdit = null
                }
            )
        } else {
            EditEntryDialog(
                entry = entryToEdit!!,
                foods = foods,
                onDismiss = { entryToEdit = null },
                onSave = { updated ->
                    vm.updateEntry(updated)
                    entryToEdit = null
                }
            )
        }
    }

    if (scannedFood != null) {
        AddAmountDialog(
            food = scannedFood!!,
            onDismiss = { scannedFood = null },
            onConfirm = { amount, portion, mealSlot ->
                vm.addEntry(scannedFood!!, amount, portion, mealSlot)
                scannedFood = null
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
                        val existing = if (food.barcode != null) vm.findFoodByBarcode(food.barcode!!) else null
                        if (existing != null) {
                            finalFood = existing
                        } else {
                            finalFood = vm.addFood(
                                food.name, food.kcalPer100g, food.proteinPer100g,
                                food.carbsPer100g, food.sugarPer100g, food.fatPer100g,
                                food.saturatedFatPer100g, food.alcoholPercent, food.baseUnit,
                                food.portions, food.packages, food.barcode, food.brand
                            )
                        }
                    }
                    vm.addEntry(finalFood, amount, portion, mealSlot)
                },
                onAddMeal = { meal, mealSlot ->
                    vm.addMealEntry(meal, mealSlot)
                },
                onScanRequest = {
                    scope.launch {
                        val barcode = scannerService.startScan()
                        if (barcode != null) {
                            var food = vm.findFoodByBarcode(barcode)
                            if (food == null) {
                                val fetched = scannerService.fetchProduct(barcode)
                                if (fetched != null) {
                                    food = vm.addFood(
                                        fetched.name, fetched.kcalPer100g, fetched.proteinPer100g,
                                        fetched.carbsPer100g, fetched.sugarPer100g, fetched.fatPer100g,
                                        fetched.saturatedFatPer100g, fetched.alcoholPercent, fetched.baseUnit,
                                        fetched.portions, fetched.packages, fetched.barcode, fetched.brand
                                    )
                                    snackbarHostState.showSnackbar("Produkt erfolgreich importiert.")
                                } else {
                                    snackbarHostState.showSnackbar("Produkt nicht gefunden.")
                                }
                            }
                            if (food != null) {
                                scannedFood = food
                            }
                        }
                    }
                },
                onSearchRequest = { query -> scannerService.searchProducts(query) }
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
                    SwipeActionContainer(
                        onDeleteRequest = { entryToDelete = entry.id },
                        onEditRequest = { entryToEdit = entry }
                    ) {
                        CompactEntryRow(entry)
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
                    modifier = Modifier.fillMaxWidth()
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
            .clickable { onToggle() }
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
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
            MacroNumber(totalSaturatedFat, SaturatedGrey)
            MacroNumber(totalUnsaturatedFat, UnsaturatedYellow)
        }
        HorizontalDivider(modifier = Modifier.padding(top = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun AddAmountDialog(
    food: FoodItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double, FoodPortionEntity?, String) -> Unit
) {
    val firstPortion = food.portions.firstOrNull()
    var amount by remember { mutableStateOf(if (firstPortion != null) "1" else "100") }
    var selectedPortion by remember { mutableStateOf<FoodPortionEntity?>(firstPortion) }
    var mealSlot by remember { mutableStateOf("Snack") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutoSelectTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Menge") },
                    modifier = Modifier.fillMaxWidth()
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
                    food.portions.forEach { portion ->
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

@Composable
private fun EditEntryDialog(
    entry: FoodEntryEntity,
    foods: List<FoodItemEntity>,
    onDismiss: () -> Unit,
    onSave: (FoodEntryEntity) -> Unit
) {
    val food = foods.find { it.id == entry.foodItemId } ?: return
    var amount by remember { mutableStateOf(entry.amount.roundString()) }
    var selectedPortion by remember {
        mutableStateOf(food.portions.find { it.name == entry.unitLabel })
    }
    var mealSlot by remember { mutableStateOf(entry.mealSlot) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eintrag bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(food.name, fontWeight = FontWeight.Bold)
                AutoSelectTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Menge") },
                    modifier = Modifier.fillMaxWidth()
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
                    food.portions.forEach { portion ->
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
            TextButton(onClick = {
                val numAmount = amount.num()
                val grams = if (selectedPortion != null) numAmount * selectedPortion!!.grams else numAmount
                onSave(
                    entry.copy(
                        amount = numAmount,
                        unitLabel = selectedPortion?.name ?: food.baseUnit,
                        grams = grams,
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
private fun EditMealEntryDialog(
    entry: FoodEntryEntity,
    foods: List<FoodItemEntity>,
    onDismiss: () -> Unit,
    onSave: (FoodEntryEntity) -> Unit
) {
    var mealSlot by remember { mutableStateOf(entry.mealSlot) }
    val ingredients = remember { mutableStateListOf<MealIngredientEntity>().apply { 
        entry.mealIngredients?.let { addAll(it) } 
    } }
    
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
                        trailingIcon = { Icon(Icons.Default.Search, null) }
                    )
                    DropdownMenu(
                        expanded = expanded && filteredFoods.isNotEmpty(),
                        onDismissRequest = { expanded = false },
                        properties = PopupProperties(focusable = false)
                    ) {
                        filteredFoods.forEach { food ->
                            DropdownMenuItem(
                                text = { Text(food.name) },
                                onClick = {
                                    val firstPortion = food.portions.firstOrNull()
                                    ingredients.add(
                                        MealIngredientEntity(
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
                                            baseUnit = food.baseUnit
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
                    items(ingredients) { ingredient ->
                        IngredientAdjustRow(
                            ingredient = ingredient,
                            foods = foods,
                            onUpdate = { updated ->
                                val idx = ingredients.indexOf(ingredient)
                                if (idx != -1) ingredients[idx] = updated
                            },
                            onRemove = { ingredients.remove(ingredient) }
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(onClick = {
                        onSave(entry.copy(mealSlot = mealSlot, mealIngredients = ingredients.toList()))
                    }) { Text("Speichern") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun IngredientAdjustRow(
    ingredient: MealIngredientEntity,
    foods: List<FoodItemEntity>,
    onUpdate: (MealIngredientEntity) -> Unit,
    onRemove: () -> Unit
) {
    val food = foods.find { it.id == ingredient.foodItemId }
    var amountText by remember(ingredient.amount, ingredient.unitLabel) { 
        mutableStateOf(ingredient.amount.roundString()) 
    }
    val selectedPortion = food?.portions?.find { it.name == ingredient.unitLabel }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ingredient.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onRemove) {
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
                    modifier = Modifier.weight(0.3f)
                )
                
                Column(Modifier.weight(0.7f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedPortion == null,
                            onClick = { 
                                val oldGrams = if (selectedPortion != null) amountText.num() * selectedPortion.grams else amountText.num()
                                val newAmt = oldGrams
                                amountText = newAmt.roundString()
                                onUpdate(ingredient.copy(amount = newAmt, unitLabel = food?.baseUnit ?: "g", grams = oldGrams))
                            },
                            label = { Text(food?.baseUnit ?: "g") }
                        )
                        food?.portions?.forEach { portion ->
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

@Composable
private fun CompactEntryRow(entry: FoodEntryEntity) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { if (entry.isMeal) isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    if (!entry.brand.isNullOrBlank()) {
                        Text(entry.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                    }
                    Text(entry.displayAmount(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Text(entry.kcal.round0(), modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold)
                MacroNumber(entry.protein, ProteinGreen)
                Separator()
                MacroNumber(entry.complexCarbs, CarbOrange)
                MacroNumber(entry.sugar, SugarRed)
                Separator()
                MacroNumber(entry.saturatedFat, SaturatedGrey)
                MacroNumber(entry.unsaturatedFat, UnsaturatedYellow)
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LegendDot(ProteinGreen, "Protein")
        LegendDot(CarbOrange, "KH")
        LegendDot(SugarRed, "Zucker")
        LegendDot(SaturatedGrey, "ges.")
        LegendDot(UnsaturatedYellow, "unges.")
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MacroNumber(value: Double, color: Color) {
    Text(value.round0(), color = color, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 20.dp))
}

@Composable
private fun Separator() {
    Text("|", color = MaterialTheme.colorScheme.outline)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEntryCard(
    foods: List<FoodItemEntity>,
    meals: List<MealEntity>,
    onAddEntry: (FoodItemEntity, Double, FoodPortionEntity?, String) -> Unit,
    onAddMeal: (MealEntity, String) -> Unit,
    onScanRequest: () -> Unit,
    onSearchRequest: suspend (String) -> List<FoodItemEntity>
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFood by remember { mutableStateOf<FoodItemEntity?>(null) }
    var selectedMeal by remember { mutableStateOf<MealEntity?>(null) }
    var selectedPortion by remember(selectedFood) { mutableStateOf(selectedFood?.portions?.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    
    // Default to 1 if portion exists, else 100
    var amount by remember(selectedFood) { 
        mutableStateOf(if (selectedFood?.portions?.isNotEmpty() == true) "1" else "100") 
    }
    
    var mealSlot by remember { mutableStateOf("Snack") }
    var isMinimized by remember { mutableStateOf(false) }
    
    var remoteResults by remember { mutableStateOf(emptyList<FoodItemEntity>()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchTrigger by remember { mutableIntStateOf(0) }

    val rotation by animateFloatAsState(if (isMinimized) -90f else 0f)

    LaunchedEffect(searchQuery, searchTrigger) {
        if (searchQuery.length >= 3) {
            val isManual = searchTrigger > 0
            val isJustSelected = (selectedFood != null && selectedFood?.name == searchQuery) || (selectedMeal != null && selectedMeal?.name == searchQuery)
            
            if (!isJustSelected || isManual) {
                isSearching = true
                if (!isManual) delay(500)
                remoteResults = onSearchRequest(searchQuery)
                isSearching = false
                expanded = true
            }
        } else {
            remoteResults = emptyList()
        }
    }

    val filteredLocal = remember(searchQuery, foods) {
        if (searchQuery.isEmpty()) emptyList()
        else {
            val queryWords = searchQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }
            foods.filter { food ->
                queryWords.all { word ->
                    food.name.contains(word, ignoreCase = true) || 
                    (food.brand?.contains(word, ignoreCase = true) ?: false)
                }
            }
        }
    }

    val filteredMeals = remember(searchQuery, meals) {
        if (searchQuery.isEmpty()) emptyList()
        else {
            meals.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    Card {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { isMinimized = !isMinimized },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Eintragen", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotation).size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }

            AnimatedVisibility(visible = !isMinimized) {
                Column(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        AutoSelectTextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                if (selectedFood != null && it != selectedFood?.name) selectedFood = null
                                if (selectedMeal != null && it != selectedMeal?.name) selectedMeal = null
                                expanded = true
                            },
                            label = { Text("Suchen (Artikel oder Mahlzeit)...") },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSearching) {
                                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        IconButton(onClick = { if (searchQuery.length >= 3) searchTrigger++ }) {
                                            Icon(Icons.Default.Search, contentDescription = "Suchen")
                                        }
                                    }
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.length >= 3) searchTrigger++
                            }),
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded && (filteredLocal.isNotEmpty() || filteredMeals.isNotEmpty() || remoteResults.isNotEmpty() || isSearching), 
                            onDismissRequest = { expanded = false }
                        ) {
                            if (isSearching && remoteResults.isEmpty()) {
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(12.dp))
                                            Text("Suche Datenbank...", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    },
                                    onClick = {}
                                )
                            }
                            
                            if (filteredMeals.isNotEmpty()) {
                                Text("Deine Mahlzeiten", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                filteredMeals.forEach { meal ->
                                    DropdownMenuItem(
                                        text = { Text(meal.name) },
                                        onClick = {
                                            selectedMeal = meal
                                            selectedFood = null
                                            searchQuery = meal.name
                                            expanded = false
                                        },
                                        leadingIcon = { Icon(Icons.Default.SoupKitchen, null, modifier = Modifier.size(18.dp)) }
                                    )
                                }
                            }

                            if (filteredLocal.isNotEmpty()) {
                                Text("Deine Artikel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                filteredLocal.forEach { food ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(food.name)
                                                if (!food.brand.isNullOrBlank()) {
                                                    Text(food.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedFood = food
                                            selectedMeal = null
                                            searchQuery = food.name
                                            expanded = false
                                        }
                                    )
                                }
                            }
                            if (remoteResults.isNotEmpty()) {
                                if (filteredLocal.isNotEmpty() || filteredMeals.isNotEmpty()) HorizontalDivider()
                                Text("Datenbank", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                remoteResults.forEach { food ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text(food.name)
                                                if (!food.brand.isNullOrBlank()) {
                                                    Text(food.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        },
                                        onClick = {
                                            selectedFood = food
                                            selectedMeal = null
                                            searchQuery = food.name
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (selectedMeal == null) {
                        AutoSelectTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            label = { Text("Menge") },
                            modifier = Modifier.fillMaxWidth()
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
                                label = { Text(selectedFood?.baseUnit ?: "g") }
                            )
                            selectedFood?.portions?.forEach { portion ->
                                FilterChip(
                                    selected = selectedPortion == portion,
                                    onClick = { 
                                        val oldGrams = if (selectedPortion != null) amount.num() * selectedPortion!!.grams else amount.num()
                                        selectedPortion = portion
                                        amount = (oldGrams / portion.grams).roundString()
                                    },
                                    label = { Text("${portion.name} (${portion.grams.roundString()}${selectedFood?.baseUnit ?: "g"})") }
                                )
                            }
                        }
                    } else {
                        Text("Mahlzeit ausgewählt: ${selectedMeal!!.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text("${selectedMeal!!.totalKcal.round0()} kcal gesamt", style = MaterialTheme.typography.labelSmall)
                    }

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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = selectedFood != null || selectedMeal != null,
                            onClick = {
                                if (selectedFood != null) {
                                    onAddEntry(selectedFood!!, amount.num(), selectedPortion, mealSlot)
                                } else if (selectedMeal != null) {
                                    onAddMeal(selectedMeal!!, mealSlot)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Hinzufügen")
                        }

                        Button(
                            onClick = onScanRequest,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Barcode")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodsScreen(
    foods: List<FoodItemEntity>,
    onAddFood: (String, Double, Double, Double, Double, Double, Double, Double, String, List<FoodPortionEntity>, List<FoodPackageEntity>, String?, String?) -> Unit,
    onDeleteFood: (Long) -> Unit,
    onUpdateFood: (FoodItemEntity) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scannerService = remember { BarcodeScannerService(context) }
    
    var foodToDelete by remember { mutableStateOf<Long?>(null) }
    var foodToEdit by remember { mutableStateOf<FoodItemEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (foodToDelete != null) {
        AlertDialog(
            onDismissRequest = { foodToDelete = null },
            title = { Text("Lebensmittel löschen") },
            text = { Text("Möchtest du dieses Lebensmittel und alle zugehörigen Einträge wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFood(foodToDelete!!)
                    foodToDelete = null
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
            onDismiss = { foodToEdit = null },
            onSave = { updated ->
                onUpdateFood(updated)
                foodToEdit = null
            }
        )
    }

    if (showAddDialog) {
        FoodEditDialog(
            food = foodToEdit, 
            onDismiss = { 
                showAddDialog = false
                foodToEdit = null
            },
            onSave = { newFood ->
                if (newFood.id == 0L) {
                    onAddFood(
                        newFood.name, newFood.kcalPer100g, newFood.proteinPer100g,
                        newFood.carbsPer100g, newFood.sugarPer100g, newFood.fatPer100g,
                        newFood.saturatedFatPer100g, newFood.alcoholPercent, newFood.baseUnit,
                        newFood.portions, newFood.packages, newFood.barcode, newFood.brand
                    )
                } else {
                    onUpdateFood(newFood)
                }
                showAddDialog = false
                foodToEdit = null
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { 
                        foodToEdit = null
                        showAddDialog = true 
                    }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Neu")
                }
                
                Button(
                    onClick = {
                        scope.launch {
                            val barcode = scannerService.startScan()
                            if (barcode != null) {
                                val fetched = scannerService.fetchProduct(barcode)
                                if (fetched != null) {
                                    foodToEdit = fetched
                                    showAddDialog = true
                                } else {
                                    scope.launch { 
                                        snackbarHostState.showSnackbar("Produktdaten konnten nicht geladen werden. Bitte manuell eintragen.")
                                    }
                                    foodToEdit = FoodItemEntity(name = "", kcalPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, sugarPer100g = 0.0, fatPer100g = 0.0, saturatedFatPer100g = 0.0, barcode = barcode)
                                    showAddDialog = true
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.QrCodeScanner, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
            }
        }
        items(foods, key = { it.id }) { food ->
            SwipeActionContainer(
                onDeleteRequest = { foodToDelete = food.id },
                onEditRequest = { foodToEdit = food; showAddDialog = true }
            ) {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(food.name, fontWeight = FontWeight.Bold)
                        if (!food.brand.isNullOrBlank()) {
                            Text(food.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Text("${food.kcalPer100g.round0()} kcal / 100 ${food.baseUnit}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MacroNumber(food.proteinPer100g, ProteinGreen)
                            Separator()
                            MacroNumber(food.complexCarbsPer100g, CarbOrange)
                            MacroNumber(food.sugarPer100g, SugarRed)
                            Separator()
                            MacroNumber(food.saturatedFatPer100g, SaturatedGrey)
                            MacroNumber(food.unsaturatedFatPer100g, UnsaturatedYellow)
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
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodEditDialog(
    food: FoodItemEntity?,
    onDismiss: () -> Unit,
    onSave: (FoodItemEntity) -> Unit
) {
    var name by remember { mutableStateOf(food?.name ?: "") }
    var brand by remember { mutableStateOf(food?.brand ?: "") }
    var protein by remember { mutableStateOf(food?.proteinPer100g?.toString()?.replace(".0", "") ?: "") }
    var carbs by remember { mutableStateOf(food?.carbsPer100g?.toString()?.replace(".0", "") ?: "") }
    var sugar by remember { mutableStateOf(food?.sugarPer100g?.toString()?.replace(".0", "") ?: "") }
    var fat by remember { mutableStateOf(food?.fatPer100g?.toString()?.replace(".0", "") ?: "") }
    var saturatedFat by remember { mutableStateOf(food?.saturatedFatPer100g?.toString()?.replace(".0", "") ?: "") }
    var alcohol by remember { mutableStateOf(food?.alcoholPercent?.toString()?.replace(".0", "") ?: "0") }
    var baseUnit by remember { mutableStateOf(food?.baseUnit ?: "g") }
    var barcode by remember { mutableStateOf(food?.barcode ?: "") }
    
    var unitExpanded by remember { mutableStateOf(false) }

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
                    text = if (food == null || food.id == 0L) "Lebensmittel anlegen" else "Lebensmittel bearbeiten",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                AutoSelectTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                AutoSelectTextField(brand, { brand = it }, label = { Text("Marke") }, modifier = Modifier.fillMaxWidth())

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
                    AutoSelectTextField(protein, { protein = it }, label = { Text("Protein") }, modifier = Modifier.weight(1f))
                    AutoSelectTextField(carbs, { carbs = it }, label = { Text("KH ges.") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(sugar, { sugar = it }, label = { Text("davon Zucker") }, modifier = Modifier.weight(1f))
                    AutoSelectTextField(alcohol, { alcohol = it }, label = { Text("Alc.-%") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(fat, { fat = it }, label = { Text("Fett ges.") }, modifier = Modifier.weight(1f))
                    AutoSelectTextField(saturatedFat, { saturatedFat = it }, label = { Text("davon ges.") }, modifier = Modifier.weight(1f))
                }

                AutoSelectTextField(barcode, { barcode = it }, label = { Text("Barcode") }, modifier = Modifier.fillMaxWidth())

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Portionen (Stückmengen)", fontWeight = FontWeight.Bold)
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

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Packungsgrößen", fontWeight = FontWeight.Bold)
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

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(enabled = name.isNotBlank(), onClick = {
                        onSave(
                            FoodItemEntity(
                                id = food?.id ?: 0,
                                name = name,
                                brand = brand.takeIf { it.isNotBlank() },
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
                                packages = packages.map { FoodPackageEntity(0, it.name, it.quantity.num(), it.unit) }
                            )
                        )
                    }) { Text("Speichern") }
                }
            }
        }
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
