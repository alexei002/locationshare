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

    // Кастомный цвет на карте "#RRGGBB" (null = авто-назначение палитрой)
    var customColor: String?
        get() = sp.getString("color", null)
        set(v) {
            if (v == null) sp.edit().remove("color").apply()
            else sp.edit().putString("color", v).apply()
        }

    // Онбординг «Пригласите близких» уже показан
    var onboardingShown: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }

    // Автозапрос разрешений при старте уже выполнялся (один раз на установку)
    var permAutoAsked: Boolean
        get() = sp.getBoolean("permAutoAsked", false)
        set(v) { sp.edit().putBoolean("permAutoAsked", v).apply() }

    // Прямой системный запрос оптимизации уже выполнялся: после отказа система
    // его больше не показывает — дальше ведём в список настроек
    var batteryRequestShown: Boolean
        get() = sp.getBoolean("batteryRequestShown", false)
        set(v) { sp.edit().putBoolean("batteryRequestShown", v).apply() }

    // Мастер разрешений уже показывался автоматически (один раз на установку)
    var masterShown: Boolean
        get() = sp.getBoolean("masterShown", false)
        set(v) { sp.edit().putBoolean("masterShown", v).apply() }

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
