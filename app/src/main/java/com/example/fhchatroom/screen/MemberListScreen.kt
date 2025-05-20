package com.example.fhchatroom.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Room
import com.example.fhchatroom.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun MemberListScreen(
    roomId: String,
    onBack: () -> Unit,
    onLeaveRoom: () -> Unit = {}
) {
    var members by remember { mutableStateOf(listOf<User>()) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

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
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${user.firstName} ${user.lastName}")
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color = if (user.isOnline) Color.Green else Color.Gray, shape = CircleShape)
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val firestore = Injection.instance()
                            val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
                            if (currentUserEmail != null) {
                                firestore.collection("rooms").document(roomId)
                                    .update("members", FieldValue.arrayRemove(currentUserEmail))
                                    .await()
                            }
                            // After leaving, navigate back to home (room list)
                            onLeaveRoom()
                        } catch (e: Exception) {
                            errorMessage = "Failed to leave room: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Leave Room")
            }
            Button(onClick = onBack) {
                Text("Close")
            }
            }
        }
}