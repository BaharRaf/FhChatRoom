package com.example.fhchatroom.data


import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude

data class Message(
    @DocumentId
    val id: String = "",
    val senderFirstName: String = "",
    val senderId:String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val deletedFor: List<String> = emptyList(),
    @get:Exclude val isSentByCurrentUser: Boolean = false
)
