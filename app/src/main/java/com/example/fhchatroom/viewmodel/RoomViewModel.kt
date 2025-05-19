package com.example.fhchatroom.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Result.*
import com.example.fhchatroom.data.Room
import com.example.fhchatroom.data.RoomRepository
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

class RoomViewModel : ViewModel() {

    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> get() = _rooms
    private val roomRepository: RoomRepository

    init {
        roomRepository = RoomRepository(Injection.instance())
        loadRooms()
    }

    fun createRoom(name: String) {
        viewModelScope.launch {
            // Create the room via repository
            val email = FirebaseAuth.getInstance().currentUser?.email
            if (email != null) {
                roomRepository.createRoom(name, email)
            }
            // Refresh the room list so the new room appears in the UI.
            loadRooms()
        }
    }

    fun loadRooms() {
        viewModelScope.launch {
            when (val result = roomRepository.getRooms()) {
                is Success -> _rooms.value = result.data
                is Error -> {
                    // Optionally log or handle the error here.
                }
            }
        }
    }
    //join then reload list
    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            val email = FirebaseAuth.getInstance().currentUser?.email
            if (email != null) {
                roomRepository.joinRoom(roomId, email)
                loadRooms()
            }
        }
    }
    //leave then reload list
    fun leaveRoom(roomId: String) {
        viewModelScope.launch {
            val email = FirebaseAuth.getInstance().currentUser?.email
            if (email != null) {
                roomRepository.leaveRoom(roomId, email)
                loadRooms()
            }
        }
    }
    //delete then reload list
    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            roomRepository.deleteRoom(roomId)
            loadRooms()
        }
    }
}
