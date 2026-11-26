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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.style.TextDecoration
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
    var cookingModeMeal by remember { mutableStateOf<MealEntity?>(null) }
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

    if (cookingModeMeal != null) {
        CookingModeDialog(
            meal = cookingModeMeal!!,
            foods = vm.foods,
            onDismiss = { cookingModeMeal = null }
        )
    }

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
            onSave = { name, ingredients, servings, tags, imageUrl, recipeSteps ->
                if (mealToEdit == null) {
                    vm.addMealTemplate(name, ingredients, servings, tags, imageUrl, recipeSteps)
                } else {
                    vm.updateMealTemplate(mealToEdit!!.copy(name = name, ingredients = ingredients, servings = servings, tags = tags, imageUrl = imageUrl, recipeSteps = recipeSteps))
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
                            if (meal.recipeSteps.isNotEmpty()) {
                                IconButton(
                                    onClick = { cookingModeMeal = meal },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.SoupKitchen,
                                        contentDescription = "Kochmodus",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                        
                        AnimatedVisibility(visible = expanded) {
                            val poolComparison = remember(meal, vm.plannedMealPool.size) { vm.compareMealWithPool(meal) }
                            var showComparisonDialog by remember { mutableStateOf(false) }

                            if (showComparisonDialog && poolComparison != null) {
                                RecipeComparisonDialog(
                                    comparison = poolComparison,
                                    onDismiss = { showComparisonDialog = false },
                                    onAdopt = { adoptIngredients, adoptInstructions ->
                                        val poolItem = vm.plannedMealPool.find { it.mealName.trim().equals(meal.name.trim(), ignoreCase = true) }
                                        if (poolItem != null) {
                                            vm.adoptRecipeFromPool(poolItem, adoptIngredients, adoptInstructions)
                                        }
                                        showComparisonDialog = false
                                    }
                                )
                            }

                            Column(Modifier.padding(top = 8.dp)) {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                
                                if (poolComparison != null) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp)
                                            .clickable { showComparisonDialog = true }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CompareArrows, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                                            Spacer(Modifier.width(6.dp))
                                            if (poolComparison.isIdentical) {
                                                Text("Haushalts-Rezept: Identisch", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.SemiBold)
                                            } else {
                                                Text("Haushalts-Rezept vorhanden (Abweichungen: ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                if (poolComparison.hasIngredientNameDiff) Text("Artikel ", style = MaterialTheme.typography.labelSmall, color = Color(0xFFE65100), fontWeight = FontWeight.Bold)
                                                if (poolComparison.hasAmountDiff) Text("Mengen ", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF57F17), fontWeight = FontWeight.Bold)
                                                if (poolComparison.hasInstructionDiff) Text("Anleitung", style = MaterialTheme.typography.labelSmall, color = Color(0xFF7B1FA2), fontWeight = FontWeight.Bold)
                                                Text(")", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(Modifier.weight(1f))
                                            Text("Vergleichen", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

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

                                Button(
                                    onClick = { cookingModeMeal = meal },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Icon(Icons.Default.SoupKitchen, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (meal.recipeSteps.isEmpty()) "Kochmodus (Rezept leer)"
                                        else "Kochmodus (${meal.recipeSteps.size} Schritt${if (meal.recipeSteps.size == 1) "" else "e"})"
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
    onSave: (String, List<MealIngredientEntity>, Double, List<String>, String?, List<RecipeStepEntity>) -> Unit
) {
    val foods = vm.foods
    var name by remember(meal?.id) { mutableStateOf(meal?.name ?: "") }
    var servings by remember(meal?.id) { mutableStateOf(meal?.servings?.roundString() ?: "1") }
    val tags = remember(meal?.id) { mutableStateListOf<String>().apply { meal?.tags?.let { addAll(it) } } }
    var newTagText by remember { mutableStateOf("") }
    var imageUrl by remember(meal?.id) { mutableStateOf(meal?.imageUrl) }
    val recipeSteps = remember(meal?.id) { 
        mutableStateListOf<RecipeStepEntity>().apply { 
            meal?.recipeSteps?.let { addAll(it) } 
        } 
    }
    var showRecipeStepsEditor by remember { mutableStateOf(false) }
    
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
                    if (showRecipeStepsEditor) {
                        RecipeStepsEditorDialog(
                            initialSteps = recipeSteps,
                            mealIngredients = ingredients,
                            foods = foods,
                            onDismiss = { showRecipeStepsEditor = false },
                            onSaveSteps = { updatedSteps ->
                                recipeSteps.clear()
                                recipeSteps.addAll(updatedSteps)
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = { showRecipeStepsEditor = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (recipeSteps.isEmpty()) "Kochrezept anlegen"
                            else "Kochrezept bearbeiten (${recipeSteps.size} Schritt${if (recipeSteps.size == 1) "" else "e"})"
                        )
                    }
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
                            onClick = { onSave(name, ingredients.toList(), servings.replace(',', '.').toDoubleOrNull() ?: 1.0, tags.toList(), imageUrl, recipeSteps.toList()) }
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

    var showSearchAssignDialog by remember { mutableStateOf(false) }
    var searchAssignQuery by remember { mutableStateOf(ingredient.name) }
    var pendingCreateFoodItem by remember { mutableStateOf<FoodItemEntity?>(null) }
    
    val filteredAssignFoods by remember(searchAssignQuery, foods) {
        derivedStateOf {
            if (searchAssignQuery.isBlank()) emptyList()
            else foods.filter { it.name.contains(searchAssignQuery, ignoreCase = true) }
                .sortedWith(compareByDescending<FoodItemEntity> { it.isGeneric }.thenBy { it.name })
                .take(10)
        }
    }

    if (pendingCreateFoodItem != null) {
        val initialPortionInput = if (ingredient.unitLabel !in listOf("g", "kg", "ml", "l") && ingredient.unitLabel.isNotBlank()) ingredient.unitLabel else ""
        FoodEditDialog(
            food = pendingCreateFoodItem,
            vm = vm,
            initialAiPortionInput = initialPortionInput,
            onDismiss = { pendingCreateFoodItem = null },
            onSave = { newFood ->
                val finalFood = if (newFood.id == 0L) {
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
                    newFood
                }
                
                onUpdate(ingredient.copy(
                    foodItemId = finalFood.id,
                    name = finalFood.name,
                    brand = finalFood.brand,
                    kcalPer100g = finalFood.kcalPer100g,
                    proteinPer100g = finalFood.proteinPer100g,
                    carbsPer100g = finalFood.carbsPer100g,
                    sugarPer100g = finalFood.sugarPer100g,
                    fatPer100g = finalFood.fatPer100g,
                    saturatedFatPer100g = finalFood.saturatedFatPer100g,
                    alcoholPercent = finalFood.alcoholPercent,
                    baseUnit = finalFood.baseUnit,
                    store = finalFood.store,
                    category = finalFood.category,
                    isGeneric = finalFood.isGeneric
                ))
                pendingCreateFoodItem = null
                showSearchAssignDialog = false
            }
        )
    }

    if (showSearchAssignDialog) {
        Dialog(onDismissRequest = { showSearchAssignDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Artikel für '${ingredient.name}' zuweisen", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchAssignQuery,
                        onValueChange = { searchAssignQuery = it },
                        label = { Text("Suchen...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredAssignFoods, key = { "assign_${it.id}" }) { foodItem ->
                            ListItem(
                                headlineContent = { Text(foodItem.name) },
                                supportingContent = foodItem.brand?.takeIf { it.isNotBlank() && !foodItem.isGeneric }?.let { { Text(it) } },
                                trailingContent = { Text("${foodItem.kcalPer100g.round0()} kcal/100${foodItem.baseUnit}") },
                                modifier = Modifier.clickable {
                                    val isMetric = ingredient.unitLabel in listOf("g", "kg", "ml", "l", "Stück")
                                    val hasPortion = foodItem.portions.any { it.name.equals(ingredient.unitLabel, ignoreCase = true) } || foodItem.baseUnit == ingredient.unitLabel
                                    if (!isMetric && !hasPortion) {
                                        pendingCreateFoodItem = foodItem
                                    } else {
                                        onUpdate(ingredient.copy(
                                            foodItemId = foodItem.id,
                                            name = foodItem.name,
                                            brand = foodItem.brand,
                                            kcalPer100g = foodItem.kcalPer100g,
                                            proteinPer100g = foodItem.proteinPer100g,
                                            carbsPer100g = foodItem.carbsPer100g,
                                            sugarPer100g = foodItem.sugarPer100g,
                                            fatPer100g = foodItem.fatPer100g,
                                            saturatedFatPer100g = foodItem.saturatedFatPer100g,
                                            alcoholPercent = foodItem.alcoholPercent,
                                            baseUnit = foodItem.baseUnit,
                                            store = foodItem.store,
                                            category = foodItem.category,
                                            isGeneric = foodItem.isGeneric
                                        ))
                                    }
                                    showSearchAssignDialog = false
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showSearchAssignDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Abbrechen")
                    }
                }
            }
        }
    }

    Card(
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
                        colors = if (ingredient.foodItemId == 0L && ingredient.unitLabel !in listOf("g", "kg", "ml", "l", "Stück")) OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Red, focusedBorderColor = Color.Red) else OutlinedTextFieldDefaults.colors(),
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
}

data class ParsedRecipeLink(
    val fullTag: String,
    val range: IntRange,
    val displayText: String,
    val ingredientRef: String,
    val ingredientId: Long,
    val amount: Double,
    val unitLabel: String
)

object RecipeTextParser {
    private val BRACKET_TAG_REGEX = Regex("""\["([^"]+)"@([^#\]]+)(?:#([0-9.,]*)\s*([^\]]*))?\]""")
    private val MARKDOWN_TAG_REGEX = Regex("""\[([^\]]+)\]\(ing:(\d+):([0-9.]+):([^)]+)\)""")

    fun parseLinks(text: String): List<ParsedRecipeLink> {
        val list = mutableListOf<ParsedRecipeLink>()

        BRACKET_TAG_REGEX.findAll(text).forEach { match ->
            val displayText = match.groupValues[1]
            val ingRef = match.groupValues[2].trim()
            val amtNum = match.groupValues[3].replace(',', '.').toDoubleOrNull() ?: 0.0
            val unitStr = match.groupValues[4].trim()

            val ingId = if (ingRef.contains("|")) {
                ingRef.substringAfter("|").trim().toLongOrNull() ?: 0L
            } else {
                ingRef.toLongOrNull() ?: 0L
            }

            list.add(
                ParsedRecipeLink(
                    fullTag = match.value,
                    range = match.range,
                    displayText = displayText,
                    ingredientRef = ingRef,
                    ingredientId = ingId,
                    amount = amtNum,
                    unitLabel = unitStr
                )
            )
        }

        MARKDOWN_TAG_REGEX.findAll(text).forEach { match ->
            val displayText = match.groupValues[1]
            val ingId = match.groupValues[2].toLongOrNull() ?: 0L
            val amtNum = match.groupValues[3].toDoubleOrNull() ?: 0.0
            val unitStr = match.groupValues[4]

            if (!list.any { it.range.first == match.range.first }) {
                list.add(
                    ParsedRecipeLink(
                        fullTag = match.value,
                        range = match.range,
                        displayText = displayText,
                        ingredientRef = ingId.toString(),
                        ingredientId = ingId,
                        amount = amtNum,
                        unitLabel = unitStr
                    )
                )
            }
        }

        return list.sortedBy { it.range.first }
    }

    fun extractStepIngredients(
        description: String,
        mealIngredients: List<MealIngredientEntity>,
        foods: List<FoodItemEntity>
    ): List<RecipeStepIngredient> {
        val links = parseLinks(description)
        if (links.isEmpty()) return emptyList()

        val grouped = links.groupBy { link ->
            val mealIng = resolveMealIngredient(link, mealIngredients)
            mealIng?.id ?: link.ingredientId
        }

        return grouped.map { (ingId, linkList) ->
            val mealIng = mealIngredients.find { it.id == ingId }
            val food = foods.find { it.id == mealIng?.foodItemId || (mealIng != null && it.matchesIngredient(mealIng)) }

            var totalGrams = 0.0
            var totalAmtInIngUnit = 0.0

            linkList.forEach { link ->
                val (linkGrams, linkAmtInIngUnit) = calculateLinkGramsAndAmount(link, mealIng, food)
                totalGrams += linkGrams
                totalAmtInIngUnit += linkAmtInIngUnit
            }

            RecipeStepIngredient(
                ingredientId = ingId,
                amount = totalAmtInIngUnit,
                grams = totalGrams
            )
        }.filter { it.ingredientId > 0 }
    }

    fun resolveMealIngredient(
        link: ParsedRecipeLink,
        mealIngredients: List<MealIngredientEntity>
    ): MealIngredientEntity? {
        if (link.ingredientId > 0) {
            val found = mealIngredients.find { it.id == link.ingredientId }
            if (found != null) return found
        }

        val refName = link.ingredientRef.substringBefore("|").trim()
        return mealIngredients.find { it.name.equals(refName, ignoreCase = true) || it.id.toString() == refName }
    }

    fun calculateLinkGramsAndAmount(
        link: ParsedRecipeLink,
        mealIng: MealIngredientEntity?,
        food: FoodItemEntity?
    ): Pair<Double, Double> {
        if (mealIng == null) return Pair(0.0, link.amount)

        val allPortions = food?.getAllPortions() ?: emptyList()
        val allPackages = food?.packages ?: emptyList()
        val unitKey = link.unitLabel.trim()

        val portionMatch = allPortions.find { it.name.equals(unitKey, ignoreCase = true) || matchUnitAlias(unitKey, it.name) }
        val packageMatch = allPackages.find { it.name.equals(unitKey, ignoreCase = true) || matchUnitAlias(unitKey, it.name) }

        val grams: Double
        val amtInIngUnit: Double

        if (portionMatch != null && portionMatch.grams > 0) {
            grams = link.amount * portionMatch.grams
            amtInIngUnit = if (mealIng.unitLabel.equals(portionMatch.name, ignoreCase = true)) {
                link.amount
            } else if (mealIng.grams > 0) {
                grams * (mealIng.amount / mealIng.grams)
            } else {
                link.amount
            }
        } else if (packageMatch != null && packageMatch.quantity > 0) {
            grams = link.amount * packageMatch.quantity
            amtInIngUnit = if (mealIng.unitLabel.equals(packageMatch.name, ignoreCase = true)) {
                link.amount
            } else if (mealIng.grams > 0) {
                grams * (mealIng.amount / mealIng.grams)
            } else {
                link.amount
            }
        } else if (unitKey.equals("g", ignoreCase = true) || unitKey.equals("ml", ignoreCase = true)) {
            grams = link.amount
            amtInIngUnit = if (mealIng.unitLabel.equals(unitKey, ignoreCase = true)) {
                link.amount
            } else if (mealIng.grams > 0) {
                link.amount * (mealIng.amount / mealIng.grams)
            } else {
                link.amount
            }
        } else {
            val aliasGramsPerUnit = getAliasGramsPerUnit(unitKey)
            if (aliasGramsPerUnit != null) {
                grams = link.amount * aliasGramsPerUnit
                amtInIngUnit = if (mealIng.grams > 0) grams * (mealIng.amount / mealIng.grams) else link.amount
            } else {
                amtInIngUnit = link.amount
                grams = if (mealIng.amount > 0) link.amount * (mealIng.grams / mealIng.amount) else link.amount
            }
        }

        return Pair(grams, amtInIngUnit)
    }

    private fun matchUnitAlias(unitKey: String, portionName: String): Boolean {
        val u = unitKey.lowercase()
        val p = portionName.lowercase()
        if (u == p) return true
        if ((u == "tl" || u == "teelöffel") && (p == "tl" || p == "teelöffel")) return true
        if ((u == "el" || u == "esslöffel") && (p == "el" || p == "esslöffel")) return true
        if ((u == "stk" || u == "stück" || u == "x") && (p == "stk" || p == "stück")) return true
        return false
    }

    private fun getAliasGramsPerUnit(unitKey: String): Double? {
        return when (unitKey.lowercase()) {
            "tl", "teelöffel" -> 5.0
            "el", "esslöffel" -> 15.0
            "prise" -> 1.0
            "msp", "messerspitze" -> 0.5
            "becher", "tasse" -> 150.0
            "spritzer" -> 2.0
            "schuss" -> 10.0
            "zehe" -> 5.0
            "dose" -> 400.0
            "packung", "pkg" -> 250.0
            else -> null
        }
    }
}

data class AutocompleteContext(
    val type: AutocompleteType,
    val tagStartIndex: Int,
    val atIndex: Int,
    val hashIndex: Int,
    val query: String,
    val selectedIngRef: String?
)

enum class AutocompleteType {
    NONE,
    INGREDIENT,
    UNIT
}

fun detectAutocompleteContext(
    textFieldValue: TextFieldValue
): AutocompleteContext {
    val text = textFieldValue.text
    val cursorPos = textFieldValue.selection.start.coerceIn(0, text.length)
    if (cursorPos == 0) return AutocompleteContext(AutocompleteType.NONE, -1, -1, -1, "", null)

    val textBeforeCursor = text.substring(0, cursorPos)

    val lastBracket = textBeforeCursor.lastIndexOf("[")
    if (lastBracket == -1) return AutocompleteContext(AutocompleteType.NONE, -1, -1, -1, "", null)

    val closingBracket = textBeforeCursor.indexOf("]", lastBracket)
    if (closingBracket != -1) return AutocompleteContext(AutocompleteType.NONE, -1, -1, -1, "", null)

    val atIndex = textBeforeCursor.indexOf("@", lastBracket)
    val hashIndex = textBeforeCursor.indexOf("#", lastBracket)

    if (atIndex == -1) {
        return AutocompleteContext(AutocompleteType.NONE, lastBracket, -1, -1, "", null)
    }

    if (hashIndex == -1) {
        val query = textBeforeCursor.substring(atIndex + 1)
        return AutocompleteContext(
            type = AutocompleteType.INGREDIENT,
            tagStartIndex = lastBracket,
            atIndex = atIndex,
            hashIndex = -1,
            query = query,
            selectedIngRef = null
        )
    }

    val selectedIngRef = textBeforeCursor.substring(atIndex + 1, hashIndex).trim()
    val unitQuery = textBeforeCursor.substring(hashIndex + 1)

    return AutocompleteContext(
        type = AutocompleteType.UNIT,
        tagStartIndex = lastBracket,
        atIndex = atIndex,
        hashIndex = hashIndex,
        query = unitQuery,
        selectedIngRef = selectedIngRef
    )
}

data class UnitOption(
    val label: String,
    val unitKey: String,
    val gramsPerUnit: Double?
)

fun getUnitOptionsForIngredient(ing: MealIngredientEntity, food: FoodItemEntity?): List<UnitOption> {
    val list = mutableListOf<UnitOption>()
    val baseUnit = food?.baseUnit ?: ing.baseUnit.ifBlank { "g" }

    // 1. Grund-Einheit (g oder ml)
    list.add(UnitOption(label = baseUnit, unitKey = baseUnit, gramsPerUnit = 1.0))

    // 2. Eigenständige Einheit dieser Zutat aus dem Rezept
    if (ing.unitLabel.isNotBlank() && !ing.unitLabel.equals(baseUnit, ignoreCase = true)) {
        val gramsPerOne = if (ing.amount > 0) ing.grams / ing.amount else null
        val gramsDisplay = if (gramsPerOne != null && gramsPerOne > 0) " (${gramsPerOne.roundString()} $baseUnit)" else ""
        list.add(UnitOption(label = "${ing.unitLabel}$gramsDisplay", unitKey = ing.unitLabel, gramsPerUnit = gramsPerOne))
    }

    // 3. Portions-Einheiten dieses spezifischen Lebensmittels
    food?.getAllPortions()?.forEach { portion ->
        if (!list.any { it.unitKey.equals(portion.name, ignoreCase = true) }) {
            list.add(UnitOption(label = "${portion.name} (${portion.grams.roundString()} $baseUnit)", unitKey = portion.name, gramsPerUnit = portion.grams))
        }
    }

    // 4. Verpackungseinheiten dieses spezifischen Lebensmittels
    food?.packages?.forEach { pkg ->
        if (!list.any { it.unitKey.equals(pkg.name, ignoreCase = true) }) {
            list.add(UnitOption(label = "${pkg.name} (${pkg.quantity.roundString()} $baseUnit)", unitKey = pkg.name, gramsPerUnit = pkg.quantity))
        }
    }

    return list.distinctBy { it.unitKey.lowercase() }
}

@Composable
fun renderRecipeDescription(
    description: String,
    mealIngredients: List<MealIngredientEntity>,
    foods: List<FoodItemEntity> = emptyList(),
    scaleFactor: Double = 1.0
): AnnotatedString {
    val links = RecipeTextParser.parseLinks(description)
    if (links.isEmpty()) {
        return AnnotatedString(description)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)

    return buildAnnotatedString {
        var lastIndex = 0
        links.forEach { link ->
            if (link.range.first > lastIndex) {
                append(description.substring(lastIndex, link.range.first))
            }

            val mealIng = RecipeTextParser.resolveMealIngredient(link, mealIngredients)
            val food = foods.find { it.id == mealIng?.foodItemId || (mealIng != null && it.matchesIngredient(mealIng)) }
            val (baseGrams, _) = RecipeTextParser.calculateLinkGramsAndAmount(link, mealIng, food)

            val scaledAmt = link.amount * scaleFactor
            val scaledGrams = baseGrams * scaleFactor

            val amtStr = if (scaledAmt % 1.0 == 0.0) "%.0f".format(scaledAmt) else "%.1f".format(scaledAmt)
            val gramsStr = if (scaledGrams % 1.0 == 0.0) "%.0f".format(scaledGrams) else "%.1f".format(scaledGrams)

            val unitLabelClean = link.unitLabel.trim()
            val amountDisplay = if (link.amount <= 0.0) {
                ""
            } else if (unitLabelClean.equals("g", ignoreCase = true) || unitLabelClean.equals("ml", ignoreCase = true)) {
                val gDisplay = if (scaledGrams > 0) gramsStr else amtStr
                "$gDisplay$unitLabelClean"
            } else if (unitLabelClean.equals("x", ignoreCase = true) || unitLabelClean.equals("Stück", ignoreCase = true) || unitLabelClean.equals("Stk.", ignoreCase = true)) {
                "$amtStr Stk."
            } else if (unitLabelClean.isNotBlank()) {
                "$amtStr $unitLabelClean"
            } else {
                ""
            }

            val rawText = link.displayText.trim()
            val formattedLinkText = if (amountDisplay.isBlank()) {
                rawText
            } else if (rawText.startsWith(amountDisplay) || rawText.contains(amountDisplay)) {
                rawText
            } else {
                "$amountDisplay $rawText"
            }

            val startSpan = length
            append(formattedLinkText)
            val endSpan = length

            addStyle(
                style = SpanStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    background = primaryContainer
                ),
                start = startSpan,
                end = endSpan
            )

            lastIndex = link.range.last + 1
        }

        if (lastIndex < description.length) {
            append(description.substring(lastIndex))
        }
    }
}

class RecipeTagVisualTransformation(
    private val mealIngredients: List<MealIngredientEntity>,
    private val primaryColor: Color,
    private val containerColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val rawText = text.text
        val links = RecipeTextParser.parseLinks(rawText)

        if (links.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder()
        val originalToTransformed = IntArray(rawText.length + 1)
        val transformedToOriginal = mutableListOf<Int>()

        var origIdx = 0
        var transIdx = 0

        links.forEach { link ->
            while (origIdx < link.range.first) {
                originalToTransformed[origIdx] = transIdx
                transformedToOriginal.add(origIdx)
                builder.append(rawText[origIdx])
                origIdx++
                transIdx++
            }

            val mealIng = RecipeTextParser.resolveMealIngredient(link, mealIngredients)
            val ingName = mealIng?.name ?: link.displayText.trim()

            val amtStr = if (link.amount % 1.0 == 0.0) "%.0f".format(link.amount) else "%.1f".format(link.amount)
            val unitClean = link.unitLabel.trim()
            val amountDisplay = if (link.amount <= 0.0) {
                ""
            } else if (unitClean.equals("g", ignoreCase = true) || unitClean.equals("ml", ignoreCase = true)) {
                "$amtStr$unitClean"
            } else if (unitClean.isNotBlank()) {
                "$amtStr $unitClean"
            } else {
                ""
            }

            val rawDisp = link.displayText.trim()
            val linkVisualText = if (amountDisplay.isBlank()) {
                " $rawDisp "
            } else if (rawDisp.startsWith(amountDisplay) || rawDisp.contains(amountDisplay)) {
                " $rawDisp "
            } else {
                " $amountDisplay $ingName "
            }

            val tagStartTrans = transIdx
            builder.append(linkVisualText)
            val tagEndTrans = transIdx + linkVisualText.length

            builder.addStyle(
                style = SpanStyle(
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    background = containerColor
                ),
                start = tagStartTrans,
                end = tagEndTrans
            )

            val tagLength = link.range.last - link.range.first + 1
            for (i in 0 until tagLength) {
                val mappedTrans = (tagStartTrans + (i.toFloat() / tagLength * linkVisualText.length).toInt())
                    .coerceIn(tagStartTrans, tagEndTrans)
                originalToTransformed[origIdx + i] = mappedTrans
            }
            for (i in 0 until linkVisualText.length) {
                val mappedOrig = (link.range.first + (i.toFloat() / linkVisualText.length * tagLength).toInt())
                    .coerceIn(link.range.first, link.range.last)
                transformedToOriginal.add(mappedOrig)
            }

            origIdx = link.range.last + 1
            transIdx = tagEndTrans
        }

        while (origIdx < rawText.length) {
            originalToTransformed[origIdx] = transIdx
            transformedToOriginal.add(origIdx)
            builder.append(rawText[origIdx])
            origIdx++
            transIdx++
        }

        originalToTransformed[rawText.length] = transIdx
        transformedToOriginal.add(rawText.length)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return originalToTransformed[offset.coerceIn(0, rawText.length)]
            }

            override fun transformedToOriginal(offset: Int): Int {
                return transformedToOriginal[offset.coerceIn(0, transformedToOriginal.size - 1)]
            }
        }

        return TransformedText(builder.toAnnotatedString(), offsetMapping)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepIngredientChipsRow(
    description: String,
    mealIngredients: List<MealIngredientEntity>,
    foods: List<FoodItemEntity> = emptyList(),
    scaleFactor: Double = 1.0
) {
    val links = remember(description) { RecipeTextParser.parseLinks(description) }
    if (links.isEmpty()) return

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 6.dp)
    ) {
        links.forEach { link ->
            val mealIng = RecipeTextParser.resolveMealIngredient(link, mealIngredients)
            val food = foods.find { it.id == mealIng?.foodItemId || (mealIng != null && it.matchesIngredient(mealIng)) }
            val (baseGrams, _) = RecipeTextParser.calculateLinkGramsAndAmount(link, mealIng, food)

            val scaledAmt = link.amount * scaleFactor
            val scaledGrams = baseGrams * scaleFactor

            val amtStr = if (scaledAmt % 1.0 == 0.0) "%.0f".format(scaledAmt) else "%.1f".format(scaledAmt)
            val gramsStr = if (scaledGrams % 1.0 == 0.0) "%.0f".format(scaledGrams) else "%.1f".format(scaledGrams)

            val unitLabelClean = link.unitLabel.trim()
            val amountDisplay = if (link.amount <= 0.0) {
                ""
            } else if (unitLabelClean.equals("g", ignoreCase = true) || unitLabelClean.equals("ml", ignoreCase = true)) {
                val gDisplay = if (scaledGrams > 0) gramsStr else amtStr
                "$gDisplay$unitLabelClean"
            } else if (unitLabelClean.isNotBlank()) {
                "$amtStr $unitLabelClean"
            } else {
                ""
            }

            val ingName = mealIng?.name ?: link.displayText.trim()
            val chipText = if (amountDisplay.isNotBlank()) {
                if (ingName.startsWith(amountDisplay) || ingName.contains(amountDisplay)) ingName
                else "$amountDisplay $ingName"
            } else {
                ingName
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.RestaurantMenu,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = chipText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeStepsEditorDialog(
    initialSteps: List<RecipeStepEntity>,
    mealIngredients: List<MealIngredientEntity>,
    foods: List<FoodItemEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSaveSteps: (List<RecipeStepEntity>) -> Unit
) {
    val steps = remember { mutableStateListOf<RecipeStepEntity>().apply { addAll(initialSteps) } }
    var stepToEdit by remember { mutableStateOf<RecipeStepEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    if (isCreatingNew || stepToEdit != null) {
        val editingStep = stepToEdit ?: RecipeStepEntity(
            id = System.currentTimeMillis(),
            stepNumber = steps.size + 1,
            title = "",
            description = "",
            ingredients = emptyList()
        )
        SingleStepEditDialog(
            step = editingStep,
            mealIngredients = mealIngredients,
            foods = foods,
            onDismiss = {
                stepToEdit = null
                isCreatingNew = false
            },
            onSave = { savedStep ->
                if (stepToEdit != null) {
                    val idx = steps.indexOfFirst { it.id == savedStep.id }
                    if (idx != -1) steps[idx] = savedStep
                } else {
                    steps.add(savedStep)
                }
                steps.forEachIndexed { index, s -> s.stepNumber = index + 1 }
                stepToEdit = null
                isCreatingNew = false
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Kochrezept Schritte", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Text(
                    "Hier kannst du Zubereitungsschritte anlegen und Zutaten direkt im Beschreibungstext verlinken.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (steps.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(8.dp))
                            Text("Noch keine Schritte vorhanden.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(steps, key = { _, s -> s.id }) { index, step ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Schritt ${index + 1}: ${step.title.ifBlank { "Zubereitung" }}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row {
                                            if (index > 0) {
                                                IconButton(onClick = {
                                                    val item = steps.removeAt(index)
                                                    steps.add(index - 1, item)
                                                    steps.forEachIndexed { i, s -> s.stepNumber = i + 1 }
                                                }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.ArrowUpward, "Nach oben", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            if (index < steps.size - 1) {
                                                IconButton(onClick = {
                                                    val item = steps.removeAt(index)
                                                    steps.add(index + 1, item)
                                                    steps.forEachIndexed { i, s -> s.stepNumber = i + 1 }
                                                }, modifier = Modifier.size(32.dp)) {
                                                    Icon(Icons.Default.ArrowDownward, "Nach unten", modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            IconButton(onClick = { stepToEdit = step }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Edit, "Bearbeiten", modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(onClick = {
                                                steps.removeAt(index)
                                                steps.forEachIndexed { i, s -> s.stepNumber = i + 1 }
                                            }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Delete, "Löschen", tint = Color.Red, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                    if (step.description.isNotBlank()) {
                                        Text(
                                            text = renderRecipeDescription(step.description, mealIngredients, foods),
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                        StepIngredientChipsRow(step.description, mealIngredients, foods)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { isCreatingNew = true }) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Schritt hinzufügen")
                    }
                    Button(onClick = {
                        steps.forEachIndexed { i, s -> s.stepNumber = i + 1 }
                        onSaveSteps(steps.toList())
                        onDismiss()
                    }) {
                        Text("Übernehmen")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IngredientLinkDialog(
    ingredient: MealIngredientEntity,
    food: FoodItemEntity? = null,
    onDismiss: () -> Unit,
    onConfirm: (tagText: String) -> Unit
) {
    val totalRecipeGrams = if (ingredient.grams > 0) ingredient.grams else ingredient.amount
    val totalRecipeAmount = if (ingredient.amount > 0) ingredient.amount else ingredient.grams
    val baseUnit = ingredient.unitLabel.ifBlank { "g" }

    val unitOptions = remember(ingredient, food) {
        getUnitOptionsForIngredient(ingredient, food)
    }

    var selectedUnitOption by remember { mutableStateOf(unitOptions.firstOrNull() ?: UnitOption(baseUnit, baseUnit, 1.0)) }

    val maxUnitAmount = remember(selectedUnitOption, totalRecipeAmount, totalRecipeGrams) {
        val gPerUnit = selectedUnitOption.gramsPerUnit
        if (gPerUnit != null && gPerUnit > 0) {
            totalRecipeGrams / gPerUnit
        } else if (selectedUnitOption.unitKey.equals(ingredient.unitLabel, ignoreCase = true)) {
            totalRecipeAmount
        } else {
            totalRecipeAmount
        }
    }

    fun formatAmt(value: Double): String {
        return when {
            value <= 0.0 -> "0"
            value % 1.0 == 0.0 -> "%.0f".format(value)
            (value * 10) % 1.0 == 0.0 -> "%.1f".format(value)
            else -> "%.2f".format(value)
        }
    }

    var sliderRatio by remember { mutableFloatStateOf(1.0f) }

    // Synchronized amount text input
    var selectedAmountText by remember(selectedUnitOption, maxUnitAmount) {
        mutableStateOf(formatAmt(maxUnitAmount))
    }

    // Separate grammar / label text field (defaults to ingredient.name e.g. "Nudeln")
    var ingredientCustomLabel by remember(ingredient.name) { mutableStateOf(ingredient.name) }

    fun updateFromSlider(ratio: Float) {
        sliderRatio = ratio.coerceIn(0.01f, 1.0f)
        val calc = (sliderRatio * maxUnitAmount).toDouble()
        selectedAmountText = formatAmt(calc)
    }

    fun updateFromAmountText(text: String) {
        selectedAmountText = text
        val parsed = text.replace(',', '.').toDoubleOrNull()
        if (parsed != null && parsed > 0 && maxUnitAmount > 0) {
            val clamped = parsed.coerceAtMost(maxUnitAmount)
            sliderRatio = (clamped / maxUnitAmount).toFloat().coerceIn(0.01f, 1.0f)
        }
    }

    val unitKeyClean = selectedUnitOption.unitKey.trim()
    val formattedAmountDisplay = remember(selectedAmountText, unitKeyClean) {
        if (selectedAmountText.isBlank()) ""
        else if (unitKeyClean.equals("g", ignoreCase = true) || unitKeyClean.equals("ml", ignoreCase = true)) {
            "$selectedAmountText$unitKeyClean"
        } else if (unitKeyClean.equals("x", ignoreCase = true) || unitKeyClean.equals("Stück", ignoreCase = true) || unitKeyClean.equals("Stk.", ignoreCase = true)) {
            "$selectedAmountText Stk."
        } else if (unitKeyClean.isNotBlank()) {
            "$selectedAmountText $unitKeyClean"
        } else {
            selectedAmountText
        }
    }

    val finalPreviewText = remember(formattedAmountDisplay, ingredientCustomLabel) {
        val labelTrimmed = ingredientCustomLabel.trim()
        if (formattedAmountDisplay.isBlank()) labelTrimmed
        else if (labelTrimmed.startsWith(formattedAmountDisplay) || labelTrimmed.contains(formattedAmountDisplay)) labelTrimmed
        else "$formattedAmountDisplay $labelTrimmed"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zutat verlinken: ${ingredient.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Gesamt in dieser Mahlzeit: ${ingredient.displayAmount()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text("Einheit wählen:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    unitOptions.forEach { opt ->
                        FilterChip(
                            selected = selectedUnitOption.unitKey == opt.unitKey,
                            onClick = {
                                selectedUnitOption = opt
                                updateFromSlider(sliderRatio)
                            },
                            label = { Text(opt.label) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = selectedAmountText,
                        onValueChange = { updateFromAmountText(it) },
                        label = { Text("Menge") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = { Text(selectedUnitOption.unitKey, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp)) }
                    )
                }

                Slider(
                    value = sliderRatio,
                    onValueChange = { updateFromSlider(it) },
                    valueRange = 0.05f..1.0f,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(0.25f to "25%", 0.5f to "50%", 0.75f to "75%", 1.0f to "100%").forEach { (pct, label) ->
                        AssistChip(
                            onClick = { updateFromSlider(pct) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                OutlinedTextField(
                    value = ingredientCustomLabel,
                    onValueChange = { ingredientCustomLabel = it },
                    label = { Text("Bezeichnung / Grammatik im Text") },
                    placeholder = { Text("z.B. der Nudeln, des Mehls") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Vorschau im Text:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = finalPreviewText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val amt = selectedAmountText.replace(',', '.').toDoubleOrNull() ?: (sliderRatio * maxUnitAmount).toDouble()
                val ingRef = "${ingredient.name}|${ingredient.id}"
                val tag = "[\"$finalPreviewText\"@$ingRef#$amt${selectedUnitOption.unitKey}]"
                onConfirm(tag)
            }) {
                Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Link einfügen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

fun handleAtomicTagDeletion(
    oldValue: TextFieldValue,
    newValue: TextFieldValue
): TextFieldValue {
    val oldText = oldValue.text
    val newText = newValue.text

    if (newText.length >= oldText.length) return newValue

    val oldLinks = RecipeTextParser.parseLinks(oldText)
    if (oldLinks.isEmpty()) return newValue

    val oldSelection = oldValue.selection
    val newSelection = newValue.selection

    val deletedRangeStart = newSelection.start.coerceIn(0, oldText.length)
    val deletedRangeEnd = oldSelection.end.coerceIn(0, oldText.length)

    val affectedLink = oldLinks.find { link ->
        (deletedRangeStart <= link.range.last + 1 && deletedRangeEnd >= link.range.first)
    }

    if (affectedLink != null) {
        val sb = StringBuilder(oldText)
        sb.replace(affectedLink.range.first, affectedLink.range.last + 1, "")
        val resultText = sb.toString()
        val newCursor = affectedLink.range.first.coerceIn(0, resultText.length)
        return TextFieldValue(
            text = resultText,
            selection = TextRange(newCursor)
        )
    }

    return newValue
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SingleStepEditDialog(
    step: RecipeStepEntity,
    mealIngredients: List<MealIngredientEntity>,
    foods: List<FoodItemEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (RecipeStepEntity) -> Unit
) {
    var title by remember { mutableStateOf(step.title) }
    var descriptionFieldValue by remember {
        mutableStateOf(TextFieldValue(text = step.description, selection = TextRange(step.description.length)))
    }
    var selectedIngredientForLink by remember { mutableStateOf<MealIngredientEntity?>(null) }

    val parsedLinks = remember(descriptionFieldValue.text) {
        RecipeTextParser.parseLinks(descriptionFieldValue.text)
    }

    val computedStepIngredients = remember(parsedLinks, mealIngredients, foods) {
        RecipeTextParser.extractStepIngredients(descriptionFieldValue.text, mealIngredients, foods)
    }

    if (selectedIngredientForLink != null) {
        val targetFood = foods.find { it.id == selectedIngredientForLink!!.foodItemId }
        IngredientLinkDialog(
            ingredient = selectedIngredientForLink!!,
            food = targetFood,
            onDismiss = { selectedIngredientForLink = null },
            onConfirm = { tagText ->
                val text = descriptionFieldValue.text
                val start = descriptionFieldValue.selection.min.coerceIn(0, text.length)
                val end = descriptionFieldValue.selection.max.coerceIn(0, text.length)

                val prefix = if (start > 0 && !text[start - 1].isWhitespace()) " " else ""
                val suffix = if (end < text.length && !text[end].isWhitespace()) " " else " "
                val formattedTag = "$prefix$tagText$suffix"

                val newText = text.substring(0, start) + formattedTag + text.substring(end)
                val newCursorPos = start + formattedTag.length

                descriptionFieldValue = TextFieldValue(
                    text = newText,
                    selection = TextRange(newCursorPos)
                )
                selectedIngredientForLink = null
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (step.title.isBlank()) "Schritt ${step.stepNumber}" else "Schritt ${step.stepNumber} bearbeiten") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Bezeichnung / Titel") },
                    placeholder = { Text("z.B. Gemüse anbraten") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Horizontal Ingredient Bar (Tippen zum Verlinken)
                if (mealIngredients.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "🥗 Zutaten aus dieser Mahlzeit (tippen zum Verlinken):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            mealIngredients.forEach { ing ->
                                AssistChip(
                                    onClick = { selectedIngredientForLink = ing },
                                    label = { Text("${ing.name} (${ing.displayAmount()})") },
                                    leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Anleitung / Beschreibung",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    val primaryColor = MaterialTheme.colorScheme.primary
                    val primaryContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)

                    OutlinedTextField(
                        value = descriptionFieldValue,
                        onValueChange = { newValue ->
                            descriptionFieldValue = handleAtomicTagDeletion(descriptionFieldValue, newValue)
                        },
                        placeholder = { Text("z.B. Kartoffeln schälen, im Topf kochen und mit Butter verfeinern...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        visualTransformation = RecipeTagVisualTransformation(mealIngredients, primaryColor, primaryContainer)
                    )

                    Text(
                        "Tipp: Klicke oben auf eine Zutat, um Menge und Link direkt an der Textposition einzufügen.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Live Preview
                if (parsedLinks.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Vorschau der Anleitung:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = renderRecipeDescription(descriptionFieldValue.text, mealIngredients, foods),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Linked ingredients Chips Summary
                if (parsedLinks.isNotEmpty()) {
                    Text("Verlinkte Zutaten in diesem Schritt:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        parsedLinks.forEach { link ->
                            AssistChip(
                                onClick = { },
                                label = { Text(link.displayText, style = MaterialTheme.typography.labelMedium) },
                                leadingIcon = {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val newText = descriptionFieldValue.text.replace(link.fullTag, "").replace("  ", " ")
                                            descriptionFieldValue = TextFieldValue(
                                                text = newText,
                                                selection = TextRange(newText.length)
                                            )
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Löschen", modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                        }
                    }
                }

                // Ingredients Usage Summary in Meal
                if (mealIngredients.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Verlinkungs-Fortschritt in der Mahlzeit:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                    mealIngredients.forEach { ing ->
                        val linkedStepIng = computedStepIngredients.find { it.ingredientId == ing.id }
                        val linkedGrams = linkedStepIng?.grams ?: 0.0
                        val isFullyLinked = linkedGrams >= ing.grams && ing.grams > 0

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ing.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "In Schritt: ${linkedGrams.round0()}g / Gesamt: ${ing.displayAmount()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFullyLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            if (isFullyLinked) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Vollständig verlinkt",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalStepIngredients = RecipeTextParser.extractStepIngredients(
                        descriptionFieldValue.text,
                        mealIngredients,
                        foods
                    )
                    val updatedStep = step.copy(
                        title = title.trim(),
                        description = descriptionFieldValue.text.trim(),
                        ingredients = finalStepIngredients
                    )
                    onSave(updatedStep)
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookingModeDialog(
    meal: MealEntity,
    foods: List<FoodItemEntity> = emptyList(),
    onDismiss: () -> Unit
) {
    var portionCount by remember { mutableStateOf(if (meal.servings > 0) meal.servings else 1.0) }
    var portionInput by remember { mutableStateOf(portionCount.roundString()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var currentStepIndex by remember { mutableIntStateOf(0) }

    val baseServings = if (meal.servings > 0) meal.servings else 1.0
    val scaleFactor = portionCount / baseServings
    val steps = meal.recipeSteps.sortedBy { it.stepNumber }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SoupKitchen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Kochmodus", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Text(
                    meal.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Portionen:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val newCount = (portionCount - 0.5).coerceAtLeast(0.5)
                                    portionCount = newCount
                                    portionInput = newCount.roundString()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Remove, "Portion verringern")
                            }
                            AutoSelectTextField(
                                value = portionInput,
                                onValueChange = { text ->
                                    portionInput = text
                                    val parsed = text.replace(',', '.').toDoubleOrNull()
                                    if (parsed != null && parsed > 0) {
                                        portionCount = parsed
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.width(90.dp),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    val newCount = portionCount + 0.5
                                    portionCount = newCount
                                    portionInput = newCount.roundString()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.Add, "Portion erhöhen")
                            }
                        }
                    }
                }

                if (steps.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(12.dp))
                            Text("Kein Kochrezept vorhanden", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Für diese Mahlzeit wurde noch kein Kochrezept angelegt.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Gesamtes Rezept") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Schritt für Schritt") }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    if (selectedTab == 0) {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(steps, key = { it.id }) { step ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "Schritt ${step.stepNumber}: ${step.title.ifBlank { "Zubereitung" }}",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (step.description.isNotBlank()) {
                                            Text(
                                                text = renderRecipeDescription(step.description, meal.ingredients, foods, scaleFactor = scaleFactor),
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                            StepIngredientChipsRow(step.description, meal.ingredients, foods, scaleFactor = scaleFactor)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val safeIndex = currentStepIndex.coerceIn(0, steps.size - 1)
                        val step = steps[safeIndex]

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Schritt ${safeIndex + 1} von ${steps.size}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${((safeIndex + 1).toFloat() / steps.size * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (safeIndex + 1).toFloat() / steps.size },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        step.title.ifBlank { "Schritt ${step.stepNumber}" },
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    if (step.description.isNotBlank()) {
                                        Text(
                                            text = renderRecipeDescription(step.description, meal.ingredients, foods, scaleFactor = scaleFactor),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        StepIngredientChipsRow(step.description, meal.ingredients, foods, scaleFactor = scaleFactor)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = { if (currentStepIndex > 0) currentStepIndex-- },
                                    enabled = safeIndex > 0
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Zurück")
                                }
                                Button(
                                    onClick = {
                                        if (safeIndex < steps.size - 1) {
                                            currentStepIndex++
                                        } else {
                                            onDismiss()
                                        }
                                    }
                                ) {
                                    Text(if (safeIndex < steps.size - 1) "Weiter" else "Fertig")
                                    if (safeIndex < steps.size - 1) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
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

private fun String.num(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun Double.round0(): String = "%.0f".format(this)
private fun Double.roundString(): String = toString().replace(".0", "")

