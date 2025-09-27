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

                    // 🔹 Prune listeners for rooms no longer present
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

    // Overload: preserve original signature (calls new one as public room)
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
                // Keep both for compatibility with any existing docs/readers
                ref.update(mapOf("private" to isPrivate))
            }
    }

    fun inviteToRoom(roomId: String, email: String) {
        firestore.collection("rooms").document(roomId)
            .update("members", FieldValue.arrayUnion(email))
    }

    fun joinRoom(roomId: String) {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return
        firestore.collection("rooms")
            .document(roomId)
            .update("members", FieldValue.arrayUnion(email))
    }

    fun leaveRoom(roomId: String) {
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return
        firestore.collection("rooms")
            .document(roomId)
            .update("members", FieldValue.arrayRemove(email))
    }

    fun deleteRoom(roomId: String) {
        messageListeners.remove(roomId)?.remove()
        firestore.collection("rooms").document(roomId).delete()
    }

    /**
     * Open or create a 1:1 DM room between the current user and targetEmail.
     * - DOES NOT auto-create anything unless the user taps "Message".
     * - If a legacy 2-member room exists but wasn't marked as DM, it is normalized
     *   (isDirect=true, isPrivate=true) so it moves to Private and keeps message history.
     * - Returns the SAME room id for both the People list and the Private tab.
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
                    members.size == 2 && members.contains(targetEmail)
                }

                if (existing != null) {
                    val isDirect = (existing.get("isDirect") as? Boolean)
                        ?: (existing.get("direct") as? Boolean) ?: false
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
