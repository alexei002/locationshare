package com.locationshare.app.data

import com.locationshare.app.data.Repo.DeviceState
import com.locationshare.app.data.Repo.Pt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepoDeviceStateTest {

    private fun state(ts: Long?): DeviceState = DeviceState(
        id = "dev1",
        name = "Dev",
        current = ts?.let { Pt(55.75, 37.61, it) },
        accuracy = 10f,
        battery = 80,
    )

    // ---------- DeviceState.online ----------

    @Test
    fun online_freshTimestamp() {
        assertTrue(state(System.currentTimeMillis()).online)
    }

    @Test
    fun online_justUnder15Min() {
        assertTrue(state(System.currentTimeMillis() - 14 * 60_000).online)
    }

    @Test
    fun offline_justOver15Min() {
        assertFalse(state(System.currentTimeMillis() - 16 * 60_000).online)
    }

    @Test
    fun offline_noCurrent() {
        assertFalse(state(null).online)
    }

    @Test
    fun offline_zeroTimestamp() {
        assertFalse(state(0L).online)
    }

    @Test
    fun offline_veryOldTimestamp() {
        assertFalse(state(System.currentTimeMillis() - 24 * 3_600_000L).online)
    }
}
