package com.example.fhchatroom.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FriendsRepository(private val firestore: FirebaseFirestore) {

    // Send friend request
    suspend fun sendFriendRequest(fromEmail: String, toEmail: String, fromName: String, toName: String, fromProfilePhoto: String): Result<Unit> {
        return try {
            // Check if users are already friends
            val existingFriendship = firestore.collection("friendships")
                .whereEqualTo("user1", fromEmail)
                .whereEqualTo("user2", toEmail)
                .get()
                .await()

            val existingFriendship2 = firestore.collection("friendships")
                .whereEqualTo("user1", toEmail)
                .whereEqualTo("user2", fromEmail)
                .get()
                .await()

            if (!existingFriendship.isEmpty || !existingFriendship2.isEmpty) {
                return Result.Error(Exception("Already friends"))
            }

            // Check if request already exists
            val existingRequest = firestore.collection("friendRequests")
                .whereEqualTo("fromEmail", fromEmail)
                .whereEqualTo("toEmail", toEmail)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .get()
                .await()

            if (!existingRequest.isEmpty) {
                return Result.Error(Exception("Friend request already sent"))
            }

            val request = FriendRequest(
                fromEmail = fromEmail,
                toEmail = toEmail,
                fromName = fromName,
                toName = toName,
                fromProfilePhoto = fromProfilePhoto,
                statusString = FriendRequestStatus.PENDING.name,
                sentAt = System.currentTimeMillis()
            )

            firestore.collection("friendRequests").add(request).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // Get received friend requests
    fun getReceivedFriendRequests(userEmail: String): Flow<List<FriendRequest>> = callbackFlow {
        val subscription = firestore.collection("friendRequests")
            .whereEqualTo("toEmail", userEmail)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val requests = it.documents.mapNotNull { doc ->
                        doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
                    }
                    trySend(requests)
                }
            }
        awaitClose { subscription.remove() }
    }

    // Get sent friend requests
    fun getSentFriendRequests(userEmail: String): Flow<List<FriendRequest>> = callbackFlow {
        val subscription = firestore.collection("friendRequests")
            .whereEqualTo("fromEmail", userEmail)
            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
            .orderBy("sentAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val requests = it.documents.mapNotNull { doc ->
                        doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
                    }
                    trySend(requests)
                }
            }
        awaitClose { subscription.remove() }
    }

    // Accept friend request
    suspend fun acceptFriendRequest(requestId: String, fromEmail: String, toEmail: String): Result<Unit> = try {
        firestore.runBatch { batch ->
            // Update request status
            val requestRef = firestore.collection("friendRequests").document(requestId)
            batch.update(requestRef, mapOf(
                "status" to FriendRequestStatus.ACCEPTED.name,
                "respondedAt" to System.currentTimeMillis()
            ))

            // Create friendship (both directions for easier querying)
            val friendship1 = hashMapOf(
                "user1" to fromEmail,
                "user2" to toEmail,
                "createdAt" to System.currentTimeMillis()
            )
            val friendship2 = hashMapOf(
                "user1" to toEmail,
                "user2" to fromEmail,
                "createdAt" to System.currentTimeMillis()
            )

            batch.set(firestore.collection("friendships").document(), friendship1)
            batch.set(firestore.collection("friendships").document(), friendship2)
        }.await()

        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    // Decline friend request
    suspend fun declineFriendRequest(requestId: String): Result<Unit> = try {
        firestore.collection("friendRequests").document(requestId)
            .update(mapOf(
                "status" to FriendRequestStatus.DECLINED.name,
                "respondedAt" to System.currentTimeMillis()
            ))
            .await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    // Cancel sent friend request
    suspend fun cancelFriendRequest(requestId: String): Result<Unit> = try {
        firestore.collection("friendRequests").document(requestId)
            .update(mapOf(
                "status" to FriendRequestStatus.CANCELLED.name,
                "respondedAt" to System.currentTimeMillis()
            ))
            .await()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    // Get friends list
    fun getFriends(userEmail: String): Flow<List<Friend>> = callbackFlow {
        val subscription = firestore.collection("friendships")
            .whereEqualTo("user1", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                snapshot?.let { friendshipSnapshot ->
                    val friendEmails = friendshipSnapshot.documents.mapNotNull { doc ->
                        doc.getString("user2")
                    }

                    if (friendEmails.isNotEmpty()) {
                        // Get user details for all friends
                        firestore.collection("users")
                            .whereIn("email", friendEmails)
                            .get()
                            .addOnSuccessListener { userSnapshot ->
                                val friends = userSnapshot.documents.mapNotNull { userDoc ->
                                    val user = userDoc.toObject(User::class.java)
                                    user?.let {
                                        Friend(
                                            email = it.email,
                                            firstName = it.firstName,
                                            lastName = it.lastName,
                                            profilePhotoUrl = it.profilePhotoUrl,
                                            isOnline = it.isOnline
                                        )
                                    }
                                }
                                trySend(friends)
                            }
                    } else {
                        trySend(emptyList())
                    }
                }
            }
        awaitClose { subscription.remove() }
    }

    // Remove friend
    suspend fun removeFriend(userEmail: String, friendEmail: String): Result<Unit> = try {
        // Remove both directions of friendship
        val friendship1 = firestore.collection("friendships")
            .whereEqualTo("user1", userEmail)
            .whereEqualTo("user2", friendEmail)
            .get()
            .await()

        val friendship2 = firestore.collection("friendships")
            .whereEqualTo("user1", friendEmail)
            .whereEqualTo("user2", userEmail)
            .get()
            .await()

        firestore.runBatch { batch ->
            friendship1.documents.forEach { batch.delete(it.reference) }
            friendship2.documents.forEach { batch.delete(it.reference) }
        }.await()

        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e)
    }

    // Search users (potential friends)
    suspend fun searchUsers(query: String, currentUserEmail: String): Result<List<User>> = try {
        val users = firestore.collection("users")
            .orderBy("firstName")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .get()
            .await()

        val userList = users.documents.mapNotNull { doc ->
            doc.toObject(User::class.java)
        }.filter { it.email != currentUserEmail } // Exclude current user

        Result.Success(userList)
    } catch (e: Exception) {
        Result.Error(e)
    }

    // Check friendship status
    suspend fun getFriendshipStatus(userEmail: String, otherUserEmail: String): Result<FriendshipStatus> {
        return try {
            // Check if already friends
            val friendship = firestore.collection("friendships")
                .whereEqualTo("user1", userEmail)
                .whereEqualTo("user2", otherUserEmail)
                .get()
                .await()

            if (!friendship.isEmpty) {
                return Result.Success(FriendshipStatus.FRIENDS)
            }

            // Check pending requests
            val sentRequest = firestore.collection("friendRequests")
                .whereEqualTo("fromEmail", userEmail)
                .whereEqualTo("toEmail", otherUserEmail)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .get()
                .await()

            if (!sentRequest.isEmpty) {
                return Result.Success(FriendshipStatus.REQUEST_SENT)
            }

            val receivedRequest = firestore.collection("friendRequests")
                .whereEqualTo("fromEmail", otherUserEmail)
                .whereEqualTo("toEmail", userEmail)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .get()
                .await()

            if (!receivedRequest.isEmpty) {
                return Result.Success(FriendshipStatus.REQUEST_RECEIVED)
            }

            Result.Success(FriendshipStatus.NOT_FRIENDS)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}

enum class FriendshipStatus {
    FRIENDS,
    REQUEST_SENT,
    REQUEST_RECEIVED,
    NOT_FRIENDS
}