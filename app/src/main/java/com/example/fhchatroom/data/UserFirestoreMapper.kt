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

fun DocumentSnapshot.toUserOrNull(): User? {
    val email = (getString("email") ?: id).trim()
    if (email.isBlank() || !email.contains("@")) {
        return null
    }

    val semester = coerceLong(get("semester"))
    val semesterBucket = (getString("semesterBucket") ?: semesterBucketFor(semester)).ifBlank {
        semesterBucketFor(semester)
    }
    val recommendedRoomIds = (get("recommendedRoomIds") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    return User(
        firstName = (getString("firstName") ?: "").trim(),
        lastName = (getString("lastName") ?: "").trim(),
        email = email,
        studyPath = (getString("studyPath") ?: "").trim(),
        semester = semester,
        semesterBucket = semesterBucket,
        isOnline = getBoolean("isOnline") ?: false,
        profilePhotoUrl = getString("profilePhotoUrl") ?: "",
        createdAt = coerceLong(get("createdAt")),
        recommendedRoomIds = recommendedRoomIds,
        recommendationsUpdatedAt = coerceLong(get("recommendationsUpdatedAt")),
        recommendationSource = (getString("recommendationSource") ?: "NONE")
    )
}
