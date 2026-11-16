package com.locationshare.app.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.locationshare.app.data.Prefs
import com.locationshare.app.data.Repo
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Эко-режим: никакого foreground-сервиса. Раз в 15 мин WorkManager берёт
 * последнюю известную/пассивную локацию и публикует одну точку.
 * Фикс старше 30 мин не публикуется — не показывать вчерашнюю точку как «онлайн».
 */
class EcoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!Prefs.sharing || !Prefs.eco) return Result.success()
        val code = Prefs.groupCode ?: return Result.success()
        val fused = LocationServices.getFusedLocationProviderClient(applicationContext)

        // Цепочка источников: пассивный фикс → последний известный → дешёвые активные запросы
        val loc = runCatching {
            fused.getCurrentLocation(Priority.PRIORITY_PASSIVE, null).await()
        }.getOrNull()
            ?: LocationService.lastLocation
            ?: runCatching {
                fused.getCurrentLocation(Priority.PRIORITY_LOW_POWER, null).await()
            }.getOrNull()
            ?: runCatching {
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
            }.getOrNull()
            ?: return Result.success()

        // PASSIVE может отдать вчерашний фикс: слишком старый — тихо пропускаем
        if (System.currentTimeMillis() - loc.time > STALE_MS) return Result.success()

        val bat = try {
            val bm = applicationContext.getSystemService(android.os.BatteryManager::class.java)
            bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 1..100 }
        } catch (_: Exception) {
            null
        }
        Repo.publishCurrent(code, Prefs.deviceId, Prefs.deviceName, loc, bat)
        // Консистентность стейта watchdog: эко-публикации тоже учитываются
        LocationService.lastPublishMs = System.currentTimeMillis()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "eco"
        private const val STALE_MS = 30 * 60_000L // фикс старше — не публиковать
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<EcoWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        }
    }
}
