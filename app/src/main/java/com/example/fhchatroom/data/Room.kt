package com.example.fhchatroom.data

data class Room(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val members: List<String> = emptyList()
)
