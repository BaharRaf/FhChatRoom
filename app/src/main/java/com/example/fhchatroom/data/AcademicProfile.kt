package com.example.fhchatroom.data

private val whitespaceRegex = "\\s+".toRegex()

fun normalizeStudyPath(studyPath: String): String {
    return studyPath.trim().replace(whitespaceRegex, " ")
}

fun semesterBucketFor(semester: Long): String {
    return when {
        semester <= 0L -> "Unknown"
        semester <= 2L -> "1-2"
        semester <= 4L -> "3-4"
        semester <= 6L -> "5-6"
        else -> "7+"
    }
}
