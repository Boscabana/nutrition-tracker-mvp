package com.nick.nutritiontracker.ui

import androidx.compose.foundation.clickable
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
import com.nick.nutritiontracker.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecipeComparisonDialog(
    comparison: RecipeComparisonResult,
    onDismiss: () -> Unit,
    onAdopt: (adoptIngredientsAndAmounts: Boolean, adoptInstructions: Boolean) -> Unit
) {
    var adoptIngredients by remember {
        mutableStateOf(comparison.hasIngredientNameDiff || comparison.hasAmountDiff || comparison.isMissingLocally)
    }
    var adoptInstructions by remember {
        mutableStateOf(comparison.hasInstructionDiff || comparison.isMissingLocally)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CompareArrows,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = comparison.remoteMealName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (comparison.remoteAuthor.isNotBlank()) {
                            Text(
                                text = "Rezept im Haushalt (von ${comparison.remoteAuthor})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Schließen")
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Badges / Summary Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (comparison.isMissingLocally) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Neu in deiner Bibliothek") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    } else if (comparison.isIdentical) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Identisch mit deinem Rezept") },
                            leadingIcon = { Icon(Icons.Default.Check, null, tint = Color(0xFF2E7D32)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFE8F5E9))
                        )
                    } else {
                        if (comparison.hasIngredientNameDiff) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Artikel abweichend") },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFFFF3E0), labelColor = Color(0xFFE65100))
                            )
                        }
                        if (comparison.hasAmountDiff) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Mengen abweichend") },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFFFF8E1), labelColor = Color(0xFFF57F17))
                            )
                        }
                        if (comparison.hasInstructionDiff) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Anleitung abweichend") },
                                colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFFF3E5F5), labelColor = Color(0xFF7B1FA2))
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()

                // Content comparison list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Section 1: Ingredients & Amounts comparison
                    item {
                        Text(
                            text = "🥗 Zutaten & Mengen (pro Portion)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (comparison.ingredientDiffs.isEmpty()) {
                        item {
                            Text("Keine Zutaten angegeben.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        items(comparison.ingredientDiffs) { diff ->
                            IngredientDiffRow(diff)
                        }
                    }

                    // Section 2: Instructions comparison
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "📖 Rezept-Anleitung / Schritte",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    item {
                        InstructionDiffCard(
                            remoteSteps = comparison.remoteRecipeSteps,
                            localSteps = comparison.localRecipeSteps,
                            isMissingLocally = comparison.isMissingLocally
                        )
                    }
                }

                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                // Selective adoption section
                Text(
                    text = "Übernehmen-Optionen",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { adoptIngredients = !adoptIngredients }
                ) {
                    Checkbox(
                        checked = adoptIngredients,
                        onCheckedChange = { adoptIngredients = it }
                    )
                    Column {
                        Text(
                            text = "Zutaten & Mengen übernehmen",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Aktualisiert deine lokale Zutatenliste und Portionsmengen.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { adoptInstructions = !adoptInstructions }
                ) {
                    Checkbox(
                        checked = adoptInstructions,
                        onCheckedChange = { adoptInstructions = it }
                    )
                    Column {
                        Text(
                            text = "Anleitung übernehmen",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Übernimmt Kochschritte & Zubereitungstext.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Abbrechen")
                    }

                    Button(
                        onClick = { onAdopt(adoptIngredients, adoptInstructions) },
                        enabled = adoptIngredients || adoptInstructions,
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.DownloadDone, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (comparison.isMissingLocally) "Hinzufügen" else "Übernehmen")
                    }
                }
            }
        }
    }
}

@Composable
private fun IngredientDiffRow(diff: IngredientDiff) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = when (diff.status) {
            IngredientDiffStatus.MATCH -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            IngredientDiffStatus.AMOUNT_DIFF -> Color(0xFFFFF8E1)
            IngredientDiffStatus.ADDED_IN_REMOTE -> Color(0xFFE3F2FD)
            IngredientDiffStatus.MISSING_IN_REMOTE -> Color(0xFFFFEBEE)
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (diff.status) {
                    IngredientDiffStatus.MATCH -> Icons.Default.CheckCircle
                    IngredientDiffStatus.AMOUNT_DIFF -> Icons.Default.Tune
                    IngredientDiffStatus.ADDED_IN_REMOTE -> Icons.Default.Add
                    IngredientDiffStatus.MISSING_IN_REMOTE -> Icons.Default.Remove
                },
                contentDescription = null,
                tint = when (diff.status) {
                    IngredientDiffStatus.MATCH -> Color(0xFF2E7D32)
                    IngredientDiffStatus.AMOUNT_DIFF -> Color(0xFFF57F17)
                    IngredientDiffStatus.ADDED_IN_REMOTE -> Color(0xFF1976D2)
                    IngredientDiffStatus.MISSING_IN_REMOTE -> Color(0xFFC62828)
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = diff.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (diff.localAmountDisplay != null) {
                        Text(
                            text = "Lokal: ${diff.localAmountDisplay}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    if (diff.remoteAmountDisplay != null) {
                        Text(
                            text = "Haushalt: ${diff.remoteAmountDisplay}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (diff.status == IngredientDiffStatus.AMOUNT_DIFF || diff.status == IngredientDiffStatus.ADDED_IN_REMOTE) FontWeight.Bold else FontWeight.Normal,
                            color = if (diff.status == IngredientDiffStatus.ADDED_IN_REMOTE) Color(0xFF1976D2) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionDiffCard(
    remoteSteps: List<RecipeStepEntity>,
    localSteps: List<RecipeStepEntity>,
    isMissingLocally: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FormatListNumbered, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Haushalts-Anleitung (${remoteSteps.size} Schritt${if (remoteSteps.size == 1) "" else "e"})",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
                if (!isMissingLocally) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Deine Schritte: ${localSteps.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (remoteSteps.isEmpty()) {
                Text(
                    text = "Keine Kochschritte im Haushalts-Rezept hinterlegt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                remoteSteps.sortedBy { it.stepNumber }.forEach { step ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "${step.stepNumber}.",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(20.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            if (step.title.isNotBlank()) {
                                Text(
                                    text = step.title,
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (step.description.isNotBlank()) {
                                Text(
                                    text = step.description,
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
