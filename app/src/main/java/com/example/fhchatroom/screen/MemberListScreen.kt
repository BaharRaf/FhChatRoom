package com.example.fhchatroom.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Room
import com.example.fhchatroom.data.User
import kotlinx.coroutines.tasks.await

@Composable
fun MemberListScreen(roomId: String, onBack: () -> Unit) {
    var members by remember { mutableStateOf(listOf<User>()) }
    var errorMessage by remember { mutableStateOf("") }

    // Load members when roomId changes
    LaunchedEffect(roomId) {
        try {
            val firestore = Injection.instance()
            val roomDoc = firestore.collection("rooms").document(roomId).get().await()
            if (roomDoc.exists()) {
                val room = roomDoc.toObject(Room::class.java)
                val memberEmails = room?.members ?: emptyList()
                val loadedMembers = mutableListOf<User>()
                for (email in memberEmails) {
                    val userDoc = firestore.collection("users").document(email).get().await()
                    userDoc.toObject(User::class.java)?.let { loadedMembers.add(it) }
                }
                members = loadedMembers
            } else {
                errorMessage = "Room not found"
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load members: ${e.message}"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Members",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = Color.Red,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(members) { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${user.firstName} ${user.lastName}")
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (user.isOnline) Color.Green else Color.Gray,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
        Button(onClick = onBack, modifier = Modifier.align(Alignment.End)) {
            Text("Close")
        }
    }
}
