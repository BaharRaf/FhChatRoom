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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class MessageViewModel : ViewModel() {

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
        if (_currentUser.value != null && _roomId.value != null) {
            val messageData = hashMapOf(
                "senderFirstName" to _currentUser.value!!.firstName,
                "senderId" to _currentUser.value!!.email,
                "text" to text,
                "timestamp" to System.currentTimeMillis(),
                "type" to MessageType.TEXT.name,
                "reactions" to emptyMap<String, String>(),
                "deletedFor" to emptyList<String>()
            )

            // Add reply fields if replying
            replyToMessage?.let {
                messageData["replyToMessageId"] = it.id ?: ""
                messageData["replyToMessageText"] = when(it.type) {
                    MessageType.IMAGE -> "📷 Photo"
                    MessageType.VOICE -> "🎤 Voice message"
                    else -> it.text.take(100)
                }
                messageData["replyToSenderName"] = it.senderFirstName
            }

            viewModelScope.launch {
                try {
                    firestore.collection("rooms")
                        .document(_roomId.value!!)
                        .collection("messages")
                        .add(messageData)
                        .await()
                    _sendResult.value = Success(Unit)
                } catch (e: Exception) {
                    _sendResult.value = Error(e)
                    Log.e("MessageViewModel", "Failed to send message", e)
                }
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

                val messageData = hashMapOf(
                    "senderFirstName" to _currentUser.value!!.firstName,
                    "senderId" to _currentUser.value!!.email,
                    "text" to "Photo",
                    "timestamp" to System.currentTimeMillis(),
                    "type" to MessageType.IMAGE.name,
                    "mediaUrl" to downloadUrl,
                    "reactions" to emptyMap<String, String>(),
                    "deletedFor" to emptyList<String>()
                )

                firestore.collection("rooms")
                    .document(roomId)
                    .collection("messages")
                    .add(messageData)
                    .await()

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

                val messageData = hashMapOf(
                    "senderFirstName" to _currentUser.value!!.firstName,
                    "senderId" to _currentUser.value!!.email,
                    "text" to "Photo",
                    "timestamp" to System.currentTimeMillis(),
                    "type" to MessageType.IMAGE.name,
                    "mediaUrl" to downloadUrl,
                    "reactions" to emptyMap<String, String>(),
                    "deletedFor" to emptyList<String>()
                )

                firestore.collection("rooms")
                    .document(roomId)
                    .collection("messages")
                    .add(messageData)
                    .await()

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

                val messageData = hashMapOf(
                    "senderFirstName" to _currentUser.value!!.firstName,
                    "senderId" to _currentUser.value!!.email,
                    "text" to "Voice message",
                    "timestamp" to System.currentTimeMillis(),
                    "type" to MessageType.VOICE.name,
                    "mediaUrl" to downloadUrl,
                    "mediaDuration" to duration,
                    "reactions" to emptyMap<String, String>(),
                    "deletedFor" to emptyList<String>()
                )

                firestore.collection("rooms")
                    .document(roomId)
                    .collection("messages")
                    .add(messageData)
                    .await()

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

                val forwardData = hashMapOf(
                    "senderFirstName" to currentUser.firstName,
                    "senderId" to currentUser.email,
                    "timestamp" to System.currentTimeMillis(),
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

                firestore.collection("rooms")
                    .document(toRoomId)
                    .collection("messages")
                    .add(forwardData)
                    .await()

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
}