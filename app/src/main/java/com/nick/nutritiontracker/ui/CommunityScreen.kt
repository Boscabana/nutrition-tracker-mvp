package com.nick.nutritiontracker.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.data.*
import com.nick.nutritiontracker.viewmodel.NutritionViewModel
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CommunityScreen(vm: NutritionViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Haushalts-Feed, 1 = App-Communities & Entdecken

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Haushalts-Feed")
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("App-Communities")
                    }
                }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                HouseholdFeedTab(vm = vm)
            } else {
                PublicCommunityFeedTab(vm = vm)
            }
        }
    }
}

// ==========================================
// TAB 1: HAUSHALTS-FEED & CHAT
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HouseholdFeedTab(vm: NutritionViewModel) {
    val household by vm.firebaseManager.household.collectAsState()
    val members = vm.householdMembers
    val currentUser by vm.firebaseManager.currentUser.collectAsState()
    val feed = vm.householdFeed
    val context = LocalContext.current

    var textInput by remember { mutableStateOf("") }
    var showShareSheet by remember { mutableStateOf(false) }
    var showMembersExpand by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val json = remember { Json { ignoreUnknownKeys = true } }

    LaunchedEffect(Unit) {
        vm.refreshHouseholdMembers()
    }

    LaunchedEffect(feed.size) {
        if (feed.isNotEmpty()) {
            listState.animateScrollToItem(feed.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        if (household == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tritt einem Haushalt bei, um dich mit deinen Haushaltsmitgliedern auszutauschen.")
            }
        } else {
            // Compact Household Banner Bar
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showMembersExpand = !showMembersExpand }
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = household!!.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Einladungscode", household!!.inviteCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Code kopiert!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Code: ${household!!.inviteCode}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    AnimatedVisibility(visible = showMembersExpand) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            members.forEach { member ->
                                val uid = member["uid"] ?: ""
                                val name = member["name"] ?: "Mitglied"
                                val isMe = uid == currentUser?.uid
                                AssistChip(
                                    onClick = {},
                                    label = { Text(if (isMe) "$name (Du)" else name, style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = { Icon(Icons.Default.Person, null, modifier = Modifier.size(12.dp)) },
                                    modifier = Modifier.height(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Chat Messages Feed
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (feed.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Noch keine Nachrichten im Haushalts-Feed.\nSchreibe eine Nachricht oder teile ein Rezept!",
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(feed, key = { it.id }) { msg ->
                            val isMe = msg.senderUid == currentUser?.uid
                            ChatBubbleItem(
                                msg = msg,
                                isMe = isMe,
                                json = json,
                                vm = vm
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Message Input Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                IconButton(onClick = { showShareSheet = true }) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Rezept/Artikel teilen", tint = MaterialTheme.colorScheme.primary)
                }

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Nachricht an den Haushalt...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.isNotBlank()) {
                            vm.sendHouseholdTextMessage(textInput)
                            textInput = ""
                        }
                    })
                )

                Spacer(Modifier.width(6.dp))

                IconButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            vm.sendHouseholdTextMessage(textInput)
                            textInput = ""
                        }
                    },
                    enabled = textInput.isNotBlank()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Senden", tint = if (textInput.isNotBlank()) MaterialTheme.colorScheme.primary else Color.Gray)
                }
            }
        }
    }

    if (showShareSheet) {
        ShareToHouseholdFeedSheet(vm = vm, onDismiss = { showShareSheet = false })
    }
}

// ==========================================
// TAB 2: PUBLIC OFFICIAL COMMUNITIES & DISCOVER FEED
// ==========================================
@Composable
private fun PublicCommunityFeedTab(vm: NutritionViewModel) {
    val posts = vm.communityPosts
    val selectedTopic = vm.selectedCommunityTopic
    val followedTopics = vm.followedTopics
    val currentUser by vm.firebaseManager.currentUser.collectAsState()

    var showChannelsOverview by remember { mutableStateOf(false) }
    var showPublishAdminDialog by remember { mutableStateOf(false) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    val filteredPosts = remember(posts, selectedTopic, followedTopics) {
        when (selectedTopic) {
            "Gefolgt" -> posts.filter { followedTopics.contains(it.topic) }
            null -> posts
            else -> posts.filter { it.topic == selectedTopic }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Compact Redaktion Action Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPublishAdminDialog = true },
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "👑 Als Entwickler/Redaktion ein Rezept veröffentlichen",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }

        // Channels Section Accordion / Header
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showChannelsOverview = !showChannelsOverview },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Explore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Kanäle (${followedTopics.size}/${OFFICIAL_COMMUNITIES.size} gefolgt)",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (showChannelsOverview) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        AnimatedVisibility(visible = showChannelsOverview) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Alle offiziellen Kanäle", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    OFFICIAL_COMMUNITIES.forEach { topic ->
                        val isFollowed = followedTopics.contains(topic.name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.selectedCommunityTopic = topic.name },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(topic.icon, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(topic.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text(topic.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Button(
                                onClick = { vm.toggleFollowTopic(topic.name) },
                                colors = if (isFollowed) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) else ButtonDefaults.buttonColors(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(if (isFollowed) "✓ Gefolgt" else "+ Folgen", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Topic Filters Chips Row (Compact height)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedTopic == null,
                onClick = { vm.selectedCommunityTopic = null },
                label = { Text("⚡ Alle Rezepte", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )

            FilterChip(
                selected = selectedTopic == "Gefolgt",
                onClick = { vm.selectedCommunityTopic = "Gefolgt" },
                label = { Text("⭐ Meine Kanäle (${followedTopics.size})", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(28.dp)
            )

            OFFICIAL_COMMUNITIES.forEach { topic ->
                val isTopicFollowed = followedTopics.contains(topic.name)
                FilterChip(
                    selected = selectedTopic == topic.name,
                    onClick = { vm.selectedCommunityTopic = topic.name },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(topic.name, style = MaterialTheme.typography.labelSmall)
                            if (isTopicFollowed) {
                                Spacer(Modifier.width(4.dp))
                                Text("✓", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    },
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        HorizontalDivider()

        // Community Feed Posts List
        if (filteredPosts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (selectedTopic == "Gefolgt") "Du folgst noch keinen Kanälen.\nKlicke oben auf ein Thema, um ihm zu folgen!"
                    else "Aktuell noch keine offiziellen Rezepte in diesem Channel.\nNeue geprüfte Rezepte folgen in Kürze!",
                    color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredPosts, key = { it.id }) { post ->
                    CommunityPostCard(
                        post = post,
                        currentUserUid = currentUser?.uid ?: "",
                        vm = vm,
                        json = json
                    )
                }
            }
        }
    }

    if (showPublishAdminDialog) {
        PublishOfficialRecipeDialog(
            vm = vm,
            onDismiss = { showPublishAdminDialog = false }
        )
    }
}

// ==========================================
// COMPONENTS: CHAT BUBBLE & POST CARD
// ==========================================
@Composable
private fun ChatBubbleItem(
    msg: HouseholdChatMessage,
    isMe: Boolean,
    json: Json,
    vm: NutritionViewModel
) {
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm · dd.MM").withZone(ZoneId.systemDefault()) }
    val timeStr = remember(msg.timestamp) { formatter.format(Instant.ofEpochMilli(msg.timestamp)) }
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isMe) "Du" else msg.senderName,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        Spacer(Modifier.height(2.dp))

        Surface(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isMe) 12.dp else 2.dp,
                bottomEnd = if (isMe) 2.dp else 12.dp
            ),
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (msg.type == ChatMessageType.TEXT) {
                    Text(
                        text = msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (msg.type == ChatMessageType.RECIPE && !msg.payloadJson.isNullOrBlank()) {
                    val recipeData = remember(msg.payloadJson) {
                        try { json.decodeFromString<RecipeData>(msg.payloadJson) } catch (e: Exception) { null }
                    }
                    if (recipeData != null) {
                        Text(msg.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(recipeData.meal.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                }
                                Text(
                                    "${recipeData.meal.kcalPerServing.toInt()} kcal/Portion · ${recipeData.meal.ingredients.size} Zutaten",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            vm.pendingRecipeImport = recipeData
                                            Toast.makeText(context, "Rezept-Import gestartet!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.SoupKitchen, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Importieren", style = MaterialTheme.typography.labelSmall)
                                    }
                                    Button(
                                        onClick = {
                                            vm.addMealToPool(recipeData.meal, recipeData.meal.servings)
                                            Toast.makeText(context, "'${recipeData.meal.name}' in den Pool gelegt!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("In Pool", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                } else if (msg.type == ChatMessageType.FOOD && !msg.payloadJson.isNullOrBlank()) {
                    val food = remember(msg.payloadJson) {
                        try { json.decodeFromString<FoodItemEntity>(msg.payloadJson) } catch (e: Exception) { null }
                    }
                    if (food != null) {
                        Text(msg.text, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(food.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                }
                                Text(
                                    "${food.kcalPer100g.toInt()} kcal/100g · P: ${food.proteinPer100g.toInt()}g · KH: ${food.carbsPer100g.toInt()}g · F: ${food.fatPer100g.toInt()}g",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Button(
                                    onClick = {
                                        vm.addFood(
                                            food.name, food.kcalPer100g, food.proteinPer100g,
                                            food.carbsPer100g, food.sugarPer100g, food.fatPer100g,
                                            food.saturatedFatPer100g, food.alcoholPercent, food.baseUnit,
                                            food.portions, food.packages, food.barcode, food.brand, food.category,
                                            isGeneric = food.isGeneric, parentId = food.parentId, store = food.store,
                                            isPantryItem = food.isPantryItem
                                        )
                                        Toast.makeText(context, "'${food.name}' importiert!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Download, null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Artikel importieren", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommunityPostCard(
    post: CommunityPost,
    currentUserUid: String,
    vm: NutritionViewModel,
    json: Json
) {
    val formatter = remember { DateTimeFormatter.ofPattern("dd.MM, HH:mm").withZone(ZoneId.systemDefault()) }
    val dateStr = remember(post.timestamp) { formatter.format(Instant.ofEpochMilli(post.timestamp)) }
    val isLikedByMe = remember(post.likedByUids, currentUserUid) { post.likedByUids.contains(currentUserUid) }
    val household by vm.firebaseManager.household.collectAsState()
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header: Author Avatar & Name & Topic
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "N",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.authorName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 2.dp)
                        ) {
                            Text(
                                "Geprüft",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }

                AssistChip(
                    onClick = { vm.selectedCommunityTopic = post.topic },
                    label = { Text(post.topic, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(24.dp)
                )
            }

            // Post Text / Caption
            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Attached Content Card (Recipe or Food)
            if (post.payloadType == "RECIPE" && !post.payloadJson.isNullOrBlank()) {
                val recipeData = remember(post.payloadJson) {
                    try { json.decodeFromString<RecipeData>(post.payloadJson) } catch (e: Exception) { null }
                }
                if (recipeData != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(recipeData.meal.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "${recipeData.meal.kcalPerServing.toInt()} kcal/Portion · P: ${(recipeData.meal.totalProtein / recipeData.meal.servings).toInt()}g · KH: ${(recipeData.meal.totalCarbs / recipeData.meal.servings).toInt()}g · F: ${(recipeData.meal.totalFat / recipeData.meal.servings).toInt()}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        vm.pendingRecipeImport = recipeData
                                        Toast.makeText(context, "Rezept '${recipeData.meal.name}' wird importiert!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.SoupKitchen, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Importieren")
                                }
                                if (household != null) {
                                    Button(
                                        onClick = {
                                            vm.addMealToPool(recipeData.meal, recipeData.meal.servings)
                                            Toast.makeText(context, "In den Haushalts-Pool gelegt!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("In Pool")
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (post.payloadType == "FOOD" && !post.payloadJson.isNullOrBlank()) {
                val food = remember(post.payloadJson) {
                    try { json.decodeFromString<FoodItemEntity>(post.payloadJson) } catch (e: Exception) { null }
                }
                if (food != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(food.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                "${food.kcalPer100g.toInt()} kcal/100g · P: ${food.proteinPer100g.toInt()}g · KH: ${food.carbsPer100g.toInt()}g · F: ${food.fatPer100g.toInt()}g",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Button(
                                onClick = {
                                    vm.addFood(
                                        food.name, food.kcalPer100g, food.proteinPer100g,
                                        food.carbsPer100g, food.sugarPer100g, food.fatPer100g,
                                        food.saturatedFatPer100g, food.alcoholPercent, food.baseUnit,
                                        food.portions, food.packages, food.barcode, food.brand, food.category,
                                        isGeneric = food.isGeneric, parentId = food.parentId, store = food.store,
                                        isPantryItem = food.isPantryItem
                                    )
                                    Toast.makeText(context, "'${food.name}' importiert!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Artikel importieren")
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Social Engagement Footer (Like)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = { vm.toggleLikeCommunityPost(post) }) {
                    Icon(
                        imageVector = if (isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Gefällt mir",
                        tint = if (isLikedByMe) Color.Red else MaterialTheme.colorScheme.outline
                    )
                }
                Text(
                    text = "${post.likesCount}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ShareToHouseholdFeedSheet(
    vm: NutritionViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val meals = vm.meals
    val foods = vm.foods

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("In den Haushalts-Feed teilen") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Rezept") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Artikel") })
                }

                if (selectedTab == 0) {
                    if (meals.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Keine eigenen Rezepte vorhanden.")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(meals) { meal ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.shareRecipeToHouseholdFeed(meal)
                                            onDismiss()
                                        }
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(meal.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("${meal.kcalPerServing.toInt()} kcal · ${meal.ingredients.size} Zutaten", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (foods.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Keine Artikel vorhanden.")
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(foods) { food ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.shareFoodToHouseholdFeed(food)
                                            onDismiss()
                                        }
                                ) {
                                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Inventory2, null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(food.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                            Text("${food.kcalPer100g.toInt()} kcal/100g", style = MaterialTheme.typography.labelSmall)
                                        }
                                        Icon(Icons.AutoMirrored.Filled.Send, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishOfficialRecipeDialog(
    vm: NutritionViewModel,
    onDismiss: () -> Unit
) {
    var selectedTopic by remember { mutableStateOf(OFFICIAL_COMMUNITIES.first().name) }
    var captionText by remember { mutableStateOf("") }
    var selectedMeal by remember { mutableStateOf<MealEntity?>(null) }

    var expandedTopicDropdown by remember { mutableStateOf(false) }
    var showMealPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("👑 Offizielles Rezept veröffentlichen") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Topic Selector
                ExposedDropdownMenuBox(
                    expanded = expandedTopicDropdown,
                    onExpandedChange = { expandedTopicDropdown = !expandedTopicDropdown }
                ) {
                    OutlinedTextField(
                        value = selectedTopic,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ziel-Community / Kanal") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTopicDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTopicDropdown,
                        onDismissRequest = { expandedTopicDropdown = false }
                    ) {
                        OFFICIAL_COMMUNITIES.forEach { topic ->
                            DropdownMenuItem(
                                text = { Text(topic.name) },
                                onClick = {
                                    selectedTopic = topic.name
                                    expandedTopicDropdown = false
                                }
                            )
                        }
                    }
                }

                // Selected Recipe
                if (selectedMeal != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RestaurantMenu, null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selectedMeal!!.name, fontWeight = FontWeight.Bold)
                                Text("${selectedMeal!!.kcalPerServing.toInt()} kcal · ${selectedMeal!!.ingredients.size} Zutaten", style = MaterialTheme.typography.labelSmall)
                            }
                            IconButton(onClick = { selectedMeal = null }) { Icon(Icons.Default.Close, null) }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showMealPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Rezept aus eigener Bibliothek wählen")
                    }
                }

                // Description
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    label = { Text("Kurze Beschreibung / Empfehlung") },
                    placeholder = { Text("z.B. Saftig, eiweißreich und ideal für Meal Prep!") },
                    modifier = Modifier.fillMaxWidth().height(90.dp)
                )
            }
        },
        confirmButton = {
            var isPublishing by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    if (selectedMeal != null && !isPublishing) {
                        isPublishing = true
                        vm.publishOfficialCommunityPost(
                            topic = selectedTopic,
                            text = captionText,
                            meal = selectedMeal!!
                        ) { success, errorMsg ->
                            isPublishing = false
                            if (success) {
                                Toast.makeText(context, "Rezept '${selectedMeal!!.name}' veröffentlicht!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Fehler: ${errorMsg ?: "Fehlende Berechtigung"}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = selectedMeal != null && !isPublishing
            ) {
                Text(if (isPublishing) "Veröffentliche..." else "Veröffentlichen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )

    if (showMealPicker) {
        AlertDialog(
            onDismissRequest = { showMealPicker = false },
            title = { Text("Rezept aus deiner Bibliothek wählen") },
            text = {
                if (vm.meals.isEmpty()) {
                    Text("Keine Rezepte vorhanden. Erstelle erst ein Rezept im 'Mahlzeiten'-Tab.")
                } else {
                    LazyColumn(Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(vm.meals) { meal ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedMeal = meal
                                        showMealPicker = false
                                    }
                            ) {
                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.RestaurantMenu, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(meal.name, fontWeight = FontWeight.Bold)
                                        Text("${meal.kcalPerServing.toInt()} kcal/Portion · ${meal.ingredients.size} Zutaten", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMealPicker = false }) { Text("Schließen") } }
        )
    }
}
