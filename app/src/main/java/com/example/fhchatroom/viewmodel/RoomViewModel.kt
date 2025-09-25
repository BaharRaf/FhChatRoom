package com.example.fhchatroom.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Message
import com.example.fhchatroom.data.Room
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject

class RoomViewModel : ViewModel() {

    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> get() = _rooms

    private val firestore = Injection.instance()
    private var roomListener: ListenerRegistration? = null
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()

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

                    // Observe last message for each room
                    updatedRooms.forEach { room ->
                        observeLastMessageForRoom(room.id)
                    }
                }
            }
    }

    private fun observeLastMessageForRoom(roomId: String) {
        // Remove existing listener if any
        messageListeners[roomId]?.remove()

        // Add new listener for the last message
        val listener = firestore.collection("rooms")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val lastMessage = snapshot.documents.firstOrNull()?.toObject<Message>()
                if (lastMessage != null) {
                    // Update the room with last message info
                    updateRoomWithLastMessage(roomId, lastMessage)
                }
            }

        messageListeners[roomId] = listener
    }

    private fun updateRoomWithLastMessage(roomId: String, message: Message) {
        // Update the room document with last message info
        val updates = hashMapOf<String, Any>(
            "lastMessage" to when(message.type.name) {
                "IMAGE" -> "📷 Photo"
                "VOICE" -> "🎤 Voice message"
                else -> message.text.take(50) // Limit preview to 50 characters
            },
            "lastMessageSender" to message.senderFirstName,
            "lastMessageTimestamp" to message.timestamp,
            "lastMessageType" to message.type.name
        )

        firestore.collection("rooms")
            .document(roomId)
            .update(updates)
    }

    override fun onCleared() {
        super.onCleared()
        roomListener?.remove()
        messageListeners.values.forEach { it.remove() }
    }

    fun createRoom(name: String, description: String) {
        val currentUserEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
        val newRoom = Room(
            name = name,
            description = description,
            members = currentUserEmail?.let { listOf(it) } ?: emptyList(),
            ownerEmail = currentUserEmail ?: "",
            lastMessage = "",
            lastMessageSender = "",
            lastMessageTimestamp = System.currentTimeMillis(),
            lastMessageType = "TEXT"
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
        // Remove message listener before deleting
        messageListeners[roomId]?.remove()
        messageListeners.remove(roomId)

        firestore.collection("rooms").document(roomId).delete()
    }
}