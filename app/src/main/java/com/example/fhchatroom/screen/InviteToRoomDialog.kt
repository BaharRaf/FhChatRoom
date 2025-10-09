package com.example.fhchatroom.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.User
import com.example.fhchatroom.viewmodel.RoomViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteToRoomDialog(
    roomId: String,
    roomName: String,
    currentMembers: List<String>,
    onDismiss: () -> Unit,
    roomViewModel: RoomViewModel = viewModel()
) {
    var users by remember { mutableStateOf(listOf<User>()) }
    var selectedEmail by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var inviteResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Load all users from Firestore
        try {
            val firestore = Injection.instance()
            val snapshot = firestore.collection("users").get().await()
            val allUsers = snapshot.documents.mapNotNull { it.toObject(User::class.java) }

            // Filter out current members and current user
            val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
            users = allUsers.filter { user ->
                !currentMembers.contains(user.email) &&
                        user.email != currentUserEmail &&
                        user.email.contains("@")
            }
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Invite to $roomName")
            }
        },
        text = {
            Column {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (users.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No users available to invite",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column {
                        Text(
                            "Select a user to invite:",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) {
                            items(users) { user ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedEmail == user.email)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    onClick = { selectedEmail = user.email }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${user.firstName} ${user.lastName}",
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = user.email,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (selectedEmail == user.email) {
                                            RadioButton(
                                                selected = true,
                                                onClick = null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        inviteResult?.let { result ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (result.startsWith("User invited"))
                                        MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Text(
                                    text = result,
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedEmail.isNotEmpty()) {
                        roomViewModel.inviteToRoom(roomId, selectedEmail) { success, message ->
                            inviteResult = message
                            if (success) {
                                // Clear selection after successful invite
                                selectedEmail = ""
                                // Refresh users list
                                users = users.filter { it.email != selectedEmail }
                            }
                        }
                    }
                },
                enabled = selectedEmail.isNotEmpty()
            ) {
                Text("Invite")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}