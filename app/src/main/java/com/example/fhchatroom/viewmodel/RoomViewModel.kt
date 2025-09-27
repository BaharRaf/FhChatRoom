package com.example.fhchatroom.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.fhchatroom.Injection
import com.example.fhchatroom.data.Message
import com.example.fhchatroom.data.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject

class RoomViewModel : ViewModel() {

    private val _rooms = MutableLiveData<List<Room>>()
    val rooms: LiveData<List<Room>> get() = _rooms

    private val firestore = Injection.instance()
    private var roomListener: ListenerRegistration? = null
    private val messageListeners = mutableMapOf<String, ListenerRegistration>()
    private val auth = FirebaseAuth.getInstance()

    init {
        observeRoomsInRealTime()
    }

    private fun observeRoomsInRealTime() {
        roomListener = firestore.collection("rooms")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    return@addSnapshotListener
                }
                val self = auth.currentUser?.email
                if (snapshot != null) {
                    // Build rooms with robust boolean mapping for "isPrivate"/"private" and "isDirect"/"direct"
                    var updatedRooms = snapshot.documents.mapNotNull { doc ->
                        val base = doc.toObject<Room>()?.copy(id = doc.id) ?: return@mapNotNull null

                        // Fallbacks for documents where booleans were stored under "private"/"direct"
                        val privateFromDoc = (doc.getBoolean("isPrivate") ?: doc.getBoolean("private")) ?: base.isPrivate
                        val directFromDoc  = (doc.getBoolean("isDirect")  ?: doc.getBoolean("direct"))  ?: base.isDirect

                        // Check if user has hidden this room (for DMs)
                        val hiddenBy = (doc.get("hiddenBy") as? List<String>) ?: emptyList()

                        // Don't show room if user has hidden it
                        if (self != null && hiddenBy.contains(self)) {
                            return@mapNotNull null
                        }

                        base.copy(
                            isPrivate = privateFromDoc,
                            isDirect = directFromDoc
                        )
                    }

                    // Visibility: if signed-in, keep public + your private rooms; otherwise keep only public
                    updatedRooms = if (self != null) {
                        updatedRooms.filter { !it.isPrivate || it.ownerEmail == self || it.members.contains(self) }
                    } else {
                        updatedRooms.filter { !it.isPrivate }
                    }

                    _rooms.value = updatedRooms

                    // Prune listeners for rooms no longer present
                    val newIds = updatedRooms.map { it.id }.toSet()
                    val toRemove = messageListeners.keys - newIds
                    toRemove.forEach { id ->
                        messageListeners.remove(id)?.remove()
                    }

                    // Observe last message for each room (existing + new)
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
        val updates = hashMapOf<String, Any>(
            "lastMessage" to when (message.type.name) {
                "IMAGE" -> "📷 Photo"
                "VOICE" -> "🎤 Voice message"
                else -> message.text.take(50)
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
        messageListeners.clear()
    }

    fun createRoom(name: String, description: String) =
        createRoom(name, description, isPrivate = false)

    fun createRoom(name: String, description: String, isPrivate: Boolean) {
        val email = FirebaseAuth.getInstance().currentUser?.email
        val newRoom = Room(
            name = name,
            description = description,
            members = email?.let { listOf(it) } ?: emptyList(),
            ownerEmail = email ?: "",
            isPrivate = isPrivate,
            isDirect = false,
            createdAt = System.currentTimeMillis()
        )
        // Write the room, then add compatibility fields ("private")
        firestore.collection("rooms").add(newRoom)
            .addOnSuccessListener { ref ->
                // Keep both for compatibility with existing docs/readers
                ref.update(mapOf("private" to isPrivate))
            }
    }

    fun inviteToRoom(roomId: String, email: String, onResult: (Boolean, String) -> Unit) {
        // First check if the room exists and get its details
        firestore.collection("rooms").document(roomId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    onResult(false, "Room not found")
                    return@addOnSuccessListener
                }

                val room = document.toObject(Room::class.java)
                val currentUserEmail = auth.currentUser?.email

                // Check if current user is owner or member (for private groups)
                if (room?.isPrivate == true && room.ownerEmail != currentUserEmail && !room.members.contains(currentUserEmail)) {
                    onResult(false, "You don't have permission to invite to this room")
                    return@addOnSuccessListener
                }

                // Check if user is already a member
                if (room?.members?.contains(email) == true) {
                    onResult(false, "User is already a member")
                    return@addOnSuccessListener
                }

                // Add the user to the room
                firestore.collection("rooms").document(roomId)
                    .update("members", FieldValue.arrayUnion(email))
                    .addOnSuccessListener {
                        onResult(true, "User invited successfully")
                    }
                    .addOnFailureListener {
                        onResult(false, "Failed to invite user")
                    }
            }
            .addOnFailureListener {
                onResult(false, "Failed to check room details")
            }
    }

    fun joinRoom(roomId: String) {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return
        firestore.collection("rooms")
            .document(roomId)
            .update("members", FieldValue.arrayUnion(email))
    }

    fun leaveRoom(roomId: String, onComplete: (Boolean) -> Unit = {}) {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return

        firestore.collection("rooms").document(roomId)
            .get()
            .addOnSuccessListener { document ->
                val room = document.toObject(Room::class.java)

                if (room?.isDirect == true) {
                    // For DMs, add to hiddenBy list instead of removing from members
                    firestore.collection("rooms").document(roomId)
                        .update("hiddenBy", FieldValue.arrayUnion(email))
                        .addOnSuccessListener { onComplete(true) }
                        .addOnFailureListener { onComplete(false) }
                } else {
                    // For regular rooms, remove from members
                    firestore.collection("rooms").document(roomId)
                        .update("members", FieldValue.arrayRemove(email))
                        .addOnSuccessListener { onComplete(true) }
                        .addOnFailureListener { onComplete(false) }
                }
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun hideDM(roomId: String, onComplete: (Boolean) -> Unit = {}) {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return

        // Add current user to hiddenBy array
        firestore.collection("rooms").document(roomId)
            .update("hiddenBy", FieldValue.arrayUnion(email))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun unhideDM(roomId: String, onComplete: (Boolean) -> Unit = {}) {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return

        // Remove current user from hiddenBy array
        firestore.collection("rooms").document(roomId)
            .update("hiddenBy", FieldValue.arrayRemove(email))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun deleteRoom(roomId: String) {
        messageListeners.remove(roomId)?.remove()
        firestore.collection("rooms").document(roomId).delete()
    }

    /**
     * Open or create a 1:1 DM room between the current user and targetEmail.
     */
    fun openOrCreateDirectRoom(targetEmail: String, onResult: (String) -> Unit) {
        val self = FirebaseAuth.getInstance().currentUser?.email ?: return
        val roomsRef = firestore.collection("rooms")

        // 1) Find ANY 2-member room containing both users (regardless of isDirect).
        roomsRef
            .whereArrayContains("members", self)
            .get()
            .addOnSuccessListener { qs ->
                val existing = qs.documents.firstOrNull { doc ->
                    val members = (doc.get("members") as? List<*>)?.map { it as String } ?: emptyList()
                    val hiddenBy = (doc.get("hiddenBy") as? List<String>) ?: emptyList()
                    members.size == 2 && members.contains(targetEmail)
                }

                if (existing != null) {
                    val isDirect = (existing.get("isDirect") as? Boolean)
                        ?: (existing.get("direct") as? Boolean) ?: false
                    val hiddenBy = (existing.get("hiddenBy") as? List<String>) ?: emptyList()

                    // If user had hidden this DM, unhide it
                    if (hiddenBy.contains(self)) {
                        existing.reference.update("hiddenBy", FieldValue.arrayRemove(self))
                    }

                    if (!isDirect) {
                        // Normalize legacy 2-member room to a DM (set both field name variants)
                        existing.reference.update(
                            mapOf(
                                "isDirect" to true,
                                "isPrivate" to true,
                                "direct" to true,
                                "private" to true
                            )
                        ).addOnCompleteListener {
                            onResult(existing.id)
                        }
                    } else {
                        onResult(existing.id)
                    }
                } else {
                    // 2) Create a brand new DM
                    val dm = Room(
                        name = "DM",
                        description = "",
                        members = listOf(self, targetEmail),
                        ownerEmail = self,
                        isPrivate = true,
                        isDirect = true,
                        createdAt = System.currentTimeMillis()
                    )
                    roomsRef.add(dm).addOnSuccessListener { ref ->
                        // Also set compatibility keys
                        ref.update(mapOf("private" to true, "direct" to true))
                        onResult(ref.id)
                    }
                }
            }
    }
}