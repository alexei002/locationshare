package com.locationshare.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdaterTest {

    // ---------- tagCode ----------

    @Test
    fun tag_fullVersion() {
        assertEquals(20261102, Updater.tagCode("2026.11.2"))
    }

    @Test
    fun tag_zeroPaddingNotRequired() {
        assertEquals(20261102, Updater.tagCode("2026.11.02"))
    }

    @Test
    fun tag_spacesAroundPartsAreTrimmed() {
        assertEquals(20261103, Updater.tagCode("2026. 11. 3"))
    }

    @Test
    fun tag_plainNumber() {
        assertEquals(4, Updater.tagCode("4"))
        assertEquals(0, Updater.tagCode("0"))
        assertEquals(-3, Updater.tagCode("-3"))
    }

    @Test
    fun tag_twoPartsIsNull() {
        assertNull(Updater.tagCode("2026.11"))
    }

    @Test
    fun tag_fourPartsIsNull() {
        assertNull(Updater.tagCode("2026.11.2.1"))
    }

    @Test
    fun tag_nonNumericIsNull() {
        assertNull(Updater.tagCode("abc"))
        assertNull(Updater.tagCode("v2026.11.2"))
        assertNull(Updater.tagCode(""))
    }
}
