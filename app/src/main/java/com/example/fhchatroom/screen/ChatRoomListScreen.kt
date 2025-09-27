package com.example.fhchatroom.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Room
import com.example.fhchatroom.data.User
import com.example.fhchatroom.viewmodel.RoomViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.lifecycle.viewmodel.compose.viewModel
import android.widget.Toast
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext

enum class SortOption {
    NAME_ASC,
    NAME_DESC,
    MEMBER_COUNT_ASC,
    MEMBER_COUNT_DESC,
    NEWEST_FIRST,
    OLDEST_FIRST
}
private enum class RoomTab { PUBLIC, PRIVATE }

@Composable
fun ChatRoomListScreen(
    roomViewModel: RoomViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onJoinClicked: (Room) -> Unit,
    onLogout: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToFriends: () -> Unit,
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

    // Private creation toggle (enabled)
    var isPrivate by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(RoomTab.PUBLIC) }

    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Top app bar
        ChatAppTopBar(
            onLogout = onLogout,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToFriends = onNavigateToFriends,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme
        )

        // Public/Private toggle
        Spacer(modifier = Modifier.height(6.dp))
        SegmentedButtons(tab) { tab = it }

        Spacer(modifier = Modifier.height(8.dp))

        // Header (no counts)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chat Rooms",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.weight(1f)
            )

            Row {
                // Search toggle
                IconButton(onClick = {
                    showSearchBar = !showSearchBar
                    if (!showSearchBar) searchQuery = ""
                }) {
                    Icon(
                        imageVector = if (showSearchBar) Icons.Filled.Clear else Icons.Filled.Search,
                        contentDescription = if (showSearchBar) "Close Search" else "Search Rooms"
                    )
                }

                // Sort
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(
                        imageVector = Icons.Filled.Sort,
                        contentDescription = "Sort Rooms"
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(text = { Text("Name (A-Z) ↑") }, onClick = { sortOption = SortOption.NAME_ASC; showSortMenu = false })
                    DropdownMenuItem(text = { Text("Name (Z-A) ↓") }, onClick = { sortOption = SortOption.NAME_DESC; showSortMenu = false })
                    DropdownMenuItem(text = { Text("Members (Low to High) ↑") }, onClick = { sortOption = SortOption.MEMBER_COUNT_ASC; showSortMenu = false })
                    DropdownMenuItem(text = { Text("Members (High to Low) ↓") }, onClick = { sortOption = SortOption.MEMBER_COUNT_DESC; showSortMenu = false })
                    DropdownMenuItem(text = { Text("Newest First") }, onClick = { sortOption = SortOption.NEWEST_FIRST; showSortMenu = false })
                    DropdownMenuItem(text = { Text("Oldest First") }, onClick = { sortOption = SortOption.OLDEST_FIRST; showSortMenu = false })
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
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                singleLine = true
            )
        }

        // TAB FILTERING
        // PUBLIC: only non-private rooms (excludes DMs and private groups)
        // PRIVATE: all private rooms the user can access: DMs (isDirect) AND private groups (isPrivate)
        val tabRooms = when (tab) {
            RoomTab.PUBLIC -> rooms.filter { !it.isPrivate }
            RoomTab.PRIVATE -> rooms.filter { it.isPrivate && currentUserEmail != null && (it.members.contains(currentUserEmail) || it.ownerEmail == currentUserEmail) }
        }

        // Apply search on the tab list
        val filteredRooms = tabRooms.filter { room ->
            searchQuery.isEmpty() ||
                    room.name.contains(searchQuery, ignoreCase = true) ||
                    room.description.contains(searchQuery, ignoreCase = true)
        }

        // Sort after filtering
        val sortedRooms = when (sortOption) {
            SortOption.NAME_ASC -> filteredRooms.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> filteredRooms.sortedByDescending { it.name.lowercase() }
            SortOption.MEMBER_COUNT_ASC -> filteredRooms.sortedBy { it.members.size }
            SortOption.MEMBER_COUNT_DESC -> filteredRooms.sortedByDescending { it.members.size }
            SortOption.NEWEST_FIRST -> filteredRooms.sortedByDescending { it.lastMessageTimestamp }
            SortOption.OLDEST_FIRST -> filteredRooms.sortedBy { it.lastMessageTimestamp }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List
        val currentUser = currentUserEmail
        if (sortedRooms.isEmpty()) {
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
                items(sortedRooms, key = { it.id }) { room ->
                    val isMember = currentUser != null && room.members.contains(currentUser)
                    val isOwner = currentUser != null && (
                            room.ownerEmail == currentUser ||
                                    (room.ownerEmail.isBlank() && room.members.firstOrNull() == currentUser)
                            )

                    RoomItem(
                        room = room,
                        isMember = isMember,
                        isOwner = isOwner,
                        memberCount = room.members.size,
                        onDeleteClicked = {
                            // Only allow delete for non-DM groups
                            if (!room.isDirect) {
                                roomViewModel.deleteRoom(room.id)
                            }
                        },
                        onJoinClicked = {
                            if (!isMember) {
                                roomViewModel.joinRoom(room.id)
                            }
                            onJoinClicked(room)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Create Room (public or private based on checkbox)
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
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = isPrivate,
                                onCheckedChange = { isPrivate = it }
                            )
                            Text("Private (visible only to you & invited)")
                        }
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(onClick = {
                            if (name.isNotBlank()) {
                                roomViewModel.createRoom(name, description, isPrivate)
                                name = ""
                                description = ""
                                isPrivate = false
                                showDialog = false
                            }
                        }) { Text("Add") }

                        Button(onClick = {
                            showDialog = false
                            name = ""
                            description = ""
                            isPrivate = false
                        }) { Text("Cancel") }
                    }
                }
            )
        }
    }
}

@Composable
private fun SegmentedButtons(current: RoomTab, onChange: (RoomTab) -> Unit) {
    Row(
        Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .padding(4.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        // Label kept as "Private"
        listOf(RoomTab.PUBLIC to "Public", RoomTab.PRIVATE to "Private").forEach { (value, label) ->
            val selected = current == value
            val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
            val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                label,
                color = fg,
                modifier = Modifier
                    .background(bg, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clickable { onChange(value) },
                fontSize = 14.sp
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
    onHideClicked: (Room) -> Unit = {},
    onJoinClicked: (Room) -> Unit,
    roomViewModel: RoomViewModel = viewModel()
) {
    val context = LocalContext.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }

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
                    val currentEmail = FirebaseAuth.getInstance().currentUser?.email
                    val dmOther = room.members.firstOrNull { it != currentEmail } ?: room.members.firstOrNull()

                    // Title: for DMs, show other user's name (fallback to email), else room.name
                    var titleText by remember { mutableStateOf(room.name) }
                    LaunchedEffect(room.id, dmOther, room.isDirect) {
                        if (room.isDirect && dmOther != null) {
                            try {
                                val firestore = Injection.instance()
                                val qs = firestore.collection("users")
                                    .whereEqualTo("email", dmOther)
                                    .limit(1)
                                    .get()
                                    .await()
                                val user = qs.documents.firstOrNull()?.toObject(User::class.java)
                                val full = listOfNotNull(user?.firstName?.trim(), user?.lastName?.trim())
                                    .joinToString(" ")
                                    .trim()
                                titleText = if (full.isNotEmpty()) full else dmOther
                            } catch (_: Exception) {
                                titleText = dmOther ?: room.name
                            }
                        } else {
                            titleText = room.name
                        }
                    }

                    Text(
                        text = titleText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )

                    if (room.isDirect) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // DM tag
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                            Text(
                                text = "DM",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    } else if (room.isPrivate) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // Private tag for non-DM private rooms
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                            Text(
                                text = "Private",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }

                    // Remove "Joined" tag for DMs; keep for non-DM rooms
                    if (isMember && !room.isDirect) {
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

                // Last message preview / description
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
                } else if (room.description.isNotEmpty() && !room.isDirect) {
                    Text(
                        text = room.description,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                if (!room.isDirect) {
                    Text(
                        text = "$memberCount ${if (memberCount == 1) "member" else "members"}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = "Direct Message",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onJoinClicked(room) }) {
                    Text(if (isMember) "Enter" else "Join")
                }

                // Show delete/hide button based on room type
                if (room.isDirect) {
                    // For DMs, show hide button
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = "Hide Chat",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                } else if (isOwner && !room.isDirect) {
                    // For regular rooms, show delete for owner
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { onDeleteClicked(room) }) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Delete Room")
                    }
                }
            }
        }
    }

    // Confirmation dialog for hiding DMs
    if (showDeleteConfirmation && room.isDirect) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Hide Conversation") },
            text = {
                Text("This will hide the conversation from your chat list. You can still access it by messaging this person again. The other person will still see the conversation.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        roomViewModel.hideDM(room.id) { success ->
                            if (success) {
                                Toast.makeText(context, "Conversation hidden", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to hide conversation", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showDeleteConfirmation = false
                    }
                ) {
                    Text("Hide")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Helper function to format timestamp for last message
private fun formatMessageTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        diff < 604_800_000 -> "${diff / 86_400_000}d"
        else -> {
            val date = java.util.Date(timestamp)
            val format = java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault())
            format.format(date)
        }
    }
}