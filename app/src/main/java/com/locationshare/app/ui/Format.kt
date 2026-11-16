package com.locationshare.app.ui

import kotlin.math.abs

// Чистое форматирование без Android-зависимостей (вынесено из MainScreen.kt для тестируемости)

private val PALETTE = intArrayOf(
    0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFB8C00.toInt(),
    0xFF8E24AA.toInt(), 0xFF00ACC1.toInt(), 0xFFF4511E.toInt(), 0xFF3949AB.toInt(),
)

internal fun colorFor(id: String): Int = PALETTE[abs(id.hashCode()) % PALETTE.size]

/**
 * Стабильное назначение цветов устройствам: ids сортируются и получают цвета палитры
 * по порядку, циклично при >8 устройствах. В отличие от hash в colorFor не даёт
 * дублей цветов, пока устройств не больше 8.
 */
internal fun colorMapFor(ids: Collection<String>): Map<String, Int> =
    ids.sorted().withIndex().associate { (i, id) -> id to PALETTE[i % PALETTE.size] }

internal fun timeAgo(ts: Long): String {
    if (ts <= 0) return "—"
    val d = System.currentTimeMillis() - ts
    return when {
        d < 60_000 -> "только что"
        d < 3_600_000 -> "${d / 60_000} мин"
        d < 86_400_000 -> "${d / 3_600_000} ч"
        else -> "давно"
    }
}
