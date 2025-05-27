package com.example.fhchatroom.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Room
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.toObject

class RoomViewModel : ViewModel() {

    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> get() = _rooms

    private val firestore = Injection.instance()
    private var roomListener: ListenerRegistration? = null

    init {
        observeRoomsInRealTime()
    }

    private fun observeRoomsInRealTime() {
        roomListener = firestore.collection("rooms")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val updatedRooms = snapshot.documents.mapNotNull { doc ->
                        val room = doc.toObject<Room>()
                        room?.copy(id = doc.id)
                    }
                    _rooms.value = updatedRooms
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        roomListener?.remove()
    }

    fun createRoom(name: String, description: String) {
        val currentUserEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
        val newRoom = Room(
            name = name,
            description = description,
            members = currentUserEmail?.let { listOf(it) } ?: emptyList(),
            ownerEmail = currentUserEmail ?: ""  // **Added**: store creator's email as owner
        )
        firestore.collection("rooms").add(newRoom)
    }

    fun joinRoom(roomId: String) {
        val email = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: return
        firestore.collection("rooms")
            .document(roomId)
            .update("members", com.google.firebase.firestore.FieldValue.arrayUnion(email))
    }

    fun leaveRoom(roomId: String) {
        val email = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: return
        firestore.collection("rooms")
            .document(roomId)
            .update("members", com.google.firebase.firestore.FieldValue.arrayRemove(email))
    }

    fun deleteRoom(roomId: String) {
        firestore.collection("rooms").document(roomId).delete()
    }
}
