package com.example.fhchatroom.data

data class Room(
    val id: String = "",
    val name: String = "",
    // Added: track group creator
    val createdBy: String = "",
    // Added: list of joined users
    val members: List<String> = emptyList()
)
