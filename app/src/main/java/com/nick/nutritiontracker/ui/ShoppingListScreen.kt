package com.nick.nutritiontracker.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.FoodItemEntity
import com.nick.nutritiontracker.data.ShoppingItem
import com.nick.nutritiontracker.data.UserProfile
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ParsedShoppingInput(
    val rawText: String,
    val amount: Double,
    val unit: String,
    val name: String,
    val hasParsedQuantity: Boolean
)

private val KNOWN_UNITS = setOf(
    "g", "gramm", "kg", "kilo", "kilogramm",
    "ml", "milliliter", "l", "liter",
    "x", "stk", "stk.", "stück", "stueck",
    "pck", "pck.", "packung", "pkg", "pkg.", "pckg",
    "el", "tl", "dose", "dosen", "flasche", "flaschen",
    "becher", "glas", "gläser", "prise", "prisen",
    "zehe", "zehen", "portion", "portionen", "beutel"
)

fun parseShoppingInput(input: String): ParsedShoppingInput {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) {
        return ParsedShoppingInput(input, 0.0, "g", "", false)
    }

    // Pattern 1: Number + unit prefix + optional remainder (e.g. "100g Haferflocken", "250ml Milch", "3x Äpfel", "4Pck Nudeln")
    val prefixRegex = Regex("""^(\d+(?:[.,]\d+)?)\s*([a-zA-ZäöüÄÖÜ.]+)\b\s*(.*)$""", RegexOption.IGNORE_CASE)
    val match1 = prefixRegex.matchEntire(trimmed)
    if (match1 != null) {
        val rawNum = match1.groupValues[1].replace(',', '.')
        val num = rawNum.toDoubleOrNull() ?: 0.0
        val rawUnit = match1.groupValues[2]
        val remainder = match1.groupValues[3].trim()

        if (rawUnit.lowercase() in KNOWN_UNITS) {
            val normalizedUnit = normalizeUnit(rawUnit)
            return ParsedShoppingInput(
                rawText = input,
                amount = num,
                unit = normalizedUnit,
                name = remainder,
                hasParsedQuantity = true
            )
        } else if (remainder.isNotBlank()) {
            return ParsedShoppingInput(
                rawText = input,
                amount = num,
                unit = "Stück",
                name = "$rawUnit $remainder".trim(),
                hasParsedQuantity = true
            )
        } else {
            return ParsedShoppingInput(
                rawText = input,
                amount = num,
                unit = "Stück",
                name = rawUnit.trim(),
                hasParsedQuantity = true
            )
        }
    }

    // Pattern 2: Number + space + name (e.g. "3 Bananen")
    val numOnlyRegex = Regex("""^(\d+(?:[.,]\d+)?)\s+(.+)$""")
    val match2 = numOnlyRegex.matchEntire(trimmed)
    if (match2 != null) {
        val rawNum = match2.groupValues[1].replace(',', '.')
        val num = rawNum.toDoubleOrNull() ?: 0.0
        val name = match2.groupValues[2].trim()

        return ParsedShoppingInput(
            rawText = input,
            amount = num,
            unit = "Stück",
            name = name,
            hasParsedQuantity = true
        )
    }

    // Fallback: Name only
    return ParsedShoppingInput(
        rawText = input,
        amount = 0.0,
        unit = "g",
        name = trimmed,
        hasParsedQuantity = false
    )
}

private fun normalizeUnit(raw: String): String {
    return when (raw.lowercase().removeSuffix(".")) {
        "g", "gramm" -> "g"
        "kg", "kilo", "kilogramm" -> "kg"
        "ml", "milliliter" -> "ml"
        "l", "liter" -> "l"
        "x", "stk", "stück", "stueck" -> "Stück"
        "pck", "packung", "pkg", "pckg" -> "Stück"
        "el" -> "EL"
        "tl" -> "TL"
        "dose", "dosen" -> "Dose"
        "flasche", "flaschen" -> "Flasche"
        "becher" -> "Becher"
        "glas", "gläser" -> "Glas"
        "beutel" -> "Beutel"
        "prise", "prisen" -> "Prise"
        "zehe", "zehen" -> "Zehe"
        "portion", "portionen" -> "Portion"
        else -> raw
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingListScreen(vm: NutritionViewModel, userProfile: UserProfile) {
    val rawShoppingList = vm.shoppingList
    val household by vm.firebaseManager.household.collectAsState()
    
    var showQuickInputBar by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ShoppingItem?>(null) }
    var isArchiveExpanded by remember { mutableStateOf(false) }

    val activeItems by remember(rawShoppingList, vm.showPantryInShoppingList) { 
        derivedStateOf { 
            rawShoppingList.filter { 
                !it.isChecked && (vm.showPantryInShoppingList || !it.isPantryItem)
            } 
        } 
    }
    val checkedItems by remember(rawShoppingList) { 
        derivedStateOf { rawShoppingList.filter { it.isChecked } } 
    }

    val categoryOrder = listOf(
        "Obst", "Gemüse", "Backwaren", "Kühlregal", "Fleisch", 
        "Milchprodukte", "Protein", "Teigwaren", "Convenience", 
        "Fertiggerichte", "Tiefkühlprodukte", "Süßigkeiten", "Getränke"
    )

    val aggregatedActiveList by remember(activeItems, vm.isShoppingListAggregated) {
        derivedStateOf {
            if (vm.isShoppingListAggregated) {
                activeItems.groupBy { it.name.lowercase().trim() to it.baseUnit }
                    .flatMap { (_, items) ->
                        val weightItems = items.filter { it.weightGrams > 0 }
                        val otherItems = items.filter { it.weightGrams <= 0 }
                        
                        val results = mutableListOf<ShoppingItem>()
                        
                        if (weightItems.isNotEmpty()) {
                            val totalWeight = weightItems.sumOf { it.weightGrams }
                            val first = weightItems.first()
                            results.add(
                                ShoppingItem(
                                    id = first.id,
                                    name = first.name,
                                    amount = totalWeight,
                                    unit = first.baseUnit,
                                    isChecked = false,
                                    isAutoGenerated = weightItems.any { it.isAutoGenerated },
                                    householdId = first.householdId,
                                    sourceName = if (weightItems.size > 1) "Aus ${weightItems.size} Quellen" else first.sourceName,
                                    weightGrams = totalWeight,
                                    baseUnit = first.baseUnit,
                                    category = first.category,
                                    isPantryItem = weightItems.any { it.isPantryItem }
                                )
                            )
                        }
                        
                        if (otherItems.isNotEmpty()) {
                            otherItems.groupBy { it.unit.lowercase().trim() }
                                .forEach { (_, group) ->
                                    val first = group.first()
                                    results.add(
                                        ShoppingItem(
                                            id = first.id,
                                            name = first.name,
                                            amount = group.sumOf { it.amount },
                                            unit = first.unit,
                                            isChecked = false,
                                            isAutoGenerated = group.any { it.isAutoGenerated },
                                            householdId = first.householdId,
                                            sourceName = if (group.size > 1) "Aus ${group.size} Quellen" else first.sourceName,
                                            weightGrams = 0.0,
                                            baseUnit = first.baseUnit,
                                            category = first.category,
                                            isPantryItem = group.any { it.isPantryItem }
                                        )
                                    )
                                }
                        }
                        results
                    }
            } else {
                activeItems
            }
        }
    }

    val groupedActiveItems by remember(aggregatedActiveList, vm.isShoppingListAggregated, vm.shoppingListSortByCategory) {
        derivedStateOf {
            val result = when {
                vm.shoppingListSortByCategory -> {
                    aggregatedActiveList.groupBy { 
                        val cat = it.category
                        if (cat.isNullOrBlank()) "Sonstiges" else cat 
                    }
                    .toList()
                    .sortedBy { (cat, _) -> 
                        val idx = categoryOrder.indexOf(cat)
                        if (idx == -1) categoryOrder.size else idx
                    }.toMap()
                }
                else -> {
                    aggregatedActiveList.groupBy { it.sourceName ?: "Manuell hinzugefügt" }
                }
            }
            
            result.mapKeys { (key, _) -> formatSourceName(key) }
        }
    }

    if (household == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Bitte erstelle einen Haushalt im Profil, um die Einkaufsliste zu nutzen.")
        }
        return
    }

    Scaffold(
        floatingActionButton = {
            if (!showQuickInputBar) {
                FloatingActionButton(onClick = { showQuickInputBar = true }) {
                    Icon(Icons.Default.Add, "Artikel hinzufügen")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
            ) {
                if (!household?.name.isNullOrBlank()) {
                    Text(
                        text = "Haushalt: ${household!!.name}", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = if (showQuickInputBar) 120.dp else 80.dp)
                ) {
                    groupedActiveItems.forEach { (header, items) ->
                        item(key = "header_$header", span = { GridItemSpan(2) }) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(items, key = { it.id }) { item ->
                            ShoppingItemTile(
                                item = item,
                                onToggle = { vm.toggleShoppingItem(item) },
                                onEdit = { itemToEdit = item }
                            )
                        }
                    }

                    if (checkedItems.isNotEmpty()) {
                        item(key = "archive_header", span = { GridItemSpan(2) }) {
                            Column {
                                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                                Surface(
                                    onClick = { isArchiveExpanded = !isArchiveExpanded },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Transparent
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            "Zuletzt verwendet (${checkedItems.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        Icon(
                                            imageVector = if (isArchiveExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        if (isArchiveExpanded) {
                            items(checkedItems, key = { "checked_${it.id}" }, span = { GridItemSpan(2) }) { item ->
                                ShoppingItemRow(
                                    item = item,
                                    onToggle = { vm.toggleShoppingItem(item) },
                                    onEdit = { itemToEdit = item },
                                    onDelete = { vm.deleteShoppingItem(item.id) }
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showQuickInputBar,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                QuickAddShoppingItemBar(
                    vm = vm,
                    isPremium = userProfile.isPremium,
                    onClose = { showQuickInputBar = false },
                    onAddItem = { name, amount, unit, category ->
                        vm.addShoppingItem(name, amount, unit, category, userProfile.isPremium)
                    }
                )
            }
        }
    }

    itemToEdit?.let { item ->
        EditShoppingItemDialog(
            item = item,
            vm = vm,
            onDismiss = { itemToEdit = null },
            onConfirm = { updated ->
                vm.updateShoppingItem(updated)
                itemToEdit = null
            }
        )
    }
}

@Composable
fun QuickAddShoppingItemBar(
    vm: NutritionViewModel,
    isPremium: Boolean,
    onClose: () -> Unit,
    onAddItem: (String, Double, String, String?) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    val parsedInput = remember(inputText) { parseShoppingInput(inputText) }

    val suggestions by remember(parsedInput.name, vm.foods) {
        derivedStateOf {
            val query = parsedInput.name.trim().lowercase()
            if (query.isEmpty()) emptyList()
            else {
                vm.foods
                    .filter { food -> food.name.lowercase().contains(query) }
                    .sortedWith(compareByDescending<FoodItemEntity> { it.isGeneric }.thenBy { it.name.length })
                    .take(6)
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun submitCurrentInput(foodMatch: FoodItemEntity? = null) {
        val itemName = foodMatch?.name ?: parsedInput.name.ifBlank { inputText.trim() }
        if (itemName.isNotBlank()) {
            val matchedFood = foodMatch ?: vm.foods.find { it.name.equals(itemName, ignoreCase = true) }
            val amount = parsedInput.amount
            val unit = if (parsedInput.hasParsedQuantity) parsedInput.unit else (matchedFood?.baseUnit ?: "g")
            val initialCategory = matchedFood?.category

            if (initialCategory != null) {
                onAddItem(itemName, amount, unit, initialCategory)
                inputText = ""
            } else {
                coroutineScope.launch {
                    val suggestedCat = if (isPremium && itemName.length > 2) {
                        vm.suggestCategory(itemName, isPremium)
                    } else null
                    onAddItem(itemName, amount, unit, suggestedCat)
                }
                inputText = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Dropup suggestions list from Artikelliste (vm.foods)
        if (suggestions.isNotEmpty() && parsedInput.name.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(suggestions) { food ->
                        ListItem(
                            leadingContent = {
                                Icon(
                                    imageVector = if (food.isGeneric) Icons.Default.Inventory2 else Icons.AutoMirrored.Filled.Label,
                                    contentDescription = if (food.isGeneric) "Basisartikel" else "Spezifischer Artikel",
                                    tint = if (food.isGeneric) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            headlineContent = {
                                Text(food.name, fontWeight = FontWeight.SemiBold)
                            },
                            supportingContent = {
                                val cat = food.category ?: "Sonstiges"
                                val label = if (food.isGeneric) {
                                    "Basisartikel • $cat"
                                } else {
                                    if (!food.brand.isNullOrBlank()) "$cat • ${food.brand}" else cat
                                }
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            },
                            modifier = Modifier.clickable {
                                submitCurrentInput(foodMatch = food)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        // Quick Input Bar
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { 
                        Text(
                            "z.B. 100g Haferflocken, 250ml Milch, 3x Äpfel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        ) 
                    },
                    singleLine = true,
                    leadingIcon = if (parsedInput.hasParsedQuantity && parsedInput.amount > 0) {
                        {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "${parsedInput.amount.roundString()} ${parsedInput.unit}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else null,
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inputText.isNotBlank()) {
                                IconButton(onClick = { submitCurrentInput() }) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = "Hinzufügen",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(onClick = onClose) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Schließen",
                                    tint = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitCurrentInput() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingItemTile(item: ShoppingItem, onToggle: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .combinedClickable(
                onClick = onToggle,
                onLongClick = onEdit
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name, 
                    fontWeight = FontWeight.Bold, 
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (item.isPantryItem) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Kitchen, 
                        contentDescription = "Vorrat", 
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            if (item.amount > 0) {
                Text(
                    text = "${item.amount.roundString()} ${item.unit}", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShoppingItemRow(item: ShoppingItem, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onToggle,
            onLongClick = onEdit
        ),
        colors = if (item.isChecked) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else CardDefaults.cardColors()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.isChecked, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name, 
                        fontWeight = FontWeight.Bold, 
                        style = if (item.isChecked) MaterialTheme.typography.bodyMedium.copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough) else MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isPantryItem) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Kitchen, 
                            contentDescription = "Vorrat", 
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                if (item.amount > 0) {
                    Text("${item.amount.roundString()} ${item.unit}", style = MaterialTheme.typography.labelSmall)
                }
                if (item.isAutoGenerated) {
                    Text("Auto-generiert aus Planer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Löschen", tint = Color.Red)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditShoppingItemDialog(
    item: ShoppingItem,
    vm: NutritionViewModel,
    onDismiss: () -> Unit,
    onConfirm: (ShoppingItem) -> Unit
) {
    var name by remember { mutableStateOf(item.name) }
    var amount by remember { mutableStateOf(if (item.amount > 0) item.amount.roundString() else "") }
    var unit by remember { mutableStateOf(item.unit) }
    var category by remember { mutableStateOf(item.category) }

    val matchedFood = remember(name) {
        vm.foods.find { it.name.equals(name.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Artikel bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Menge") }, modifier = Modifier.fillMaxWidth())

                Text("Einheit", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val defaultUnits = listOf("g", "ml", "Stück")
                    val portions = matchedFood?.portions?.map { it.name } ?: emptyList()
                    val packages = matchedFood?.packages?.map { it.name } ?: emptyList()
                    val allUnits = (defaultUnits + portions + packages).distinct()
                    
                    allUnits.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { 
                                unit = u
                                if ((amount.isEmpty() || amount == "0" || amount == "0.0") && u != "g" && u != "ml") {
                                    amount = "1"
                                }
                            },
                            label = { Text(u) }
                        )
                    }
                }

                CategoryDropdown(
                    selectedCategory = category,
                    categories = vm.categories,
                    onCategorySelected = { category = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { 
                onConfirm(item.copy(
                    name = name,
                    amount = amount.toDoubleOrNull() ?: 0.0,
                    unit = unit,
                    category = category
                ))
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    selectedCategory: String?,
    categories: List<String>,
    onCategorySelected: (String) -> Unit,
    isSuggesting: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedCategory ?: "",
            onValueChange = {},
            readOnly = true,
            label = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kategorie")
                    if (isSuggesting) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                        Text("Suchen...", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat) },
                    onClick = {
                        onCategorySelected(cat)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Sonstiges") },
                onClick = {
                    onCategorySelected("Sonstiges")
                    expanded = false
                }
            )
        }
    }
}

private fun formatSourceName(rawSource: String?): String {
    if (rawSource == null || !rawSource.contains(" @ ")) return rawSource ?: "Manuell hinzugefügt"
    return try {
        val parts = rawSource.split(" @ ")
        val name = parts[0]
        val date = LocalDate.parse(parts[1])
        val dayLabel = when (date) {
            LocalDate.now() -> "Heute"
            LocalDate.now().plusDays(1) -> "Morgen"
            else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.GERMAN)
        }
        val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM."))
        "$name ($dayLabel, $dateStr)"
    } catch (e: Exception) {
        rawSource
    }
}

private fun Double.roundString(): String = if (this % 1.0 == 0.0) "%.0f".format(this) else "%.1f".format(this)
