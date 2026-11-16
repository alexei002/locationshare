package com.locationshare.app

import android.app.Application
import com.google.firebase.database.FirebaseDatabase
import com.locationshare.app.data.Prefs
import com.locationshare.app.data.Repo
import com.yandex.mapkit.MapKitFactory

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        MapKitFactory.setApiKey(BuildConfig.MAPKIT_KEY)
        MapKitFactory.initialize(this)
        try {
            Repo.db.setPersistenceEnabled(true)
        } catch (_: Exception) {
        }
    }
}
