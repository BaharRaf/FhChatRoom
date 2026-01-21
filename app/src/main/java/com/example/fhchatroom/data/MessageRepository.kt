package com.example.fhchatroom.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Date

class MessageRepository(private val firestore: FirebaseFirestore) {

    fun getChatMessages(roomId: String, currentUserEmail: String): Flow<List<Message>> = callbackFlow {
        val subscription = firestore.collection("rooms")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                snapshot?.let { qs ->
                    val messages = qs.documents.mapNotNull { doc ->
                        try {
                            // Manually extract fields to handle both old (Long) and new (Timestamp) formats
                            val deletedFor = (doc.get("deletedFor") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

                            // Skip messages deleted for current user
                            if (deletedFor.contains(currentUserEmail)) {
                                return@mapNotNull null
                            }

                            val senderFirstName = doc.getString("senderFirstName") ?: ""
                            val senderId = doc.getString("senderId") ?: ""
                            val text = doc.getString("text") ?: ""

                            // Handle timestamp - can be Long (old data), Timestamp (new data), or null
                            val timestamp: Date? = when (val ts = doc.get("timestamp")) {
                                is Long -> Date(ts)
                                is Timestamp -> ts.toDate()
                                is Number -> Date(ts.toLong())
                                else -> null
                            }

                            val typeString = doc.getString("type") ?: "TEXT"
                            val type = try {
                                MessageType.valueOf(typeString)
                            } catch (e: Exception) {
                                MessageType.TEXT
                            }

                            val mediaUrl = doc.getString("mediaUrl")
                            val mediaDuration = doc.getLong("mediaDuration")?.toInt()

                            @Suppress("UNCHECKED_CAST")
                            val reactions = (doc.get("reactions") as? Map<String, String>) ?: emptyMap()

                            val replyToMessageId = doc.getString("replyToMessageId")
                            val replyToMessageText = doc.getString("replyToMessageText")
                            val replyToSenderName = doc.getString("replyToSenderName")

                            Message(
                                senderFirstName = senderFirstName,
                                senderId = senderId,
                                text = text,
                                timestamp = timestamp,
                                isSentByCurrentUser = senderId == currentUserEmail,
                                deletedFor = deletedFor,
                                id = doc.id,
                                type = type,
                                mediaUrl = mediaUrl,
                                mediaDuration = mediaDuration,
                                reactions = reactions,
                                replyToMessageId = replyToMessageId,
                                replyToMessageText = replyToMessageText,
                                replyToSenderName = replyToSenderName
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    trySend(messages)
                }
            }

        awaitClose { subscription.remove() }
    }

    suspend fun deleteMessageForEveryone(roomId: String, messageId: String) {
        firestore.collection("rooms")
            .document(roomId)
            .collection("messages")
            .document(messageId)
            .delete()
    }

    suspend fun deleteMessageForMe(roomId: String, messageId: String, userEmail: String) {
        firestore.collection("rooms")
            .document(roomId)
            .collection("messages")
            .document(messageId)
            .update("deletedFor", com.google.firebase.firestore.FieldValue.arrayUnion(userEmail))
    }
}