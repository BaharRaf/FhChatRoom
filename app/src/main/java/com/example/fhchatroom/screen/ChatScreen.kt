package com.example.fhchatroom.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
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

    val showDeleteDialog = remember { mutableStateOf(false) }
    val selectedMessage = remember { mutableStateOf<Message?>(null) }

    // Search states
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentSearchIndex by remember { mutableStateOf(-1) }
    val lazyListState = rememberLazyListState()

    // Filter messages based on search query
    val filteredMessages = if (searchQuery.isEmpty()) {
        messages
    } else {
        messages
    }

    // Find messages that match search query
    val searchMatches = if (searchQuery.isNotEmpty()) {
        messages.mapIndexedNotNull { index, message ->
            if (message.text.contains(searchQuery, ignoreCase = true) ||
                message.senderFirstName.contains(searchQuery, ignoreCase = true)) {
                index
            } else null
        }
    } else {
        emptyList()
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
            Toast.makeText(context, "Microphone permission required for voice messages", Toast.LENGTH_SHORT).show()
        }
    }

    // Message options menu
    var showMessageMenu by remember { mutableStateOf(false) }
    var menuMessage by remember { mutableStateOf<Message?>(null) }

    // Show attachment options dialog
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
    LaunchedEffect(currentSearchIndex) {
        if (currentSearchIndex >= 0 && currentSearchIndex < searchMatches.size) {
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
                        currentSearchIndex = -1
                    }
                }) {
                    Icon(
                        imageVector = if (showSearchBar) Icons.Default.Clear else Icons.Default.Search,
                        contentDescription = if (showSearchBar) "Close Search" else "Search Messages"
                    )
                }
                IconButton(onClick = onShowMembers) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "Members Menu")
                }
            }
        }

        // Search bar
        AnimatedVisibility(
            visible = showSearchBar,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        currentSearchIndex = if (it.isNotEmpty() && searchMatches.isNotEmpty()) 0 else -1
                    },
                    label = { Text("Search messages...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                currentSearchIndex = -1
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    singleLine = true
                )

                // Search navigation
                if (searchMatches.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentSearchIndex + 1} of ${searchMatches.size} matches",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                }
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = lazyListState
        ) {
            items(messages.mapIndexed { index, message -> index to message }) { (index, message) ->
                val isMine = message.senderId == messageViewModel.currentUser.value?.email
                val isHighlighted = searchQuery.isNotEmpty() &&
                        searchMatches.contains(index) &&
                        searchMatches.indexOf(index) == currentSearchIndex

                ChatMessageItem(
                    message = message.copy(isSentByCurrentUser = isMine),
                    searchQuery = searchQuery,
                    isHighlighted = isHighlighted,
                    onLongPress = {
                        menuMessage = message
                        showMessageMenu = true
                    }
                )
            }
        }

        // Message options dropdown menu
        if (showMessageMenu && menuMessage != null) {
            val msg = menuMessage!!
            val isSender = msg.senderId == messageViewModel.currentUser.value?.email

            AlertDialog(
                onDismissRequest = {
                    showMessageMenu = false
                    menuMessage = null
                },
                title = { Text("Message Options") },
                text = {
                    Column {
                        // Copy option (only for text messages)
                        if (msg.type == MessageType.TEXT) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("message", msg.text)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Message copied to clipboard", Toast.LENGTH_SHORT).show()
                                        showMessageMenu = false
                                        menuMessage = null
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
                                    messageViewModel.deleteMessageForMe(
                                        roomId,
                                        msg.id!!,
                                        messageViewModel.currentUser.value!!.email
                                    )
                                    showMessageMenu = false
                                    menuMessage = null
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Delete for me")
                        }

                        // Delete for everyone (only for sender)
                        if (isSender) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        messageViewModel.deleteMessageForEveryone(roomId, msg.id!!)
                                        showMessageMenu = false
                                        menuMessage = null
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
                    TextButton(onClick = {
                        showMessageMenu = false
                        menuMessage = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
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
                // Attachment button
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

                // Voice or send button
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
                                messageViewModel.sendMessage(content)
                                textState.value = ""
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

    LaunchedEffect(sendResult) {
        when (val result = sendResult) {
            is Result.Success -> {
                // Success handled silently
            }
            is Result.Error -> {
                Toast.makeText(context, "Failed to send: ${result.exception.message}", Toast.LENGTH_LONG).show()
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
    onLongPress: () -> Unit = {}
) {
    var isPlaying by remember { mutableStateOf(false) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .combinedClickable(
                onLongClick = onLongPress,
                onClick = {}
            ),
        horizontalAlignment = if (message.isSentByCurrentUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = when {
                        isHighlighted -> MaterialTheme.colorScheme.tertiaryContainer
                        message.isSentByCurrentUser -> Color(0xFFBB86FC)
                        else -> Color.Gray
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            when (message.type) {
                MessageType.TEXT -> {
                    // Highlight search terms in text
                    if (searchQuery.isNotEmpty() && message.text.contains(searchQuery, ignoreCase = true)) {
                        val annotatedString = buildAnnotatedString {
                            var currentIndex = 0
                            val lowerText = message.text.lowercase()
                            val lowerQuery = searchQuery.lowercase()

                            while (currentIndex < message.text.length) {
                                val index = lowerText.indexOf(lowerQuery, currentIndex)
                                if (index >= currentIndex) {
                                    // Add text before match
                                    append(message.text.substring(currentIndex, index))
                                    // Add highlighted match
                                    withStyle(style = SpanStyle(
                                        background = Color.Yellow.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Bold
                                    )) {
                                        append(message.text.substring(index, index + searchQuery.length))
                                    }
                                    currentIndex = index + searchQuery.length
                                } else {
                                    // Add remaining text
                                    append(message.text.substring(currentIndex))
                                    break
                                }
                            }
                        }
                        Text(
                            text = annotatedString,
                            color = Color.White,
                            style = TextStyle(fontSize = 16.sp)
                        )
                    } else {
                        Text(
                            text = message.text,
                            color = Color.White,
                            style = TextStyle(fontSize = 16.sp)
                        )
                    }
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
        Spacer(modifier = Modifier.height(4.dp))
        // Highlight sender name if it matches search
        if (searchQuery.isNotEmpty() && message.senderFirstName.contains(searchQuery, ignoreCase = true)) {
            Text(
                text = message.senderFirstName,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            )
        } else {
            Text(
                text = message.senderFirstName,
                style = TextStyle(fontSize = 12.sp, color = Color.Gray)
            )
        }
        Text(
            text = formatTimestamp(message.timestamp),
            style = TextStyle(fontSize = 12.sp, color = Color.Gray)
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }
}

private fun startRecording(
    context: android.content.Context,
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
        dt.toLocalDate() == now.toLocalDate() -> "today ${dt.format(timeFmt)}"
        dt.toLocalDate().plusDays(1) == now.toLocalDate() -> "yesterday ${dt.format(timeFmt)}"
        else -> dt.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }
}