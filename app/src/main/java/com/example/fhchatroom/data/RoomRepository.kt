package com.example.fhchatroom.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.google.firebase.firestore.FieldValue

class RoomRepository(private val firestore: FirebaseFirestore) {

    suspend fun createRoom(name: String, creatorEmail: String): Result<Unit> = try {
        val room = Room(name = name, createdBy = creatorEmail, members = listOf(creatorEmail))
        firestore.collection("rooms").add(room).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    suspend fun getRooms(): Result<List<Room>> = try {
        val querySnapshot = firestore.collection("rooms").get().await()
        val rooms = querySnapshot.documents.map { document ->
            document.toObject(Room::class.java)!!.copy(id = document.id)
        }
        Result.Success(rooms)
    } catch (e: Exception) {
        Result.Error(e)
    }
    // add current user to room’s members array
    suspend fun joinRoom(roomId: String, userEmail: String): Result<Unit> = try {
        firestore.collection("rooms").document(roomId)
            .update("members", FieldValue.arrayUnion(userEmail)).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }
    //remove user from room’s members array
    suspend fun leaveRoom(roomId: String, userEmail: String): Result<Unit> = try {
        firestore.collection("rooms").document(roomId)
            .update("members", FieldValue.arrayRemove(userEmail)).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }
    // delete room document entirely
    suspend fun deleteRoom(roomId: String): Result<Unit> = try {
        firestore.collection("rooms").document(roomId).delete().await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }
}
