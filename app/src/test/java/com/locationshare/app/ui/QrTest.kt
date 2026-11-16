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
    // ВНИМАНИЕ: parseJoinLink внутри использует android.net.Uri, которого нет в
    // JVM-тестах (mockable jar бросает «not mocked», catch превращает в null),
    // поэтому здесь проверяются только входы, отсекаемые до Uri.parse,
    // а также контракт «мусор → null».

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
    }
}
