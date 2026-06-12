package com.nick.nutritiontracker.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.nick.nutritiontracker.data.Gender
import com.nick.nutritiontracker.data.UserProfile
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel
import java.io.File

@Composable
fun ProfileScreen(viewModel: ProfileViewModel, nutritionViewModel: NutritionViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    
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
                        modifier = Modifier.weight(1f)
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
                        modifier = Modifier.weight(1f)
                    )
                    AutoSelectTextField(
                        value = height,
                        onValueChange = { height = it },
                        label = { Text("Größe (cm)") },
                        modifier = Modifier.weight(1f)
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
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Ihr TDEE (Bürotätigkeit) liegt bei ca. ${userProfile.tdee.toInt()} kcal", style = MaterialTheme.typography.bodySmall)
            }
        }
        
        Card {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Makroverteilung (%)", fontWeight = FontWeight.Bold)
                MacroPercentInput("Protein", pPct) { pPct = it }
                MacroPercentInput("Komplexe KH", cCarbPct) { cCarbPct = it }
                MacroPercentInput("Zucker", sugarPct) { sugarPct = it }
                MacroPercentInput("Ungesättigte Fette", uFatPct) { uFatPct = it }
                MacroPercentInput("Gesättigte Fette", sFatPct) { sFatPct = it }
                
                Spacer(Modifier.height(8.dp))
                Text(
                    "Gesamt: $totalPct %", 
                    color = if (isValid) Color(0xFF2E7D32) else Color.Red,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
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
        
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    try {
                        val json = nutritionViewModel.getBackupJson()
                        val file = File(context.cacheDir, "nutrition_backup.json")
                        file.writeText(json)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Backup exportieren"))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export fehlgeschlagen: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.FileUpload, null)
                Spacer(Modifier.width(8.dp))
                Text("Exportieren")
            }
            
            Button(
                onClick = {
                    importLauncher.launch("*/*") // Best practice to use */* or application/json, but some pickers are picky
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) {
                Icon(Icons.Default.FileDownload, null)
                Spacer(Modifier.width(8.dp))
                Text("Importieren")
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MacroPercentInput(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        AutoSelectTextField(
            value = value,
            onValueChange = { if (it.isEmpty() || it.toIntOrNull() != null) onValueChange(it) },
            modifier = Modifier.width(80.dp),
            singleLine = true
        )
    }
}
