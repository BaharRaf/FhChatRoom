package com.example.fhchatroom.data

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Message(
    val senderFirstName: String = "",
    val senderId: String = "",
    val text: String = "",
    @ServerTimestamp
    val timestamp: Date? = null,
    val isSentByCurrentUser: Boolean = false,
    val deletedFor: List<String> = emptyList(),
    val id: String? = null,
    val type: MessageType = MessageType.TEXT,
    val mediaUrl: String? = null,
    val mediaDuration: Int? = null, // Duration in seconds for voice messages
    val reactions: Map<String, String> = emptyMap(), // userId to emoji
    val replyToMessageId: String? = null, // ID of message being replied to
    val replyToMessageText: String? = null, // Text of original message
    val replyToSenderName: String? = null // Name of original sender
) {
    // Helper property to get timestamp as Long for backwards compatibility
    val timestampMillis: Long
        get() = timestamp?.time ?: System.currentTimeMillis()
}

enum class MessageType {
    TEXT,
    IMAGE,
    VOICE
}