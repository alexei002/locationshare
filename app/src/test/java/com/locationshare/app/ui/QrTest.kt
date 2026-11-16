package com.locationshare.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrTest {

    // ---------- link ----------

    @Test
    fun link_canonicalFormat() {
        assertEquals("locationshare://join?code=2MZ6K3", Qr.link("2MZ6K3"))
    }

    @Test
    fun link_keepsCodeAsIs() {
        // link не нормализует регистр — это делает parseJoinLink
        assertEquals("locationshare://join?code=abc", Qr.link("abc"))
    }

    // ---------- parseJoinLink ----------

    @Test
    fun parse_roundTrip() {
        assertEquals("2MZ6K3", Qr.parseJoinLink(Qr.link("2mz6k3")))
    }

    @Test
    fun parse_uppercasesAndTrims() {
        assertEquals("FILATOV", Qr.parseJoinLink("  locationshare://join?code=filatov  "))
    }

    @Test
    fun parse_takesAtMost10Chars() {
        assertEquals("ABCDEFGHIJ", Qr.parseJoinLink("locationshare://join?code=ABCDEFGHIJKLMN"))
    }

    @Test
    fun parse_extraParams() {
        assertEquals("XYZ", Qr.parseJoinLink("locationshare://join?a=1&code=XYZ&b=2"))
    }

    @Test
    fun parse_fragmentStripped() {
        assertEquals("ABC", Qr.parseJoinLink("locationshare://join?code=ABC#frag"))
    }

    @Test
    fun parse_nullInput() {
        assertNull(Qr.parseJoinLink(null))
    }

    @Test
    fun parse_blankInput() {
        assertNull(Qr.parseJoinLink(""))
        assertNull(Qr.parseJoinLink("   "))
    }

    @Test
    fun parse_garbageIsNull() {
        assertNull(Qr.parseJoinLink("hello world"))
        assertNull(Qr.parseJoinLink("https://example.com/join?code=ABC"))
        assertNull(Qr.parseJoinLink("mailto:test@test.ru"))
        assertNull(Qr.parseJoinLink("locationshares://join?code=ABC"))
    }

    @Test
    fun parse_missingOrEmptyCode() {
        assertNull(Qr.parseJoinLink("locationshare://join"))
        assertNull(Qr.parseJoinLink("locationshare://join?"))
        assertNull(Qr.parseJoinLink("locationshare://join?other=1"))
        assertNull(Qr.parseJoinLink("locationshare://join?code="))
    }
}
