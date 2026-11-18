package com.locationshare.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Чистка истории: окно отсчитывается от серверного ts свежей точки, не от локальных часов */
class RepoHistCleanupTest {

    private val newest = 1_794_000_000_000L // серверный ts свежайшей точки

    @Test
    fun window_isFourHours() {
        assertEquals(4 * 3_600_000L, Repo.HIST_WINDOW_MS)
    }

    @Test
    fun pointExactlyAtWindowBoundary_stale() {
        // «Ровно 4 часа назад» — удаляется (граница включительно)
        assertTrue(Repo.isStaleHistPoint(newest - Repo.HIST_WINDOW_MS, newest))
    }

    @Test
    fun pointJustInsideWindow_kept() {
        assertFalse(Repo.isStaleHistPoint(newest - Repo.HIST_WINDOW_MS + 1, newest))
        assertFalse(Repo.isStaleHistPoint(newest, newest))
        assertFalse(Repo.isStaleHistPoint(newest - Repo.HIST_WINDOW_MS / 2, newest))
    }

    @Test
    fun clockSkew_irrelevant_cutoffFollowsServerTs() {
        // Часы телефона убежали на 5 часов: срез всё равно от серверного «сейчас» —
        // свежие точки не подпадают, старше окна — удаляются. Локального времени
        // в isStaleHistPoint нет вовсе, так что сдвиг часов на результат не влияет.
        val newestSkewedDevice = newest - 5 * 3_600_000L // серверный ts от «правильного» сервера
        assertFalse(Repo.isStaleHistPoint(newestSkewedDevice, newestSkewedDevice))
        assertFalse(
            Repo.isStaleHistPoint(newestSkewedDevice - Repo.HIST_WINDOW_MS + 1, newestSkewedDevice),
        )
        assertTrue(Repo.isStaleHistPoint(newestSkewedDevice - Repo.HIST_WINDOW_MS, newestSkewedDevice))
    }
}
