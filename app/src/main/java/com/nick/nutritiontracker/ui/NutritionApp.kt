package com.nick.nutritiontracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nick.nutritiontracker.data.*
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ProteinGreen = Color(0xFF2E7D32)
private val CarbOrange = Color(0xFFFF9800)
private val SugarRed = Color(0xFFD32F2F)
private val SaturatedGrey = Color(0xFF757575)
private val UnsaturatedYellow = Color(0xFFFBC02D)
private val EditYellow = Color(0xFFFFC107)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionApp(vm: NutritionViewModel, profileVm: ProfileViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val foods = vm.foods
    val entries = vm.todayEntries
    val userProfile by profileVm.userProfile.collectAsState()

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Text(
                        when (tab) {
                            0 -> "Heute"
                            1 -> "Lebensmittel"
                            else -> "Profil & Ziele"
                        }
                    )
                })
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("Heute") },
                        icon = { Icon(Icons.Default.Today, null) }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("Lebensmittel") },
                        icon = { Icon(Icons.Default.Restaurant, null) }
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        label = { Text("Profil") },
                        icon = { Icon(Icons.Default.Person, null) }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> TodayScreen(userProfile, foods, entries, vm)
                    1 -> FoodsScreen(foods, vm::addFood, vm::deleteFood, vm::updateFood)
                    2 -> ProfileScreen(profileVm)
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
    val maxSwipePx = with(LocalDensity.current) { 72.dp.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Edit Layer (Links)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EditYellow, RoundedCornerShape(12.dp))
                .padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            IconButton(
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    onEditRequest()
                },
                modifier = Modifier.width(72.dp).fillMaxHeight()
            ) {
                Icon(Icons.Default.Edit, "Bearbeiten", tint = Color.White)
            }
        }

        // Delete Layer (Rechts)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SugarRed, RoundedCornerShape(12.dp))
                .padding(end = 12.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                onClick = {
                    scope.launch { offsetX.animateTo(0f) }
                    onDeleteRequest()
                },
                modifier = Modifier.width(72.dp).fillMaxHeight()
            ) {
                Icon(Icons.Default.Delete, "Löschen", tint = Color.White)
            }
        }

        // Vordergrund
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            scope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceIn(-maxSwipePx, maxSwipePx)
                                offsetX.snapTo(newOffset)
                            }
                            change.consume()
                        },
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value < -maxSwipePx * 0.6f) {
                                    offsetX.animateTo(-maxSwipePx)
                                } else if (offsetX.value > maxSwipePx * 0.6f) {
                                    offsetX.animateTo(maxSwipePx)
                                } else {
                                    offsetX.animateTo(0f)
                                }
                            }
                        }
                    )
                }
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
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
    vm: NutritionViewModel
) {
    var entryToDelete by remember { mutableStateOf<Long?>(null) }
    var entryToEdit by remember { mutableStateOf<FoodEntryEntity?>(null) }

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

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            MacroProgressSection(
                userProfile = userProfile,
                currentKcal = vm.todayTotalKcal,
                currentProtein = vm.todayTotalProtein,
                currentComplexCarbs = vm.todayTotalComplexCarbs,
                currentSugar = vm.todayTotalSugar,
                currentUnsaturatedFat = vm.todayTotalUnsaturatedFat,
                currentSaturatedFat = vm.todayTotalSaturatedFat
            )
        }
        item {
            Card {
                Column(Modifier.padding(12.dp)) {
                    MacroLegendRow()
                }
            }
        }
        item { AddEntryCard(foods, vm::addEntry) }
        items(entries, key = { it.id }) { entry ->
            SwipeActionContainer(
                onDeleteRequest = { entryToDelete = entry.id },
                onEditRequest = { entryToEdit = entry }
            ) {
                CompactEntryRow(entry)
            }
        }
    }
}

@Composable
private fun EditEntryDialog(
    entry: FoodEntryEntity,
    foods: List<FoodItemEntity>,
    onDismiss: () -> Unit,
    onSave: (FoodEntryEntity) -> Unit
) {
    val food = foods.find { it.id == entry.foodItemId } ?: return
    var amount by remember { mutableStateOf(entry.amount.toString().replace(".0", "")) }
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
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Menge") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Einheit", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPortion == null,
                        onClick = { selectedPortion = null },
                        label = { Text(food.baseUnit) }
                    )
                    food.portions.forEach { portion ->
                        FilterChip(
                            selected = selectedPortion == portion,
                            onClick = { selectedPortion = portion },
                            label = { Text("${portion.name} (${portion.grams.round0()}${food.baseUnit})") }
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
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun CompactEntryRow(entry: FoodEntryEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.name, fontWeight = FontWeight.Bold, maxLines = 1)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddEntryCard(
    foods: List<FoodItemEntity>,
    onAddEntry: (FoodItemEntity, Double, FoodPortionEntity?, String) -> Unit
) {
    var selectedFood by remember(foods) { mutableStateOf(foods.firstOrNull()) }
    var selectedPortion by remember(selectedFood) { mutableStateOf(selectedFood?.portions?.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("1") }
    var mealSlot by remember { mutableStateOf("Snack") }

    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Eintragen", fontWeight = FontWeight.Bold)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    value = selectedFood?.name ?: "Wählen...",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Lebensmittel") },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    foods.forEach { food ->
                        DropdownMenuItem(
                            text = { Text(food.name) },
                            onClick = {
                                selectedFood = food
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Menge") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Einheit", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedPortion == null,
                    onClick = { selectedPortion = null },
                    label = { Text(selectedFood?.baseUnit ?: "g") }
                )
                selectedFood?.portions?.forEach { portion ->
                    FilterChip(
                        selected = selectedPortion == portion,
                        onClick = { selectedPortion = portion },
                        label = { Text("${portion.name} (${portion.grams.round0()}${selectedFood?.baseUnit})") }
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
            Button(
                enabled = selectedFood != null,
                onClick = {
                    selectedFood?.let { onAddEntry(it, amount.num(), selectedPortion, mealSlot) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Hinzufügen") }
        }
    }
}

@Composable
private fun FoodsScreen(
    foods: List<FoodItemEntity>,
    onAddFood: (String, Double, Double, Double, Double, Double, Double, Double, String, List<FoodPortionEntity>, List<FoodPackageEntity>, String?) -> Unit,
    onDeleteFood: (Long) -> Unit,
    onUpdateFood: (FoodItemEntity) -> Unit
) {
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

    if (foodToEdit != null) {
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
            food = null,
            onDismiss = { showAddDialog = false },
            onSave = { newFood ->
                onAddFood(
                    newFood.name, newFood.kcalPer100g, newFood.proteinPer100g,
                    newFood.carbsPer100g, newFood.sugarPer100g, newFood.fatPer100g,
                    newFood.saturatedFatPer100g, newFood.alcoholPercent, newFood.baseUnit, newFood.portions, newFood.packages, newFood.barcode
                )
                showAddDialog = false
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Neues Lebensmittel")
            }
        }
        items(foods, key = { it.id }) { food ->
            SwipeActionContainer(
                onDeleteRequest = { foodToDelete = food.id },
                onEditRequest = { foodToEdit = food }
            ) {
                Card {
                    Column(Modifier.padding(12.dp)) {
                        Text(food.name, fontWeight = FontWeight.Bold)
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
    var protein by remember { mutableStateOf(food?.proteinPer100g?.toString() ?: "") }
    var carbs by remember { mutableStateOf(food?.carbsPer100g?.toString() ?: "") }
    var sugar by remember { mutableStateOf(food?.sugarPer100g?.toString() ?: "") }
    var fat by remember { mutableStateOf(food?.fatPer100g?.toString() ?: "") }
    var saturatedFat by remember { mutableStateOf(food?.saturatedFatPer100g?.toString() ?: "") }
    var alcohol by remember { mutableStateOf(food?.alcoholPercent?.toString() ?: "0") }
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
                    text = if (food == null) "Lebensmittel anlegen" else "Lebensmittel bearbeiten",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())

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
                    OutlinedTextField(protein, { protein = it }, label = { Text("Protein") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(carbs, { carbs = it }, label = { Text("KH ges.") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sugar, { sugar = it }, label = { Text("davon Zucker") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(alcohol, { alcohol = it }, label = { Text("Alc.-%") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(fat, { fat = it }, label = { Text("Fett ges.") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(saturatedFat, { saturatedFat = it }, label = { Text("davon ges.") }, modifier = Modifier.weight(1f))
                }

                OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode") }, modifier = Modifier.fillMaxWidth())

                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Portionen (Stückmengen)", fontWeight = FontWeight.Bold)
                portions.forEachIndexed { index, p ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(p.name, { p.name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(p.grams, { p.grams = it }, label = { Text(baseUnit) }, modifier = Modifier.weight(0.6f))
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
                        OutlinedTextField(pkg.name, { pkg.name = it }, label = { Text("Name") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(pkg.quantity, { pkg.quantity = it }, label = { Text("Menge") }, modifier = Modifier.weight(0.7f))
                        OutlinedTextField(pkg.unit, { pkg.unit = it }, label = { Text("Einh.") }, modifier = Modifier.weight(0.7f))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}

@Composable
private fun MacroGroup(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun MacroNumber(value: Double, color: Color) {
    Text(value.round0(), color = color, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 20.dp))
}

@Composable
private fun Separator() {
    Text("|", color = MaterialTheme.colorScheme.outline)
}
