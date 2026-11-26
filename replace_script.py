import re
with open('app/src/main/java/com/nick/nutritiontracker/ui/MealsScreen.kt', 'r', encoding='utf-8') as f:
    text = f.read()

pattern = r'(Card\(\s*modifier = Modifier\.fillMaxWidth\(\)\.padding\(vertical = 4\.dp\),.*?)(?=\n@)'

def replace_func(m):
    return '''Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = when {
            isOrphaned -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
            isDivergent -> CardDefaults.cardColors(containerColor = Color(0xFFFFE082).copy(alpha = 0.2f))
            else -> CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        },
        border = if (isDivergent) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFC107).copy(alpha = 0.5f)) else null
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = ingredient.name,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isOrphaned -> Color.Red
                        isDivergent -> Color(0xFFFFA000)
                        else -> Color.Unspecified
                    }
                )

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

                Spacer(Modifier.height(8.dp))

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
                        isError = ingredient.foodItemId == 0L && ingredient.unitLabel !in listOf("g", "kg", "ml", "l", "Stück"),
                        modifier = Modifier.weight(0.3f)
                    )

                    Column(Modifier.weight(0.7f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Einheit", style = MaterialTheme.typography.labelSmall)
                            if (ingredient.foodItemId == 0L && ingredient.unitLabel !in listOf("g", "kg", "ml", "l", "Stück")) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Info, "Portion fehlt", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                Text(" KI-Portion checken!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            } else if (ingredient.foodItemId != 0L && selectedPortion == null && ingredient.unitLabel !in listOf("g", "kg", "ml", "l") && ingredient.unitLabel != food?.baseUnit) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.Info, "Portion fehlt", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(12.dp))
                                Text(" ${ingredient.unitLabel} ergänzen!", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            } else if (ingredient.foodItemId == 0L) {
                                Spacer(Modifier.width(4.dp))
                                Text(" (${ingredient.unitLabel})", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                            }
                        }
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

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                }

                if (isOrphaned || isDivergent) {
                    if (ingredient.foodItemId == 0L) {
                        Button(
                            onClick = { showSearchAssignDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Zuweisen", style = MaterialTheme.typography.labelMedium)
                        }
                        Button(
                            onClick = {
                                pendingCreateFoodItem = FoodItemEntity(
                                    name = ingredient.name,
                                    baseUnit = ingredient.unitLabel.takeIf { it in listOf("g", "ml", "kg", "l") } ?: "g",
                                    isGeneric = true
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        }
                    } else {
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isOrphaned) Icons.Default.CloudDownload else Icons.Default.Sync,
                                contentDescription = if (isOrphaned) "Zutat übernehmen" else "Zutat aktualisieren",
                                tint = if (isOrphaned) MaterialTheme.colorScheme.error else Color(0xFFFFA000),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                if (food != null && !isOrphaned && !isDivergent) {
                    IconButton(
                        onClick = { pendingCreateFoodItem = food },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Edit, "Artikel bearbeiten", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }

                if (relatives.isNotEmpty()) {
                    Box {
                        IconButton(
                            onClick = { showSwapMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isOrphaned) Icons.Default.FindReplace else Icons.Default.SwapHoriz,
                                contentDescription = "Zutat tauschen",
                                modifier = Modifier.size(20.dp),
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
        }
    }
}'''

new_text = re.sub(pattern, replace_func, text, flags=re.DOTALL)

with open('app/src/main/java/com/nick/nutritiontracker/ui/MealsScreen.kt', 'w', encoding='utf-8') as f:
    f.write(new_text)