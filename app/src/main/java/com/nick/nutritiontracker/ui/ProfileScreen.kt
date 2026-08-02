package com.nick.nutritiontracker.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import android.util.Log
import com.google.firebase.auth.FirebaseAuthException
import com.nick.nutritiontracker.data.*
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, nutritionViewModel: NutritionViewModel, userProfile: UserProfile) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val firebaseManager = nutritionViewModel.firebaseManager
    val user by firebaseManager.currentUser.collectAsState()
    val household by firebaseManager.household.collectAsState()
    
    var firstName by remember(userProfile) { mutableStateOf(userProfile.firstName) }
    var age by remember(userProfile) { mutableStateOf(userProfile.age.toString()) }
    var weight by remember(userProfile) { mutableStateOf(userProfile.weightKg.toString()) }
    var height by remember(userProfile) { mutableStateOf(userProfile.heightCm.toString()) }
    var gender by remember(userProfile) { mutableStateOf(userProfile.gender) }
    var goal by remember(userProfile) { mutableStateOf(userProfile.goal) }
    var intensity by remember(userProfile) { mutableStateOf(userProfile.goalIntensity.toString()) }

    var weighInEnabled by remember(userProfile) { mutableStateOf(userProfile.weighInReminderEnabled) }
    var weighInTime by remember(userProfile) { mutableStateOf(userProfile.weighInReminderTime) }
    var breakfastEnabled by remember(userProfile) { mutableStateOf(userProfile.breakfastReminderEnabled) }
    var breakfastTime by remember(userProfile) { mutableStateOf(userProfile.breakfastReminderTime) }
    
    var pPct by remember(userProfile) { mutableStateOf(userProfile.proteinPercent.toString()) }
    var cCarbPct by remember(userProfile) { mutableStateOf(userProfile.complexCarbsPercent.toString()) }
    var sugarPct by remember(userProfile) { mutableStateOf(userProfile.sugarPercent.toString()) }
    var uFatPct by remember(userProfile) { mutableStateOf(userProfile.unsaturatedFatPercent.toString()) }
    var sFatPct by remember(userProfile) { mutableStateOf(userProfile.saturatedFatPercent.toString()) }

    val totalPct = (pPct.toIntOrNull() ?: 0) + (cCarbPct.toIntOrNull() ?: 0) + 
                   (sugarPct.toIntOrNull() ?: 0) + (uFatPct.toIntOrNull() ?: 0) + 
                   (sFatPct.toIntOrNull() ?: 0)
    
    val isValid = totalPct == 100

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    if (nutritionViewModel.importBackup(content)) {
                        Toast.makeText(context, "Daten erfolgreich importiert!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Fehler beim Importieren.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Mein Profil", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        
        Card {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Persönliche Daten", fontWeight = FontWeight.Bold)
                AutoSelectTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Vorname") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(
                        value = age,
                        onValueChange = { age = it },
                        label = { Text("Alter") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Geschlecht", style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = gender == Gender.MALE, onClick = { gender = Gender.MALE })
                            Text("M")
                            RadioButton(selected = gender == Gender.FEMALE, onClick = { gender = Gender.FEMALE })
                            Text("W")
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AutoSelectTextField(
                        value = weight,
                        onValueChange = { weight = it },
                        label = { Text("Gewicht (kg)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next)
                    )
                    AutoSelectTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Größe (cm)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                    )
                }
            }
        }
        
        Card {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Dein Ziel & Kalorien", fontWeight = FontWeight.Bold)
                
                // Goal Selection
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoalChip(
                        selected = goal == UserGoal.LOSE_WEIGHT,
                        label = "Abnehmen",
                        onClick = { goal = UserGoal.LOSE_WEIGHT }
                    )
                    GoalChip(
                        selected = goal == UserGoal.MAINTAIN,
                        label = "Halten",
                        onClick = { goal = UserGoal.MAINTAIN }
                    )
                    GoalChip(
                        selected = goal == UserGoal.BUILD_MUSCLE,
                        label = "Aufbauen",
                        onClick = { goal = UserGoal.BUILD_MUSCLE }
                    )
                }

                if (goal != UserGoal.MAINTAIN) {
                    Column {
                        Text("Intensität: ${intensity} kcal", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = intensity.toFloatOrNull() ?: 500f,
                            onValueChange = { intensity = it.toInt().toString() },
                            valueRange = 300f..500f,
                            steps = 1
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tagesbudget (BMR + Ziel)", style = MaterialTheme.typography.labelSmall)
                        Text("${userProfile.calorieBudget} kcal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Nur Grundumsatz", style = MaterialTheme.typography.labelSmall)
                        Text("${userProfile.bmr.toInt()} kcal", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        
        Card {
            var expanded by remember { mutableStateOf(false) }
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Makroverteilung (%)", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                    }
                }
                
                if (expanded) {
                    MacroPercentInput(label = "Ungesättigte Fette", value = uFatPct, onValueChange = { uFatPct = it })
                    MacroPercentInput(label = "Gesättigte Fette", value = sFatPct, onValueChange = { sFatPct = it })
                    MacroPercentInput(label = "Komplexe KH", value = cCarbPct, onValueChange = { cCarbPct = it })
                    MacroPercentInput(label = "Zucker", value = sugarPct, onValueChange = { sugarPct = it })
                    MacroPercentInput(label = "Protein", value = pPct, onValueChange = { pPct = it })
                    
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Gesamt: $totalPct %", 
                        color = if (isValid) Color(0xFF2E7D32) else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("P: $pPct% · KH: ${(cCarbPct.toIntOrNull() ?: 0) + (sugarPct.toIntOrNull() ?: 0)}% · F: ${(uFatPct.toIntOrNull() ?: 0) + (sFatPct.toIntOrNull() ?: 0)}%", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        CategoryManagementCard(nutritionViewModel)
        HouseholdManagementSection(nutritionViewModel)
        
        Card {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("App-Einstellungen & Erinnerungen", fontWeight = FontWeight.Bold)
                
                ReminderSettingRow(
                    label = "An Wiegen erinnern",
                    enabled = weighInEnabled,
                    time = weighInTime,
                    onEnabledChange = { weighInEnabled = it },
                    onTimeChange = { weighInTime = it }
                )

                ReminderSettingRow(
                    label = "An Frühstück erinnern",
                    enabled = breakfastEnabled,
                    time = breakfastTime,
                    onEnabledChange = { breakfastEnabled = it },
                    onTimeChange = { breakfastTime = it }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tour bei jedem App-Start zeigen", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = nutritionViewModel.forceOnboardingOnStart,
                        onCheckedChange = { nutritionViewModel.setForceOnboarding(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                }
            }
        }
        
        Button(
            onClick = {
                viewModel.updateProfile(userProfile.copy(
                    firstName = firstName,
                    age = age.toIntOrNull() ?: userProfile.age,
                    weightKg = weight.toDoubleOrNull() ?: userProfile.weightKg,
                    heightCm = height.toIntOrNull() ?: userProfile.heightCm,
                    gender = gender,
                    goal = goal,
                    goalIntensity = intensity.toIntOrNull() ?: userProfile.goalIntensity,
                    weighInReminderEnabled = weighInEnabled,
                    weighInReminderTime = weighInTime,
                    breakfastReminderEnabled = breakfastEnabled,
                    breakfastReminderTime = breakfastTime,
                    proteinPercent = pPct.toIntOrNull() ?: userProfile.proteinPercent,
                    complexCarbsPercent = cCarbPct.toIntOrNull() ?: userProfile.complexCarbsPercent,
                    sugarPercent = sugarPct.toIntOrNull() ?: userProfile.sugarPercent,
                    unsaturatedFatPercent = uFatPct.toIntOrNull() ?: userProfile.unsaturatedFatPercent,
                    saturatedFatPercent = sFatPct.toIntOrNull() ?: userProfile.saturatedFatPercent
                ))
            },
            enabled = isValid && firstName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Profil speichern")
        }

        // --- DEVELOPER OPTIONS ---
        DeveloperOptionsSection(nutritionViewModel, viewModel, userProfile)

        Spacer(Modifier.height(8.dp))
        Text("Daten-Sicherung", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        try {
                            val json = nutritionViewModel.getBackupJson()
                            val file = File(context.cacheDir, "nutrition_backup_full.json")
                            file.writeText(json)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Komplett-Backup exportieren"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Export fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.FileUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Backup")
                }
                
                Button(
                    onClick = { importLauncher.launch("*/*") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
            }

            Button(
                onClick = {
                    try {
                        val json = nutritionViewModel.getCatalogJson()
                        val file = File(context.cacheDir, "nutrition_catalog.json")
                        file.writeText(json)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Katalog exportieren"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            ) {
                Icon(Icons.Default.Restaurant, null)
                Spacer(Modifier.width(8.dp))
                Text("Katalog exportieren")
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun GoalChip(selected: Boolean, label: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.padding(2.dp)
    )
}

@Composable
private fun DeveloperOptionsSection(
    vm: NutritionViewModel,
    profileVm: ProfileViewModel,
    profile: UserProfile
) {
    var expanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(if (expanded) "Entwickleroptionen ausblenden" else "Entwickleroptionen einblenden", style = MaterialTheme.typography.labelSmall)
        }

        if (expanded) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("⚙️ Debug & Kalkulation", fontWeight = FontWeight.Bold)
                    
                    Button(
                        onClick = { profileVm.updateProfile(profile.copy(setupCompleted = false)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Setup Wizard jetzt starten")
                    }

                    HorizontalDivider()
                    Text("Formel-Inspektor (Mifflin-St Jeor)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    
                    val constant = if (profile.gender == Gender.MALE) "+5" else "-161"
                    Text(
                        "BMR = (10 * ${profile.weightKg}) + (6.25 * ${profile.heightCm}) - (5 * ${profile.age}) $constant",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "= ${profile.bmr.toInt()} kcal Grundumsatz",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    val goalIntensity = profile.goalIntensity
                    val goalText = when(profile.goal) {
                        UserGoal.LOSE_WEIGHT -> "Abnehmen (-$goalIntensity)"
                        UserGoal.MAINTAIN -> "Halten (0)"
                        UserGoal.BUILD_MUSCLE -> "Aufbauen (+$goalIntensity)"
                    }
                    Text("Ziel: $goalText", style = MaterialTheme.typography.labelSmall)
                    Text("Finales Budget: ${profile.calorieBudget} kcal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                    HorizontalDivider()
                    Text("🤖 Gemini 3.x AI Integration", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    
                    var keyInput by remember { mutableStateOf(vm.geminiApiKey) }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { 
                            keyInput = it
                            vm.updateGeminiApiKey(it)
                        },
                        label = { Text("Gemini API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (keyInput.isEmpty()) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        singleLine = true
                    )
                    Text("Wird benötigt für die Bilderkennung im Tagebuch.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                    Spacer(Modifier.height(8.dp))
                    
                    Button(onClick = { vm.probeAiModels() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Verfügbare AI Modelle prüfen")
                    }

                    if (vm.availableAiModels.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            vm.availableAiModels.forEach { status ->
                                val model = status.modelName
                                val isAvailable = status.isAvailable
                                
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(
                                            selected = vm.selectedAiModel == model, 
                                            onClick = { if (isAvailable) vm.updateSelectedAiModel(model) },
                                            enabled = isAvailable
                                        )
                                        Text(
                                            text = model, 
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isAvailable) Color.Unspecified else Color.Gray,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (isAvailable) {
                                            Icon(Icons.Default.Check, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                        } else {
                                            Icon(Icons.Default.Close, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (!isAvailable && !status.errorMessage.isNullOrBlank()) {
                                        Text(
                                            text = status.errorMessage,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.Red,
                                            modifier = Modifier.padding(start = 48.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    Text("Stoffwechsel-Analyse", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    
                    val factor = profile.metabolicFactor
                    Text("Aktueller Faktor: ${"%.2f".format(factor)}", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Dieser Faktor vergleicht das theoretische Defizit mit dem realen Gewichtsverlust auf der Waage. 1.0 = exakte Übereinstimmung.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryManagementCard(vm: NutritionViewModel) {
    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<String?>(null) }
    var newCategoryName by remember { mutableStateOf("") }

    if (showAddDialog || categoryToEdit != null) {
        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                categoryToEdit = null
                newCategoryName = ""
            },
            title = { Text(if (categoryToEdit != null) "Kategorie bearbeiten" else "Neue Kategorie") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Name der Kategorie") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (categoryToEdit != null) {
                            vm.updateCategory(categoryToEdit!!, newCategoryName)
                        } else {
                            vm.addCategory(newCategoryName)
                        }
                        showAddDialog = false
                        categoryToEdit = null
                        newCategoryName = ""
                    },
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddDialog = false
                    categoryToEdit = null
                    newCategoryName = ""
                }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Card {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kategorien verwalten", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Kategorie hinzufügen")
                }
            }
            
            vm.categories.forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(cat, modifier = Modifier.weight(1f))
                    IconButton(onClick = { 
                        categoryToEdit = cat
                        newCategoryName = cat
                    }) {
                        Icon(Icons.Default.Edit, "Bearbeiten", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { vm.deleteCategory(cat) }) {
                        Icon(Icons.Default.Delete, "Löschen", tint = Color.Red, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HouseholdManagementSection(vm: NutritionViewModel) {
    val firebaseManager = vm.firebaseManager
    val user by firebaseManager.currentUser.collectAsState()
    val household by firebaseManager.household.collectAsState()
    val scope = rememberCoroutineScope()
    
    var householdName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }
    var showJoinDialog by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kollaboration & Haushalt", fontWeight = FontWeight.Bold)
            
            if (user == null) {
                var isLoggingIn by remember { mutableStateOf(false) }
                val context = LocalContext.current
                
                Text("Melde dich an, um Daten mit anderen zu teilen.")
                
                if (isLoggingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Button(onClick = { 
                        isLoggingIn = true
                        com.google.firebase.auth.FirebaseAuth.getInstance().signInAnonymously()
                            .addOnSuccessListener {
                                isLoggingIn = false
                                Toast.makeText(context, "Erfolgreich angemeldet!", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                isLoggingIn = false
                                val errorCode = (e as? FirebaseAuthException)?.errorCode ?: "Unbekannt"
                                Log.e("FirebaseAuth", "Login Fehler: ${e.message}, Code: $errorCode", e)
                                Toast.makeText(context, "Fehler ($errorCode): ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }) {
                        Text("Anonym anmelden (MVP)")
                    }
                }
            } else {
                Text("Haushalt: ${household?.name ?: "Keiner"}")
                
                if (household == null) {
                    Text("Du gehörst aktuell zu keinem Haushalt.")
                    OutlinedTextField(
                        value = householdName,
                        onValueChange = { householdName = it },
                        label = { Text("Haushaltsname") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        scope.launch {
                            try {
                                firebaseManager.createHousehold(householdName)
                            } catch (e: Exception) {
                                // Error handling
                            }
                        }
                    }, enabled = householdName.isNotBlank()) {
                        Text("Neuen Haushalt erstellen")
                    }
                    
                    HorizontalDivider()
                    
                    Button(onClick = { showJoinDialog = true }) {
                        Text("Einem Haushalt beitreten")
                    }
                } else {
                    Text("Einladungscode: ${household?.inviteCode}", color = MaterialTheme.colorScheme.primary)
                    Text("Mitglieder: ${household?.members?.size}")
                    
                    Button(onClick = { firebaseManager.signOut() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Abmelden / Haushalt verlassen")
                    }
                }
            }
        }
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Haushalt beitreten") },
            text = {
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { inviteCode = it },
                    label = { Text("Einladungscode") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        try {
                            firebaseManager.joinHousehold(inviteCode)
                            showJoinDialog = false
                        } catch (e: Exception) {
                            // Error handling
                        }
                    }
                }) { Text("Beitreten") }
            }
        )
    }
}

@Composable
private fun ReminderSettingRow(
    label: String,
    enabled: Boolean,
    time: String,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (String) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (enabled) {
                Text(text = "Um $time Uhr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        
        if (enabled) {
            var showTimePicker by remember { mutableStateOf(false) }
            if (showTimePicker) {
                TimePickerDialog(
                    initialTime = time,
                    onDismiss = { showTimePicker = false },
                    onConfirm = { 
                        onTimeChange(it)
                        showTimePicker = false
                    }
                )
            }
            
            TextButton(onClick = { showTimePicker = true }) {
                Text(time)
            }
        }
        
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.scale(0.8f)
        )
    }
}

@Composable
private fun TimePickerDialog(
    initialTime: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val parts = initialTime.split(":")
    var hour by remember { mutableStateOf(parts.getOrNull(0) ?: "08") }
    var minute by remember { mutableStateOf(parts.getOrNull(1) ?: "00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uhrzeit wählen") },
        text = {
            Row(
                Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hour,
                    onValueChange = { if (it.length <= 2) hour = it },
                    modifier = Modifier.width(64.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(":", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.headlineMedium)
                OutlinedTextField(
                    value = minute,
                    onValueChange = { if (it.length <= 2) minute = it },
                    modifier = Modifier.width(64.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val h = hour.padStart(2, '0')
                val m = minute.padStart(2, '0')
                onConfirm("$h:$m")
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun MacroPercentInput(
    label: String, 
    value: String, 
    onValueChange: (String) -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        AutoSelectTextField(
            value = value,
            onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) onValueChange(it) },
            modifier = Modifier.width(80.dp),
            singleLine = true,
            keyboardOptions = keyboardOptions
        )
    }
}
