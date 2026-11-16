package com.locationshare.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object Qr {
    private const val SCHEME = "locationshare"

    fun link(code: String): String = "$SCHEME://join?code=$code"

    /** Возвращает код группы из ссылки locationshare://join?code=XXX или null */
    fun parseJoinLink(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(raw.trim())
            if (uri.scheme != SCHEME || uri.host != "join") null
            else uri.getQueryParameter("code")?.uppercase()?.take(10)
        } catch (_: Exception) {
            null
        }
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
