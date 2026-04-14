package com.example.fhchatroom.data

data class User(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val studyPath: String = "",
    val semester: Long = 0L,
    val semesterBucket: String = semesterBucketFor(semester),
    var isOnline: Boolean = false,
    val profilePhotoUrl: String = "", // Added for profile photo
    val createdAt: Long = System.currentTimeMillis(),
    val recommendedRoomIds: List<String> = emptyList(),
    val recommendationsUpdatedAt: Long = 0L,
    val recommendationSource: String = "NONE"
)
