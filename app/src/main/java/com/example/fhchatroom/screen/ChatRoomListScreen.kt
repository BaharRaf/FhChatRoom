package com.example.fhchatroom.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fhchatroom.data.Room
import com.example.fhchatroom.viewmodel.RoomViewModel
import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomListScreen(
    roomViewModel: RoomViewModel = viewModel(),
    onJoinClicked: (Room) -> Unit,
    onLogout: () -> Unit
) {
    val rooms by roomViewModel.rooms.observeAsState(emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Include the top app bar with the Logout button.
        ChatAppTopBar(onLogout = onLogout)

        Spacer(modifier = Modifier.height(8.dp))

        // List of chat rooms
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
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
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
                                    roomViewModel.createRoom(name)
                                    name = ""
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
    val roomViewModel: RoomViewModel = viewModel()
    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
    val isMember = currentUserEmail != null && room.members.contains(currentUserEmail)
    //check if current user created this room
    val isCreator = currentUserEmail != null && room.createdBy == currentUserEmail

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = room.name, fontSize = 16.sp, fontWeight = FontWeight.Normal)
        Row {
            if (!isMember) {
                OutlinedButton(
                    onClick = {
                        if (currentUserEmail != null) {
                            roomViewModel.joinRoom(room.id)
                        }
                        onJoinClicked(room)
                    }
                ) {
                    Text("Join")
                }
            } else {
                //join→enter for existing members
                OutlinedButton(
                    onClick = { onJoinClicked(room) }
                ) {
                    Text("Enter")
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (isCreator) {
                    //only creator sees Delete
                    OutlinedButton(
                        onClick = {
                            roomViewModel.deleteRoom(room.id)
                        }
                    ) {
                        Text("Delete")
                    }
                } else {
                    //non-creator members see Leave
                    OutlinedButton(
                        onClick = {
                            roomViewModel.leaveRoom(room.id)
                        }
                    ) {
                        Text("Leave")
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun RoomListPreview() {
    ChatRoomListScreen(
        onJoinClicked = { },
        onLogout = { }
    )
}

@Preview
@Composable
fun ItemPreview() {
    RoomItem(room = Room("id.com", "Name")) { }
}
