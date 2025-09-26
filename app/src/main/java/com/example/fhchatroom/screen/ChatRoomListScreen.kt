package com.example.fhchatroom.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fhchatroom.data.Room
import com.example.fhchatroom.viewmodel.RoomViewModel
import com.google.firebase.auth.FirebaseAuth

enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    MEMBER_COUNT_ASC,
    MEMBER_COUNT_DESC,
    NEWEST_FIRST,
    OLDEST_FIRST
}

@Composable
fun ChatRoomListScreen(
    roomViewModel: RoomViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onJoinClicked: (Room) -> Unit,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val rooms by roomViewModel.rooms.observeAsState(emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // Search and Sort states
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Filter rooms based on search query
    val filteredRooms = rooms.filter { room ->
        searchQuery.isEmpty() ||
                room.name.contains(searchQuery, ignoreCase = true) ||
                room.description.contains(searchQuery, ignoreCase = true)
    }

    // Sort rooms based on selected option
    val sortedRooms = when (sortOption) {
        SortOption.NAME_ASC -> filteredRooms.sortedBy { it.name.lowercase() }
        SortOption.NAME_DESC -> filteredRooms.sortedByDescending { it.name.lowercase() }
        SortOption.MEMBER_COUNT_ASC -> filteredRooms.sortedBy { it.members.size }
        SortOption.MEMBER_COUNT_DESC -> filteredRooms.sortedByDescending { it.members.size }
        SortOption.NEWEST_FIRST -> filteredRooms.sortedByDescending { it.lastMessageTimestamp }
        SortOption.OLDEST_FIRST -> filteredRooms.sortedBy { it.lastMessageTimestamp }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Include the top app bar with Logout and theme toggle.
        ChatAppTopBar(
            onLogout = onLogout,
            onNavigateToProfile = onNavigateToProfile,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Search and Sort Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chat Rooms (${sortedRooms.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )

            Row {
                // Search Toggle Button
                IconButton(onClick = {
                    showSearchBar = !showSearchBar
                    if (!showSearchBar) searchQuery = ""
                }) {
                    Icon(
                        imageVector = if (showSearchBar) Icons.Default.Clear else Icons.Default.Search,
                        contentDescription = if (showSearchBar) "Close Search" else "Search Rooms"
                    )
                }

                // Sort Button
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = "Sort Rooms"
                    )
                }

                // Sort Dropdown Menu
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Name (A-Z) ↑") },
                        onClick = {
                            sortOption = SortOption.NAME_ASC
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Name (Z-A) ↓") },
                        onClick = {
                            sortOption = SortOption.NAME_DESC
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Members (Low to High) ↑") },
                        onClick = {
                            sortOption = SortOption.MEMBER_COUNT_ASC
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Members (High to Low) ↓") },
                        onClick = {
                            sortOption = SortOption.MEMBER_COUNT_DESC
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Newest First") },
                        onClick = {
                            sortOption = SortOption.NEWEST_FIRST
                            showSortMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Oldest First") },
                        onClick = {
                            sortOption = SortOption.OLDEST_FIRST
                            showSortMenu = false
                        }
                    )
                }
            }
        }

        // Animated Search Bar
        AnimatedVisibility(
            visible = showSearchBar,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search rooms by name or description...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true
            )
        }

        // Current sort indicator
        if (sortOption != SortOption.NAME_ASC) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "Sorted by: ${getSortOptionLabel(sortOption)}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List of available chat rooms with descriptions
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

        if (sortedRooms.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (searchQuery.isNotEmpty())
                        "No rooms found matching \"$searchQuery\""
                    else
                        "No chat rooms available",
                    color = Color.Gray
                )
                if (searchQuery.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { searchQuery = "" }) {
                        Text("Clear search")
                    }
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sortedRooms) { room ->
                    // Determine if user is already a member and if user is the room creator
                    val isMember = currentUserEmail != null && room.members.contains(currentUserEmail)
                    val isOwner = currentUserEmail != null && (
                            room.ownerEmail == currentUserEmail ||
                                    (room.ownerEmail.isBlank() && room.members.firstOrNull() == currentUserEmail)
                            )

                    RoomItem(
                        room = room,
                        isMember = isMember,
                        isOwner = isOwner,
                        memberCount = room.members.size,
                        onDeleteClicked = {
                            // Only allow creator to delete the room
                            roomViewModel.deleteRoom(room.id)
                        },
                        onJoinClicked = {
                            // If not a member yet, join the room first
                            if (!isMember) {
                                roomViewModel.joinRoom(room.id)
                            }
                            // Navigate to the chat screen for this room
                            onJoinClicked(room)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { showDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create Room")
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Create a new room") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Room Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(8.dp)
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            maxLines = 3
                        )
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(onClick = {
                            if (name.isNotBlank()) {
                                roomViewModel.createRoom(name, description)
                                name = ""
                                description = ""
                                showDialog = false
                            }
                        }) {
                            Text("Add")
                        }
                        Button(onClick = {
                            showDialog = false
                            name = ""
                            description = ""
                        }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun RoomItem(
    room: Room,
    isMember: Boolean = false,
    isOwner: Boolean = false,
    memberCount: Int = 0,
    onDeleteClicked: (Room) -> Unit = {},
    onJoinClicked: (Room) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMember)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = room.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (isMember) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "Joined",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                // Last message preview
                if (room.lastMessage.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${room.lastMessageSender}: ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = room.lastMessage,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, false)
                        )
                        if (room.lastMessageTimestamp > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatMessageTime(room.lastMessageTimestamp),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else if (room.description.isNotEmpty()) {
                    Text(
                        text = room.description,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "$memberCount ${if (memberCount == 1) "member" else "members"}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onJoinClicked(room) }) {
                    Text(if (isMember) "Enter" else "Join")
                }
                if (isOwner) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { onDeleteClicked(room) }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete Room")
                    }
                }
            }
        }
    }
}

// Helper function to format timestamp for last message
fun formatMessageTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "now" // Less than 1 minute
        diff < 3_600_000 -> "${diff / 60_000}m" // Less than 1 hour
        diff < 86_400_000 -> "${diff / 3_600_000}h" // Less than 24 hours
        diff < 604_800_000 -> "${diff / 86_400_000}d" // Less than 7 days
        else -> {
            val date = java.util.Date(timestamp)
            val format = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            format.format(date)
        }
    }
}

fun getSortOptionLabel(sortOption: SortOption): String {
    return when (sortOption) {
        SortOption.NAME_ASC -> "Name (A-Z)"
        SortOption.NAME_DESC -> "Name (Z-A)"
        SortOption.MEMBER_COUNT_ASC -> "Members (Low to High)"
        SortOption.MEMBER_COUNT_DESC -> "Members (High to Low)"
        SortOption.NEWEST_FIRST -> "Newest First"
        SortOption.OLDEST_FIRST -> "Oldest First"
    }
}