package com.locationshare.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.locationshare.app.MainActivity

/**
 * «Найти телефон»: громкий сигнал на ALARM-канале (звучит даже при выключенном
 * мультимедиа-звуке), максимум 90 секунд + уведомление с кнопкой «Нашёл».
 */
object RingPlayer {
    private const val CHANNEL = "ring"
    private const val NOTIF_ID = 2
    private const val DURATION_MS = 90_000L

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private var endCallback: (() -> Unit)? = null

    fun start(ctx: Context, by: String?, onEnd: (() -> Unit)? = null) {
        val appCtx = ctx.applicationContext
        stop(appCtx)
        endCallback = onEnd
        try {
            val am = appCtx.getSystemService(AudioManager::class.java)
            am.setStreamVolume(
                AudioManager.STREAM_ALARM,
                am.getStreamMaxVolume(AudioManager.STREAM_ALARM),
                0,
            )
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val mp = MediaPlayer()
            mp.setDataSource(appCtx, uri)
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.isLooping = true
            mp.setVolume(1f, 1f)
            mp.prepare()
            mp.start()
            player = mp
        } catch (_: Exception) {
        }
        val r = Runnable { stop(appCtx) }
        stopRunnable = r
        handler.postDelayed(r, DURATION_MS)
        notify(appCtx, by)
    }

    fun stop(ctx: Context) {
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        try {
            player?.stop()
        } catch (_: Exception) {
        }
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
        endCallback?.invoke()
        endCallback = null
    }

    /** Вызывается, когда сигнал прекратился (кнопка «Нашёл», авто-стоп или команда) */
    fun onEnd(callback: (() -> Unit)?) {
        endCallback = callback
    }

    private fun notify(ctx: Context, by: String?) {        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Поиск устройства", NotificationManager.IMPORTANCE_HIGH)
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, RingStopReceiver::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (!by.isNullOrBlank()) "«$by» ищет ваш телефон" else "Телефон ищут"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(title)
            .setContentText("Сигнал продлится до 90 сек — или нажмите «Нашёл»")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Нашёл", stop)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }
}

class RingStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RingPlayer.stop(context)
    }
}
