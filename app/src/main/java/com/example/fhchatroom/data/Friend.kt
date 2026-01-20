package com.example.fhchatroom.data

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class Friend(
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val profilePhotoUrl: String = "",
    val isOnline: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

data class FriendRequest(
    val id: String = "",
    val fromEmail: String = "",
    val toEmail: String = "",
    val fromName: String = "",
    val toName: String = "",
    val fromProfilePhoto: String = "",
    @PropertyName("status")
    var statusString: String = FriendRequestStatus.PENDING.name,
    val sentAt: Long = System.currentTimeMillis(),
    val respondedAt: Long = 0L
) {
    @get:Exclude
    val status: FriendRequestStatus
        get() = try {
            FriendRequestStatus.valueOf(statusString)
        } catch (e: Exception) {
            FriendRequestStatus.PENDING
        }
}

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED
}