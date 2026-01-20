package com.example.fhchatroom.screen

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.FriendshipStatus
import com.example.fhchatroom.data.Result
import com.example.fhchatroom.data.Room
import com.example.fhchatroom.data.User
import com.example.fhchatroom.viewmodel.FriendsViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.derivedStateOf
import com.example.fhchatroom.data.Friend

@Composable
fun MemberListScreen(
    roomId: String,
    onBack: () -> Unit,
    onLeaveRoom: () -> Unit = {},
    friendsViewModel: FriendsViewModel = viewModel(),
    onStartDirectMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var members by remember { mutableStateOf(listOf<User>()) }
    var room by remember { mutableStateOf<Room?>(null) }
    var showInviteDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val firestore = Injection.instance()
    val database = FirebaseDatabase.getInstance()
    val activeListeners = remember { mutableMapOf<String, ValueEventListener>() }
    var roomListener: ListenerRegistration? by remember { mutableStateOf(null) }
    val TAG = "MemberListScreen"
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

    // Observe friends operation results
    val operationResult by friendsViewModel.operationResult.observeAsState()
    val friends by friendsViewModel.friends.observeAsState(emptyList())

    // Handle operation results
    LaunchedEffect(operationResult) {
        when (operationResult) {
            is Result.Success -> {
                Toast.makeText(
                    context,
                    (operationResult as Result.Success<String>).data,
                    Toast.LENGTH_SHORT
                ).show()
                friendsViewModel.clearOperationResult()
            }
            is Result.Error -> {
                Toast.makeText(
                    context,
                    (operationResult as Result.Error).exception.message,
                    Toast.LENGTH_SHORT
                ).show()
                friendsViewModel.clearOperationResult()
            }
            else -> {}
        }
    }

    DisposableEffect(roomId) {
        Log.d(TAG, "Start member listener for room $roomId")
        roomListener = firestore.collection("rooms").document(roomId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Room listen error", err)
                    Toast.makeText(context, "Failed to load members", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    val fetchedRoom = snap.toObject(Room::class.java)
                    room = fetchedRoom
                    val emails = fetchedRoom?.members ?: emptyList()

                    // clear old listeners
                    activeListeners.forEach { (email, listener) ->
                        val enc = email.replace(".", ",")
                        database.getReference("status/$enc").removeEventListener(listener)
                    }
                    activeListeners.clear()

                    if (emails.isNotEmpty()) {
                        // SAFELY FETCH USERS IN CHUNKS OF ≤ 10
                        coroutineScope.launch {
                            try {
                                val snapshots = emails.chunked(10).map { chunk ->
                                    firestore.collection("users")
                                        .whereIn("email", chunk)
                                        .get()
                                        .await()
                                }
                                val users = snapshots
                                    .flatMap { it.documents }
                                    .mapNotNull { it.toObject(User::class.java) }

                                // initialize all as offline
                                members = users.map { it.copy(isOnline = false) }

                                // attach RTDB listeners
                                users.forEach { user ->
                                    val enc = user.email.replace(".", ",")
                                    val ref = database.getReference("status/$enc")
                                    val listener = object : ValueEventListener {
                                        override fun onDataChange(ds: DataSnapshot) {
                                            val online = ds.getValue(Boolean::class.java) ?: false
                                            members = members.map {
                                                if (it.email == user.email) it.copy(isOnline = online)
                                                else it
                                            }
                                        }

                                        override fun onCancelled(e: DatabaseError) {
                                            Log.e(TAG, "Status listener cancelled for ${user.email}", e.toException())
                                        }
                                    }
                                    ref.addValueEventListener(listener)
                                    activeListeners[user.email] = listener
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Fetch users failed", e)
                                Toast.makeText(context, "Failed to load members", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        members = emptyList()
                    }
                } else {
                    Toast.makeText(context, "Room not found", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        onDispose {
            roomListener?.remove()
            activeListeners.forEach { (email, listener) ->
                val enc = email.replace(".", ",")
                database.getReference("status/$enc").removeEventListener(listener)
            }
            activeListeners.clear()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Members", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                room?.let { r ->
                    if (!r.isDirect && r.name.isNotEmpty()) {
                        Text(
                            r.name,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            Text(
                "${members.size} member${if (members.size != 1) "s" else ""}",
                fontSize = 14.sp, color = Color.Gray
            )
        }

        // Add invite button for all groups (not DMs)
        room?.let { r ->
            if (!r.isDirect &&
                (r.ownerEmail == currentUserEmail || r.members.contains(currentUserEmail))) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showInviteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Invite")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Invite Members")
                }
            }
        }

        // Members list
        LazyColumn(
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(members, key = { it.email }) { user ->
                MemberItem(
                    user = user,
                    isCurrentUser = user.email == currentUserEmail,
                    isOwner = room?.ownerEmail == user.email,
                    onAddFriend = { targetUser ->
                        friendsViewModel.sendFriendRequest(targetUser)
                    },
                    friendsViewModel = friendsViewModel,
                    friends = friends,
                    onStartDirectMessage = onStartDirectMessage
                )
            }
        }

        // Action buttons
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            // Don't show leave button for DMs
            if (room?.isDirect != true) {
                OutlinedButton(onClick = {
                    coroutineScope.launch {
                        val email = FirebaseAuth.getInstance().currentUser?.email
                        if (email != null) {
                            try {
                                firestore.collection("rooms").document(roomId)
                                    .update("members", FieldValue.arrayRemove(email)).await()
                                Toast.makeText(context, "Left room", Toast.LENGTH_SHORT).show()
                                onLeaveRoom()
                            } catch (e: Exception) {
                                Log.e(TAG, "Leave failed", e)
                                Toast.makeText(context, "Failed to leave", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }, Modifier.padding(end = 8.dp)) {
                    Text("Leave Room")
                }
            }
            Button(onClick = onBack) {
                Text("Close")
            }
        }
    }

    // Show invite dialog
    if (showInviteDialog) {
        room?.let { r ->
            InviteToRoomDialog(
                roomId = roomId,
                roomName = r.name,
                currentMembers = r.members,
                onDismiss = { showInviteDialog = false }
            )
        }
    }
}

@Composable
fun MemberItem(
    user: User,
    isCurrentUser: Boolean,
    isOwner: Boolean = false,
    onAddFriend: (User) -> Unit,
    friendsViewModel: FriendsViewModel,
    friends: List<Friend>,
    onStartDirectMessage: (String) -> Unit
) {
    val receivedRequests by friendsViewModel.receivedRequests.observeAsState(emptyList())
    val sentRequests by friendsViewModel.sentRequests.observeAsState(emptyList())
    val friendshipStatus by remember(user.email, friends, receivedRequests, sentRequests) {
        derivedStateOf { friendsViewModel.resolveFriendshipStatus(user.email) }
    }
    
    // Room VM used to open/create a 1:1 room
    val rooms = androidx.lifecycle.viewmodel.compose.viewModel<com.example.fhchatroom.viewmodel.RoomViewModel>()

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile photo
            Box {
                if (user.profilePhotoUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(user.profilePhotoUrl),
                        contentDescription = "Profile Photo",
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${user.firstName.firstOrNull() ?: ""}${user.lastName.firstOrNull() ?: ""}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Online status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (user.isOnline) Color.Green else Color.Gray)
                        .align(Alignment.BottomEnd)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // User info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${user.firstName} ${user.lastName}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp
                    )
                    if (isOwner) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = { },
                            label = { Text("Owner", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                labelColor = MaterialTheme.colorScheme.tertiary
                            ),
                            modifier = Modifier.height(22.dp)
                        )
                    }
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AssistChip(
                            onClick = { },
                            label = { Text("You", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                labelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.height(22.dp)
                        )
                    }
                }

                Text(
                    text = if (user.isOnline) "Online" else "Offline",
                    fontSize = 12.sp,
                    color = if (user.isOnline) Color.Green else Color.Gray
                )
            }

            if (!isCurrentUser) {
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
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Friends",
                                            modifier = Modifier.size(16.dp)
                                        )
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
                            // REAL-TIME SYNC FIX: Add Cancel button for sent requests
                            val sentRequest = sentRequests.find { it.toEmail == user.email }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                AssistChip(
                                    onClick = { },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Schedule,
                                                contentDescription = "Pending",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pending", fontSize = 12.sp)
                                        }
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                                // Cancel button
                                if (sentRequest != null) {
                                    AssistChip(
                                        onClick = {
                                            friendsViewModel.cancelFriendRequest(sentRequest)
                                        },
                                        label = { Text("Cancel", fontSize = 12.sp) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            labelColor = MaterialTheme.colorScheme.error
                                        )
                                    )
                                }
                            }
                        }
                        FriendshipStatus.REQUEST_RECEIVED -> {
                            // REAL-TIME SYNC FIX: Show Respond button (can navigate to Requests tab)
                            AssistChip(
                                onClick = { 
                                    // Could navigate to Requests screen or show inline Accept/Decline
                                    // For now, just show the button to indicate there's a pending request
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Notifications,
                                            contentDescription = "Respond",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Respond", fontSize = 12.sp)
                                    }
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                        FriendshipStatus.NOT_FRIENDS -> {
                            Button(
                                onClick = { 
                                    onAddFriend(user)
                                },
                                modifier = Modifier.height(36.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Default.PersonAdd,
                                    contentDescription = "Add Friend",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Friend", fontSize = 12.sp)
                            }
                        }
                        null -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    // Message button UNDER the friend button/chip
                    OutlinedButton(onClick = {
                        rooms.openOrCreateDirectRoom(user.email) { dmRoomId ->
                            onStartDirectMessage(dmRoomId)
                        }
                    }) {
                        Text("Message", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}