package com.locationshare.app.data

import android.content.Context
import android.os.Build
import android.util.Log
import com.locationshare.app.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Сбор краш-логов:
 *  - Java-краши: uncaught handler → файл + синхронный POST в Firebase (/crashes)
 *  - Нативные краши/убийства процесса: «след» шагов инициализации (mark) пишется
 *    в файл; если прошлый запуск не дошёл до CLEAN_MARK — при следующем старте
 *    след отправляется синхронно, до любой рискованной инициализации
 *  - Офлайн-запас: неотправленные файлы уходят при следующем запуске
 */
object CrashReporter {
    private const val MAX_CHARS = 14_000
    private const val CLEAN_MARK = "app:ok"
    private lateinit var stageFile: File

    @Volatile
    private var lastMark = ""

    fun install(appCtx: Context) {
        stageFile = File(appCtx.filesDir, "last_run_stages.txt")

        // Прошлый запуск закончился нечисто? Шлём его след прямо сейчас,
        // пока процесс жив (нативные краши иначе вообще невидимы).
        try {
            val prev = if (stageFile.exists()) stageFile.readText() else ""
            if (prev.isNotBlank() && !prev.trimEnd().endsWith(CLEAN_MARK)) {
                post("DIRTY RUN (краш или убийство процесса)\n$prev")
            }
            stageFile.delete()
        } catch (_: Exception) {
        }
        mark("app:create")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val text = buildLog(thread, throwable) + "\nlast stage: " + lastMark
                val dir = File(appCtx.filesDir, "crashes").apply { mkdirs() }
                File(dir, "crash-${System.currentTimeMillis()}.txt").writeText(text)
                post(text)
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** Отметка пройденного шага инициализации */
    fun mark(stage: String) {
        lastMark = stage
        try {
            stageFile.appendText(stage + "\n")
        } catch (_: Exception) {
        }
    }

    /** Отправить краш-файлы, накопившиеся офлайн (вызывать при старте в фоне) */
    fun sendPending(appCtx: Context) {
        try {
            val dir = File(appCtx.filesDir, "crashes")
            val files = dir.listFiles()?.sortedBy { it.name } ?: return
            for (f in files.take(5)) {
                if (post(f.readText())) f.delete() else break
            }
        } catch (_: Exception) {
        }
    }

    private fun buildLog(thread: Thread, t: Throwable): String {
        val sb = StringBuilder()
        sb.append("app: ").append(BuildConfig.VERSION_NAME)
            .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
        sb.append("device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(", Android ").append(Build.VERSION.RELEASE)
            .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("group: ").append(Prefs.groupCode ?: "-").append('\n')
        sb.append("thread: ").append(thread.name).append('\n')
        sb.append('\n')
        sb.append(Log.getStackTraceString(t))
        return sb.toString().take(MAX_CHARS)
    }

    /** Синхронный POST в /crashes; true — ушло */
    private fun post(text: String): Boolean = try {
        val conn = URL(Repo.DB_URL + "/crashes.json").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = 3_000
        conn.readTimeout = 3_000
        val body = JSONObject().apply {
            put("ts", System.currentTimeMillis())
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android", Build.VERSION.RELEASE)
            put("app", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            put("group", Prefs.groupCode ?: "")
            put("log", text)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val ok = conn.responseCode in 200..299
        try {
            conn.inputStream.close()
        } catch (_: Exception) {
        }
        ok
    } catch (e: Exception) {
        false
    }
}
