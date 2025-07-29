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
import com.example.fhchatroom.data.Result.Error
import com.example.fhchatroom.data.Result.Success
import com.example.fhchatroom.data.User
import com.example.fhchatroom.data.UserRepository
import com.google.firebase.auth.FirebaseAuth
//import com.google.firebase.inappmessaging.model.MessageType
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import com.example.fhchatroom.data.MessageType

class MessageViewModel : ViewModel() {

    private val messageRepository: MessageRepository
    private val userRepository: UserRepository
    private val storage = FirebaseStorage.getInstance()

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

    fun sendMessage(text: String) {
        if (_currentUser.value != null) {
            val message = Message(
                senderFirstName = _currentUser.value!!.firstName,
                senderId = _currentUser.value!!.email,
                text = text,
                type = MessageType.TEXT
            )
            viewModelScope.launch {
                val result = messageRepository.sendMessage(_roomId.value.toString(), message)
                _sendResult.value = result
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

                val message = Message(
                    senderFirstName = _currentUser.value!!.firstName,
                    senderId = _currentUser.value!!.email,
                    text = "Photo",
                    type = MessageType.IMAGE,
                    mediaUrl = downloadUrl
                )

                val result = messageRepository.sendMessage(roomId, message)
                _sendResult.value = result
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

                // Convert bitmap to byte array
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

                val message = Message(
                    senderFirstName = _currentUser.value!!.firstName,
                    senderId = _currentUser.value!!.email,
                    text = "Photo",
                    type = MessageType.IMAGE,
                    mediaUrl = downloadUrl
                )

                val result = messageRepository.sendMessage(roomId, message)
                _sendResult.value = result
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

                // Delete local file after upload
                audioFile.delete()

                val message = Message(
                    senderFirstName = _currentUser.value!!.firstName,
                    senderId = _currentUser.value!!.email,
                    text = "Voice message",
                    type = MessageType.VOICE,
                    mediaUrl = downloadUrl,
                    mediaDuration = duration
                )

                val result = messageRepository.sendMessage(roomId, message)
                _sendResult.value = result
                _uploadProgress.value = 100f
            } catch (e: Exception) {
                _sendResult.value = Error(e)
                Log.e("MessageViewModel", "Failed to upload voice message", e)
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