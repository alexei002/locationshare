package com.locationshare.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Redmi/POCO тоже Xiaomi: по производителю решаем, показывать ли MIUI-шаги настроек */
class DeviceIntentsTest {

    @Test
    fun xiaomi_family_is_detected() {
        assertTrue(isXiaomi("Xiaomi"))
        assertTrue(isXiaomi("xiaomi"))
        assertTrue(isXiaomi("XIAOMI"))
    }

    @Test
    fun other_manufacturers_are_not_xiaomi() {
        assertFalse(isXiaomi("Google"))
        assertFalse(isXiaomi("Samsung"))
        assertFalse(isXiaomi("HUAWEI"))
        assertFalse(isXiaomi("XiaomiCorp")) // подстрока не считается
        assertFalse(isXiaomi(null))
        assertFalse(isXiaomi(""))
    }
}
