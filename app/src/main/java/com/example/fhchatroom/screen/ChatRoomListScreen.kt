package com.example.fhchatroom.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fhchatroom.data.Room
import com.example.fhchatroom.viewmodel.RoomViewModel

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Include the top app bar with Logout and theme toggle.
        ChatAppTopBar(onLogout = onLogout, isDarkTheme = isDarkTheme, onToggleTheme = onToggleTheme)

        Spacer(modifier = Modifier.height(8.dp))

        // List of available chat rooms with descriptions
        LazyColumn {
            items(rooms) { room ->
                RoomItem(room = room, onJoinClicked = { onJoinClicked(room) })
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                        )
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Description") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            maxLines = 3
                        )
                    }
                },
                confirmButton = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = {
                                if (name.isNotBlank()) {
                                    roomViewModel.createRoom(name, description)
                                    name = ""
                                    description = ""
                                    showDialog = false
                                }
                            }
                        ) {
                            Text("Add")
                        }
                        Button(
                            onClick = {
                                showDialog = false
                                name = ""
                                description = ""
                            }
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun RoomItem(room: Room, onJoinClicked: (Room) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = room.name, fontSize = 16.sp, fontWeight = FontWeight.Normal)
            if (room.description.isNotEmpty()) {
                Text(text = room.description, fontSize = 14.sp, color = Color.Gray)
            }
        }
        OutlinedButton(onClick = { onJoinClicked(room) }) {
            Text("Join")
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun RoomListPreview() {
    ChatRoomListScreen(
        onJoinClicked = { },
        onLogout = { },
        isDarkTheme = false,
        onToggleTheme = { }
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun ItemPreview() {
    RoomItem(room = Room(id = "id1", name = "Sample Room", description = "Sample description")) { }
}
