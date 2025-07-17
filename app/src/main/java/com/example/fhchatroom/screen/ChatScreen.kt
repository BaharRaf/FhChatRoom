package com.example.fhchatroom.screen

import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fhchatroom.data.Message
import com.example.fhchatroom.data.Result
import com.example.fhchatroom.viewmodel.MessageViewModel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatScreen(
    roomId: String,
    messageViewModel: MessageViewModel = viewModel(),
    onShowMembers: () -> Unit,
    onBack: () -> Unit = {}
) {
    // Observe messages and initialize the chat room
    val messages by messageViewModel.messages.observeAsState(emptyList())
    messageViewModel.setRoomId(roomId)

    // UI state for message input
    val textState = remember { mutableStateOf("") }
    val sendResult by messageViewModel.sendResult.observeAsState()
    val context = LocalContext.current

    // UI state for deletion dialog
    val showDeleteDialog = remember { mutableStateOf(false) }
    val selectedMessage = remember { mutableStateOf<Message?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar with back button and members menu
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(text = "Chat", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = onShowMembers) {
                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Members Menu")
            }
        }

        // Messages list
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(messages) { message ->
                // Determine if this message was sent by the current user
                val isMine = message.senderId == messageViewModel.currentUser.value?.email
                ChatMessageItem(
                    message = message.copy(isSentByCurrentUser = isMine),
                    onLongPress = {
                        // On long press, select message and show delete options
                        selectedMessage.value = message
                        showDeleteDialog.value = true
                    }
                )
            }
        }

        // Input field and send button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textState.value,
                onValueChange = { textState.value = it },
                placeholder = { Text("Type a message...") },
                textStyle = TextStyle(fontSize = 16.sp),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            IconButton(
                onClick = {
                    val content = textState.value.trim()
                    if (content.isNotEmpty()) {
                        messageViewModel.sendMessage(content)
                        textState.value = ""
                        // (Re-loading messages is handled via snapshot listener automatically)
                    }
                }
            ) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
            }
        }
    }

    // Deletion confirmation dialog
    if (showDeleteDialog.value && selectedMessage.value != null) {
        val message = selectedMessage.value!!
        val isSender = message.senderId == messageViewModel.currentUser.value?.email
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog.value = false
                selectedMessage.value = null
            },
            title = { Text("Delete Message") },
            text = {
                if (isSender) {
                    Text("Delete this message for everyone or just for you?")
                } else {
                    Text("Delete this message for you? (It will remain visible to others.)")
                }
            },
            confirmButton = {
                // "Delete for everyone" if user is sender, otherwise "Delete for me"
                if (isSender) {
                    TextButton(onClick = {
                        // Delete the message for everyone (remove from Firestore)
                        messageViewModel.deleteMessageForEveryone(roomId, message.id!!)
                        showDeleteDialog.value = false
                        selectedMessage.value = null
                    }) {
                        Text("Delete for everyone")
                    }
                } else {
                    TextButton(onClick = {
                        // Delete the message for me (current user only)
                        messageViewModel.deleteMessageForMe(roomId, message.id!!, messageViewModel.currentUser.value!!.email)
                        showDeleteDialog.value = false
                        selectedMessage.value = null
                    }) {
                        Text("Delete for me")
                    }
                }
            },
            dismissButton = {
                if (isSender) {
                    // If sender, the dismiss button serves as "Delete for me"
                    TextButton(onClick = {
                        messageViewModel.deleteMessageForMe(roomId, message.id!!, messageViewModel.currentUser.value!!.email)
                        showDeleteDialog.value = false
                        selectedMessage.value = null
                    }) {
                        Text("Delete for me")
                    }
                } else {
                    // If not sender, offer a Cancel option
                    TextButton(onClick = {
                        showDeleteDialog.value = false
                        selectedMessage.value = null
                    }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Toast feedback on send success/failure
    LaunchedEffect(sendResult) {
        when (val result = sendResult) {
            is Result.Success -> {
                Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
            }
            is Result.Error -> {
                Toast.makeText(context, "Failed to send: ${result.exception.message}", Toast.LENGTH_LONG).show()
            }
            else -> { /* No action for loading/idle states */ }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatMessageItem(message: Message, onLongPress: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                onLongClick = onLongPress,
                onClick = {}  // We only handle long-press for context menu
            ),
        horizontalAlignment = if (message.isSentByCurrentUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (message.isSentByCurrentUser) Color(0xFFBB86FC) else Color.Gray,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            Text(
                text = message.text,
                color = Color.White,
                style = TextStyle(fontSize = 16.sp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message.senderFirstName,
            style = TextStyle(fontSize = 12.sp, color = Color.Gray)
        )
        Text(
            text = formatTimestamp(message.timestamp),
            style = TextStyle(fontSize = 12.sp, color = Color.Gray)
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun formatTimestamp(timestamp: Long): String {
    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
    val now = LocalDateTime.now()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    return when {
        dt.toLocalDate() == now.toLocalDate() -> "today ${dt.format(timeFmt)}"
        dt.toLocalDate().plusDays(1) == now.toLocalDate() -> "yesterday ${dt.format(timeFmt)}"
        else -> dt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}
