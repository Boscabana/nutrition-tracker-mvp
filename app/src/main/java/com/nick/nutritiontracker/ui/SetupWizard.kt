package com.nick.nutritiontracker.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.DietaryPreference
import com.nick.nutritiontracker.data.Gender
import com.nick.nutritiontracker.data.UserGoal
import com.nick.nutritiontracker.data.UserProfile
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import com.nick.nutritiontracker.viewmodel.ProfileViewModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SetupWizard(profileVm: ProfileViewModel, vm: NutritionViewModel) {
    var currentStep by remember { mutableIntStateOf(0) }
    
    // Setup state
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(Gender.MALE) }
    var age by remember { mutableStateOf("30") }
    var height by remember { mutableStateOf("175") }
    var weight by remember { mutableStateOf("75") }
    var diet by remember { mutableStateOf(DietaryPreference.NONE) }
    var goal by remember { mutableStateOf(UserGoal.MAINTAIN) }
    var intensity by remember { mutableIntStateOf(500) }

    val userProfile = UserProfile(
        firstName = name,
        age = age.toIntOrNull() ?: 30,
        heightCm = height.toIntOrNull() ?: 175,
        weightKg = weight.toDoubleOrNull() ?: 75.0,
        gender = gender,
        dietaryPreference = diet,
        goal = goal,
        goalIntensity = intensity,
        setupCompleted = false
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Progress Bar
            LinearProgressIndicator(
                progress = { (currentStep + 1) / 7f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            )

            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                slideOutHorizontally { width -> width } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    }, label = "SetupStepTransition"
                ) { step ->
                    when (step) {
                        0 -> NameStep(name) { name = it }
                        1 -> GenderAgeStep(gender, age, { gender = it }, { age = it })
                        2 -> StatsStep(height, weight, { height = it }, { weight = it })
                        3 -> DietStep(diet) { diet = it }
                        4 -> GoalStep(goal) { goal = it }
                        5 -> IntensityStep(goal, intensity) { intensity = it }
                        6 -> SummaryStep(userProfile)
                    }
                }
            }

            // Navigation Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 0) {
                    TextButton(onClick = { currentStep-- }) {
                        Text("Zurück")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentStep < 6) {
                            currentStep++
                        } else {
                            vm.setForceOnboarding(false)
                            profileVm.updateProfile(userProfile.copy(setupCompleted = true))
                        }
                    },
                    enabled = when(currentStep) {
                        0 -> name.isNotBlank()
                        1 -> age.toIntOrNull() != null
                        2 -> height.toIntOrNull() != null && weight.toDoubleOrNull() != null
                        else -> true
                    }
                ) {
                    Text(if (currentStep < 6) "Weiter" else "Fertig")
                    if (currentStep < 6) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NameStep(name: String, onNameChange: (String) -> Unit) {
    StepContainer(
        icon = Icons.Default.Person,
        title = "Wie ist dein Name?",
        subtitle = "Lass uns den Nutrition Tracker für dich personalisieren."
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Vorname") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )
    }
}

@Composable
private fun GenderAgeStep(
    gender: Gender,
    age: String,
    onGenderChange: (Gender) -> Unit,
    onAgeChange: (String) -> Unit
) {
    StepContainer(
        icon = Icons.Default.Wc,
        title = "Erzähl uns von dir",
        subtitle = "Diese Angaben helfen uns, deinen Grundumsatz genau zu berechnen."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Geschlecht", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                listOf(Gender.MALE to "Männlich", Gender.FEMALE to "Weiblich").forEach { (g, label) ->
                    FilterChip(
                        selected = gender == g,
                        onClick = { onGenderChange(g) },
                        label = { Text(label) },
                        leadingIcon = {
                            if (gender == g) Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                        }
                    )
                }
            }
            
            Spacer(Modifier.height(8.dp))
            
            OutlinedTextField(
                value = age,
                onValueChange = onAgeChange,
                label = { Text("Alter") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
        }
    }
}

@Composable
private fun StatsStep(
    height: String,
    weight: String,
    onHeightChange: (String) -> Unit,
    onWeightChange: (String) -> Unit
) {
    StepContainer(
        icon = Icons.Default.Straighten,
        title = "Körpermaße",
        subtitle = "Deine Größe und dein aktuelles Gewicht."
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = height,
                onValueChange = onHeightChange,
                label = { Text("Größe (cm)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
            )
            OutlinedTextField(
                value = weight,
                onValueChange = onWeightChange,
                label = { Text("Gewicht (kg)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)
            )
        }
    }
}

@Composable
private fun DietStep(current: DietaryPreference, onDietChange: (DietaryPreference) -> Unit) {
    StepContainer(
        icon = Icons.Default.Restaurant,
        title = "Deine Ernährung",
        subtitle = "Hast du besondere Vorlieben oder Einschränkungen?"
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DietOption(
                selected = current == DietaryPreference.NONE,
                title = "Allesesser",
                description = "Keine besonderen Einschränkungen.",
                onClick = { onDietChange(DietaryPreference.NONE) }
            )
            DietOption(
                selected = current == DietaryPreference.VEGETARIAN,
                title = "Vegetarisch",
                description = "Kein Fleisch oder Fisch.",
                onClick = { onDietChange(DietaryPreference.VEGETARIAN) }
            )
            DietOption(
                selected = current == DietaryPreference.VEGAN,
                title = "Vegan",
                description = "Rein pflanzliche Ernährung.",
                onClick = { onDietChange(DietaryPreference.VEGAN) }
            )
            DietOption(
                selected = current == DietaryPreference.LOW_CARB,
                title = "Low Carb",
                description = "Reduzierte Kohlenhydrate.",
                onClick = { onDietChange(DietaryPreference.LOW_CARB) }
            )
        }
    }
}

@Composable
private fun DietOption(selected: Boolean, title: String, description: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun GoalStep(currentGoal: UserGoal, onGoalChange: (UserGoal) -> Unit) {
    StepContainer(
        icon = Icons.Default.Flag,
        title = "Was ist dein Ziel?",
        subtitle = "Wir passen dein Kalorienbudget entsprechend an."
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GoalOption(
                selected = currentGoal == UserGoal.LOSE_WEIGHT,
                title = "Abnehmen",
                description = "Körperfett reduzieren durch ein Kaloriendefizit.",
                icon = Icons.AutoMirrored.Filled.TrendingDown,
                onClick = { onGoalChange(UserGoal.LOSE_WEIGHT) }
            )
            GoalOption(
                selected = currentGoal == UserGoal.MAINTAIN,
                title = "Gewicht halten",
                description = "Gesund ernähren und das aktuelle Gewicht stabilisieren.",
                icon = Icons.Default.HorizontalRule,
                onClick = { onGoalChange(UserGoal.MAINTAIN) }
            )
            GoalOption(
                selected = currentGoal == UserGoal.BUILD_MUSCLE,
                title = "Muskelaufbau",
                description = "Zunehmen und Kraft aufbauen durch einen Kalorienüberschuss.",
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                onClick = { onGoalChange(UserGoal.BUILD_MUSCLE) }
            )
        }
    }
}

@Composable
private fun IntensityStep(goal: UserGoal, intensity: Int, onIntensityChange: (Int) -> Unit) {
    if (goal == UserGoal.MAINTAIN) {
        StepContainer(
            icon = Icons.Default.DoneAll,
            title = "Perfekt!",
            subtitle = "Da du dein Gewicht halten möchtest, berechnen wir deinen Kalorienbedarf ohne Anpassung."
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Klicke auf Weiter für die Zusammenfassung.", textAlign = TextAlign.Center)
            }
        }
        return
    }

    StepContainer(
        icon = Icons.Default.Speed,
        title = if (goal == UserGoal.LOSE_WEIGHT) "Wie schnell willst du abnehmen?" else "Wie schnell willst du aufbauen?",
        subtitle = "Wähle dein tägliches Defizit bzw. deinen Überschuss (kcal)."
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                text = "${intensity} kcal",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            Slider(
                value = intensity.toFloat(),
                onValueChange = { onIntensityChange(it.toInt()) },
                valueRange = 300f..500f,
                steps = 1
            )
            
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Moderat (300)", style = MaterialTheme.typography.labelSmall)
                Text("Ambitioniert (500)", style = MaterialTheme.typography.labelSmall)
            }
            
            val recommendation = when {
                intensity <= 350 -> "Empfohlen für langfristige, gesunde Erfolge."
                intensity >= 450 -> "Erfordert hohe Disziplin."
                else -> "Ein guter Mittelweg."
            }
            Text(recommendation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SummaryStep(profile: UserProfile) {
    StepContainer(
        icon = Icons.Default.Calculate,
        title = "Deine Berechnung",
        subtitle = "Basierend auf der Mifflin-St Jeor Formel."
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Täglich verfügbares Budget:", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${profile.calorieBudget} kcal",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(16.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Grundumsatz (BMR):", style = MaterialTheme.typography.bodyMedium)
                    Text("${profile.bmr.toInt()} kcal", fontWeight = FontWeight.Bold)
                }
                
                val adj = profile.calorieBudget - profile.bmr.toInt()
                if (adj != 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (adj < 0) "Geplantes Defizit:" else "Geplanter Überschuss:", style = MaterialTheme.typography.bodyMedium)
                        Text("${if (adj > 0) "+" else ""}$adj kcal", fontWeight = FontWeight.Bold, color = if (adj < 0) Color.Red else Color(0xFF2E7D32))
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                Text(
                    "Wichtig: Aktivitätskalorien (Sport, Schritte) sind hier noch nicht enthalten. Diese werden im Tagebuch separat berücksichtigt.",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StepContainer(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        content()
    }
}

@Composable
private fun GoalOption(
    selected: Boolean,
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
