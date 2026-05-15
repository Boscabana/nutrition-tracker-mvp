package com.nick.nutritiontracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.FoodEntryEntity
import com.nick.nutritiontracker.data.FoodItemEntity
import com.nick.nutritiontracker.data.FoodPortionEntity
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import kotlinx.coroutines.launch

private val ProteinGreen = Color(0xFF2E7D32)
private val CarbOrange = Color(0xFFFF9800)
private val SugarRed = Color(0xFFD32F2F)
private val SaturatedGrey = Color(0xFF757575)
private val UnsaturatedYellow = Color(0xFFFBC02D)

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
                    TodayScreen(foods, entries, vm::addEntry, vm::deleteEntry)
                } else {
                    FoodsScreen(foods, vm::addFood, vm::deleteFood)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDeleteContainer(
    onDeleteConfirmed: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                showDialog = true
                true // Stay swiped to show the background trash area
            } else {
                false
            }
        }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { 
                showDialog = false
                scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
            },
            title = { Text("Eintrag löschen") },
            text = { Text("Möchtest du diesen Eintrag wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteConfirmed()
                    showDialog = false
                    scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
                }) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDialog = false
                    scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                Color.Red
            } else {
                Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                IconButton(onClick = { showDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Löschen",
                        tint = Color.White
                    )
                }
            }
        }
    ) {
        content()
    }
}

@Composable
private fun TodayScreen(
    foods: List<FoodItemEntity>,
    entries: List<FoodEntryEntity>,
    onAddEntry: (FoodItemEntity, Double, FoodPortionEntity?, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val kcal = entries.sumOf { it.kcal }
    val protein = entries.sumOf { it.protein }
    val complexCarbs = entries.sumOf { it.complexCarbs }
    val sugar = entries.sumOf { it.sugar }
    val saturatedFat = entries.sumOf { it.saturatedFat }
    val unsaturatedFat = entries.sumOf { it.unsaturatedFat }

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
            SwipeToDeleteContainer(onDeleteConfirmed = { onDelete(entry.id) }) {
                CompactEntryRow(entry)
            }
        }
    }
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
    onDeleteFood: (Long) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var sugar by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var saturatedFat by remember { mutableStateOf("") }
    var portionName by remember { mutableStateOf("Portion") }
    var portionGrams by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Lebensmittel anlegen", fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(kcal, { kcal = it }, label = { Text("kcal pro 100g") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(protein, { protein = it }, label = { Text("Protein pro 100g") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(carbs, { carbs = it }, label = { Text("KH pro 100g") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(sugar, { sugar = it }, label = { Text("Zucker pro 100g") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(fat, { fat = it }, label = { Text("Fett pro 100g") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(saturatedFat, { saturatedFat = it }, label = { Text("ges. Fett pro 100g") }, modifier = Modifier.fillMaxWidth())
                    
                    Text("Standard Portion", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(portionName, { portionName = it }, label = { Text("Name (z.B. Stück)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(portionGrams, { portionGrams = it }, label = { Text("Gramm") }, modifier = Modifier.weight(0.6f))
                    }
                    
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val portions = if (portionGrams.num() > 0) {
                                listOf(FoodPortionEntity(0, portionName, portionGrams.num()))
                            } else emptyList()
                            
                            onAddFood(
                                name, kcal.num(), protein.num(), carbs.num(), sugar.num(),
                                fat.num(), saturatedFat.num(), portions, null
                            )
                            name = ""; kcal = ""; protein = ""; carbs = ""; sugar = ""; fat = ""; saturatedFat = ""; portionGrams = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Speichern") }
                }
            }
        }
        items(foods, key = { it.id }) { food ->
            SwipeToDeleteContainer(onDeleteConfirmed = { onDeleteFood(food.id) }) {
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
