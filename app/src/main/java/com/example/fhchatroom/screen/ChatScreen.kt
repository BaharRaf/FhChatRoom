package com.example.fhchatroom.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.fhchatroom.data.Message
import com.example.fhchatroom.data.MessageType
import com.example.fhchatroom.data.Result
import com.example.fhchatroom.viewmodel.MessageViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
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
    val messages by messageViewModel.messages.observeAsState(emptyList())
    messageViewModel.setRoomId(roomId)

    val textState = remember { mutableStateOf("") }
    val sendResult by messageViewModel.sendResult.observeAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Reply state
    var replyToMessage by remember { mutableStateOf<Message?>(null) }

    // Forward state
    var showForwardDialog by remember { mutableStateOf(false) }
    var messageToForward by remember { mutableStateOf<Message?>(null) }

    // Search states
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentSearchIndex by remember { mutableStateOf(0) }
    val lazyListState = rememberLazyListState()

    // Find messages that match search query
    val searchMatches = remember(messages, searchQuery) {
        if (searchQuery.isNotEmpty()) {
            messages.mapIndexedNotNull { index, message ->
                if (message.text.contains(searchQuery, ignoreCase = true) ||
                    message.senderFirstName.contains(searchQuery, ignoreCase = true)) {
                    index
                } else null
            }
        } else {
            emptyList()
        }
    }

    // Voice recording states
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }

    // Photo picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            messageViewModel.sendPhotoMessage(it, roomId)
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            messageViewModel.sendCameraPhoto(it, roomId)
        }
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startRecording(context, mediaRecorder) { recorder, file ->
                mediaRecorder = recorder
                audioFile = file
                isRecording = true
            }
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    var showAttachmentDialog by remember { mutableStateOf(false) }

    // Recording duration timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                delay(1000)
                recordingDuration++
            }
        }
    }

    // Scroll to search match
    LaunchedEffect(currentSearchIndex, searchMatches) {
        if (searchMatches.isNotEmpty() && currentSearchIndex in searchMatches.indices) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(searchMatches[currentSearchIndex])
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar with search
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
            Row {
                // Search button
                IconButton(onClick = {
                    showSearchBar = !showSearchBar
                    if (!showSearchBar) {
                        searchQuery = ""
                        currentSearchIndex = 0
                    }
                }) {
                    Icon(
                        imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (showSearchBar) "Close Search" else "Search Messages"
                    )
                }
                IconButton(onClick = onShowMembers) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Members Menu")
                }
            }
        }

        // Search bar with navigation
        AnimatedVisibility(
            visible = showSearchBar,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            currentSearchIndex = 0
                        },
                        label = { Text("Search messages...") },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    currentSearchIndex = 0
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Search results navigation
                    if (searchMatches.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentSearchIndex + 1} of ${searchMatches.size} results",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        if (currentSearchIndex > 0) {
                                            currentSearchIndex--
                                        }
                                    },
                                    enabled = currentSearchIndex > 0
                                ) {
                                    Icon(Icons.Default.KeyboardArrowUp, "Previous")
                                }
                                IconButton(
                                    onClick = {
                                        if (currentSearchIndex < searchMatches.size - 1) {
                                            currentSearchIndex++
                                        }
                                    },
                                    enabled = currentSearchIndex < searchMatches.size - 1
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, "Next")
                                }
                            }
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        Text(
                            text = "No results found",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = lazyListState
        ) {
            items(messages.size) { index ->
                val message = messages[index]
                val isMine = message.senderId == messageViewModel.currentUser.value?.email
                val isHighlighted = searchMatches.contains(index) &&
                        searchMatches.indexOf(index) == currentSearchIndex

                ChatMessageItem(
                    message = message.copy(isSentByCurrentUser = isMine),
                    searchQuery = if (showSearchBar) searchQuery else "",
                    isHighlighted = isHighlighted,
                    onReply = {
                        replyToMessage = message
                    },
                    onReact = { emoji ->
                        message.id?.let { messageId ->
                            messageViewModel.addReaction(roomId, messageId, emoji)
                        }
                    },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("message", message.text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        message.id?.let { messageId ->
                            messageViewModel.deleteMessageForMe(
                                roomId,
                                messageId,
                                messageViewModel.currentUser.value!!.email
                            )
                        }
                    },
                    onDeleteForEveryone = {
                        if (isMine) {
                            message.id?.let { messageId ->
                                messageViewModel.deleteMessageForEveryone(roomId, messageId)
                            }
                        }
                    },
                    onForward = {
                        messageToForward = message
                        showForwardDialog = true
                    },
                    currentUserId = messageViewModel.currentUser.value?.email ?: ""
                )
            }
        }

        // Reply preview
        if (replyToMessage != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Reply,
                        contentDescription = "Replying to",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replying to ${replyToMessage!!.senderFirstName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when(replyToMessage!!.type) {
                                MessageType.IMAGE -> "📷 Photo"
                                MessageType.VOICE -> "🎤 Voice message"
                                else -> replyToMessage!!.text.take(50)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = { replyToMessage = null },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply")
                    }
                }
            }
        }

        // Voice recording indicator
        if (isRecording) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = "Recording",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Recording... ${formatDuration(recordingDuration)}")
                    }
                    Row {
                        IconButton(onClick = {
                            stopRecording(mediaRecorder)
                            isRecording = false
                            audioFile?.delete()
                            audioFile = null
                        }) {
                            Icon(Icons.Default.Cancel, "Cancel")
                        }
                        IconButton(onClick = {
                            stopRecording(mediaRecorder)
                            isRecording = false
                            audioFile?.let { file ->
                                messageViewModel.sendVoiceMessage(file, recordingDuration, roomId)
                            }
                        }) {
                            Icon(Icons.Default.Send, "Send Voice")
                        }
                    }
                }
            }
        }

        // Input field and buttons
        if (!isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showAttachmentDialog = true }) {
                    Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach")
                }

                OutlinedTextField(
                    value = textState.value,
                    onValueChange = { textState.value = it },
                    placeholder = { Text("Type a message...") },
                    textStyle = TextStyle(fontSize = 16.sp),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )

                if (textState.value.isBlank()) {
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                startRecording(context, mediaRecorder) { recorder, file ->
                                    mediaRecorder = recorder
                                    audioFile = file
                                    isRecording = true
                                }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice")
                    }
                } else {
                    IconButton(
                        onClick = {
                            val content = textState.value.trim()
                            if (content.isNotEmpty()) {
                                messageViewModel.sendMessage(
                                    text = content,
                                    replyToMessage = replyToMessage
                                )
                                textState.value = ""
                                replyToMessage = null
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }

    // Attachment options dialog
    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = { Text("Choose attachment") },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentDialog = false
                                photoPickerLauncher.launch("image/*")
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Gallery")
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Choose from Gallery")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAttachmentDialog = false
                                cameraLauncher.launch(null)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Take Photo")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAttachmentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Forward dialog
    if (showForwardDialog && messageToForward != null) {
        ForwardMessageDialog(
            message = messageToForward!!,
            currentRoomId = roomId,
            onForwardToRoom = { targetRoomId ->
                messageViewModel.forwardMessage(messageToForward!!, targetRoomId)
                Toast.makeText(
                    context,
                    if (targetRoomId == roomId) "Message forwarded to this room"
                    else "Message forwarded successfully",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDismiss = {
                showForwardDialog = false
                messageToForward = null
            }
        )
    }

    LaunchedEffect(sendResult) {
        when (val result = sendResult) {
            is Result.Success -> {
                // Success handled silently
            }
            is Result.Error -> {
                Toast.makeText(context, "Failed to send: ${result.exception.message}", Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ChatMessageItem(
    message: Message,
    searchQuery: String = "",
    isHighlighted: Boolean = false,
    onReply: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onCopy: () -> Unit = {},
    onDelete: () -> Unit = {},
    onDeleteForEveryone: () -> Unit = {},
    onForward: () -> Unit = {},
    currentUserId: String = ""
) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showOptions by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }

    val commonReactions = listOf("❤️", "👍", "😂", "😮", "😢", "🎉")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onLongClick = { showOptions = true },
                onClick = {}
            ),
        horizontalAlignment = if (message.isSentByCurrentUser) Alignment.End else Alignment.Start
    ) {
        // Reply reference if exists
        message.replyToMessageId?.let { replyId ->
            Card(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = message.replyToSenderName ?: "Unknown",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = message.replyToMessageText?.take(50) ?: "Message",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Box {
            Column(
                modifier = Modifier
                    .background(
                        color = when {
                            isHighlighted -> MaterialTheme.colorScheme.tertiaryContainer
                            message.isSentByCurrentUser -> Color(0xFFBB86FC)
                            else -> Color.Gray
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp)
            ) {
                when (message.type) {
                    MessageType.TEXT -> {
                        Text(
                            text = highlightSearchQuery(message.text, searchQuery),
                            color = Color.White,
                            style = TextStyle(fontSize = 16.sp)
                        )
                    }
                    MessageType.IMAGE -> {
                        message.mediaUrl?.let { url ->
                            Image(
                                painter = rememberAsyncImagePainter(url),
                                contentDescription = "Photo",
                                modifier = Modifier
                                    .size(200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    MessageType.VOICE -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(4.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = null
                                        isPlaying = false
                                    } else {
                                        message.mediaUrl?.let { url ->
                                            mediaPlayer = MediaPlayer().apply {
                                                setDataSource(url)
                                                prepareAsync()
                                                setOnPreparedListener {
                                                    start()
                                                    isPlaying = true
                                                }
                                                setOnCompletionListener {
                                                    isPlaying = false
                                                    release()
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop" else "Play",
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice ${message.mediaDuration?.let { formatDuration(it) } ?: ""}",
                                color = Color.White,
                                style = TextStyle(fontSize = 14.sp)
                            )
                        }
                    }
                }
            }

            // Reactions display
            if (message.reactions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(y = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    message.reactions.values.groupBy { it }.forEach { (emoji, users) ->
                        Text(
                            text = "$emoji${if (users.size > 1) users.size else ""}",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        }

        // Sender name and timestamp
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = highlightSearchQuery(message.senderFirstName, searchQuery),
                style = TextStyle(fontSize = 12.sp, color = Color.Gray)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatTimestamp(message.timestamp),
                style = TextStyle(fontSize = 12.sp, color = Color.Gray)
            )
        }

        // Reaction picker
        if (showReactionPicker) {
            Card(
                modifier = Modifier.padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                LazyRow(
                    modifier = Modifier.padding(8.dp)
                ) {
                    items(commonReactions) { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 24.sp,
                            modifier = Modifier
                                .padding(4.dp)
                                .clickable {
                                    onReact(emoji)
                                    showReactionPicker = false
                                }
                        )
                    }
                }
            }
        }

        // Enhanced Options menu with all features
        if (showOptions) {
            AlertDialog(
                onDismissRequest = { showOptions = false },
                title = { Text("Message Options") },
                text = {
                    Column {
                        // React
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showOptions = false
                                    showReactionPicker = true
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("😊", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("React")
                        }

                        // Reply
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showOptions = false
                                    onReply()
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Reply, contentDescription = "Reply")
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Reply")
                        }

                        // Forward
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showOptions = false
                                    onForward()
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Forward, contentDescription = "Forward")
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Forward")
                        }

                        // Copy (text only)
                        if (message.type == MessageType.TEXT) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showOptions = false
                                        onCopy()
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Copy Message")
                            }
                        }

                        // Delete for me
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showOptions = false
                                    onDelete()
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Delete for me")
                        }

                        // Delete for everyone (sender only)
                        if (message.senderId == currentUserId) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showOptions = false
                                        onDeleteForEveryone()
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Delete for everyone", color = Color.Red)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showOptions = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }
}

@Composable
fun highlightSearchQuery(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    return if (query.isNotEmpty() && text.contains(query, ignoreCase = true)) {
        buildAnnotatedString {
            var currentIndex = 0
            val lowerText = text.lowercase()
            val lowerQuery = query.lowercase()

            while (currentIndex < text.length) {
                val index = lowerText.indexOf(lowerQuery, currentIndex)
                if (index >= currentIndex) {
                    append(text.substring(currentIndex, index))
                    withStyle(style = SpanStyle(
                        background = Color.Yellow.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold
                    )) {
                        append(text.substring(index, index + query.length))
                    }
                    currentIndex = index + query.length
                } else {
                    append(text.substring(currentIndex))
                    break
                }
            }
        }
    } else {
        buildAnnotatedString { append(text) }
    }
}

private fun startRecording(
    context: Context,
    currentRecorder: MediaRecorder?,
    onRecordingStarted: (MediaRecorder, File) -> Unit
) {
    try {
        currentRecorder?.release()
        val audioFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.3gp")

        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFile.absolutePath)
            prepare()
            start()
        }

        onRecordingStarted(recorder, audioFile)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to start recording: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun stopRecording(recorder: MediaRecorder?) {
    try {
        recorder?.apply {
            stop()
            release()
        }
    } catch (e: Exception) {
        // Handle error silently
    }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%d:%02d", minutes, secs)
}

@RequiresApi(Build.VERSION_CODES.O)
private fun formatTimestamp(timestamp: Long): String {
    val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault())
    val now = LocalDateTime.now()
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    return when {
        dt.toLocalDate() == now.toLocalDate() -> dt.format(timeFmt)
        dt.toLocalDate().plusDays(1) == now.toLocalDate() -> "yesterday"
        else -> dt.format(DateTimeFormatter.ofPattern("MMM d"))
    }
}