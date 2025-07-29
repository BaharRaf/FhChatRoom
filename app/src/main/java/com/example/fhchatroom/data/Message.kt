
package com.example.fhchatroom.data

data class Message(
    val senderFirstName: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isSentByCurrentUser: Boolean = false,
    val deletedFor: List<String> = emptyList(),
    val id: String? = null,
    val type: MessageType = MessageType.TEXT,
    val mediaUrl: String? = null,
    val mediaDuration: Int? = null // Duration in seconds for voice messages
)

enum class MessageType {
    TEXT,
    IMAGE,
    VOICE
}