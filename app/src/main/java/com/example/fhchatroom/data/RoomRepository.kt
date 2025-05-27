package com.example.fhchatroom.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RoomRepository(private val firestore: FirebaseFirestore) {

    suspend fun createRoom(name: String, description: String): Result<Unit> = try {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        val initialMembers = if (currentUserEmail != null) listOf(currentUserEmail) else emptyList()
        val room = Room(name = name, description = description, members = initialMembers)
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

    suspend fun joinRoom(roomId: String): Result<Unit> = try {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
            ?: throw Exception("User not logged in")
        firestore.collection("rooms").document(roomId)
            .update("members", FieldValue.arrayUnion(currentUserEmail)).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    //  Remove current user from room members (leave room)
    suspend fun leaveRoom(roomId: String): Result<Unit> = try {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
            ?: throw Exception("User not logged in")
        firestore.collection("rooms").document(roomId)
            .update("members", FieldValue.arrayRemove(currentUserEmail)).await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }
}
