package com.example.fhchatroom.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top bar
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
                val isMine = message.senderId == messageViewModel.currentUser.value?.email
                ChatMessageItem(
                    message = message.copy(isSentByCurrentUser = isMine),
                    onLongPress = {
                        selectedMessage.value = message
                        showDeleteDialog.value = true
                    }
                )
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

    // Delete dialog (unchanged)
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
                if (isSender) {
                    TextButton(onClick = {
                        messageViewModel.deleteMessageForEveryone(roomId, message.id!!)
                        showDeleteDialog.value = false
                        selectedMessage.value = null
                    }) {
                        Text("Delete for everyone")
                    }
                } else {
                    TextButton(onClick = {
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
                    TextButton(onClick = {
                        messageViewModel.deleteMessageForMe(roomId, message.id!!, messageViewModel.currentUser.value!!.email)
                        showDeleteDialog.value = false
                        selectedMessage.value = null
                    }) {
                        Text("Delete for me")
                    }
                } else {
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
fun ChatMessageItem(message: Message, onLongPress: () -> Unit = {}) {
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
                    color = if (message.isSentByCurrentUser) Color(0xFFBB86FC) else Color.Gray,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(8.dp)
        ) {
            when (message.type) {
                MessageType.TEXT -> {
                    Text(
                        text = message.text,
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