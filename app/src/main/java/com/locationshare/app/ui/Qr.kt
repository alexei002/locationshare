package com.locationshare.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object Qr {
    private const val SCHEME = "locationshare"
    private const val PREFIX = "$SCHEME://join"

    fun link(code: String): String = "$PREFIX?code=$code"

    /** Возвращает код группы из ссылки locationshare://join?code=XXX или null.
     *  Чистый парсинг без android.net.Uri — тестируется в JVM-юнит-тестах. */
    fun parseJoinLink(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        if (!s.startsWith(PREFIX, ignoreCase = true)) return null
        val rest = s.substring(PREFIX.length)
        if (rest.isNotEmpty() && rest[0] != '?') return null
        for (pair in rest.drop(1).split('&')) {
            val i = pair.indexOf('=')
            if (i <= 0) continue
            if (!pair.substring(0, i).equals("code", ignoreCase = true)) continue
            val v = pair.substring(i + 1).substringBefore('#').trim()
            if (v.isEmpty()) return null
            return v.uppercase().take(10)
        }
        return null
    }

    fun bitmap(content: String, size: Int = 640): Bitmap {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
