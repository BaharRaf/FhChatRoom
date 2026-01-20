package com.example.fhchatroom.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FriendsRepository(private val firestore: FirebaseFirestore) {

    private val TAG = "FriendsRepository"

    // Send friend request
    suspend fun sendFriendRequest(
        fromEmail: String,
        toEmail: String,
        fromName: String,
        toName: String,
        fromProfilePhoto: String
    ): Result<FriendRequest> {
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
                Result.Error(Exception("Already friends"))
            } else {
                // Check if request already exists (in either direction)
                val existingRequest = firestore.collection("friendRequests")
                    .whereEqualTo("fromEmail", fromEmail)
                    .whereEqualTo("toEmail", toEmail)
                    .get()
                    .await()

                // Filter for PENDING status client-side to avoid index requirement
                val pendingRequests = existingRequest.documents.filter { doc ->
                    doc.getString("statusString") == FriendRequestStatus.PENDING.name
                }

                if (pendingRequests.isNotEmpty()) {
                    Result.Error(Exception("Friend request already sent"))
                } else {
                    // Check if there's a pending request from the other user
                    val reverseRequest = firestore.collection("friendRequests")
                        .whereEqualTo("fromEmail", toEmail)
                        .whereEqualTo("toEmail", fromEmail)
                        .get()
                        .await()

                    val reversePendingRequests = reverseRequest.documents.filter { doc ->
                        doc.getString("statusString") == FriendRequestStatus.PENDING.name
                    }

                    if (reversePendingRequests.isNotEmpty()) {
                        Result.Error(Exception("This user has already sent you a friend request"))
                    } else {
                        val request = FriendRequest(
                            fromEmail = fromEmail,
                            toEmail = toEmail,
                            fromName = fromName,
                            toName = toName,
                            fromProfilePhoto = fromProfilePhoto,
                            statusString = FriendRequestStatus.PENDING.name,
                            sentAt = System.currentTimeMillis()
                        )

                        val requestRef = firestore.collection("friendRequests").add(request).await()
                        Log.d(TAG, "Friend request sent successfully: ${requestRef.id}")
                        Result.Success(request.copy(id = requestRef.id))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending friend request", e)
            Result.Error(e)
        }
    }

    // Get received friend requests - Fixed query to avoid index issues
    fun getReceivedFriendRequests(userEmail: String): Flow<List<FriendRequest>> = callbackFlow {
        Log.d(TAG, "Setting up received requests listener for: $userEmail")

        // Use simpler query without orderBy to avoid composite index requirement
        // Then filter and sort client-side
        val subscription = firestore.collection("friendRequests")
            .whereEqualTo("toEmail", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to received requests", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val requests = it.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing friend request document", e)
                            null
                        }
                    }
                        // Filter for PENDING status and sort by sentAt client-side
                        .filter { request -> request.statusString == FriendRequestStatus.PENDING.name }
                        .sortedByDescending { request -> request.sentAt }

                    Log.d(TAG, "Received ${requests.size} pending friend requests")
                    trySend(requests)
                }
            }
        awaitClose {
            Log.d(TAG, "Closing received requests listener")
            subscription.remove()
        }
    }

    // Get sent friend requests - Fixed query to avoid index issues
    fun getSentFriendRequests(userEmail: String): Flow<List<FriendRequest>> = callbackFlow {
        Log.d(TAG, "Setting up sent requests listener for: $userEmail")

        // Use simpler query without orderBy to avoid composite index requirement
        val subscription = firestore.collection("friendRequests")
            .whereEqualTo("fromEmail", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to sent requests", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                snapshot?.let {
                    val requests = it.documents.mapNotNull { doc ->
                        try {
                            doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing friend request document", e)
                            null
                        }
                    }
                        // Filter for PENDING status and sort by sentAt client-side
                        .filter { request -> request.statusString == FriendRequestStatus.PENDING.name }
                        .sortedByDescending { request -> request.sentAt }

                    Log.d(TAG, "Sent ${requests.size} pending friend requests")
                    trySend(requests)
                }
            }
        awaitClose {
            Log.d(TAG, "Closing sent requests listener")
            subscription.remove()
        }
    }

    // Accept friend request - DELETE the request and create friendship
    suspend fun acceptFriendRequest(requestId: String, fromEmail: String, toEmail: String): Result<Unit> {
        return try {
            Log.d(TAG, "Accepting friend request: $requestId from $fromEmail to $toEmail")

            // First verify the request still exists and is pending
            val requestDoc = firestore.collection("friendRequests").document(requestId).get().await()
            if (!requestDoc.exists()) {
                Log.e(TAG, "Friend request not found: $requestId")
                Result.Error(Exception("Friend request not found"))
            } else {
                val currentStatus = requestDoc.getString("statusString")
                if (currentStatus != FriendRequestStatus.PENDING.name) {
                    Log.e(TAG, "Friend request is not pending: $currentStatus")
                    Result.Error(Exception("Friend request is no longer pending"))
                } else {
                    firestore.runBatch { batch ->
                        // DELETE the request
                        val requestRef = firestore.collection("friendRequests").document(requestId)
                        batch.delete(requestRef)

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

                    Log.d(TAG, "Friend request accepted successfully")
                    Result.Success(Unit)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting friend request", e)
            Result.Error(e)
        }
    }

    // Decline friend request - DELETE the request
    suspend fun declineFriendRequest(requestId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Declining friend request: $requestId")

            // Verify the request exists
            val requestDoc = firestore.collection("friendRequests").document(requestId).get().await()
            if (!requestDoc.exists()) {
                Log.e(TAG, "Friend request not found: $requestId")
                Result.Error(Exception("Friend request not found"))
            } else {
                // DELETE the request
                firestore.collection("friendRequests").document(requestId).delete().await()
                Log.d(TAG, "Friend request declined successfully")
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error declining friend request", e)
            Result.Error(e)
        }
    }

    // Cancel sent friend request - DELETE the request
    suspend fun cancelFriendRequest(requestId: String): Result<Unit> {
        return try {
            Log.d(TAG, "Canceling friend request: $requestId")

            // Verify the request exists
            val requestDoc = firestore.collection("friendRequests").document(requestId).get().await()
            if (!requestDoc.exists()) {
                Log.e(TAG, "Friend request not found: $requestId")
                Result.Error(Exception("Friend request not found"))
            } else {
                // DELETE the request
                firestore.collection("friendRequests").document(requestId).delete().await()
                Log.d(TAG, "Friend request cancelled successfully")
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error canceling friend request", e)
            Result.Error(e)
        }
    }

    // Get friends list
    fun getFriends(userEmail: String): Flow<List<Friend>> = callbackFlow {
        Log.d(TAG, "Setting up friends listener for: $userEmail")

        val subscription = firestore.collection("friendships")
            .whereEqualTo("user1", userEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to friendships", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                snapshot?.let { friendshipSnapshot ->
                    val friendEmails = friendshipSnapshot.documents.mapNotNull { doc ->
                        doc.getString("user2")
                    }

                    Log.d(TAG, "Found ${friendEmails.size} friends")

                    if (friendEmails.isNotEmpty()) {
                        // Get user details for all friends in batches of 10 (Firestore limit)
                        val allFriends = mutableListOf<Friend>()
                        val chunks = friendEmails.chunked(10)

                        var completedChunks = 0
                        chunks.forEach { chunk ->
                            firestore.collection("users")
                                .whereIn("email", chunk)
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
                                    allFriends.addAll(friends)
                                    completedChunks++

                                    if (completedChunks == chunks.size) {
                                        trySend(allFriends.toList())
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error fetching friend details", e)
                                    completedChunks++
                                    if (completedChunks == chunks.size) {
                                        trySend(allFriends.toList())
                                    }
                                }
                        }
                    } else {
                        trySend(emptyList())
                    }
                }
            }
        awaitClose {
            Log.d(TAG, "Closing friends listener")
            subscription.remove()
        }
    }

    // Remove friend
    suspend fun removeFriend(userEmail: String, friendEmail: String): Result<Unit> {
        return try {
            Log.d(TAG, "Removing friend: $friendEmail")

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

            Log.d(TAG, "Friend removed successfully")
            Result.Success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error removing friend", e)
            Result.Error(e)
        }
    }

    // Search users (potential friends)
    suspend fun searchUsers(query: String, currentUserEmail: String): Result<List<User>> {
        return try {
            Log.d(TAG, "Searching users with query: $query")

            val users = firestore.collection("users")
                .orderBy("firstName")
                .startAt(query)
                .endAt(query + "\uf8ff")
                .get()
                .await()

            val userList = users.documents.mapNotNull { doc ->
                doc.toObject(User::class.java)
            }.filter { it.email != currentUserEmail } // Exclude current user

            Log.d(TAG, "Found ${userList.size} users matching query")
            Result.Success(userList)
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users", e)
            Result.Error(e)
        }
    }

    // Check friendship status
    suspend fun getFriendshipStatus(userEmail: String, otherUserEmail: String): Result<FriendshipStatus> {
        return try {
            Log.d(TAG, "Checking friendship status between $userEmail and $otherUserEmail")

            // Check if already friends
            val friendship = firestore.collection("friendships")
                .whereEqualTo("user1", userEmail)
                .whereEqualTo("user2", otherUserEmail)
                .get()
                .await()

            if (!friendship.isEmpty) {
                Log.d(TAG, "Status: FRIENDS")
                Result.Success(FriendshipStatus.FRIENDS)
            } else {
                // Check pending requests sent by user
                val sentRequest = firestore.collection("friendRequests")
                    .whereEqualTo("fromEmail", userEmail)
                    .whereEqualTo("toEmail", otherUserEmail)
                    .get()
                    .await()

                val pendingSent = sentRequest.documents.filter { doc ->
                    doc.getString("statusString") == FriendRequestStatus.PENDING.name
                }

                if (pendingSent.isNotEmpty()) {
                    Log.d(TAG, "Status: REQUEST_SENT")
                    Result.Success(FriendshipStatus.REQUEST_SENT)
                } else {
                    // Check pending requests received from other user
                    val receivedRequest = firestore.collection("friendRequests")
                        .whereEqualTo("fromEmail", otherUserEmail)
                        .whereEqualTo("toEmail", userEmail)
                        .get()
                        .await()

                    val pendingReceived = receivedRequest.documents.filter { doc ->
                        doc.getString("statusString") == FriendRequestStatus.PENDING.name
                    }

                    if (pendingReceived.isNotEmpty()) {
                        Log.d(TAG, "Status: REQUEST_RECEIVED")
                        Result.Success(FriendshipStatus.REQUEST_RECEIVED)
                    } else {
                        Log.d(TAG, "Status: NOT_FRIENDS")
                        Result.Success(FriendshipStatus.NOT_FRIENDS)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting friendship status", e)
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