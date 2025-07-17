package com.example.fhchatroom.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import com.google.firebase.auth.FirebaseAuth
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Message
import com.example.fhchatroom.data.MessageRepository
import com.example.fhchatroom.data.Result.*
import com.example.fhchatroom.data.User
import com.example.fhchatroom.data.UserRepository
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

    private val _sendResult = MutableLiveData<com.example.fhchatroom.data.Result<Unit>?>(null)
    val sendResult: LiveData<com.example.fhchatroom.data.Result<Unit>?> get() = _sendResult

    private val _deleteResult = MutableLiveData<com.example.fhchatroom.data.Result<Unit>?>(null)
    val deleteResult: LiveData<com.example.fhchatroom.data.Result<Unit>?> get() = _deleteResult

    fun clearSendResult() {
        _sendResult.value = null
    }

    fun clearDeleteResult() {
        _deleteResult.value = null
    }

    private var messageJob: Job? = null

    fun setRoomId(roomId: String) {
        _roomId.value = roomId
        loadMessages()
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
        messageJob?.cancel()
        messageJob = viewModelScope.launch {
            if (_roomId.value != null) {
                messageRepository.getChatMessages(_roomId.value.toString())
                    .collect { list ->
                        val email = _currentUser.value?.email
                        _messages.value = if (email != null) {
                            list.filter { !it.deletedFor.contains(email) }
                        } else list
                    }
            }
        }
    }

    fun deleteMessageForEveryone(message: Message) {
        viewModelScope.launch {
            _deleteResult.value = messageRepository.deleteMessage(
                _roomId.value.toString(),
                message.id
            )
        }
    }

    fun deleteMessageForMe(message: Message) {
        val email = _currentUser.value?.email ?: return
        viewModelScope.launch {
            _deleteResult.value = messageRepository.markMessageDeletedForUser(
                _roomId.value.toString(),
                message.id,
                email
            )
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            when (val result = userRepository.getCurrentUser()) {
                is Success -> {
                    _currentUser.value = result.data
                    loadMessages()
                }
                is Error -> {
                    Log.e("MessageViewModel", "Failed to load current user", result.exception)
                }
            }
        }
    }
}
