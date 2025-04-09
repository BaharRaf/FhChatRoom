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
            roomRepository.createRoom(name)
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
}
