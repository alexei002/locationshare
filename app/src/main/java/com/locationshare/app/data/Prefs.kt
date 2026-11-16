package com.locationshare.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

object Prefs {
    private lateinit var sp: SharedPreferences
    private lateinit var appContext: android.content.Context

    // Стабильный id: ANDROID_ID не меняется при переустановке приложения
    // (защита от дублей в группе после переустановок)
    val deviceId: String by lazy {
        var id = sp.getString("deviceId", null)
        if (id == null) {
            id = try {
                android.provider.Settings.Secure.getString(
                    appContext.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID,
                )?.take(8)?.ifBlank { null } ?: UUID.randomUUID().toString().substring(0, 8)
            } catch (_: Exception) {
                UUID.randomUUID().toString().substring(0, 8)
            }
            sp.edit().putString("deviceId", id).apply()
        }
        id
    }

    val deviceNameFlow = MutableStateFlow("")
    val groupCodeFlow = MutableStateFlow<String?>(null)
    val ecoFlow = MutableStateFlow(false)

    var deviceName: String
        get() = deviceNameFlow.value
        set(v) { deviceNameFlow.value = v; sp.edit().putString("name", v).apply() }

    var groupCode: String?
        get() = groupCodeFlow.value
        set(v) { groupCodeFlow.value = v; sp.edit().putString("group", v).apply() }

    var eco: Boolean
        get() = ecoFlow.value
        set(v) { ecoFlow.value = v; sp.edit().putBoolean("eco", v).apply() }

    // Онбординг «Пригласите близких» уже показан
    var onboardingShown: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }

    fun init(context: Context) {
        if (::sp.isInitialized) return
        appContext = context.applicationContext
        sp = appContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        deviceNameFlow.value = sp.getString("name", null) ?: Build.MODEL
        groupCodeFlow.value = sp.getString("group", null)
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
