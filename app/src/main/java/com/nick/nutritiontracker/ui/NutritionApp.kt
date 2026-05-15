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
import com.nick.nutritiontracker.data.FoodEntryEntity
import com.nick.nutritiontracker.data.FoodItemEntity
import com.nick.nutritiontracker.data.FoodPortionEntity
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ProteinGreen = Color(0xFF2E7D32)
private val CarbOrange = Color(0xFFFF9800)
private val SugarRed = Color(0xFFD32F2F)
private val SaturatedGrey = Color(0xFF757575)
private val UnsaturatedYellow = Color(0xFFFBC02D)
private val EditBlue = Color(0xFF2196F3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionApp(vm: NutritionViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val foods = vm.foods
    val entries = vm.todayEntries

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Nutrition Tracker") }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        label = { Text("Heute") },
                        icon = { Box(Modifier.size(24.dp)) }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        label = { Text("Lebensmittel") },
                        icon = { Box(Modifier.size(24.dp)) }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (tab == 0) {
                    TodayScreen(foods, entries, vm::addEntry, vm::deleteEntry, vm::updateEntry)
                } else {
                    FoodsScreen(foods, vm::addFood, vm::deleteFood, vm::updateFood)
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
        // Edit Layer (revealed on swipe right)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EditBlue, RoundedCornerShape(12.dp))
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

        // Delete Layer (revealed on swipe left)
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

        // Foreground Layer
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
    foods: List<FoodItemEntity>,
    entries: List<FoodEntryEntity>,
    onAddEntry: (FoodItemEntity, Double, FoodPortionEntity?, String) -> Unit,
    onDelete: (Long) -> Unit,
    onUpdate: (FoodEntryEntity) -> Unit
) {
    var entryToDelete by remember { mutableStateOf<Long?>(null) }
    var entryToEdit by remember { mutableStateOf<FoodEntryEntity?>(null) }

    val kcal = entries.sumOf { it.kcal }
    val protein = entries.sumOf { it.protein }
    val complexCarbs = entries.sumOf { it.complexCarbs }
    val sugar = entries.sumOf { it.sugar }
    val saturatedFat = entries.sumOf { it.saturatedFat }
    val unsaturatedFat = entries.sumOf { it.unsaturatedFat }

    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Eintrag löschen") },
            text = { Text("Möchtest du diesen Eintrag wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(entryToDelete!!)
                    entryToDelete = null
                }) { Text("Löschen", color = SugarRed) }
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
                onUpdate(updated)
                entryToEdit = null
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Heute", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${kcal.round0()} kcal")
                    MacroLegendRow()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MacroNumber(protein, ProteinGreen)
                        MacroGroup {
                            MacroNumber(complexCarbs, CarbOrange)
                            MacroNumber(sugar, SugarRed)
                        }
                        MacroGroup {
                            MacroNumber(saturatedFat, SaturatedGrey)
                            MacroNumber(unsaturatedFat, UnsaturatedYellow)
                        }
                    }
                    Text("Alle Makrowerte in g", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item { AddEntryCard(foods, onAddEntry) }
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
                        label = { Text("Gramm") }
                    )
                    food.portions.forEach { portion ->
                        FilterChip(
                            selected = selectedPortion == portion,
                            onClick = { selectedPortion = portion },
                            label = { Text("${portion.name} (${portion.grams.round0()}g)") }
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
                onSave(entry.copy(
                    amount = numAmount,
                    unitLabel = selectedPortion?.name ?: "g",
                    grams = grams,
                    mealSlot = mealSlot
                ))
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    value = selectedFood?.name ?: "Noch kein Lebensmittel",
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
                    label = { Text("Gramm") }
                )
                selectedFood?.portions?.forEach { portion ->
                    FilterChip(
                        selected = selectedPortion == portion,
                        onClick = { selectedPortion = portion },
                        label = { Text("${portion.name} (${portion.grams.round0()}g)") }
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
    onAddFood: (String, Double, Double, Double, Double, Double, Double, List<FoodPortionEntity>, String?) -> Unit,
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
                    newFood.saturatedFatPer100g, newFood.portions, newFood.barcode
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
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
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
                        Text("${food.kcalPer100g.round0()} kcal / 100g", style = MaterialTheme.typography.bodySmall)
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
                        food.portions.forEach { portion ->
                            Text("• ${portion.name}: ${portion.grams.round0()}g", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodEditDialog(
    food: FoodItemEntity?,
    onDismiss: () -> Unit,
    onSave: (FoodItemEntity) -> Unit
) {
    var name by remember { mutableStateOf(food?.name ?: "") }
    var kcal by remember { mutableStateOf(food?.kcalPer100g?.toString() ?: "") }
    var protein by remember { mutableStateOf(food?.proteinPer100g?.toString() ?: "") }
    var carbs by remember { mutableStateOf(food?.carbsPer100g?.toString() ?: "") }
    var sugar by remember { mutableStateOf(food?.sugarPer100g?.toString() ?: "") }
    var fat by remember { mutableStateOf(food?.fatPer100g?.toString() ?: "") }
    var saturatedFat by remember { mutableStateOf(food?.saturatedFatPer100g?.toString() ?: "") }
    var barcode by remember { mutableStateOf(food?.barcode ?: "") }
    
    val portions = remember { 
        mutableStateListOf<PortionInputState>().apply {
            food?.portions?.forEach { add(PortionInputState(it.name, it.grams.toString().replace(".0", ""))) }
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
                OutlinedTextField(kcal, { kcal = it }, label = { Text("kcal pro 100g") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(protein, { protein = it }, label = { Text("Protein pro 100g") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(carbs, { carbs = it }, label = { Text("KH pro 100g") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sugar, { sugar = it }, label = { Text("Zucker pro 100g") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(fat, { fat = it }, label = { Text("Fett pro 100g") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(saturatedFat, { saturatedFat = it }, label = { Text("ges. Fett pro 100g") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(barcode, { barcode = it }, label = { Text("Barcode") }, modifier = Modifier.fillMaxWidth())
                
                HorizontalDivider()
                Text("Portionen", fontWeight = FontWeight.Bold)
                
                portions.forEachIndexed { index, portionState ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = portionState.name,
                            onValueChange = { portionState.name = it },
                            label = { Text("Name") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = portionState.grams,
                            onValueChange = { portionState.grams = it },
                            label = { Text("g") },
                            modifier = Modifier.weight(0.6f)
                        )
                        IconButton(onClick = { portions.removeAt(index) }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red)
                        }
                    }
                }
                
                TextButton(onClick = { portions.add(PortionInputState("", "")) }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Portion hinzufügen")
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            onSave(FoodItemEntity(
                                id = food?.id ?: 0,
                                name = name,
                                kcalPer100g = kcal.num(),
                                proteinPer100g = protein.num(),
                                carbsPer100g = carbs.num(),
                                sugarPer100g = sugar.num(),
                                fatPer100g = fat.num(),
                                saturatedFatPer100g = saturatedFat.num(),
                                barcode = barcode.takeIf { it.isNotBlank() },
                                portions = portions.map { FoodPortionEntity(0, it.name, it.grams.num()) }
                            ))
                        }
                    ) { Text("Speichern") }
                }
            }
        }
    }
}

class PortionInputState(nameInitial: String, gramsInitial: String) {
    var name by mutableStateOf(nameInitial)
    var grams by mutableStateOf(gramsInitial)
}

private fun String.num(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.round0(): String = "%.0f".format(this)

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
