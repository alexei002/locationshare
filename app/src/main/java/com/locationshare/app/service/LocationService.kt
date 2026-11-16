package com.locationshare.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.locationshare.app.MainActivity
import com.locationshare.app.data.Prefs
import com.locationshare.app.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Энергоэффективный сервис гео-шеринга.
 *
 * Два режима запроса локации:
 *  - карта на экране: HIGH_ACCURACY, ~5 с, шаг 3 м (пользователь смотрит — точность важна);
 *  - экран выключен:  BALANCED_POWER_ACCURACY, не чаще 60 с И только при смещении >25 м,
 *                     с батчингом (maxUpdateDelay 120 c) — GPS в основном спит.
 * Публикация в Firebase: при смещении >25 м, heartbeat раз в 3 мин (батарея, «онлайн»).
 */
class LocationService : Service() {

    companion object {
        const val ACTION_START = "com.locationshare.app.START"
        const val ACTION_STOP = "com.locationshare.app.STOP"
        const val ACTION_MAP_VISIBLE = "com.locationshare.app.MAP_VISIBLE"
        const val ACTION_MAP_HIDDEN = "com.locationshare.app.MAP_HIDDEN"

        @Volatile var running = false
        @Volatile var mapVisible = false
        @Volatile var lastLocation: Location? = null

        fun intent(ctx: Context, action: String): Intent =
            Intent(ctx, LocationService::class.java).setAction(action)

        fun hasPermission(ctx: Context): Boolean =
            androidx.core.content.ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
                androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        fun start(ctx: Context) {
            if (!hasPermission(ctx)) return // без разрешения location-FGS не стартует (краш на Android 14+)
            ContextCompat.startForegroundService(ctx, intent(ctx, ACTION_START))
        }

        fun stop(ctx: Context) {
            if (running) ctx.startService(intent(ctx, ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var callback: LocationCallback? = null
    private var heartbeat: kotlinx.coroutines.Job? = null
    private var lastPublished: Location? = null
    private var lastPublishTs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                unregister()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MAP_HIDDEN -> mapVisible = false
            else -> mapVisible = true // START и MAP_VISIBLE: карта открыта
        }

        // Нет разрешения геолокации — location-FGS запускать нельзя (SecurityException на 14+)
        if (!hasPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        if (!Prefs.sharing || Prefs.eco) {
            unregister()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        registerLocation()
        return START_STICKY
    }

    private fun startAsForeground() {
        val notif = buildNotification()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    @Suppress("MissingPermission")
    private fun registerLocation() {
        unregister()
        val fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            stopSelf()
            return
        }

        val hi = mapVisible
        val req = LocationRequest.Builder(
            if (hi) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            if (hi) 5_000L else 60_000L,
        ).apply {
            setMinUpdateDistanceMeters(if (hi) 3f else 25f)
            if (!hi) setMaxUpdateDelayMillis(120_000L) // батчинг: будим процесс реже
        }.build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                handleLocation(loc)
            }
        }
        callback = cb
        fused.requestLocationUpdates(req, cb, Looper.getMainLooper())
        startHeartbeat()
    }

    private fun handleLocation(loc: Location) {
        lastLocation = loc
        val code = Prefs.groupCode ?: return
        val now = System.currentTimeMillis()
        val moved = lastPublished?.let { it.distanceTo(loc).toDouble() } ?: Double.MAX_VALUE
        if (moved >= 25 || now - lastPublishTs >= HEARTBEAT_MS) {
            lastPublished = loc
            lastPublishTs = now
            Repo.publishCurrent(code, Prefs.deviceId, Prefs.deviceName, loc, batteryPct())
            if (moved >= 25) Repo.addTrackPoint(code, Prefs.deviceId, loc)
        }
    }

    private fun startHeartbeat() {
        heartbeat?.cancel()
        heartbeat = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                val loc = lastLocation ?: continue
                val code = Prefs.groupCode ?: continue
                Repo.publishCurrent(code, Prefs.deviceId, Prefs.deviceName, loc, batteryPct())
            }
        }
    }

    private fun unregister() {
        callback?.let { fused.removeLocationUpdates(it) }
        callback = null
        heartbeat?.cancel()
        heartbeat = null
    }

    override fun onDestroy() {
        unregister()
        running = false
        scope.cancel()
        super.onDestroy()
    }

    private fun batteryPct(): Int? = try {
        val bm = getSystemService(BatteryManager::class.java)
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 1..100 }
    } catch (_: Exception) {
        null
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL_ID, "Гео-шеринг", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Обмен местоположением с группой"
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1, intent(this, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("LocationShare активен")
            .setContentText("Местоположение передаётся группе")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Стоп", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

private const val NOTIF_ID = 1
private const val CHANNEL_ID = "loc"
private const val HEARTBEAT_MS = 3 * 60_000L
