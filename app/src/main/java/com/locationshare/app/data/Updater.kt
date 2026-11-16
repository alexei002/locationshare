package com.locationshare.app.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.locationshare.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Полуавтоматические обновления: проверяем GitHub Releases (или любой совместимый
 * JSON-эндпоинт), качаем APK через DownloadManager и открываем установку.
 * URL задаётся в local.properties (UPDATE_URL) и попадает в BuildConfig.
 */
object Updater {

    data class Update(val tag: Int, val version: String, val url: String)

    sealed class CheckResult {
        data class Found(val u: Update) : CheckResult()
        object UpToDate : CheckResult()
        object NotConfigured : CheckResult()
        data class Error(val msg: String) : CheckResult()
    }

    /** «2026.11.2» → 20261102; «4» → 4 (старый формат тегов) */
    private fun tagCode(t: String): Int? = try {
        if (t.contains('.')) {
            val p = t.split('.').map { it.trim().toInt() }
            if (p.size != 3) null else p[0] * 10000 + p[1] * 100 + p[2]
        } else {
            t.toInt()
        }
    } catch (e: Exception) {
        null
    }

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.UPDATE_URL
        if (endpoint.isBlank()) return@withContext CheckResult.NotConfigured
        try {
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            if (conn.responseCode != 200) {
                return@withContext CheckResult.Error("HTTP ${conn.responseCode}")
            }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tagRaw = json.optString("tag_name").removePrefix("v").trim()
            val tag = tagCode(tagRaw) ?: return@withContext CheckResult.Error("тег релиза не распознан: $tagRaw")
            if (tag <= BuildConfig.VERSION_CODE) return@withContext CheckResult.UpToDate
            val assets = json.optJSONArray("assets")
                ?: return@withContext CheckResult.Error("в релизе нет файлов")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name", "").endsWith(".apk")) {
                    url = a.optString("browser_download_url"); break
                }
            }
            url?.let { CheckResult.Found(Update(tag, tagRaw, it)) }
                ?: CheckResult.Error("APK в релизе не найден")
        } catch (e: Exception) {
            CheckResult.Error("сеть: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    fun downloadAndInstall(ctx: Context, url: String, tag: Int) {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val name = "locationshare-v$tag.apk"
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle("Обновление LocationShare v$tag")
            .setDescription("Скачивание APK")
            .setMimeType("application/vnd.android.package-archive")
            .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val id = dm.enqueue(req)

        val appCtx = ctx.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                try {
                    appCtx.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                try {
                    val uri = dm.getUriForDownloadedFile(id) ?: return
                    if (Build.VERSION.SDK_INT >= 26 &&
                        !appCtx.packageManager.canRequestPackageInstalls()
                    ) {
                        // Один раз разрешаем установку из этого приложения
                        appCtx.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:${appCtx.packageName}")
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                    appCtx.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {
                }
            }
        }
        try {
            ContextCompat.registerReceiver(
                appCtx, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED,
            )
        } catch (_: Exception) {
        }
    }
}
