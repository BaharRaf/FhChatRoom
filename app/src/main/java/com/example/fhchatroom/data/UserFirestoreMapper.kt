package com.example.fhchatroom.data

import com.google.firebase.firestore.DocumentSnapshot

private fun coerceLong(value: Any?, default: Long = 0L): Long {
    return when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is Float -> value.toLong()
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: default
        else -> default
    }
}

private fun coerceBoolean(value: Any?, default: Boolean = false): Boolean {
    return when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        else -> default
    }
}

fun DocumentSnapshot.toUserOrNull(): User? {
    val email = (getString("email") ?: id).trim()
    if (email.isBlank() || !email.contains("@")) return null

    return User(
        firstName = (getString("firstName") ?: "").trim(),
        lastName = (getString("lastName") ?: "").trim(),
        email = email,
        isOnline = coerceBoolean(get("isOnline")),
        profilePhotoUrl = (getString("profilePhotoUrl") ?: "").trim(),
        createdAt = coerceLong(get("createdAt"))
    )
}
