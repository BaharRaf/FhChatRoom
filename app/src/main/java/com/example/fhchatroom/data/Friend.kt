package com.example.fhchatroom.data



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
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val sentAt: Long = System.currentTimeMillis(),
    val respondedAt: Long = 0L
)

enum class FriendRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED
}