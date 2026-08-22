package com.nick.nutritiontracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.nick.nutritiontracker.data.*
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.PlanMatchStatus
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun MealsScreen(
    vm: NutritionViewModel,
    @Suppress("UNUSED_PARAMETER") snackbarHostState: SnackbarHostState,
    selectedMealIds: MutableState<Set<Long>>,
    userProfile: UserProfile
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
    
    var selectedFilterTags by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(vm.aiErrorMessage) {
        vm.aiErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.aiErrorMessage = null
        }
    }
    val mealCategories = listOf("Frühstück", "Mittagessen", "Abendessen", "Snack", "Vegan", "Vegetarisch", "Rind", "Geflügel", "Fisch", "Pasta", "Salat", "Dessert", "Hauptgericht", "Schnell")

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
            vm = vm,
            onDismiss = {
                showAddDialog = false
                mealToEdit = null
            },
            onSave = { name, ingredients, servings, tags, imageUrl ->
                if (mealToEdit == null) {
                    vm.addMealTemplate(name, ingredients, servings, tags, imageUrl)
                } else {
                    vm.updateMealTemplate(mealToEdit!!.copy(name = name, ingredients = ingredients, servings = servings, tags = tags, imageUrl = imageUrl))
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                
                // Tag Filtering
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilterTags.isEmpty(),
                        onClick = { selectedFilterTags = emptySet() },
                        label = { Text("Alle") }
                    )
                    mealCategories.forEach { tag ->
                        FilterChip(
                            selected = tag in selectedFilterTags,
                            onClick = { 
                                selectedFilterTags = if (tag in selectedFilterTags) selectedFilterTags - tag else selectedFilterTags + tag
                            },
                            label = { Text(tag) }
                        )
                    }
                }
            }
        }

        @OptIn(ExperimentalFoundationApi::class)
        items(
            meals.filter { meal -> selectedFilterTags.isEmpty() || selectedFilterTags.all { it in meal.tags } },
            key = { it.id }
        ) { meal ->
            val expanded = expandedStates[meal.id] ?: false
            val isSelected = selectedMealIds.value.contains(meal.id)
            val hasOrphanedIngredients = meal.ingredients.any { ing -> vm.foods.none { f -> f.id == ing.foodItemId } }

            SwipeActionContainer(
                onDeleteRequest = { mealToDelete = meal.id },
                onEditRequest = { mealToEdit = meal },
                key = meal.id,
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
                            if (meal.imageUrl != null) {
                                AsyncImage(
                                    model = meal.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    if (hasOrphanedIngredients) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Default.Warning, "Fehlende Zutaten", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    meal.tags.forEach { tag ->
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.padding(top = 2.dp)
                                        ) {
                                            Text(
                                                text = tag,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${(meal.totalWeight / meal.servings).round0()}g (1 Portion)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            CompactMacroRow(
                                kcal = meal.kcalPerServing,
                                protein = meal.totalProtein / meal.servings,
                                complexCarbs = meal.totalComplexCarbs / meal.servings,
                                sugar = meal.totalSugar / meal.servings,
                                unsaturatedFat = meal.totalUnsaturatedFat / meal.servings,
                                saturatedFat = meal.totalSaturatedFat / meal.servings
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        
                        AnimatedVisibility(visible = expanded) {
                            Column(Modifier.padding(top = 8.dp)) {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Zutaten pro Portion:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    CompactMacroRow(
                                        kcal = meal.kcalPerServing,
                                        protein = meal.totalProtein / meal.servings,
                                        complexCarbs = meal.totalComplexCarbs / meal.servings,
                                        sugar = meal.totalSugar / meal.servings,
                                        unsaturatedFat = meal.totalUnsaturatedFat / meal.servings,
                                        saturatedFat = meal.totalSaturatedFat / meal.servings
                                    )
                                }
                                
                                meal.ingredients.forEach { ingredient ->
                                    val amountPerServing = ingredient.amount / meal.servings
                                    val gramsPerServing = ingredient.grams / meal.servings
                                    val ratio = if (meal.servings > 0) 1.0 / meal.servings else 1.0
                                    
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• ${ingredient.displayAmountFor(amountPerServing, gramsPerServing)} ${ingredient.name}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1
                                        )
                                        CompactMacroRow(
                                            kcal = ingredient.kcal * ratio,
                                            protein = ingredient.protein * ratio,
                                            complexCarbs = ingredient.complexCarbs * ratio,
                                            sugar = ingredient.sugar * ratio,
                                            unsaturatedFat = ingredient.unsaturatedFat * ratio,
                                            saturatedFat = ingredient.saturatedFat * ratio
                                        )
                                    }
                                }
                                if (meal.servings != 1.0) {
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Gesamt (${meal.servings.roundString()} Portionen):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        CompactMacroRow(
                                            kcal = meal.totalKcal,
                                            protein = meal.totalProtein,
                                            complexCarbs = meal.totalComplexCarbs,
                                            sugar = meal.totalSugar,
                                            unsaturatedFat = meal.totalUnsaturatedFat,
                                            saturatedFat = meal.totalSaturatedFat
                                        )
                                    }
                                    Text(
                                        "Gesamtgewicht: ${meal.totalWeight.round0()}g",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    Text(
                                        "Gesamtgewicht: ${meal.totalWeight.round0()}g",
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MealEditDialog(
    meal: MealEntity?,
    vm: NutritionViewModel,
    onDismiss: () -> Unit,
    onSave: (String, List<MealIngredientEntity>, Double, List<String>, String?) -> Unit
) {
    val foods = vm.foods
    var name by remember(meal?.id) { mutableStateOf(meal?.name ?: "") }
    var servings by remember(meal?.id) { mutableStateOf(meal?.servings?.roundString() ?: "1") }
    val tags = remember(meal?.id) { mutableStateListOf<String>().apply { meal?.tags?.let { addAll(it) } } }
    var newTagText by remember { mutableStateOf("") }
    var imageUrl by remember(meal?.id) { mutableStateOf(meal?.imageUrl) }
    
    val standardTags = listOf("Frühstück", "Mittagessen", "Abendessen", "Snack", "Vegan", "Vegetarisch", "Rind", "Geflügel", "Fisch", "Pasta", "Salat", "Dessert", "Hauptgericht", "Schnell")

    val context = LocalContext.current
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var tempImageUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { 
            val path = vm.saveImageLocally(it)
            if (path != null) imageUrl = path
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && tempImageUri != null) {
                val path = vm.saveImageLocally(tempImageUri!!)
                if (path != null) imageUrl = path
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                tempImageUri?.let { cameraLauncher.launch(it) }
            } else {
                Toast.makeText(context, "Kamera-Berechtigung erforderlich", Toast.LENGTH_SHORT).show()
            }
        }
    )

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
                            val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            
                            try {
                                val file = File(context.cacheDir, "temp_meal_edit_image.jpg")
                                if (file.exists()) file.delete()
                                file.createNewFile()
                                
                                val uri = FileProvider.getUriForFile(
                                    context, 
                                    "${context.packageName}.fileprovider", 
                                    file
                                )
                                tempImageUri = uri
                                
                                if (hasPermission) {
                                    cameraLauncher.launch(uri)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            } catch (_: Exception) {
                                Toast.makeText(context, "Kamera konnte nicht gestartet werden", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Galerie") },
                        leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                        modifier = Modifier.clickable {
                            showImageSourceDialog = false
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                }
            },
            confirmButton = {}
        )
    }
    
    val ingredients = remember(meal?.id) { 
        mutableStateListOf<MealIngredientEntity>().apply { 
            meal?.ingredients?.let { addAll(it) } 
        } 
    }

    // Automatischer Abgleich mit der Bibliothek beim Öffnen
    LaunchedEffect(meal?.id) {
        var changed = false
        val synced = ingredients.map { ing ->
            val food = foods.find { it.id == ing.foodItemId } ?: foods.find { it.isSimilarTo(FoodEntryEntity(name = ing.name, brand = ing.brand, barcode = ing.barcode)) }
            if (food != null && !food.matchesIngredient(ing)) {
                changed = true
                ing.copy(
                    foodItemId = food.id,
                    name = food.name,
                    brand = food.brand,
                    kcalPer100g = food.kcalPer100g,
                    proteinPer100g = food.proteinPer100g,
                    carbsPer100g = food.carbsPer100g,
                    sugarPer100g = food.sugarPer100g,
                    fatPer100g = food.fatPer100g,
                    saturatedFatPer100g = food.saturatedFatPer100g,
                    alcoholPercent = food.alcoholPercent,
                    baseUnit = food.baseUnit,
                    store = food.store,
                    category = food.category,
                    barcode = food.barcode,
                    isGeneric = food.isGeneric
                )
            } else ing
        }
        if (changed) {
            ingredients.clear()
            ingredients.addAll(synced)
        }
    }
    
    var searchQuery by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var foodToConfigure by remember { mutableStateOf<FoodItemEntity?>(null) }
    var tagsExpanded by remember { mutableStateOf(false) }

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
                        brand = foodToConfigure!!.brand,
                        category = foodToConfigure!!.category,
                        barcode = foodToConfigure!!.barcode,
                        isGeneric = foodToConfigure!!.isGeneric
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
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = when {
                            meal == null -> "Mahlzeit erstellen"
                            meal.id == 0L -> "Mahlzeit aus Auswahl erstellen"
                            else -> "Mahlzeit bearbeiten"
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                item {
                    val hasOrphans = ingredients.any { ing -> foods.none { it.id == ing.foodItemId } }
                    val hasDivergent = ingredients.any { ing -> 
                        val food = foods.find { it.id == ing.foodItemId }
                        food != null && !food.matchesIngredient(ing)
                    }

                    if (hasOrphans || hasDivergent) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasOrphans) MaterialTheme.colorScheme.errorContainer 
                                                else Color(0xFFFFECB3)
                            ),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasOrphans) Icons.Default.Warning else Icons.Default.Sync, 
                                        null, 
                                        tint = if (hasOrphans) MaterialTheme.colorScheme.error else Color(0xFFE65100)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (hasOrphans) "Einige Artikel fehlen in deiner Bibliothek."
                                               else "Einige Artikel haben abweichende Werte zur Bibliothek.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (hasOrphans) MaterialTheme.colorScheme.error else Color(0xFFE65100),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { 
                                        val resolved = ingredients.map { ing ->
                                            val foodById = foods.find { it.id == ing.foodItemId }
                                            val foodBySimilarity = foods.find { it.isSimilarTo(FoodEntryEntity(name = ing.name, brand = ing.brand, barcode = ing.barcode)) }
                                            val bestFood = foodById ?: foodBySimilarity

                                            if (bestFood != null) {
                                                // Automatisch auf den Stand der Bibliothek bringen
                                                ing.copy(
                                                    foodItemId = bestFood.id,
                                                    name = bestFood.name,
                                                    brand = bestFood.brand,
                                                    kcalPer100g = bestFood.kcalPer100g,
                                                    proteinPer100g = bestFood.proteinPer100g,
                                                    carbsPer100g = bestFood.carbsPer100g,
                                                    sugarPer100g = bestFood.sugarPer100g,
                                                    fatPer100g = bestFood.fatPer100g,
                                                    saturatedFatPer100g = bestFood.saturatedFatPer100g,
                                                    alcoholPercent = bestFood.alcoholPercent,
                                                    baseUnit = bestFood.baseUnit,
                                                    store = bestFood.store,
                                                    category = bestFood.category,
                                                    barcode = bestFood.barcode,
                                                    isGeneric = bestFood.isGeneric
                                                )
                                            } else {
                                                // Artikel fehlt wirklich komplett -> Pseudo-Import anbieten (passiert in IngredientRow)
                                                ing
                                            }
                                        }
                                        ingredients.clear()
                                        ingredients.addAll(resolved)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (hasOrphans) MaterialTheme.colorScheme.error else Color(0xFFFFA000)
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        if (hasOrphans) "ALLE FEHLENDEN ARTIKEL ÜBERNEHMEN" 
                                        else "ALLE AN BIBLIOTHEK ANPASSEN", 
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    AutoSelectTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name der Mahlzeit") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    AutoSelectTextField(
                        value = servings,
                        onValueChange = { servings = it },
                        label = { Text("Portionen (Gesamt)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                }

                item {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { tagsExpanded = !tagsExpanded }
                        ) {
                            Text("Tags (${tags.size})", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            Icon(
                                if (tagsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        AnimatedVisibility(visible = tagsExpanded) {
                            Column {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                    tags.forEach { tag ->
                                        InputChip(
                                            selected = true,
                                            onClick = { },
                                            label = { Text(tag) },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Remove tag",
                                                    modifier = Modifier.size(16.dp).clickable { tags.remove(tag) }
                                                )
                                            }
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = newTagText,
                                        onValueChange = { newTagText = it },
                                        label = { Text("Neuer Tag") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(onDone = {
                                            if (newTagText.isNotBlank() && !tags.contains(newTagText.trim())) {
                                                tags.add(newTagText.trim())
                                                newTagText = ""
                                            }
                                        })
                                    )
                                    IconButton(onClick = {
                                        if (newTagText.isNotBlank() && !tags.contains(newTagText.trim())) {
                                            tags.add(newTagText.trim())
                                            newTagText = ""
                                        }
                                    }) {
                                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                                    }
                                }

                                Text("Vorschläge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    standardTags.forEach { tag ->
                                        FilterChip(
                                            selected = tags.contains(tag),
                                            onClick = {
                                                if (tags.contains(tag)) tags.remove(tag) else tags.add(tag)
                                            },
                                            label = { Text(tag) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .clickable { showImageSourceDialog = true },
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUrl != null) {
                                Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Default.AddAPhoto, null, tint = MaterialTheme.colorScheme.outline)
                            }
                        }
                        
                        Text(
                            "Bild hinzufügen",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                item {
                    if (imageUrl != null) {
                        Card(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Meal Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                
                                FilledIconButton(
                                    onClick = { imageUrl = null },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.5f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.Close, null)
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Gesamtgewicht: ${ingredients.sumOf { it.grams }.round0()}g (${(ingredients.sumOf { it.grams } / (servings.num().coerceAtLeast(1.0))).round0()}g pro Portion)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                item {
                    Text("Zutaten", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                }

                item {
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
                                                val brand = food.brand
                                                if (!food.isGeneric && !brand.isNullOrBlank()) {
                                                    Text(brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
                }

                items(ingredients, key = { it.id }) { ingredient ->
                    IngredientRow(
                        ingredient = ingredient,
                        vm = vm,
                        onUpdate = { updated ->
                            val idx = ingredients.indexOfFirst { it.id == ingredient.id }
                            if (idx != -1) ingredients[idx] = updated
                        },
                        onRemove = { ingredients.removeAll { it.id == ingredient.id } }
                    )
                }

                item {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Abbrechen") }
                        Button(
                            enabled = name.isNotBlank() && ingredients.isNotEmpty(),
                            onClick = { onSave(name, ingredients.toList(), servings.replace(',', '.').toDoubleOrNull() ?: 1.0, tags.toList(), imageUrl) }
                        ) { Text("Speichern") }
                    }
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
                val brand = food.brand
                if (!food.isGeneric && !brand.isNullOrBlank()) {
                    Text(brand, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
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
    vm: NutritionViewModel,
    onUpdate: (MealIngredientEntity) -> Unit,
    onRemove: () -> Unit
) {
    val foods = vm.foods
    val food = foods.find { it.id == ingredient.foodItemId }
    val parent = food?.parentId?.let { pId -> foods.find { it.id == pId } }
    val allPortions = food?.getAllPortions(parent) ?: emptyList()
    
    var amountText by remember { mutableStateOf(ingredient.amount.roundString()) }
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
