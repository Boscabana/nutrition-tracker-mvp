package com.nick.nutritiontracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nick.nutritiontracker.data.FoodEntryWithFood
import com.nick.nutritiontracker.data.FoodItemEntity
import com.nick.nutritiontracker.viewmodel.NutritionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionApp(vm: NutritionViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val foods by vm.foods.collectAsStateWithLifecycle()
    val entries by vm.todayEntries.collectAsStateWithLifecycle()

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Nutrition MVP") }) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Heute") }, icon = {})
                    NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Lebensmittel") }, icon = {})
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (tab == 0) TodayScreen(foods, entries, vm::addEntry, vm::deleteEntry) else FoodsScreen(foods, vm::addFood)
            }
        }
    }
}

@Composable
private fun TodayScreen(
    foods: List<FoodItemEntity>,
    entries: List<FoodEntryWithFood>,
    onAddEntry: (FoodItemEntity, Double, String, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val kcal = entries.sumOf { it.kcal }
    val protein = entries.sumOf { it.protein }
    val carbs = entries.sumOf { it.carbs }
    val fat = entries.sumOf { it.fat }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card { Column(Modifier.padding(16.dp)) {
                Text("Heute", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${kcal.round1()} kcal")
                Text("Protein ${protein.round1()} g · KH ${carbs.round1()} g · Fett ${fat.round1()} g")
            } }
        }
        item { AddEntryCard(foods, onAddEntry) }
        items(entries) { entry ->
            Card { Column(Modifier.padding(16.dp)) {
                Text(entry.name, fontWeight = FontWeight.Bold)
                Text("${entry.grams.round1()} g · ${entry.kcal.round1()} kcal")
                Text("P ${entry.protein.round1()} · KH ${entry.carbs.round1()} · F ${entry.fat.round1()}")
                TextButton(onClick = { onDelete(entry.entryId) }) { Text("Löschen") }
            } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEntryCard(foods: List<FoodItemEntity>, onAddEntry: (FoodItemEntity, Double, String, String) -> Unit) {
    var selected by remember(foods) { mutableStateOf(foods.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("g") }
    var mealSlot by remember { mutableStateOf("Snack") }

    Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Eintragen", fontWeight = FontWeight.Bold)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selected?.name ?: "Noch kein Lebensmittel",
                onValueChange = {}, readOnly = true, label = { Text("Lebensmittel") },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                foods.forEach { food -> DropdownMenuItem(text = { Text(food.name) }, onClick = { selected = food; expanded = false }) }
            }
        }
        OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Menge") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = unit == "g", onClick = { unit = "g" }, label = { Text("Gramm") })
            val portionLabel = selected?.defaultPortionName ?: "Stück"
            FilterChip(selected = unit == "portion", enabled = selected?.defaultPortionGrams != null, onClick = { unit = "portion" }, label = { Text(portionLabel) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Frühstück", "Mittag", "Abend", "Snack").forEach { slot -> FilterChip(selected = mealSlot == slot, onClick = { mealSlot = slot }, label = { Text(slot) }) }
        }
        Button(enabled = selected != null, onClick = { selected?.let { onAddEntry(it, amount.num(), unit, mealSlot) } }, modifier = Modifier.fillMaxWidth()) { Text("Hinzufügen") }
    } }
}

@Composable
private fun FoodsScreen(foods: List<FoodItemEntity>, onAddFood: (String, Double, Double, Double, Double, String?, Double?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var portionName by remember { mutableStateOf("Stück") }
    var portionGrams by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Lebensmittel anlegen", fontWeight = FontWeight.Bold)
            OutlinedTextField(name, { name = it }, label = { Text("Name, z.B. Ei M") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(kcal, { kcal = it }, label = { Text("kcal pro 100g") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(protein, { protein = it }, label = { Text("Protein pro 100g") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(carbs, { carbs = it }, label = { Text("KH pro 100g") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(fat, { fat = it }, label = { Text("Fett pro 100g") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(portionName, { portionName = it }, label = { Text("Portionsname, z.B. Stück/Riegel") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(portionGrams, { portionGrams = it }, label = { Text("Gramm pro Portion, z.B. 53") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = name.isNotBlank(), onClick = {
                onAddFood(name, kcal.num(), protein.num(), carbs.num(), fat.num(), portionName, portionGrams.num().takeIf { it > 0.0 }, null)
                name = ""; kcal = ""; protein = ""; carbs = ""; fat = ""; portionGrams = ""
            }, modifier = Modifier.fillMaxWidth()) { Text("Speichern") }
        } } }
        items(foods) { food -> Card { Column(Modifier.padding(16.dp)) {
            Text(food.name, fontWeight = FontWeight.Bold)
            Text("${food.kcalPer100g.round1()} kcal / 100g")
            Text("P ${food.proteinPer100g.round1()} · KH ${food.carbsPer100g.round1()} · F ${food.fatPer100g.round1()}")
            if (food.defaultPortionGrams != null) Text("1 ${food.defaultPortionName ?: "Portion"} = ${food.defaultPortionGrams.round1()} g")
        } } }
    }
}

private fun String.num(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.round1(): String = "%.1f".format(this)
