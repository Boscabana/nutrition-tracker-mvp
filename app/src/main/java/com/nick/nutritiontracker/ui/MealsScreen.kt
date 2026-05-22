package com.nick.nutritiontracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import com.nick.nutritiontracker.data.*
import com.nick.nutritiontracker.viewmodel.NutritionViewModel

@Composable
fun MealsScreen(
    vm: NutritionViewModel,
    @Suppress("UNUSED_PARAMETER") snackbarHostState: SnackbarHostState
) {
    val meals = vm.meals
    val foods = vm.foods
    var mealToDelete by remember { mutableStateOf<Long?>(null) }
    var mealToEdit by remember { mutableStateOf<MealEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

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
            onSave = { name, ingredients ->
                if (mealToEdit == null) {
                    vm.addMealTemplate(name, ingredients)
                } else {
                    vm.updateMealTemplate(mealToEdit!!.copy(name = name, ingredients = ingredients))
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
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Neue Mahlzeit erstellen")
            }
        }

        items(meals, key = { it.id }) { meal ->
            SwipeActionContainer(
                onDeleteRequest = { mealToDelete = meal.id },
                onEditRequest = { mealToEdit = meal }
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${meal.totalKcal.round0()} kcal", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        meal.ingredients.forEach { ingredient ->
                            Text(
                                "• ${ingredient.name}: ${ingredient.amount.roundString()} ${ingredient.unitLabel}",
                                style = MaterialTheme.typography.bodySmall
                            )
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
    onSave: (String, List<MealIngredientEntity>) -> Unit
) {
    var name by remember { mutableStateOf(meal?.name ?: "") }
    val ingredients = remember { mutableStateListOf<MealIngredientEntity>().apply { 
        meal?.ingredients?.let { addAll(it) } 
    } }
    
    var searchQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var foodToConfigure by remember { mutableStateOf<FoodItemEntity?>(null) }

    val filteredFoods = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else foods.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(5)
    }

    if (foodToConfigure != null) {
        AddIngredientDialog(
            food = foodToConfigure!!,
            onDismiss = { foodToConfigure = null },
            onConfirm = { amount, portion ->
                ingredients.add(
                    MealIngredientEntity(
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
                        baseUnit = foodToConfigure!!.baseUnit
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
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name der Mahlzeit") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Text("Zutaten", fontWeight = FontWeight.Bold)
                
                // Ingredient Search
                Box {
                    OutlinedTextField(
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
                                    foodToConfigure = food
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    items(ingredients) { ingredient ->
                        IngredientRow(
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
                    Button(
                        enabled = name.isNotBlank() && ingredients.isNotEmpty(),
                        onClick = { onSave(name, ingredients.toList()) }
                    ) { Text("Speichern") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddIngredientDialog(
    food: FoodItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double, FoodPortionEntity?) -> Unit
) {
    var amount by remember { mutableStateOf("100") }
    var selectedPortion by remember { mutableStateOf<FoodPortionEntity?>(null) }

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IngredientRow(
    ingredient: MealIngredientEntity,
    foods: List<FoodItemEntity>,
    onUpdate: (MealIngredientEntity) -> Unit,
    onRemove: () -> Unit
) {
    val food = foods.find { it.id == ingredient.foodItemId }
    var amountText by remember { mutableStateOf(ingredient.amount.roundString()) }
    val selectedPortion = food?.portions?.find { it.name == ingredient.unitLabel }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ingredient.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                        food?.portions?.forEach { portion ->
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

@Composable
fun AutoSelectTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        singleLine = true
    )
}

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
