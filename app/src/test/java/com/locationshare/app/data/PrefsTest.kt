package com.locationshare.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsTest {

    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    @Test
    fun newGroupCode_lengthIs6() {
        repeat(100) { assertEquals(6, Prefs.newGroupCode().length) }
    }

    @Test
    fun newGroupCode_onlyAllowedChars() {
        repeat(500) {
            Prefs.newGroupCode().forEach { ch ->
                assertTrue("unexpected char '$ch'", alphabet.indexOf(ch) >= 0)
            }
        }
    }

    @Test
    fun newGroupCode_noAmbiguousChars() {
        // 0/1/I/O исключены из алфавита — их легко перепутать при ручном вводе
        repeat(500) {
            val c = Prefs.newGroupCode()
            assertTrue("ambiguous char in '$c'", "01IO".none { c.contains(it) })
        }
    }

    @Test
    fun newGroupCode_producesDifferentCodes() {
        val codes = (1..100).map { Prefs.newGroupCode() }.toSet()
        assertTrue("all codes identical", codes.size > 1)
    }
}
