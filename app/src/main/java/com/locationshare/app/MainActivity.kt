package com.locationshare.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.locationshare.app.data.Prefs
import com.locationshare.app.service.LocationService
import com.locationshare.app.ui.AppRoot
import com.locationshare.app.ui.Qr
import com.locationshare.app.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot() } }
        handleDeepLink(intent)

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // Перезапуск сервиса, если система его убила, и отметка «карта видна»
                    // (видимая карта = чаще и точнее, скрытая = экономный режим GPS)
                    if (Prefs.sharing && !Prefs.eco) {
                        ContextCompat.startForegroundService(
                            this, LocationService.intent(this, LocationService.ACTION_MAP_VISIBLE)
                        )
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (LocationService.running) {
                        ContextCompat.startForegroundService(
                            this, LocationService.intent(this, LocationService.ACTION_MAP_HIDDEN)
                        )
                    }
                }
                else -> {}
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    /** locationshare://join?code=XXXXXX — из QR или ссылки */
    private fun handleDeepLink(intent: Intent?) {
        val code = Qr.parseJoinLink(intent?.dataString)
        if (!code.isNullOrBlank() && code.length >= 4 && code != Prefs.groupCode) {
            Prefs.groupCode = code
        }
    }
}
