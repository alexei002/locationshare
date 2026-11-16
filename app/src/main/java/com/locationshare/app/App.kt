package com.locationshare.app

import android.app.Application
import com.locationshare.app.data.CrashReporter
import com.locationshare.app.data.Prefs
import com.locationshare.app.data.Repo
import com.yandex.mapkit.MapKitFactory

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        Prefs.init(this)
        CrashReporter.mark("prefs")
        try {
            MapKitFactory.setApiKey(BuildConfig.MAPKIT_KEY)
            CrashReporter.mark("mapkit:key")
            MapKitFactory.initialize(this)
            CrashReporter.mark("mapkit:init")
        } catch (t: Throwable) {
            // Приложение живёт и без карты: покажем ошибку в UI вместо вылета
            mapInitError = t.toString()
            CrashReporter.mark("mapkit:FAIL $t")
        }
        try {
            Repo.db.setPersistenceEnabled(true)
            CrashReporter.mark("firebase")
        } catch (_: Exception) {
        }
        Thread { CrashReporter.sendPending(this) }.start()
    }

    companion object {
        @Volatile
        var mapInitError: String? = null
    }
}
