package com.example.fhchatroom.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.*
import com.example.fhchatroom.viewmodel.FriendsViewModel
import com.example.fhchatroom.viewmodel.RoomViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    onBack: () -> Unit = {},
    onStartDirectMessage: (String) -> Unit
) {
    val friendsViewModel: FriendsViewModel = viewModel()
    val friends by friendsViewModel.friends.observeAsState(emptyList())
    val receivedRequests by friendsViewModel.receivedRequests.observeAsState(emptyList())
    val sentRequests by friendsViewModel.sentRequests.observeAsState(emptyList())

    val firestore = Injection.instance()
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
    var users by remember { mutableStateOf(listOf<User>()) }
    var reg: ListenerRegistration? by remember { mutableStateOf(null) }
    var selectedTab by remember { mutableStateOf(0) }
    
    val snackbarHostState = remember { SnackbarHostState() }

    // Real-time users listener
    DisposableEffect(Unit) {
        reg = firestore.collection("users")
            .addSnapshotListener { snap, _ ->
                users = snap?.documents
                    ?.mapNotNull { it.toObject(User::class.java) }
                    ?.filter { it.email.isNotBlank() && it.email.contains("@") && it.email != currentUserEmail }
                    ?: emptyList()
            }
        onDispose { reg?.remove() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Friends & People") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Tab Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Friends")
                            if (friends.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge { Text(friends.size.toString()) }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Requests")
                            if (receivedRequests.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error
                                ) { Text(receivedRequests.size.toString()) }
                            }
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("All People") }
                )
            }

            // Content based on selected tab
            when (selectedTab) {
                0 -> FriendsListContent(
                    friends = friends,
                    onStartDirectMessage = onStartDirectMessage,
                    onRemoveFriend = { friend ->
                        friendsViewModel.removeFriend(friend)
                    }
                )
                1 -> RequestsContent(
                    receivedRequests = receivedRequests,
                    sentRequests = sentRequests,
                    onAcceptRequest = { request ->
                        friendsViewModel.acceptFriendRequest(request)
                    },
                    onDeclineRequest = { request ->
                        friendsViewModel.declineFriendRequest(request)
                    },
                    onCancelRequest = { request ->
                        friendsViewModel.cancelFriendRequest(request)
                    },
                    snackbarHostState = snackbarHostState
                )
                2 -> AllPeopleContent(
                    users = users.sortedBy { (it.firstName + it.lastName + it.email).lowercase() },
                    onStartDirectMessage = onStartDirectMessage,
                    friendsViewModel = friendsViewModel,
                    friends = friends,
                    receivedRequests = receivedRequests,
                    sentRequests = sentRequests,
                    onSwitchToRequestsTab = { selectedTab = 1 }
                )
            }
        }
    }
}

@Composable
private fun FriendsListContent(
    friends: List<Friend>,
    onStartDirectMessage: (String) -> Unit,
    onRemoveFriend: (Friend) -> Unit
) {
    if (friends.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.PersonOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No friends yet",
                    color = Color.Gray,
                    fontSize = 18.sp
                )
                Text(
                    "Send friend requests to connect!",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(friends, key = { it.email }) { friend ->
                FriendItem(
                    friend = friend,
                    onStartDirectMessage = onStartDirectMessage,
                    onRemoveFriend = { onRemoveFriend(friend) }
                )
            }
        }
    }
}

@Composable
private fun RequestsContent(
    receivedRequests: List<FriendRequest>,
    sentRequests: List<FriendRequest>,
    onAcceptRequest: (FriendRequest) -> Unit,
    onDeclineRequest: (FriendRequest) -> Unit,
    onCancelRequest: (FriendRequest) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (receivedRequests.isNotEmpty()) {
            item {
                Text(
                    "Received Requests",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(receivedRequests, key = { it.id }) { request ->
                ReceivedRequestItem(
                    request = request,
                    onAccept = { onAcceptRequest(request) },
                    onDecline = { onDeclineRequest(request) },
                    snackbarHostState = snackbarHostState
                )
            }
        }

        if (sentRequests.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Sent Requests",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(sentRequests, key = { it.id }) { request ->
                SentRequestItem(
                    request = request,
                    onCancel = { onCancelRequest(request) }
                )
            }
        }

        if (receivedRequests.isEmpty() && sentRequests.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No pending friend requests",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AllPeopleContent(
    users: List<User>,
    onStartDirectMessage: (String) -> Unit,
    friendsViewModel: FriendsViewModel,
    friends: List<Friend>,
    receivedRequests: List<FriendRequest>,
    sentRequests: List<FriendRequest>,
    onSwitchToRequestsTab: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(users, key = { it.email }) { user ->
            AllUsersItem(
                user = user,
                onStartDirectMessage = onStartDirectMessage,
                friendsViewModel = friendsViewModel,
                friends = friends,
                receivedRequests = receivedRequests,
                sentRequests = sentRequests,
                onSwitchToRequestsTab = onSwitchToRequestsTab
            )
        }
    }
}

@Composable
private fun FriendItem(
    friend: Friend,
    onStartDirectMessage: (String) -> Unit,
    onRemoveFriend: () -> Unit
) {
    val rooms: RoomViewModel = viewModel()
    var showRemoveDialog by remember { mutableStateOf(false) }
    var isOnline by remember { mutableStateOf(false) }
    val database = com.google.firebase.database.FirebaseDatabase.getInstance()

    // Listen to real-time online status from RTDB
    DisposableEffect(friend.email) {
        val encodedEmail = friend.email.replace(".", ",")
        val statusRef = database.getReference("status/$encodedEmail")

        val statusListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                isOnline = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Handle error silently
            }
        }

        statusRef.addValueEventListener(statusListener)

        onDispose {
            statusRef.removeEventListener(statusListener)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${friend.firstName.firstOrNull() ?: ""}${friend.lastName.firstOrNull() ?: ""}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${friend.firstName} ${friend.lastName}".trim(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color.Green else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOnline) "Online" else "Offline",
                        fontSize = 12.sp,
                        color = if (isOnline) Color.Green else Color.Gray
                    )
                }
            }

            Row {
                IconButton(onClick = {
                    rooms.openOrCreateDirectRoom(friend.email) { dmRoomId ->
                        onStartDirectMessage(dmRoomId)
                    }
                }) {
                    Icon(Icons.Filled.Message, contentDescription = "Message")
                }

                IconButton(onClick = { showRemoveDialog = true }) {
                    Icon(
                        Icons.Filled.PersonRemove,
                        contentDescription = "Remove Friend",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove Friend") },
            text = { Text("Are you sure you want to remove ${friend.firstName} from your friends?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFriend()
                        showRemoveDialog = false
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReceivedRequestItem(
    request: FriendRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var isOnline by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    val database = com.google.firebase.database.FirebaseDatabase.getInstance()
    val scope = rememberCoroutineScope()

    // Listen to real-time online status from RTDB
    DisposableEffect(request.fromEmail) {
        val encodedEmail = request.fromEmail.replace(".", ",")
        val statusRef = database.getReference("status/$encodedEmail")

        val statusListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                isOnline = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Handle error silently
            }
        }

        statusRef.addValueEventListener(statusListener)

        onDispose {
            statusRef.removeEventListener(statusListener)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = request.fromName.split(" ").mapNotNull { it.firstOrNull() }.joinToString(""),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                // Online indicator
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.Green)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.fromName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = if (isOnline) "Wants to be your friend • Online" else "Wants to be your friend",
                    fontSize = 12.sp,
                    color = if (isOnline) Color.Green else Color.Gray
                )
            }

            Row {
                Button(
                    onClick = {
                        isProcessing = true
                        onAccept()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Friend request accepted!",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    enabled = !isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Green.copy(alpha = 0.8f)
                    )
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text("Accept", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        isProcessing = true
                        onDecline()
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                message = "Friend request declined",
                                duration = SnackbarDuration.Short
                            )
                        }
                    },
                    enabled = !isProcessing
                ) {
                    Text("Decline", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SentRequestItem(
    request: FriendRequest,
    onCancel: () -> Unit
) {
    var isOnline by remember { mutableStateOf(false) }
    val database = com.google.firebase.database.FirebaseDatabase.getInstance()

    // Listen to real-time online status from RTDB
    DisposableEffect(request.toEmail) {
        val encodedEmail = request.toEmail.replace(".", ",")
        val statusRef = database.getReference("status/$encodedEmail")

        val statusListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                isOnline = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Handle error silently
            }
        }

        statusRef.addValueEventListener(statusListener)

        onDispose {
            statusRef.removeEventListener(statusListener)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = request.toName.split(" ").mapNotNull { it.firstOrNull() }.joinToString(""),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                // Online indicator
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.Green)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = request.toName,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Text(
                    text = if (isOnline) "Request pending • Online" else "Request pending",
                    fontSize = 12.sp,
                    color = if (isOnline) Color.Green else Color.Gray
                )
            }

            OutlinedButton(onClick = onCancel) {
                Text("Cancel", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AllUsersItem(
    user: User,
    onStartDirectMessage: (String) -> Unit,
    friendsViewModel: FriendsViewModel = viewModel(),
    rooms: RoomViewModel = viewModel(),
    friends: List<Friend>,
    receivedRequests: List<FriendRequest>,
    sentRequests: List<FriendRequest>,
    onSwitchToRequestsTab: () -> Unit
) {
    var isOnline by remember { mutableStateOf(false) }
    val database = com.google.firebase.database.FirebaseDatabase.getInstance()
    val friendshipStatus by remember(user.email, friends, receivedRequests, sentRequests) {
        derivedStateOf { friendsViewModel.resolveFriendshipStatus(user.email) }
    }

    // Listen to real-time online status from RTDB
    DisposableEffect(user.email) {
        val encodedEmail = user.email.replace(".", ",")
        val statusRef = database.getReference("status/$encodedEmail")

        val statusListener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                isOnline = snapshot.getValue(Boolean::class.java) ?: false
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Handle error silently
            }
        }

        statusRef.addValueEventListener(statusListener)

        onDispose {
            statusRef.removeEventListener(statusListener)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
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
                // Online status indicator
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color.Green)
                            .align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${user.firstName} ${user.lastName}".trim(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isOnline) Color.Green else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isOnline) "Online" else user.email,
                        fontSize = 12.sp,
                        color = if (isOnline) Color.Green else Color.Gray
                    )
                }
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
                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
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
                        // Find the request to get the ID
                        val sentRequest = sentRequests.find { it.toEmail == user.email }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AssistChip(
                                onClick = { },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pending", fontSize = 12.sp)
                                    }
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
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
                        Button(
                            onClick = onSwitchToRequestsTab,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Respond", fontSize = 12.sp)
                        }
                    }
                    FriendshipStatus.NOT_FRIENDS, null -> {
                        Button(
                            onClick = {
                                friendsViewModel.sendFriendRequest(user)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Friend", fontSize = 12.sp)
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
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

// Backwards-compat overload
@Composable
fun FriendsScreen(roomId: String, onBack: () -> Unit) {
    FriendsScreen(onBack = onBack, onStartDirectMessage = { })
}