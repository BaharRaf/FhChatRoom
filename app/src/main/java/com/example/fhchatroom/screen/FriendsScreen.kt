package com.example.fhchatroom.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.FriendshipStatus
import com.example.fhchatroom.data.User
import com.example.fhchatroom.viewmodel.FriendsViewModel
import com.example.fhchatroom.viewmodel.RoomViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onBack: () -> Unit = {},
    onStartDirectMessage: (String) -> Unit
) {
    val firestore = Injection.instance()
    val current = FirebaseAuth.getInstance().currentUser?.email
    var users by remember { mutableStateOf(listOf<User>()) }
    var reg: ListenerRegistration? by remember { mutableStateOf(null) }

    // Real-time users; only "signed up" (have an email that looks valid)
    DisposableEffect(Unit) {
        reg = firestore.collection("users")
            .addSnapshotListener { snap, _ ->
                users = snap?.documents
                    ?.mapNotNull { it.toObject(User::class.java) }
                    ?.filter { it.email.isNotBlank() && it.email.contains("@") }
                    ?: emptyList()
            }
        onDispose { reg?.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("People") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        // Scrollable people list
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val filtered = users
                .filter { it.email != current }
                .sortedBy { (it.firstName + it.lastName + it.email).lowercase() }

            items(filtered, key = { it.email }) { user ->
                AllUsersItem(user = user, onStartDirectMessage = onStartDirectMessage)
            }
        }
    }
}

// Backwards-compat overload (if some old route still calls FriendsScreen(roomId,...))
@Composable
fun FriendsScreen(roomId: String, onBack: () -> Unit) {
    FriendsScreen(onBack = onBack, onStartDirectMessage = { /* no-op here */ })
}

@Composable
private fun AllUsersItem(
    user: User,
    onStartDirectMessage: (String) -> Unit,
    friendsViewModel: FriendsViewModel = viewModel(),
    rooms: RoomViewModel = viewModel()
) {
    var friendshipStatus by remember { mutableStateOf<FriendshipStatus?>(null) }

    LaunchedEffect(user.email) {
        friendsViewModel.getFriendshipStatus(user.email) { status ->
            friendshipStatus = status
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar (initials)
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${user.firstName.firstOrNull() ?: ""}${user.lastName.firstOrNull() ?: ""}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${user.firstName} ${user.lastName}".trim(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = user.email,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                when (friendshipStatus) {
                    FriendshipStatus.FRIENDS -> {
                        AssistChip(
                            onClick = { },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Friends", fontSize = 12.sp)
                                }
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = Color.Green.copy(alpha = 0.2f),
                                labelColor = Color.Green
                            )
                        )
                    }
                    FriendshipStatus.REQUEST_SENT -> {
                        AssistChip(onClick = { }, label = { Text("Sent", fontSize = 12.sp) })
                    }
                    FriendshipStatus.REQUEST_RECEIVED -> {
                        AssistChip(onClick = { }, label = { Text("Respond", fontSize = 12.sp) })
                    }
                    FriendshipStatus.NOT_FRIENDS, null -> {
                        Button(
                            onClick = { friendsViewModel.sendFriendRequest(user) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Friend", fontSize = 12.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        // Create/open DM ONLY when user initiates it
                        rooms.openOrCreateDirectRoom(user.email) { dmRoomId ->
                            onStartDirectMessage(dmRoomId)
                        }
                    }
                ) {
                    Text("Message", fontSize = 12.sp)
                }
            }
        }
    }
}
