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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import android.util.Log
import com.google.firebase.auth.FirebaseAuthException
import com.nick.nutritiontracker.data.Gender
import com.nick.nutritiontracker.data.UserProfile
import com.nick.nutritiontracker.data.Household
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, nutritionViewModel: NutritionViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
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
    var goal by remember(userProfile) { mutableStateOf(userProfile.goalDescription) }
    var budget by remember(userProfile) { mutableStateOf(userProfile.calorieBudget.toString()) }
    
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
        Text("Profil & Ziele", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        
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
                
                Text("Grundumsatz (BMR): ${userProfile.bmr.toInt()} kcal", style = MaterialTheme.typography.bodySmall)

                AutoSelectTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("Ziel (z.B. Muskelaufbau)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Card {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tagesbudget", fontWeight = FontWeight.Bold)
                AutoSelectTextField(
                    value = budget,
                    onValueChange = { budget = it },
                    label = { Text("Kalorienbudget (Ziel)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                Text("Ihr TDEE (Bürotätigkeit) liegt bei ca. ${userProfile.tdee.toInt()} kcal", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Card {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Makroverteilung (%)", fontWeight = FontWeight.Bold)
                MacroPercentInput(
                    label = "Ungesättigte Fette", 
                    value = uFatPct, 
                    onValueChange = { uFatPct = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                MacroPercentInput(
                    label = "Gesättigte Fette", 
                    value = sFatPct, 
                    onValueChange = { sFatPct = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                MacroPercentInput(
                    label = "Komplexe KH", 
                    value = cCarbPct, 
                    onValueChange = { cCarbPct = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                MacroPercentInput(
                    label = "Zucker", 
                    value = sugarPct, 
                    onValueChange = { sugarPct = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                MacroPercentInput(
                    label = "Protein", 
                    value = pPct, 
                    onValueChange = { pPct = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                )
                
                Spacer(Modifier.height(8.dp))
                Text(
                    "Gesamt: $totalPct %", 
                    color = if (isValid) Color(0xFF2E7D32) else Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        CategoryManagementCard(nutritionViewModel)
        
        HouseholdManagementSection(nutritionViewModel)
        
        Button(
            onClick = {
                viewModel.updateProfile(UserProfile(
                    firstName = firstName,
                    age = age.toIntOrNull() ?: userProfile.age,
                    weightKg = weight.toDoubleOrNull() ?: userProfile.weightKg,
                    heightCm = height.toIntOrNull() ?: userProfile.heightCm,
                    gender = gender,
                    goalDescription = goal,
                    calorieBudget = budget.toIntOrNull() ?: userProfile.calorieBudget,
                    proteinPercent = pPct.toIntOrNull() ?: userProfile.proteinPercent,
                    complexCarbsPercent = cCarbPct.toIntOrNull() ?: userProfile.complexCarbsPercent,
                    sugarPercent = sugarPct.toIntOrNull() ?: userProfile.sugarPercent,
                    unsaturatedFatPercent = uFatPct.toIntOrNull() ?: userProfile.unsaturatedFatPercent,
                    saturatedFatPercent = sFatPct.toIntOrNull() ?: userProfile.saturatedFatPercent
                ))
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Profil speichern")
        }

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
                    Text("Komplett-Backup")
                }
                
                Button(
                    onClick = {
                        importLauncher.launch("*/*")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.FileDownload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Importieren")
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
                Text("Katalog exportieren (Artikel & Rezepte)")
            }
        }
        
        Spacer(Modifier.height(32.dp))
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
