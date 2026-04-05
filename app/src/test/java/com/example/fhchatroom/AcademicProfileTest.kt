package com.example.fhchatroom

import com.example.fhchatroom.data.normalizeStudyPath
import com.example.fhchatroom.data.semesterBucketFor
import org.junit.Assert.assertEquals
import org.junit.Test

class AcademicProfileTest {

    @Test
    fun `normalizes study path whitespace`() {
        assertEquals(
            "Computer Science and Digital Communications",
            normalizeStudyPath("  Computer   Science   and   Digital Communications  ")
        )
    }

    @Test
    fun `maps semester to privacy bucket`() {
        assertEquals("Unknown", semesterBucketFor(0))
        assertEquals("1-2", semesterBucketFor(1))
        assertEquals("1-2", semesterBucketFor(2))
        assertEquals("3-4", semesterBucketFor(4))
        assertEquals("5-6", semesterBucketFor(5))
        assertEquals("7+", semesterBucketFor(7))
    }
}
