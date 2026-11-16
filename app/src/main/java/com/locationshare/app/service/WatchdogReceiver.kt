package com.locationshare.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.locationshare.app.data.Prefs

/**
 * Watchdog на AlarmManager: переживает смерть процесса (MIUI/Doze убивают сервис,
 * START_STICKY на агрессивных прошивках не работает).
 *
 * Раз в 10 мин, пока выключен эко, цепочка алармов перепланирует
 * сама себя; если сервис молчит дольше интервала — перезапускает его.
 * Включили эко или вышли из группы — цепочка не перепланируется и гаснет сама.
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Prefs.init(context)
        // Условие не выполняется (эко / нет группы) — не перепланируем:
        // цепочка алармов гаснет сама
        if (Prefs.eco || Prefs.groupCode == null) return

        // Сначала звено следующего срабатывания — цепочка живёт, пока выполняется условие
        schedule(context)

        // lastPublishMs == 0 — свежий процесс, сервис ещё ни разу не публиковал;
        // либо сервис молчит дольше интервала — поднимаем заново
        val silent = LocationService.lastPublishMs == 0L ||
            System.currentTimeMillis() - LocationService.lastPublishMs >= INTERVAL_MS
        if (silent) {
            try {
                LocationService.start(context)
            } catch (_: Exception) {
                // Старт FGS из аларма разрешён (аларм даёт temporary allowlist),
                // но на всякий случай ловим — цепочка попробует ещё раз через 10 мин
            }
        }
    }

    companion object {
        const val INTERVAL_MS = 10 * 60_000L
        private const val ACTION = "com.locationshare.app.WATCHDOG"
        private const val REQ_CODE = 1001

        // Один и тот же PendingIntent на schedule/cancel: перезапись вместо дублей алармов
        private fun pendingIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, REQ_CODE,
            Intent(ctx, WatchdogReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Планирует следующее срабатывание watchdog. */
        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // RTC_WAKEUP будит CPU при выключенном экране; setAndAllowWhileIdle пробивается
            // в Doze (система пускает ~раз в 9 мин — наш ритм 10 мин проходит без
            // специальных разрешений)
            am.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + INTERVAL_MS,
                pendingIntent(ctx),
            )
        }

        /** Гасит цепочку: cancel по тому же PendingIntent. */
        fun cancel(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(ctx))
        }
    }
}
