package com.nick.nutritiontracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nick.nutritiontracker.viewmodel.NutritionViewModel

@Composable
fun CommunityScreen(vm: NutritionViewModel) {
    val household by vm.firebaseManager.household.collectAsState()
    val members = vm.householdMembers
    val currentUser by vm.firebaseManager.currentUser.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Community", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        if (household == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Tritt einem Haushalt bei, um dich mit anderen zu verbinden.")
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(household!!.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Text("Einladungscode: ${household!!.inviteCode}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }

            Text("Haushaltsmitglieder", fontWeight = FontWeight.Bold)
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(members) { member ->
                    val uid = member["uid"] ?: ""
                    val name = member["name"] ?: "Unbekannt"
                    val isMe = uid == currentUser?.uid

                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Person, null, tint = if (isMe) MaterialTheme.colorScheme.primary else Color.Gray)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (isMe) "$name (Du)" else name,
                                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
