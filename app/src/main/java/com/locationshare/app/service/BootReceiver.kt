package com.locationshare.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.locationshare.app.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Prefs.init(context)
        if (Prefs.sharing && !Prefs.eco) {
            // На Android 15+ старт location-FGS из BOOT_COMPLETED может не пройти —
            // аларм-цепочка поднимет сервис через 10 мин, если что
            WatchdogReceiver.schedule(context)
            try {
                LocationService.start(context)
            } catch (_: Exception) {
                // На Android 15+ старт location-FGS из BOOT_COMPLETED ограничен —
                // сервис поднимется при первом открытии приложения.
            }
        }
    }
}
