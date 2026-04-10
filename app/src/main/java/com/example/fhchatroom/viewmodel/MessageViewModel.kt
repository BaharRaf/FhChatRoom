package com.example.fhchatroom.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Message
import com.example.fhchatroom.data.MessageRepository
import com.example.fhchatroom.data.MessageType
import com.example.fhchatroom.data.Result.Error
import com.example.fhchatroom.data.Result.Success
import com.example.fhchatroom.data.User
import com.example.fhchatroom.data.UserRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class MessageViewModel : ViewModel() {
    companion object {
        private const val ROOM_PREVIEW_MAX_LENGTH = 50
        private const val MAX_TEXT_MESSAGE_LENGTH = 4000
    }

    private val messageRepository: MessageRepository
    private val userRepository: UserRepository
    private val storage = FirebaseStorage.getInstance()
    private val firestore = Injection.instance()

    init {
        messageRepository = MessageRepository(Injection.instance())
        userRepository = UserRepository(
            FirebaseAuth.getInstance(),
            Injection.instance()
        )
        loadCurrentUser()
    }

    private val _messages = MutableLiveData<List<Message>>()
    val messages: LiveData<List<Message>> get() = _messages

    private val _roomId = MutableLiveData<String>()
    private val _currentUser = MutableLiveData<User>()
    val currentUser: LiveData<User> get() = _currentUser

    private val _sendResult = MutableLiveData<com.example.fhchatroom.data.Result<Unit>>()
    val sendResult: LiveData<com.example.fhchatroom.data.Result<Unit>> get() = _sendResult

    private val _uploadProgress = MutableLiveData<Float>()
    val uploadProgress: LiveData<Float> get() = _uploadProgress

    fun setRoomId(roomId: String) {
        _roomId.value = roomId
        if (_currentUser.value != null) {
            loadMessages()
        }
    }

    fun sendMessage(text: String, replyToMessage: Message? = null) {
        val currentUser = _currentUser.value ?: return
        val roomId = _roomId.value ?: return
        val normalizedText = text.trim()

        if (normalizedText.isBlank()) {
            return
        }
        if (normalizedText.length > MAX_TEXT_MESSAGE_LENGTH) {
            _sendResult.value = Error(
                IllegalArgumentException("Messages can be at most $MAX_TEXT_MESSAGE_LENGTH characters.")
            )
            return
        }

        val messageData = hashMapOf<String, Any>(
            "senderFirstName" to currentUser.firstName,
            "senderId" to currentUser.email,
            "text" to normalizedText,
            "type" to MessageType.TEXT.name,
            "reactions" to emptyMap<String, String>(),
            "deletedFor" to emptyList<String>()
        )

        replyToMessage?.let {
            messageData["replyToMessageId"] = it.id ?: ""
            messageData["replyToMessageText"] = when (it.type) {
                MessageType.IMAGE -> "📷 Photo"
                MessageType.VOICE -> "🎤 Voice message"
                else -> it.text.take(100)
            }
            messageData["replyToSenderName"] = it.senderFirstName
        }

        viewModelScope.launch {
            try {
                sendMessageWithRoomUpdate(
                    roomId = roomId,
                    currentUser = currentUser,
                    messageData = messageData,
                    type = MessageType.TEXT,
                    roomPreviewText = normalizedText
                )
                _sendResult.value = Success(Unit)
            } catch (e: Exception) {
                _sendResult.value = Error(e)
                Log.e("MessageViewModel", "Failed to send message", e)
            }
        }
    }

    fun sendPhotoMessage(uri: Uri, roomId: String) {
        viewModelScope.launch {
            try {
                _uploadProgress.value = 0f
                val fileName = "images/${UUID.randomUUID()}.jpg"
                val storageRef = storage.reference.child(fileName)

                val uploadTask = storageRef.putFile(uri)

                uploadTask.addOnProgressListener { snapshot ->
                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toFloat()
                    _uploadProgress.value = progress
                }

                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val currentUser = _currentUser.value ?: throw IllegalStateException("Current user not loaded")
                val messageData = hashMapOf<String, Any>(
                    "senderFirstName" to currentUser.firstName,
                    "senderId" to currentUser.email,
                    "text" to "Photo",
                    "type" to MessageType.IMAGE.name,
                    "mediaUrl" to downloadUrl,
                    "reactions" to emptyMap<String, String>(),
                    "deletedFor" to emptyList<String>()
                )

                sendMessageWithRoomUpdate(
                    roomId = roomId,
                    currentUser = currentUser,
                    messageData = messageData,
                    type = MessageType.IMAGE,
                    roomPreviewText = "📷 Photo"
                )

                _sendResult.value = Success(Unit)
                _uploadProgress.value = 100f
            } catch (e: Exception) {
                _sendResult.value = Error(e)
                Log.e("MessageViewModel", "Failed to upload photo", e)
            }
        }
    }

    fun sendCameraPhoto(bitmap: Bitmap, roomId: String) {
        viewModelScope.launch {
            try {
                _uploadProgress.value = 0f
                val fileName = "images/${UUID.randomUUID()}.jpg"
                val storageRef = storage.reference.child(fileName)

                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
                val data = baos.toByteArray()

                val uploadTask = storageRef.putBytes(data)

                uploadTask.addOnProgressListener { snapshot ->
                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toFloat()
                    _uploadProgress.value = progress
                }

                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val currentUser = _currentUser.value ?: throw IllegalStateException("Current user not loaded")
                val messageData = hashMapOf<String, Any>(
                    "senderFirstName" to currentUser.firstName,
                    "senderId" to currentUser.email,
                    "text" to "Photo",
                    "type" to MessageType.IMAGE.name,
                    "mediaUrl" to downloadUrl,
                    "reactions" to emptyMap<String, String>(),
                    "deletedFor" to emptyList<String>()
                )

                sendMessageWithRoomUpdate(
                    roomId = roomId,
                    currentUser = currentUser,
                    messageData = messageData,
                    type = MessageType.IMAGE,
                    roomPreviewText = "📷 Photo"
                )

                _sendResult.value = Success(Unit)
                _uploadProgress.value = 100f
            } catch (e: Exception) {
                _sendResult.value = Error(e)
                Log.e("MessageViewModel", "Failed to upload camera photo", e)
            }
        }
    }

    fun sendVoiceMessage(audioFile: File, duration: Int, roomId: String) {
        viewModelScope.launch {
            try {
                _uploadProgress.value = 0f
                val fileName = "voice/${UUID.randomUUID()}.3gp"
                val storageRef = storage.reference.child(fileName)

                val uploadTask = storageRef.putFile(Uri.fromFile(audioFile))

                uploadTask.addOnProgressListener { snapshot ->
                    val progress = (100.0 * snapshot.bytesTransferred / snapshot.totalByteCount).toFloat()
                    _uploadProgress.value = progress
                }

                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                audioFile.delete()

                val currentUser = _currentUser.value ?: throw IllegalStateException("Current user not loaded")
                val messageData = hashMapOf<String, Any>(
                    "senderFirstName" to currentUser.firstName,
                    "senderId" to currentUser.email,
                    "text" to "Voice message",
                    "type" to MessageType.VOICE.name,
                    "mediaUrl" to downloadUrl,
                    "mediaDuration" to duration,
                    "reactions" to emptyMap<String, String>(),
                    "deletedFor" to emptyList<String>()
                )

                sendMessageWithRoomUpdate(
                    roomId = roomId,
                    currentUser = currentUser,
                    messageData = messageData,
                    type = MessageType.VOICE,
                    roomPreviewText = "🎤 Voice message"
                )

                _sendResult.value = Success(Unit)
                _uploadProgress.value = 100f
            } catch (e: Exception) {
                _sendResult.value = Error(e)
                Log.e("MessageViewModel", "Failed to upload voice message", e)
            }
        }
    }

    fun addReaction(roomId: String, messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                val userEmail = _currentUser.value?.email ?: return@launch

                Log.d("MessageViewModel", "Adding reaction: $emoji to message: $messageId by user: $userEmail")

                // Get current message document
                val messageRef = firestore.collection("rooms")
                    .document(roomId)
                    .collection("messages")
                    .document(messageId)

                firestore.runTransaction { transaction ->
                    val messageSnapshot = transaction.get(messageRef)

                    @Suppress("UNCHECKED_CAST")
                    val currentReactions = (messageSnapshot.get("reactions") as? Map<String, String>) ?: emptyMap()

                    // Toggle reaction - remove if already exists with same emoji, otherwise add/update
                    val updatedReactions = if (currentReactions[userEmail] == emoji) {
                        // Remove reaction
                        currentReactions.toMutableMap().apply { remove(userEmail) }
                    } else {
                        // Add or update reaction
                        currentReactions.toMutableMap().apply { put(userEmail, emoji) }
                    }

                    // Update the document
                    transaction.update(messageRef, "reactions", updatedReactions)
                }.await()

                Log.d("MessageViewModel", "Reaction updated successfully")

            } catch (e: Exception) {
                Log.e("MessageViewModel", "Failed to add reaction", e)
            }
        }
    }

    fun forwardMessage(message: Message, toRoomId: String) {
        viewModelScope.launch {
            try {
                val currentUser = _currentUser.value ?: return@launch

                val forwardData = hashMapOf<String, Any>(
                    "senderFirstName" to currentUser.firstName,
                    "senderId" to currentUser.email,
                    "reactions" to emptyMap<String, String>(),
                    "deletedFor" to emptyList<String>()
                )

                // Handle different message types
                when (message.type) {
                    MessageType.TEXT -> {
                        forwardData["text"] = "Forwarded from ${message.senderFirstName}: ${message.text}"
                        forwardData["type"] = MessageType.TEXT.name
                    }
                    MessageType.IMAGE -> {
                        forwardData["text"] = "Forwarded photo from ${message.senderFirstName}"
                        forwardData["type"] = MessageType.IMAGE.name
                        message.mediaUrl?.let { forwardData["mediaUrl"] = it }
                    }
                    MessageType.VOICE -> {
                        forwardData["text"] = "Forwarded voice message from ${message.senderFirstName}"
                        forwardData["type"] = MessageType.VOICE.name
                        message.mediaUrl?.let { forwardData["mediaUrl"] = it }
                        message.mediaDuration?.let { forwardData["mediaDuration"] = it }
                    }
                }

                val forwardType = MessageType.valueOf(forwardData["type"] as String)
                sendMessageWithRoomUpdate(
                    roomId = toRoomId,
                    currentUser = currentUser,
                    messageData = forwardData,
                    type = forwardType,
                    roomPreviewText = forwardData["text"] as? String ?: ""
                )

                Log.d("MessageViewModel", "Message forwarded successfully to room: $toRoomId")

            } catch (e: Exception) {
                Log.e("MessageViewModel", "Failed to forward message", e)
            }
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            val room = _roomId.value ?: return@launch
            val userEmail = _currentUser.value?.email ?: return@launch
            messageRepository.getChatMessages(room, userEmail).collect { fetchedMessages ->
                _messages.value = fetchedMessages
            }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            when (val result = userRepository.getCurrentUser()) {
                is Success -> {
                    _currentUser.value = result.data
                    if (_roomId.value != null) {
                        loadMessages()
                    }
                }
                is Error -> {
                    Log.e("MessageViewModel", "Failed to load current user", result.exception)
                }
            }
        }
    }

    fun deleteMessageForEveryone(roomId: String, messageId: String) {
        viewModelScope.launch {
            messageRepository.deleteMessageForEveryone(roomId, messageId)
        }
    }

    fun deleteMessageForMe(roomId: String, messageId: String, userEmail: String) {
        viewModelScope.launch {
            messageRepository.deleteMessageForMe(roomId, messageId, userEmail)
        }
    }

    private suspend fun sendMessageWithRoomUpdate(
        roomId: String,
        currentUser: User,
        messageData: HashMap<String, Any>,
        type: MessageType,
        roomPreviewText: String
    ) {
        val timestamp = Timestamp.now()
        val messageRef = firestore.collection("rooms")
            .document(roomId)
            .collection("messages")
            .document()

        val payload = HashMap(messageData).apply {
            put("timestamp", timestamp)
        }

        val previewText = when (type) {
            MessageType.IMAGE -> "📷 Photo"
            MessageType.VOICE -> "🎤 Voice message"
            MessageType.TEXT -> buildPreviewText(roomPreviewText)
        }

        val batch = firestore.batch()
        batch.set(messageRef, payload)
        batch.update(
            firestore.collection("rooms").document(roomId),
            mapOf(
                "lastMessage" to previewText,
                "lastMessageSender" to currentUser.firstName,
                "lastMessageTimestamp" to timestamp.toDate().time,
                "lastMessageType" to type.name
            )
        )
        batch.commit().await()
    }

    private fun buildPreviewText(text: String): String {
        val compact = text.trim().replace(Regex("\\s+"), " ")
        return if (compact.length <= ROOM_PREVIEW_MAX_LENGTH) {
            compact
        } else {
            compact.take(ROOM_PREVIEW_MAX_LENGTH - 3) + "..."
        }
    }
}
