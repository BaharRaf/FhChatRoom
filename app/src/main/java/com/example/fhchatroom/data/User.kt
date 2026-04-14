package com.example.fhchatroom.data

data class User(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    var isOnline: Boolean = false,
    val profilePhotoUrl: String = "", // Added for profile photo
    val createdAt: Long = System.currentTimeMillis()
)