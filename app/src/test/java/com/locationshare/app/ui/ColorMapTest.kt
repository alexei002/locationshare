package com.locationshare.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorMapTest {
    private val p = PALETTE_COLORS

    // parseColorHex / colorHex
    @Test
    fun parse_valid() = assertEquals(0xFF43A047.toInt(), parseColorHex("#43A047"))

    @Test
    fun parse_lowercase() = assertEquals(0xFF43A047.toInt(), parseColorHex("#43a047"))

    @Test
    fun parse_rejects() {
        assertNull(parseColorHex(null))
        assertNull(parseColorHex("43A047"))
        assertNull(parseColorHex("#ZZZZZZ"))
        assertNull(parseColorHex("#43A04"))
        assertNull(parseColorHex(""))
    }

    @Test
    fun hex_roundtrip() = assertEquals("#43A047", colorHex(parseColorHex("#43a047")!!))

    // colorMapFor
    @Test
    fun map_noCustom_matchesOldBehavior() =
        assertEquals(mapOf("a" to p[0], "b" to p[1]), colorMapFor(listOf("b", "a")))

    @Test
    fun map_customWins_othersSkipTaken() {
        // m фиксирует p[3]; a (без кастома) получает p[0], палитру раздаём с пропуском p[3]
        val r = colorMapFor(listOf("a", "m"), custom = mapOf("m" to p[3]))
        assertEquals(p[3], r["m"])
        assertEquals(p[0], r["a"])
        // p[0] занят кастомом m → z получает p[1]
        val r2 = colorMapFor(listOf("m", "z"), custom = mapOf("m" to p[0]))
        assertEquals(p[1], r2["z"])
    }

    @Test
    fun map_allTaken_cycles() {
        // 16 кастомных заняли всю палитру → авто-устройству достаётся p[0] (цикл, повтор допустим)
        val custom = p.indices.associate { "c$it" to p[it] }
        val r = colorMapFor(listOf("zz") + custom.keys, custom)
        assertEquals(p[0], r["zz"])
    }

    @Test
    fun map_twoDevices_sameCustomColor_allowed() {
        val r = colorMapFor(listOf("x", "y"), custom = mapOf("x" to p[5], "y" to p[5]))
        assertEquals(p[5], r["x"])
        assertEquals(p[5], r["y"])
    }

    @Test
    fun customColorsOfDevices_parsesSkipsGarbage() {
        fun d(id: String, c: String?) = com.locationshare.app.data.Repo.DeviceState(id, id, null, 0f, null, c)
        val devs = listOf(d("a", "#43A047"), d("b", null), d("c", "мусор"))
        assertEquals(mapOf("a" to 0xFF43A047.toInt()), customColors(devs))
    }
}
