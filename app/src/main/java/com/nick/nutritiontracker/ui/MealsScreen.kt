package com.nick.nutritiontracker.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import com.nick.nutritiontracker.data.*
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import java.io.File

@Composable
fun MealsScreen(
    vm: NutritionViewModel,
    @Suppress("UNUSED_PARAMETER") snackbarHostState: SnackbarHostState,
    selectedMealIds: MutableState<Set<Long>>
) {
    val meals = vm.meals
    val foods = vm.foods
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    if (vm.startRecipeImport(content)) {
                        // Conflict resolution handled by NutritionApp dialog
                    } else {
                        Toast.makeText(context, "Fehler beim Importieren.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var mealToDelete by remember { mutableStateOf<Long?>(null) }
    var mealToEdit by remember { mutableStateOf<MealEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    
    val expandedStates = remember { mutableStateMapOf<Long, Boolean>() }
    val isSelectionMode = selectedMealIds.value.isNotEmpty()

    if (mealToDelete != null) {
        AlertDialog(
            onDismissRequest = { mealToDelete = null },
            title = { Text("Mahlzeit löschen") },
            text = { Text("Möchtest du diese Mahlzeit wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMealTemplate(mealToDelete!!)
                    mealToDelete = null
                }) { Text("Löschen", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { mealToDelete = null }) { Text("Abbrechen") }
            }
        )
    }

    if (showAddDialog || mealToEdit != null) {
        MealEditDialog(
            meal = mealToEdit,
            foods = foods,
            onDismiss = {
                showAddDialog = false
                mealToEdit = null
            },
            onSave = { name, ingredients, servings ->
                if (mealToEdit == null) {
                    vm.addMealTemplate(name, ingredients, servings)
                } else {
                    vm.updateMealTemplate(mealToEdit!!.copy(name = name, ingredients = ingredients, servings = servings))
                }
                showAddDialog = false
                mealToEdit = null
            }
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Neu")
                }
                Button(
                    onClick = { importLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }

        @OptIn(ExperimentalFoundationApi::class)
        items(meals, key = { it.id }) { meal ->
            val expanded = expandedStates[meal.id] ?: false
            val isSelected = selectedMealIds.value.contains(meal.id)
            val hasOrphanedIngredients = meal.ingredients.any { ing -> vm.foods.none { f -> f.id == ing.foodItemId } }

            SwipeActionContainer(
                onDeleteRequest = { mealToDelete = meal.id },
                onEditRequest = { mealToEdit = meal },
                enabled = !isSelectionMode
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { 
                                if (isSelectionMode) {
                                    selectedMealIds.value = if (isSelected) selectedMealIds.value - meal.id else selectedMealIds.value + meal.id
                                } else {
                                    expandedStates[meal.id] = !expanded 
                                }
                            },
                            onLongClick = {
                                selectedMealIds.value = selectedMealIds.value + meal.id
                            }
                        ),
                    colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                             else if (hasOrphanedIngredients) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) 
                             else CardDefaults.cardColors()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isSelectionMode) {
                                Checkbox(checked = isSelected, onCheckedChange = { 
                                    selectedMealIds.value = if (isSelected) selectedMealIds.value - meal.id else selectedMealIds.value + meal.id
                                })
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (hasOrphanedIngredients) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.Warning, "Fehlende Zutaten", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text("${meal.kcalPerServing.round0()} kcal pro Portion", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        
                        AnimatedVisibility(visible = expanded) {
                            Column(Modifier.padding(top = 8.dp)) {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                Text("Zutaten pro Portion:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                meal.ingredients.forEach { ingredient ->
                                    val amountPerServing = ingredient.amount / meal.servings
                                    val brandInfo = if (!ingredient.brand.isNullOrBlank()) " (${ingredient.brand}${if (!ingredient.store.isNullOrBlank()) " @ ${ingredient.store}" else ""})" else ""
                                    Text(
                                        "• ${ingredient.name}$brandInfo: ${amountPerServing.roundString()} ${ingredient.unitLabel}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (meal.servings != 1.0) {
                                    Text(
                                        "Gesamtportionen: ${meal.servings.roundString()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(top = 4.dp),
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                Text(
                                    "Gesamtgewicht: ${meal.totalWeight.round0()}g (${(meal.totalWeight / meal.servings).round0()}g pro Portion)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MealEditDialog(
    meal: MealEntity?,
    foods: List<FoodItemEntity>,
    onDismiss: () -> Unit,
    onSave: (String, List<MealIngredientEntity>, Double) -> Unit
) {
    var name by remember(meal?.id) { mutableStateOf(meal?.name ?: "") }
    var servings by remember(meal?.id) { mutableStateOf(meal?.servings?.roundString() ?: "1") }
    val ingredients = remember(meal?.id) { 
        mutableStateListOf<MealIngredientEntity>().apply { 
            meal?.ingredients?.let { addAll(it) } 
        } 
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var foodToConfigure by remember { mutableStateOf<FoodItemEntity?>(null) }

    val filteredFoods by remember(searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) emptyList()
            else foods.filter { it.name.contains(searchQuery, ignoreCase = true) }
                .sortedWith(compareByDescending<FoodItemEntity> { it.isGeneric }.thenBy { it.name })
                .take(10)
        }
    }

    if (foodToConfigure != null) {
        AddIngredientDialog(
            food = foodToConfigure!!,
            foods = foods,
            onDismiss = { foodToConfigure = null },
            onConfirm = { amount, portion ->
                ingredients.add(
                    MealIngredientEntity(
                        id = System.currentTimeMillis() + ingredients.size,
                        foodItemId = foodToConfigure!!.id,
                        name = foodToConfigure!!.name,
                        amount = amount,
                        unitLabel = portion?.name ?: foodToConfigure!!.baseUnit,
                        grams = if (portion != null) amount * portion.grams else amount,
                        kcalPer100g = foodToConfigure!!.kcalPer100g,
                        proteinPer100g = foodToConfigure!!.proteinPer100g,
                        carbsPer100g = foodToConfigure!!.carbsPer100g,
                        sugarPer100g = foodToConfigure!!.sugarPer100g,
                        fatPer100g = foodToConfigure!!.fatPer100g,
                        saturatedFatPer100g = foodToConfigure!!.saturatedFatPer100g,
                        alcoholPercent = foodToConfigure!!.alcoholPercent,
                        baseUnit = foodToConfigure!!.baseUnit,
                        store = foodToConfigure!!.store,
                        brand = foodToConfigure!!.brand
                    )
                )
                foodToConfigure = null
                searchQuery = ""
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    if (meal == null) "Mahlzeit erstellen" else "Mahlzeit bearbeiten",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(8.dp))
                
                AutoSelectTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name der Mahlzeit") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                
                AutoSelectTextField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = { Text("Portionen (Gesamt)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                )

                Text(
                    text = "Gesamtgewicht: ${ingredients.sumOf { it.grams }.round0()}g (${(ingredients.sumOf { it.grams } / (servings.num().coerceAtLeast(1.0))).round0()}g pro Portion)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(Modifier.height(16.dp))
                Text("Zutaten", fontWeight = FontWeight.Bold)
                
                // Ingredient Search
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
                        properties = PopupProperties(focusable = false),
                        offset = DpOffset(0.dp, (-320).dp) // Force upward
                    ) {
                        filteredFoods.forEach { food ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (food.isGeneric) {
                                            Icon(Icons.Default.Inventory2, null, modifier = Modifier.size(16.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Column {
                                            Text(food.name)
                                            if (!food.isGeneric && !food.brand.isNullOrBlank()) {
                                                Text(food.brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    foodToConfigure = food
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    items(ingredients, key = { it.id }) { ingredient ->
                        IngredientRow(
                            ingredient = ingredient,
                            foods = foods,
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
                    Button(
                        enabled = name.isNotBlank() && ingredients.isNotEmpty(),
                        onClick = { onSave(name, ingredients.toList(), servings.replace(',', '.').toDoubleOrNull() ?: 1.0) }
                    ) { Text("Speichern") }
                }
            }
        }
    }
}

@Composable
fun AddIngredientDialog(
    food: FoodItemEntity,
    foods: List<FoodItemEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Double, FoodPortionEntity?) -> Unit
) {
    val parent = food.parentId?.let { pId -> foods.find { it.id == pId } }
    val allPortions = food.getAllPortions(parent)
    val firstPortion = allPortions.firstOrNull()
    var amount by remember { mutableStateOf(if (firstPortion != null) "1" else "100") }
    var selectedPortion by remember { mutableStateOf<FoodPortionEntity?>(firstPortion) }

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
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Einheit", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPortion == null,
                        onClick = { selectedPortion = null },
                        label = { Text(food.baseUnit) }
                    )
                    allPortions.forEach { portion ->
                        FilterChip(
                            selected = selectedPortion == portion,
                            onClick = { selectedPortion = portion },
                            label = { Text("${portion.name} (${portion.grams.round0()}${food.baseUnit})") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(amount.num(), selectedPortion)
            }) { Text("Hinzufügen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
fun IngredientRow(
    ingredient: MealIngredientEntity,
    foods: List<FoodItemEntity>,
    onUpdate: (MealIngredientEntity) -> Unit,
    onRemove: () -> Unit
) {
    val food = foods.find { it.id == ingredient.foodItemId }
    val parent = food?.parentId?.let { pId -> foods.find { it.id == pId } }
    val allPortions = food?.getAllPortions(parent) ?: emptyList()
    
    var amountText by remember { mutableStateOf(ingredient.amount.roundString()) }
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
                            color = if (isOrphaned) Color.Red else Color.Unspecified
                        )
                        if (isOrphaned) {
                            Icon(Icons.Default.Error, null, tint = Color.Red, modifier = Modifier.size(14.dp).padding(start = 4.dp))
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
                                                        Icon(Icons.Default.Label, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
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
                        Text("ACHTUNG: Dieser Artikel existiert nicht mehr!", style = MaterialTheme.typography.labelSmall, color = Color.Red)
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
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, null, tint = Color.Red) }
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
                    Text("Einheit", style = MaterialTheme.typography.labelSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedPortion == null,
                            onClick = { 
                                val amt = amountText.num()
                                onUpdate(ingredient.copy(amount = amt, unitLabel = food?.baseUnit ?: "g", grams = amt))
                            },
                            label = { Text(food?.baseUnit ?: "g") }
                        )
                        allPortions.forEach { portion ->
                            FilterChip(
                                selected = selectedPortion == portion,
                                onClick = {
                                    val amt = amountText.num()
                                    onUpdate(ingredient.copy(amount = amt, unitLabel = portion.name, grams = amt * portion.grams))
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

private fun String.num(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.round0(): String = "%.0f".format(this)
private fun Double.roundString(): String = toString().replace(".0", "")
