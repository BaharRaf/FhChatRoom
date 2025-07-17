package com.example.fhchatroom.screen

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

enum class SortOption(val label: String) {
    NAME("Name"),
    DATE("Date")
}

@Composable
fun ChatRoomListScreen(
    roomViewModel: RoomViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onJoinClicked: (Room) -> Unit,
    onLogout: () -> Unit,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val rooms by roomViewModel.rooms.observeAsState(emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var sortOption by remember { mutableStateOf(SortOption.DATE) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Include the top app bar with Logout and theme toggle.
        ChatAppTopBar(onLogout = onLogout, isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme)

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search rooms") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { sortMenuExpanded = true }) {
                Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sort")
            }
            Text(text = sortOption.label, modifier = Modifier.padding(start = 8.dp))
            DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                SortOption.values().forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            sortOption = option
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }

        // List of available chat rooms with descriptions
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        val filteredRooms = rooms.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description.contains(searchQuery, ignoreCase = true)
        }
        val sortedRooms = when (sortOption) {
            SortOption.NAME -> filteredRooms.sortedBy { it.name.lowercase() }
            SortOption.DATE -> filteredRooms.sortedBy { it.createdAt }
        }
        LazyColumn {
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
                }
            )
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Room")
                }
            }
        }
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
    onDeleteClicked: (Room) -> Unit = {},
    onJoinClicked: (Room) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = room.name, fontSize = 16.sp, fontWeight = FontWeight.Normal)
            if (room.description.isNotEmpty()) {
                Text(text = room.description, fontSize = 14.sp, color = Color.Gray)
            }
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
