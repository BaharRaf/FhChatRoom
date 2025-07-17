package com.example.fhchatroom.viewmodel

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
import kotlinx.coroutines.launch

class MessageViewModel : ViewModel() {

    private val messageRepository: MessageRepository
    private val userRepository: UserRepository

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

    fun setRoomId(roomId: String) {
        _roomId.value = roomId
        // Only load messages if current user is already known; otherwise, wait for user to load
        if (_currentUser.value != null) {
            loadMessages()
        }
    }

    fun sendMessage(text: String) {
        if (_currentUser.value != null) {
            val message = Message(
                senderFirstName = _currentUser.value!!.firstName,
                senderId = _currentUser.value!!.email,
                text = text
            )
            viewModelScope.launch {
                val result = messageRepository.sendMessage(_roomId.value.toString(), message)
                _sendResult.value = result
            }
        }
    }

    fun loadMessages() {
        viewModelScope.launch {
            val room = _roomId.value ?: return@launch
            val userEmail = _currentUser.value?.email ?: return@launch
            // Collect messages from repository, filtering out those deleted for this user
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
                    // If a chat room is already set, load messages now that user is known
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

    // NEW: Expose deletion methods from the repository
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
