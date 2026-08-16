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
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.nick.nutritiontracker.ReminderManager
import com.nick.nutritiontracker.data.*
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.PlanMatchStatus
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
    val currentUser by vm.firebaseManager.currentUser.collectAsState()
    val userProfileState by profileVm.userProfile.collectAsState()
    
    // 1. App Lock / Login Gate
    // Biometrie soll nur noch den Login ersetzen, nicht mehr die App bei jedem Start sperren (User-Wunsch)
    if (currentUser == null) {
        AuthScreen(profileVm, vm)
        return
    }

    // 2. Wait until the profile is loaded from disk
    val userProfile = userProfileState ?: return // Show nothing while loading (brief flash)

    var showSetup by remember { mutableStateOf(false) }
    var initialCheckPerformed by remember { mutableStateOf(false) }
    
    LaunchedEffect(userProfile.setupCompleted, vm.forceOnboardingOnStart) {
        if (!initialCheckPerformed) {
            // Initial app launch: check BOTH setup completion and the force toggle
            if (!userProfile.setupCompleted || vm.forceOnboardingOnStart) {
                showSetup = true
            }
            initialCheckPerformed = true
        } else {
            // App is already running: only trigger if setupCompleted is explicitly set to false (e.g. by reset button)
            if (!userProfile.setupCompleted) {
                showSetup = true
            } else {
                showSetup = false
            }
        }
    }

    if (showSetup) {
        SetupWizard(profileVm, vm)
    } else {
        val context = LocalContext.current
        LaunchedEffect(userProfile) {
            ReminderManager.scheduleReminders(context, userProfile)
        }
        MainApp(vm, profileVm, userProfile)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(vm: NutritionViewModel, profileVm: ProfileViewModel, userProfile: UserProfile) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    
    val context = LocalContext.current
    val scannerService = remember { BarcodeScannerService(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Deep Link Handling: Bei Schnell-Scan automatisch auf den Heute-Tab wechseln
    LaunchedEffect(vm.isQuickScanRunning) {
        if (vm.isQuickScanRunning) {
            tab = 0
        }
    }

    val foods = vm.foods
    val entries = vm.todayEntries
    
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
            vm.syncActivityForSelectedDate()
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

    if (vm.pendingScanResult != null) {
        val food = vm.pendingScanResult!!
        AddAmountDialog(
            food = food,
            vm = vm,
            onDismiss = { 
                vm.pendingScanResult = null 
                if (vm.isQuickScanRunning) vm.shouldCloseApp = true
            },
            onConfirm = { amount, portion, pkg, mealSlot ->
                vm.addEntry(food, amount, portion, mealSlot, pkg)
                vm.pendingScanResult = null
                if (vm.isQuickScanRunning) vm.shouldCloseApp = true
            }
        )
    }

    if (vm.pendingDuplicateFood != null) {
        val food = vm.pendingDuplicateFood!!
        AlertDialog(
            onDismissRequest = { 
                vm.pendingDuplicateFood = null 
                if (vm.isQuickScanRunning) vm.shouldCloseApp = true
            },
            title = { Text("Artikel bereits vorhanden") },
            text = { Text("Ein Artikel mit dem Barcode '${food.barcode}' ist bereits als '${food.name}' gespeichert. Möchtest du den vorhandenen Artikel verwenden?") },
            confirmButton = {
                Button(onClick = {
                    vm.pendingScanResult = food
                    vm.pendingDuplicateFood = null
                }) { Text("Verwenden") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.pendingDuplicateFood = null
                    if (vm.isQuickScanRunning) vm.shouldCloseApp = true
                }) { Text("Abbrechen") }
            }
        )
    }

    if (vm.pendingAskToCapture != null) {
        val food = vm.pendingAskToCapture!!
        AlertDialog(
            onDismissRequest = { 
                vm.pendingAskToCapture = null 
                if (vm.isQuickScanRunning) vm.shouldCloseApp = true
            },
            title = { Text("Neuer Artikel") },
            text = { Text("Möchtest du '${food.name}' dauerhaft in deinen Artikeln speichern (mit Portionen etc.) oder nur für diesen Eintrag verwenden?") },
            confirmButton = {
                Button(onClick = {
                    vm.pendingFoodToCapture = food
                    vm.pendingAskToCapture = null
                }) { Text("Dauerhaft speichern") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.pendingScanResult = food
                    vm.pendingAskToCapture = null
                }) { Text("Nur verwenden") }
            }
        )
    }

    if (vm.pendingFoodToCapture != null) {
        FoodEditDialog(
            food = vm.pendingFoodToCapture!!,
            vm = vm,
            onDismiss = { vm.pendingFoodToCapture = null },
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
                vm.pendingFoodToCapture = null
                vm.pendingScanResult = saved
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
                                DiarySelectionActions(vm, selectedEntryIds, scope, snackbarHostState, dateFormatter, userProfile)
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
                                val statusColor = Color(vm.getDayStatusColor(vm.selectedDate.toString(), userProfile))
                                val isVerified = vm.dayVerifications[vm.selectedDate.toString()] ?: false
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(statusColor, RoundedCornerShape(2.dp))
                                    )
                                    if (isVerified) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp).padding(start = 2.dp), tint = statusColor)
                                    }
                                    Spacer(Modifier.width(8.dp))
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
                                }
                            } else {
                                Text(
                                    when (tab) {
                                        1 -> "Artikel"
                                        2 -> "Mahlzeiten"
                                        4 -> "Planer"
                                        5 -> "Einkaufsliste"
                                        6 -> "Gewicht"
                                        7 -> "Postfach"
                                        8 -> "Community"
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
                                                    vm.syncActivityForSelectedDate()
                                                    snackbarHostState.showSnackbar("Aktivität aktualisiert")
                                                } else {
                                                    permissionLauncher.launch(vm.healthConnectManager.permissions)
                                                }
                                            }
                                            else -> {
                                                vm.syncActivityForSelectedDate()
                                                snackbarHostState.showSnackbar("Health Connect nicht verfügbar")
                                            }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync steps")
                                }
                            }
                            
                            // Notification Bell
                            val unreadCount = vm.unreadInboxCount
                            IconButton(onClick = { tab = 7 }) { // Tab 7 = Inbox
                                BadgedBox(
                                    badge = {
                                        if (unreadCount > 0) {
                                            Badge { Text(unreadCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Notifications, "Postfach")
                                }
                            }
                            
                            if (tab == 5) { // Shopping List
                                var showMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(onClick = { showMenu = true }) {
                                        Icon(Icons.Default.Tune, "Einstellungen")
                                    }
                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Zusammenfassen", modifier = Modifier.weight(1f))
                                                    Switch(
                                                        checked = vm.isShoppingListAggregated,
                                                        onCheckedChange = { vm.isShoppingListAggregated = it },
                                                        modifier = Modifier.scale(0.7f)
                                                    )
                                                }
                                            },
                                            onClick = { vm.isShoppingListAggregated = !vm.isShoppingListAggregated }
                                        )
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Nach Kategorie", modifier = Modifier.weight(1f))
                                                    Switch(
                                                        checked = vm.shoppingListSortByCategory,
                                                        onCheckedChange = { vm.updateShoppingListSort(it) },
                                                        modifier = Modifier.scale(0.7f)
                                                    )
                                                }
                                            },
                                            onClick = { vm.updateShoppingListSort(!vm.shoppingListSortByCategory) }
                                        )
                                        DropdownMenuItem(
                                            text = { 
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("Vorrat anzeigen", modifier = Modifier.weight(1f))
                                                    Switch(
                                                        checked = vm.showPantryInShoppingList,
                                                        onCheckedChange = { vm.showPantryInShoppingList = it },
                                                        modifier = Modifier.scale(0.7f)
                                                    )
                                                }
                                            },
                                            onClick = { vm.showPantryInShoppingList = !vm.showPantryInShoppingList }
                                        )
                                    }
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
                        selected = tab == 6,
                        onClick = { tab = 6 },
                        label = { Text("Gewicht") },
                        icon = { Icon(Icons.Default.MonitorWeight, null) }
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        label = { Text("Profil") },
                        icon = { Icon(Icons.Default.Person, null) }
                    )
                    NavigationBarItem(
                        selected = tab == 8,
                        onClick = { tab = 8 },
                        label = { Text("Community") },
                        icon = { Icon(Icons.Default.Groups, null) }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> TodayScreen(userProfile, foods, entries, vm, snackbarHostState, selectedEntryIds, selectedFoodIds, selectedMealIds, scannerService)
                    1 -> FoodsScreen(vm, snackbarHostState, selectedFoodIds)
                    2 -> MealsScreen(vm, snackbarHostState, selectedMealIds, userProfile)
                    3 -> ProfileScreen(profileVm, vm, userProfile)
                    4 -> PlannerScreen(vm, userProfile)
                    5 -> ShoppingListScreen(vm, userProfile)
                    6 -> WeightScreen(vm, profileVm)
                    7 -> InboxScreen(vm, onBack = { tab = 0 })
                    8 -> CommunityScreen(vm)
                }
            }
        }
    }

    // Weekly Summary Popup (Sunday)
    var weeklySummaryAcknowledged by remember { mutableStateOf(false) }
    val isSunday = LocalDate.now().dayOfWeek == java.time.DayOfWeek.SUNDAY
    
    // Unterdrücke Zusammenfassung im Quick-Scan-Modus
    if (isSunday && userProfile.setupCompleted && !weeklySummaryAcknowledged && !vm.isQuickScanRunning) {
        val last7Days = (0..6).map { LocalDate.now().minusDays(it.toLong()).toString() }
        val verifiedDays = last7Days.filter { vm.dayVerifications[it] == true }
        
        // Only count days with negative balance (weight loss)
        val weeklyGrams = verifiedDays.sumOf { date -> 
            val budget = vm.calculateWeightBudgetGrams(date, userProfile)
            if (budget > 0) budget else 0.0
        }

        if (weeklyGrams > 0) {
            AlertDialog(
                onDismissRequest = { weeklySummaryAcknowledged = true },
                title = { Text("Wochenrückblick 📊") },
                text = {
                    Column {
                        Text("Starke Woche, ${userProfile.firstName}!", fontWeight = FontWeight.Bold)
                        Text("Durch deine Disziplin an ${verifiedDays.size} verifizierten Tagen hast du rechnerisch ca.")
                        Text("${weeklyGrams.round0()}g Fett", style = MaterialTheme.typography.headlineMedium, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                        Text("verbrannt. Weiter so!")
                    }
                },
                confirmButton = {
                    Button(onClick = { weeklySummaryAcknowledged = true }) { Text("Super!") }
                }
            )
        }
    }
}

@Composable
fun SwipeActionContainer(
    onDeleteRequest: () -> Unit,
    onEditRequest: () -> Unit,
    key: Any? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val maxSwipePx = with(LocalDensity.current) { 80.dp.toPx() }
    val offsetX = remember(key) { Animatable(0f) }
    
    // Ensure lambdas are always fresh inside the pointerInput block
    val currentOnDelete by rememberUpdatedState(onDeleteRequest)
    val currentOnEdit by rememberUpdatedState(onEditRequest)

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
                    Modifier.pointerInput(key) {
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
                                        currentOnEdit()
                                    } else if (offsetX.value < -maxSwipePx * 0.6f) {
                                        offsetX.animateTo(0f)
                                        currentOnDelete()
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
    selectedMealIds: MutableState<Set<Long>>,
    scannerService: BarcodeScannerService // Übergeben von MainApp
) {
    val scope = rememberCoroutineScope()

    var entryToDelete by remember { mutableStateOf<Long?>(null) }
    var entryToEdit by remember { mutableStateOf<FoodEntryEntity?>(null) }
    var showStepDialog by remember { mutableStateOf(false) }

    val localContext = LocalContext.current
    
    // Widget/Quick-Scan Trigger: Wir lauschen auf den Trigger aus dem ViewModel
    LaunchedEffect(Unit) {
        vm.scanTrigger.collect {
            // Sofort konsumieren, damit es nicht bei Rotation erneut feuert
            vm.consumeScanTrigger()
            
            // Dies führt exakt denselben Code aus wie der Scan-Button unten
            val barcode = scannerService.startScan()
            if (barcode != null) {
                val existing = vm.findFoodByBarcode(barcode)
                if (existing != null) {
                    vm.pendingDuplicateFood = existing
                } else {
                    val fetched = scannerService.fetchProduct(barcode)
                    if (fetched != null) {
                        vm.pendingAskToCapture = fetched
                    } else {
                        snackbarHostState.showSnackbar("Produkt nicht gefunden. Bitte manuell erfassen.")
                        vm.pendingFoodToCapture = FoodItemEntity(name = "", kcalPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, sugarPer100g = 0.0, fatPer100g = 0.0, saturatedFatPer100g = 0.0, barcode = barcode)
                    }
                }
            } else if (vm.isQuickScanRunning) {
                vm.shouldCloseApp = true
            }
        }
    }
    
    LaunchedEffect(vm.aiErrorMessage) {
        vm.aiErrorMessage?.let {
            android.widget.Toast.makeText(localContext, it, android.widget.Toast.LENGTH_LONG).show()
            vm.aiErrorMessage = null
        }
    }

    if (vm.isAnalyzingImage) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Analysiere Bild...") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        )
    }

    if (vm.aiEstimationResult != null) {
        AiEstimationDialog(
            result = vm.aiEstimationResult!!,
            onDismiss = { vm.aiEstimationResult = null },
            onConfirm = { result ->
                // Create a temporary food item from the estimation
                val food = FoodItemEntity(
                    name = result.name,
                    kcalPer100g = (result.kcal / result.grams) * 100.0,
                    proteinPer100g = (result.protein / result.grams) * 100.0,
                    carbsPer100g = (result.carbs / result.grams) * 100.0,
                    sugarPer100g = (result.sugar / result.grams) * 100.0,
                    fatPer100g = (result.fat / result.grams) * 100.0,
                    saturatedFatPer100g = (result.saturatedFat / result.grams) * 100.0,
                    baseUnit = "g"
                )
                // Add as entry (NutritionViewModel's addEntry will handle the rest)
                vm.addEntry(food, result.grams, null, "Snack")
                vm.aiEstimationResult = null
                scope.launch { snackbarHostState.showSnackbar("${result.name} hinzugefügt") }
            }
        )
    }

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

    if (showStepDialog) {
        ActivityInputDialog(
            initialSteps = vm.todaySteps,
            initialTotalKcal = vm.dailyTotalCalories[vm.selectedDate.toString()] ?: 0.0,
            onDismiss = { showStepDialog = false },
            onConfirm = { steps, totalKcal ->
                vm.updateActivity(steps, totalKcal)
                showStepDialog = false
            }
        )
    }

    val weightBudget = vm.calculateWeightBudgetGrams(vm.selectedDate.toString(), userProfile)
    val isVerified = vm.dayVerifications[vm.selectedDate.toString()] ?: false

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
                    activityKcal = vm.todayActivityKcal,
                    weightBudgetGrams = weightBudget,
                    steps = vm.todaySteps,
                    stepKcal = vm.todayStepKcal,
                    exerciseSessions = vm.dailyExerciseSessions[vm.selectedDate.toString()] ?: emptyList()
                )
            }
        }
        
        if (!isVerified) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tag noch nicht verifiziert", fontWeight = FontWeight.Bold)
                        Text("Hast du heute bereits alles ehrlich eingetragen?", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.verifyDay(vm.selectedDate.toString(), true) }) {
                            Text("Ja, alles eingetragen!")
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Tag erfolgreich verifiziert", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall)
                }
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
                onAddEntry = { food, amount, portion, pkg, mealSlot ->
                    var finalFood = food
                    if (food.id == 0L) {
                        val existing = food.barcode?.let { vm.findFoodByBarcode(it) }
                        if (existing != null) {
                            finalFood = existing
                        } else {
                            finalFood = vm.addFood(
                                food.name, food.kcalPer100g, food.proteinPer100g,
                                food.carbsPer100g, sugar = food.sugarPer100g, fat = food.fatPer100g,
                                saturatedFat = food.saturatedFatPer100g, alcoholPercent = food.alcoholPercent, baseUnit = food.baseUnit,
                                portions = food.portions, packages = food.packages, barcode = food.barcode, brand = food.brand, category = food.category,
                                isGeneric = food.isGeneric,
                                parentId = food.parentId,
                                store = food.store
                            )
                        }
                    }
                    vm.addEntry(finalFood, amount, portion, mealSlot, pkg)
                    
                    // Quick-Scan: App nach Eintragung schließen
                    if (vm.isQuickScanRunning) {
                        vm.shouldCloseApp = true
                    }
                },
                onAddMeal = { meal, mealSlot, servings ->
                    vm.addMealEntry(meal, mealSlot, servings)
                    if (vm.isQuickScanRunning) {
                        vm.shouldCloseApp = true
                    }
                },
                onScanRequest = {
                    scope.launch {
                        val barcode = scannerService.startScan()
                        if (barcode != null) {
                            val existing = vm.findFoodByBarcode(barcode)
                            if (existing != null) {
                                vm.pendingDuplicateFood = existing
                            } else {
                                val fetched = scannerService.fetchProduct(barcode)
                                if (fetched != null) {
                                    vm.pendingAskToCapture = fetched
                                } else {
                                    snackbarHostState.showSnackbar("Produkt nicht gefunden. Bitte manuell erfassen.")
                                    vm.pendingFoodToCapture = FoodItemEntity(name = "", kcalPer100g = 0.0, proteinPer100g = 0.0, carbsPer100g = 0.0, sugarPer100g = 0.0, fatPer100g = 0.0, saturatedFatPer100g = 0.0, barcode = barcode)
                                }
                            }
                        }
                    }
                },
                onSearchRequest = { query -> scannerService.searchProducts(query) },
                onCaptureRequested = { food -> vm.pendingAskToCapture = food },
                vm = vm,
                snackbarHostState = snackbarHostState,
                isPremium = userProfile.isPremium
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
                        key = entry.id,
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
private fun ActivityInputDialog(
    initialSteps: Int,
    initialTotalKcal: Double,
    onDismiss: () -> Unit,
    onConfirm: (Int, Double) -> Unit
) {
    var steps by remember { mutableStateOf(initialSteps.toString()) }
    var totalKcal by remember { mutableStateOf(if (initialTotalKcal > 0) initialTotalKcal.roundString() else "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aktivität erfassen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Trage deine heutige Aktivität ein:")
                AutoSelectTextField(
                    value = totalKcal,
                    onValueChange = { totalKcal = it },
                    label = { Text("Gesamtkalorien (Watch)") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("z.B. 2500") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )
                AutoSelectTextField(
                    value = steps,
                    onValueChange = { steps = it },
                    label = { Text("Schritte (nur als Backup)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(steps.toIntOrNull() ?: 0, totalKcal.num()) })
                )
                Text(
                    "Hinweis: Wenn Gesamtkalorien eingetragen sind, wird dein Tagesbudget = BMR + (Watch Total - BMR) berechnet.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = { 
                onConfirm(steps.toIntOrNull() ?: 0, totalKcal.num()) 
            }) { Text("Speichern") }
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
    onConfirm: (Double, FoodPortionEntity?, FoodPackageEntity?, String) -> Unit
) {
    val parent = food.parentId?.let { pId -> vm.foods.find { it.id == pId } }
    val allPortions = food.getAllPortions(parent)
    val firstPortion = allPortions.firstOrNull()
    var amount by remember { mutableStateOf(if (firstPortion != null) "1" else "100") }
    var selectedPortion by remember { mutableStateOf<FoodPortionEntity?>(firstPortion) }
    var selectedPackage by remember { mutableStateOf<FoodPackageEntity?>(null) }
    var mealSlot by remember { mutableStateOf("Snack") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Column {
                Text(food.name)
                food.brand?.takeIf { it.isNotBlank() && !food.isGeneric }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
                    keyboardActions = KeyboardActions(onDone = { onConfirm(amount.num(), selectedPortion, selectedPackage, mealSlot) })
                )
                Text("Einheit", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPortion == null && selectedPackage == null,
                        onClick = { 
                            val oldGrams = when {
                                selectedPortion != null -> amount.num() * selectedPortion!!.grams
                                selectedPackage != null -> amount.num() * selectedPackage!!.quantity
                                else -> amount.num()
                            }
                            selectedPortion = null
                            selectedPackage = null
                            amount = oldGrams.roundString()
                        },
                        label = { Text(food.baseUnit) }
                    )
                    allPortions.forEach { portion ->
                        FilterChip(
                            selected = selectedPortion == portion,
                            onClick = { 
                                val oldGrams = when {
                                    selectedPortion != null -> amount.num() * selectedPortion!!.grams
                                    selectedPackage != null -> amount.num() * selectedPackage!!.quantity
                                    else -> amount.num()
                                }
                                selectedPortion = portion
                                selectedPackage = null
                                amount = (oldGrams / portion.grams).roundString()
                            },
                            label = { Text("${portion.name} (${portion.grams.roundString()}${food.baseUnit})") }
                        )
                    }
                    food.packages.forEach { pkg ->
                        FilterChip(
                            selected = selectedPackage == pkg,
                            onClick = {
                                val oldGrams = when {
                                    selectedPortion != null -> amount.num() * selectedPortion!!.grams
                                    selectedPackage != null -> amount.num() * selectedPackage!!.quantity
                                    else -> amount.num()
                                }
                                selectedPortion = null
                                selectedPackage = pkg
                                amount = (oldGrams / pkg.quantity).roundString()
                            },
                            label = { Text("${pkg.name} (${pkg.quantity.roundString()}${pkg.unit})") }
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
                onConfirm(amount.num(), selectedPortion, selectedPackage, mealSlot)
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
    onAddEntry: (FoodItemEntity, Double, FoodPortionEntity?, FoodPackageEntity?, String) -> Unit,
    onAddMeal: (MealEntity, String, Double) -> Unit,
    onScanRequest: () -> Unit,
    onSearchRequest: suspend (String) -> List<FoodItemEntity>,
    onCaptureRequested: (FoodItemEntity) -> Unit,
    vm: NutritionViewModel,
    snackbarHostState: SnackbarHostState,
    isPremium: Boolean
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Any>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    
    var selectedFood by remember { mutableStateOf<FoodItemEntity?>(null) }
    var selectedMeal by remember { mutableStateOf<MealEntity?>(null) }
    var showMealSlotDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempImageUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                try {
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, it))
                    } else {
                        @Suppress("DEPRECATION")
                        android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(it))
                    }
                    bitmap?.let { b ->
                        vm.analyzeMealImage(b.copy(android.graphics.Bitmap.Config.ARGB_8888, true), isPremium)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && tempImageUri != null) {
                try {
                    val bitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        android.graphics.ImageDecoder.decodeBitmap(android.graphics.ImageDecoder.createSource(context.contentResolver, tempImageUri!!))
                    } else {
                        @Suppress("DEPRECATION")
                        android.graphics.BitmapFactory.decodeStream(context.contentResolver.openInputStream(tempImageUri!!))
                    }
                    bitmap?.let { b ->
                        vm.analyzeMealImage(b.copy(android.graphics.Bitmap.Config.ARGB_8888, true), isPremium)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                tempImageUri?.let { cameraLauncher.launch(it) }
            } else {
                scope.launch { snackbarHostState.showSnackbar("Kamera-Berechtigung erforderlich") }
            }
        }
    )

    if (selectedFood != null) {
        AddAmountDialog(
            food = selectedFood!!,
            vm = vm,
            onDismiss = { selectedFood = null },
            onConfirm = { amount, portion, pkg, mealSlot ->
                onAddEntry(selectedFood!!, amount, portion, pkg, mealSlot)
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

                    if (showImageSourceDialog) {
                        AlertDialog(
                            onDismissRequest = { showImageSourceDialog = false },
                            title = { Text("Bildquelle wählen") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ListItem(
                                        headlineContent = { Text("Kamera") },
                                        leadingContent = { Icon(Icons.Default.PhotoCamera, null) },
                                        modifier = Modifier.clickable {
                                            showImageSourceDialog = false
                                            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                            
                                            try {
                                                val file = java.io.File(context.cacheDir, "temp_meal_image.jpg")
                                                if (file.exists()) file.delete()
                                                file.createNewFile()
                                                
                                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                                    context, 
                                                    "${context.packageName}.fileprovider", 
                                                    file
                                                )
                                                tempImageUri = uri
                                                
                                                if (hasPermission) {
                                                    cameraLauncher.launch(uri)
                                                } else {
                                                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("AddEntryCard", "Error starting camera", e)
                                                scope.launch { snackbarHostState.showSnackbar("Kamera konnte nicht gestartet werden") }
                                            }
                                        }
                                    )
                                    ListItem(
                                        headlineContent = { Text("Galerie") },
                                        leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                                        modifier = Modifier.clickable {
                                            showImageSourceDialog = false
                                            imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                                        }
                                    )
                                }
                            },
                            confirmButton = {}
                        )
                    }

                    FilledIconButton(
                        onClick = { 
                            if (isPremium) {
                                showImageSourceDialog = true 
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Premium-Funktion: Upgrade erforderlich für die AI Bilderkennung.")
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.AutoAwesome, "AI Bilderkennung")
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
    var selectedPackage by remember {
        mutableStateOf(food?.packages?.find { it.name == entry.unitLabel })
    }
    var mealSlot by remember { mutableStateOf(entry.mealSlot) }
    
    val matchStatus = remember(entry, foods) { vm.getMatchStatus(entry) }
    val isOrphaned = matchStatus == PlanMatchStatus.MISSING
    val isDivergent = matchStatus == PlanMatchStatus.DIVERGENT

    val relatives = remember(food, foods) {
        val root = if (food?.isGeneric == true) food else parent
        val effectiveRoot = root ?: foods.find { it.isGeneric && it.name == entry.name }
        
        if (effectiveRoot != null) {
            (listOf(effectiveRoot) + foods.filter { it.parentId == effectiveRoot.id }).filter { it.id != food?.id }
        } else {
            foods.filter { it.isGeneric }
        }
    }
    var showSwapMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Eintrag bearbeiten")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.name, 
                            style = MaterialTheme.typography.labelSmall, 
                            color = if (isOrphaned) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (relatives.isNotEmpty()) {
                            Box {
                                IconButton(
                                    onClick = { showSwapMenu = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz, 
                                        contentDescription = "Variante tauschen", 
                                        modifier = Modifier.size(16.dp), 
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(expanded = showSwapMenu, onDismissRequest = { showSwapMenu = false }) {
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
                                                // Swap variant: update entry data to match selected article
                                                onSave(entry.copy(
                                                    foodItemId = alt.id,
                                                    name = alt.name,
                                                    brand = alt.brand,
                                                    kcalPer100g = alt.kcalPer100g,
                                                    proteinPer100g = alt.proteinPer100g,
                                                    carbsPer100g = alt.carbsPer100g,
                                                    sugarPer100g = alt.sugarPer100g,
                                                    fatPer100g = alt.fatPer100g,
                                                    saturatedFatPer100g = alt.saturatedFatPer100g,
                                                    alcoholPercent = alt.alcoholPercent,
                                                    baseUnit = alt.baseUnit,
                                                    store = alt.store
                                                ))
                                                showSwapMenu = false
                                                onDismiss() // Close current dialog since we saved/swapped
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isOrphaned || isDivergent) {
                    val color = if (isOrphaned) MaterialTheme.colorScheme.error else Color(0xFFFFA000)
                    val title = if (isOrphaned) "Nicht in Bibliothek" else "Abweichende Werte"
                    val desc = if (isOrphaned) "Dieser Artikel ist noch nicht in deiner Bibliothek." 
                              else "Die Werte in deinem Planer weichen von deinem gespeicherten Artikel ab."

                    Card(
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (isOrphaned) Icons.Default.CloudDownload else Icons.Default.Warning, null, tint = color, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
                            }
                            Text(desc, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { vm.importPlannedEntryToLibrary(entry, replaceExisting = true) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = color),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (isOrphaned) "SPEICHERN" else "AKTUALISIEREN", style = MaterialTheme.typography.labelSmall)
                                }
                                if (isDivergent) {
                                    OutlinedButton(
                                        onClick = { vm.importPlannedEntryToLibrary(entry, replaceExisting = false) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("NEU ANLEGEN", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }

                AutoSelectTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Menge") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
                )
                
                if (allPortions.isNotEmpty() || (food?.packages?.isNotEmpty() == true) || !isOrphaned) {
                    Text("Einheit", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedPortion == null && selectedPackage == null,
                            onClick = { 
                                val oldGrams = when {
                                    selectedPortion != null -> amount.num() * selectedPortion!!.grams
                                    selectedPackage != null -> amount.num() * selectedPackage!!.quantity
                                    else -> amount.num()
                                }
                                selectedPortion = null
                                selectedPackage = null
                                amount = oldGrams.roundString()
                            },
                            label = { Text(food?.baseUnit ?: "g") }
                        )
                        allPortions.forEach { portion ->
                            FilterChip(
                                selected = selectedPortion == portion,
                                onClick = { 
                                    val oldGrams = when {
                                        selectedPortion != null -> amount.num() * selectedPortion!!.grams
                                        selectedPackage != null -> amount.num() * selectedPackage!!.quantity
                                        else -> amount.num()
                                    }
                                    selectedPortion = portion
                                    selectedPackage = null
                                    amount = (oldGrams / portion.grams).roundString()
                                },
                                label = { Text("${portion.name} (${portion.grams.roundString()}${food?.baseUnit ?: "g"})") }
                            )
                        }
                        food?.packages?.forEach { pkg ->
                            FilterChip(
                                selected = selectedPackage == pkg,
                                onClick = {
                                    val oldGrams = when {
                                        selectedPortion != null -> amount.num() * selectedPortion!!.grams
                                        selectedPackage != null -> amount.num() * selectedPackage!!.quantity
                                        else -> amount.num()
                                    }
                                    selectedPortion = null
                                    selectedPackage = pkg
                                    amount = (oldGrams / pkg.quantity).roundString()
                                },
                                label = { Text("${pkg.name} (${pkg.quantity.roundString()}${pkg.unit})") }
                            )
                        }
                    }
                } else {
                    Text("Einheit: ${entry.unitLabel}", style = MaterialTheme.typography.bodySmall, color = Color.Red)
                    Text("Hinweis: Da der Artikel nicht gespeichert ist, können keine Portionen gewählt werden.", style = MaterialTheme.typography.labelSmall, color = Color.Red)
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
                val grams = when {
                    selectedPortion != null -> numAmount * selectedPortion!!.grams
                    selectedPackage != null -> numAmount * selectedPackage!!.quantity
                    food != null -> numAmount
                    else -> entry.grams
                }
                onSave(
                    entry.copy(
                        amount = numAmount,
                        unitLabel = selectedPortion?.name ?: selectedPackage?.name ?: food?.baseUnit ?: entry.unitLabel,
                        grams = if (isOrphaned && selectedPortion == null && selectedPackage == null) (numAmount * (entry.grams / entry.amount.coerceAtLeast(1.0))) else grams,
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

                val matchStatus = remember(entry, foods, vm.meals) { vm.getMatchStatus(entry) }
                val hasMissingIngs = matchStatus == PlanMatchStatus.MISSING
                val isTemplateMissing = matchStatus == PlanMatchStatus.TEMPLATE_MISSING
                
                if (hasMissingIngs || isTemplateMissing) {
                    Button(
                        onClick = { 
                            vm.importPlannedMealToLibrary(entry)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasMissingIngs) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer, 
                            contentColor = if (hasMissingIngs) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(if (hasMissingIngs) Icons.Default.CloudDownload else Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (hasMissingIngs) "GANZE MAHLZEIT IN BIBLIOTHEK SPEICHERN" else "REZEPT ALS VORLAGE SPEICHERN", 
                            style = MaterialTheme.typography.labelSmall, 
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                
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
        val baseList = mutableListOf<FoodItemEntity>()
        
        // NUR Austausch-Optionen zeigen, wenn der Artikel eindeutig gefunden wurde
        if (food != null) {
            if (food.parentId != null) {
                // 1. Es ist ein Marken-Produkt: Zeige Basis-Zutat und "Geschwister" (andere Marken)
                val parentItem = foods.find { it.id == food.parentId }
                if (parentItem != null) baseList.add(parentItem)
                baseList.addAll(foods.filter { it.parentId == food.parentId })
            } else if (food.isGeneric) {
                // 2. Es ist eine Basis-Zutat: Zeige alle Marken-Varianten dafür
                baseList.addAll(foods.filter { it.parentId == food.id })
                // Ermögliche auch den Tausch zwischen Basis-Zutaten der gleichen Kategorie (z.B. Zwiebel <-> Schalotte)
                if (!food.category.isNullOrBlank()) {
                    baseList.addAll(foods.filter { it.isGeneric && it.category == food.category })
                }
            }
        }
        // Fallback per Name entfernt, da dies zu falschen Zuordnungen führte (z.B. Protein-Sahne vs. normale Milch)

        baseList.distinctBy { it.id }.filter { it.id != food?.id }.sortedBy { it.name }
    }
    var showSwapMenu by remember { mutableStateOf(false) }
    
    val matchStatus = remember(ingredient, foods) {
        val foodById = foods.find { it.id == ingredient.foodItemId }
        if (foodById != null && foodById.matchesIngredient(ingredient)) PlanMatchStatus.EXACT
        else {
            val similar = foods.find { it.name.trim().equals(ingredient.name.trim(), ignoreCase = true) && (it.brand?.trim() ?: "").equals(ingredient.brand?.trim() ?: "", ignoreCase = true) }
            when {
                similar == null -> PlanMatchStatus.MISSING
                similar.matchesIngredient(ingredient) -> PlanMatchStatus.EXACT
                else -> PlanMatchStatus.DIVERGENT
            }
        }
    }
    
    val isOrphaned = matchStatus == PlanMatchStatus.MISSING
    val isDivergent = matchStatus == PlanMatchStatus.DIVERGENT

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = when {
            isOrphaned -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            isDivergent -> CardDefaults.cardColors(containerColor = Color(0xFFFFE082).copy(alpha = 0.2f))
            else -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        },
        border = if (isDivergent) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.5f)) else null
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ingredient.name, 
                            fontWeight = FontWeight.Bold, 
                            style = MaterialTheme.typography.bodySmall,
                            color = when {
                                isOrphaned -> Color.Red
                                isDivergent -> Color(0xFFFFA000)
                                else -> Color.Unspecified
                            }
                        )
                        if (isOrphaned || isDivergent) {
                            IconButton(
                                onClick = {
                                    val pseudoEntry = FoodEntryEntity(
                                        foodItemId = ingredient.foodItemId,
                                        name = ingredient.name,
                                        brand = ingredient.brand,
                                        kcalPer100g = ingredient.kcalPer100g,
                                        proteinPer100g = ingredient.proteinPer100g,
                                        carbsPer100g = ingredient.carbsPer100g,
                                        sugarPer100g = ingredient.sugarPer100g,
                                        fatPer100g = ingredient.fatPer100g,
                                        saturatedFatPer100g = ingredient.saturatedFatPer100g,
                                        alcoholPercent = ingredient.alcoholPercent,
                                        baseUnit = ingredient.baseUnit,
                                        store = ingredient.store,
                                        category = ingredient.category,
                                        isGeneric = ingredient.isGeneric
                                    )
                                    vm.importPlannedEntryToLibrary(pseudoEntry, replaceExisting = isDivergent)
                                },
                                modifier = Modifier.size(24.dp).padding(start = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isOrphaned) Icons.Default.CloudDownload else Icons.Default.Sync, 
                                    contentDescription = if (isOrphaned) "Zutat übernehmen" else "Zutat aktualisieren", 
                                    tint = if (isOrphaned) MaterialTheme.colorScheme.error else Color(0xFFFFA000),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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
                                if (showSwapMenu) {
                                    androidx.compose.ui.window.Popup(
                                        onDismissRequest = { showSwapMenu = false },
                                        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .widthIn(max = 280.dp)
                                                .heightIn(max = 400.dp)
                                                .padding(8.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            tonalElevation = 8.dp,
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                        ) {
                                            LazyColumn(modifier = Modifier.padding(vertical = 4.dp)) {
                                                if (isOrphaned) {
                                                    item {
                                                        Text(
                                                            "Original gelöscht! Bitte Ersatz wählen:", 
                                                            style = MaterialTheme.typography.labelSmall, 
                                                            color = Color.Red,
                                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                        )
                                                    }
                                                }
                                                items(relatives) { alt ->
                                                    ListItem(
                                                        headlineContent = {
                                                            Text(
                                                                text = if (alt.isGeneric) "${alt.name} (Basis)" else alt.name,
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                        },
                                                        supportingContent = {
                                                            if (!alt.isGeneric) {
                                                                Text("${alt.brand ?: "Unbekannt"} @ ${alt.store ?: "Unbekannt"}", style = MaterialTheme.typography.labelSmall)
                                                            }
                                                        },
                                                        leadingContent = {
                                                            Icon(
                                                                imageVector = if (alt.isGeneric) Icons.Default.Inventory2 else Icons.AutoMirrored.Filled.Label,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(20.dp),
                                                                tint = if (alt.isGeneric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                                            )
                                                        },
                                                        modifier = Modifier.clickable {
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
                            }
                        }
                    }
                    if (isOrphaned) {
                        Text("Nicht in Bibliothek", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    } else if (isDivergent) {
                        Text("Abweichende Werte", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFA000))
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
                
                if (entry.imageUrl != null) {
                    AsyncImage(
                        model = entry.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
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
                            entry.brand?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1)
                            }
                            entry.store?.takeIf { it.isNotBlank() }?.let {
                                Text("@ $it", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), maxLines = 1)
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
            
            val ingredients = entry.mealIngredients
            if (isExpanded && entry.isMeal && ingredients != null) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 10.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ingredients.forEach { ing ->
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

    val userProfileState by vm.firebaseManager.userProfile.collectAsState()
    val isPremium = userProfileState?.isPremium ?: false

    if (showPantry) {
        PantryScreen(vm, onDismiss = { showPantry = false })
    }

    val searchQuery = vm.foodSearchQuery
    val selectedCategory = vm.selectedFoodCategory
    val categories = vm.categories.sorted()

    val filteredFoods by remember(searchQuery, selectedCategory, foods) {
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
                        name = newFood.name,
                        kcal = newFood.kcalPer100g,
                        protein = newFood.proteinPer100g,
                        carbs = newFood.carbsPer100g,
                        sugar = newFood.sugarPer100g,
                        fat = newFood.fatPer100g,
                        saturatedFat = newFood.saturatedFatPer100g,
                        alcoholPercent = newFood.alcoholPercent,
                        baseUnit = newFood.baseUnit,
                        portions = newFood.portions,
                        packages = newFood.packages,
                        barcode = newFood.barcode,
                        brand = newFood.brand,
                        category = newFood.category,
                        isGeneric = newFood.isGeneric,
                        parentId = newFood.parentId,
                        store = newFood.store,
                        isPantryItem = newFood.isPantryItem
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
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                }

                Button(
                    onClick = { 
                        foodToEdit = FoodItemEntity(isGeneric = false)
                        showAddDialog = true 
                    }, 
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Label, null)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp))
                }

                Button(
                    onClick = { showPantry = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                ) {
                    Icon(Icons.Default.Kitchen, null)
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
        key = food.id,
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
                    
                    if (!isExpanded) {
                        food.category?.takeIf { it.isNotBlank() }?.let {
                            SuggestionChip(onClick = {}, label = { Text(it, style = MaterialTheme.typography.labelSmall) })
                        }
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
                                food.brand?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                food.store?.takeIf { it.isNotBlank() }?.let {
                                    Text("@ $it", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
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

    val userProfileState by vm.firebaseManager.userProfile.collectAsState()
    val isPremium = userProfileState?.isPremium ?: false

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
                        isGeneric -> if (food?.id == 0L) "Neue Basis-Zutat 📦" else "Basis-Zutat bearbeiten 📦"
                        else -> if (food == null || food.id == 0L) "Neues Markenprodukt 🏷️" else "Markenprodukt bearbeiten 🏷️"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isGeneric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                    if (food?.id == 0L) {
                        val scope = rememberCoroutineScope()
                        
                        IconButton(
                            onClick = {
                                if (isPremium) {
                                    scope.launch {
                                        val result = vm.estimateGenericMacros(name, isBrandSearch = !isGeneric)
                                        if (result != null) {
                                            name = result.name
                                            protein = result.proteinPer100.roundString()
                                            carbs = result.carbsPer100.roundString()
                                            sugar = result.sugarPer100.roundString()
                                            fat = result.fatPer100.roundString()
                                            saturatedFat = result.saturatedFatPer100.roundString()
                                            baseUnit = result.baseUnit
                                            if (!result.category.isNullOrBlank() && vm.categories.contains(result.category)) {
                                                category = result.category
                                            }
                                            if (!isGeneric && !result.brand.isNullOrBlank()) {
                                                brand = result.brand
                                            }
                                            // Auto-fill portions for brands
                                            if (!isGeneric && result.portions.isNotEmpty()) {
                                                portions.clear()
                                                result.portions.forEach { p ->
                                                    portions.add(PortionInputState(p.name, p.grams.roundString()))
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = name.isNotBlank() && !vm.isAnalyzingGenericFood,
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (isPremium) (if (isGeneric) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer) 
                                    else Color.LightGray.copy(alpha = 0.3f), 
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            if (vm.isAnalyzingGenericFood) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Default.AutoAwesome, 
                                    contentDescription = "KI Hilfe",
                                    tint = if (isPremium) (if (isGeneric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary) else Color.Gray
                                )
                            }
                        }
                    }
                }

                if (vm.aiGenericFoodError != null) {
                    Text(vm.aiGenericFoodError!!, color = Color.Red, style = MaterialTheme.typography.labelSmall)
                }

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
                                        food.brand?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
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
    dateFormatter: java.time.format.DateTimeFormatter,
    userProfile: UserProfile
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
            onSave = { name, ingredients, servings, category, imageUrl ->
                vm.addMealTemplate(name, ingredients, servings, category, imageUrl)
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
    var showSendDialog by remember { mutableStateOf(false) }

    if (showSendDialog) {
        val foodId = selectedFoodIds.value.firstOrNull()
        val food = vm.foods.find { it.id == foodId }
        if (food != null) {
            SendToUserDialog(
                members = vm.householdMembers,
                onDismiss = { showSendDialog = false },
                onSend = { targetUid ->
                    vm.sendFoodToUser(targetUid, food)
                    showSendDialog = false
                    selectedFoodIds.value = emptySet()
                    scope.launch { snackbarHostState.showSnackbar("Artikel versendet!") }
                }
            )
        }
    }

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
        IconButton(onClick = { showSendDialog = true }) {
            Icon(Icons.AutoMirrored.Filled.Send, "An Haushaltsmitglied senden")
        }

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
    var showSendDialog by remember { mutableStateOf(false) }

    if (showSendDialog) {
        val mealId = selectedMealIds.value.firstOrNull()
        val meal = vm.meals.find { it.id == mealId }
        if (meal != null) {
            SendToUserDialog(
                members = vm.householdMembers,
                onDismiss = { showSendDialog = false },
                onSend = { targetUid ->
                    vm.sendRecipeToUser(targetUid, meal)
                    showSendDialog = false
                    selectedMealIds.value = emptySet()
                    scope.launch { snackbarHostState.showSnackbar("Rezept versendet!") }
                }
            )
        }
    }

    if (selectedMealIds.value.size == 1) {
        IconButton(onClick = { showSendDialog = true }) {
            Icon(Icons.AutoMirrored.Filled.Send, "An Haushaltsmitglied senden")
        }
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


@Composable
fun SendToUserDialog(
    members: List<Map<String, String>>,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Empfänger wählen") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(members) { member ->
                    val name = member["name"] ?: "Unbekannt"
                    val uid = member["uid"] ?: ""
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onSend(uid) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(name)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun AiEstimationDialog(
    result: AiEstimationResult,
    onDismiss: () -> Unit,
    onConfirm: (AiEstimationResult) -> Unit
) {
    var name by remember { mutableStateOf(result.name) }
    var kcal by remember { mutableStateOf(result.kcal.roundString()) }
    var protein by remember { mutableStateOf(result.protein.roundString()) }
    var carbs by remember { mutableStateOf(result.carbs.roundString()) }
    var sugar by remember { mutableStateOf(result.sugar.roundString()) }
    var fat by remember { mutableStateOf(result.fat.roundString()) }
    var saturatedFat by remember { mutableStateOf(result.saturatedFat.roundString()) }
    var grams by remember { mutableStateOf(result.grams.roundString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Schätzung 🤖") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Die AI hat folgendes erkannt. Bitte prüfe die Werte:", style = MaterialTheme.typography.bodySmall)
                
                AutoSelectTextField(value = name, onValueChange = { name = it }, label = { Text("Gericht") }, modifier = Modifier.fillMaxWidth())
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(value = kcal, onValueChange = { kcal = it }, label = { Text("Kalorien") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    AutoSelectTextField(value = grams, onValueChange = { grams = it }, label = { Text("Gewicht (g)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }

                Text("Makronährstoffe (Gesamt)", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(value = protein, onValueChange = { protein = it }, label = { Text("Protein") }, modifier = Modifier.weight(1f))
                    AutoSelectTextField(value = carbs, onValueChange = { carbs = it }, label = { Text("KH") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(value = sugar, onValueChange = { sugar = it }, label = { Text("davon Zucker") }, modifier = Modifier.weight(1f))
                    AutoSelectTextField(value = fat, onValueChange = { fat = it }, label = { Text("Fett") }, modifier = Modifier.weight(1f))
                }
                AutoSelectTextField(value = saturatedFat, onValueChange = { saturatedFat = it }, label = { Text("davon gesättigt") }, modifier = Modifier.fillMaxWidth())
                
                if (result.confidence > 0) {
                    Text("Vertrauen der AI: ${(result.confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(result.copy(
                    name = name,
                    kcal = kcal.num(),
                    protein = protein.num(),
                    carbs = carbs.num(),
                    sugar = sugar.num(),
                    fat = fat.num(),
                    saturatedFat = saturatedFat.num(),
                    grams = grams.num()
                ))
            }) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
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
