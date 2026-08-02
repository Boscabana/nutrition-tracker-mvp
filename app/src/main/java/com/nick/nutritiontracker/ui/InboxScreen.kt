package com.nick.nutritiontracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.FoodItemEntity
import com.nick.nutritiontracker.data.InboxMessage
import com.nick.nutritiontracker.data.MessageType
import com.nick.nutritiontracker.data.RecipeData
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(vm: NutritionViewModel, onBack: () -> Unit) {
    val messages = vm.inboxMessages
    val scope = rememberCoroutineScope()
    val json = Json { ignoreUnknownKeys = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Postfach") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (messages.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Dein Postfach ist leer.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageItem(
                        message = msg,
                        onImport = {
                            try {
                                if (msg.type == MessageType.RECIPE) {
                                    val recipe = json.decodeFromString<RecipeData>(msg.payloadJson)
                                    vm.pendingRecipeImport = recipe
                                    // NutritionApp handles the dialog
                                } else if (msg.type == MessageType.FOOD) {
                                    val food = json.decodeFromString<FoodItemEntity>(msg.payloadJson)
                                    vm.addFood(
                                        food.name, food.kcalPer100g, food.proteinPer100g,
                                        food.carbsPer100g, food.sugarPer100g, food.fatPer100g,
                                        food.saturatedFatPer100g, food.alcoholPercent, food.baseUnit,
                                        food.portions, food.packages, food.barcode, food.brand, food.category,
                                        isGeneric = food.isGeneric, parentId = food.parentId, store = food.store,
                                        isPantryItem = food.isPantryItem
                                    )
                                }
                                vm.markMessageAsRead(msg.id)
                            } catch (e: Exception) {
                                // Error
                            }
                        },
                        onDelete = {
                            vm.deleteInboxMessage(msg.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageItem(
    message: InboxMessage,
    onImport: () -> Unit,
    onDelete: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM, HH:mm").withZone(ZoneId.systemDefault()) }
    val dateStr = formatter.format(Instant.ofEpochMilli(message.timestamp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (message.isRead) CardDefaults.cardColors() else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (message.type == MessageType.RECIPE) Icons.Default.RestaurantMenu else Icons.Default.Inventory2,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (message.type == MessageType.RECIPE) "Neues Rezept" else "Neuer Artikel",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            
            Text(
                text = "Von ${message.fromName}",
                style = MaterialTheme.typography.bodyMedium
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDelete) {
                    Text("Löschen", color = Color.Red)
                }
                Button(onClick = onImport) {
                    Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Importieren")
                }
            }
        }
    }
}
