package com.locationshare.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

object Prefs {
    private lateinit var sp: SharedPreferences

    val deviceId: String by lazy {
        var id = sp.getString("deviceId", null)
        if (id == null) {
            id = UUID.randomUUID().toString().substring(0, 8)
            sp.edit().putString("deviceId", id).apply()
        }
        id
    }

    val deviceNameFlow = MutableStateFlow("")
    val groupCodeFlow = MutableStateFlow<String?>(null)
    val sharingFlow = MutableStateFlow(false)
    val ecoFlow = MutableStateFlow(false)

    var deviceName: String
        get() = deviceNameFlow.value
        set(v) { deviceNameFlow.value = v; sp.edit().putString("name", v).apply() }

    var groupCode: String?
        get() = groupCodeFlow.value
        set(v) { groupCodeFlow.value = v; sp.edit().putString("group", v).apply() }

    var sharing: Boolean
        get() = sharingFlow.value
        set(v) { sharingFlow.value = v; sp.edit().putBoolean("sharing", v).apply() }

    var eco: Boolean
        get() = ecoFlow.value
        set(v) { ecoFlow.value = v; sp.edit().putBoolean("eco", v).apply() }

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        deviceNameFlow.value = sp.getString("name", null) ?: Build.MODEL
        groupCodeFlow.value = sp.getString("group", null)
        sharingFlow.value = sp.getBoolean("sharing", true)
        ecoFlow.value = sp.getBoolean("eco", false)
    }

    fun newGroupCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    var lastUpdateCheck: Long
        get() = sp.getLong("updCheck", 0L)
        set(v) { sp.edit().putLong("updCheck", v).apply() }
}
