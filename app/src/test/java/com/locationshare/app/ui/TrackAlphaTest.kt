package com.locationshare.app.ui

import com.locationshare.app.data.Repo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackAlphaTest {

    @Test
    fun freshPoint_fullyOpaque() {
        assertEquals(255, trackAlpha(0))
    }

    @Test
    fun oldestPoint_notFainterThanHalf() {
        // Пол прозрачности 0.5: даже у края окна истории трек читается
        val floor = (255 * 0.5f).toInt()
        assertTrue(trackAlpha(Repo.HIST_WINDOW_MS) >= floor)
        assertTrue(trackAlpha(Repo.HIST_WINDOW_MS * 3) >= floor)
    }

    @Test
    fun olderPoint_neverBrighter() {
        var prev = 255
        var age = 0L
        while (age <= Repo.HIST_WINDOW_MS) {
            val a = trackAlpha(age)
            assertTrue("alpha grows with age at $age ms", a <= prev)
            prev = a
            age += Repo.HIST_WINDOW_MS / 28
        }
    }

    @Test
    fun alpha_quantizedTo14Steps() {
        // Квантование: точки внутри одной ступени окна дают одинаковую прозрачность
        val step = Repo.HIST_WINDOW_MS / 14
        assertEquals(trackAlpha(step + step / 2), trackAlpha(step * 2 - step / 2))
    }
}
