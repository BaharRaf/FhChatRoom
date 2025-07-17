package com.example.fhchatroom.data

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class MessageRepository(private val firestore: FirebaseFirestore) {

    suspend fun sendMessage(roomId: String, message: Message): Result<Unit> = try {
        firestore.collection("rooms").document(roomId)
            .collection("messages").add(message).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> = try {
        firestore.collection("rooms").document(roomId)
            .collection("messages").document(messageId).delete().await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun markMessageDeletedForUser(roomId: String, messageId: String, userEmail: String): Result<Unit> = try {
        firestore.collection("rooms").document(roomId)
            .collection("messages").document(messageId)
            .update("deletedFor", FieldValue.arrayUnion(userEmail)).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    fun getChatMessages(roomId: String): Flow<List<Message>> = callbackFlow {
        val subscription = firestore.collection("rooms").document(roomId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { querySnapshot, _ ->
                querySnapshot?.let {
                    trySend(it.documents.mapNotNull { doc ->
                        doc.toObject(Message::class.java)
                    }).isSuccess
                }
            }

        awaitClose { subscription.remove() }
    }
}
