package com.locationshare.app.ui

import kotlin.math.abs

// Чистое форматирование без Android-зависимостей (вынесено из MainScreen.kt для тестируемости)

private val PALETTE = intArrayOf(
    0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFB8C00.toInt(),
    0xFF8E24AA.toInt(), 0xFF00ACC1.toInt(), 0xFFF4511E.toInt(), 0xFF3949AB.toInt(),
    0xFFFDD835.toInt(), 0xFF00897B.toInt(), 0xFFD81B60.toInt(), 0xFF6D4C41.toInt(),
    0xFFC0CA33.toInt(), 0xFF5E35B1.toInt(), 0xFF455A64.toInt(), 0xFF212121.toInt(),
)

internal fun colorFor(id: String): Int = PALETTE[abs(id.hashCode()) % PALETTE.size]

/** Палитра для UI выбора цвета (настройки) */
internal val PALETTE_COLORS: List<Int> = PALETTE.toList()

/** ARGB → "#RRGGBB" (формат поля color в Firebase и Prefs) */
internal fun colorHex(c: Int): String = "#%06X".format(c and 0xFFFFFF)

/** "#RRGGBB" → ARGB; строгий формат, иначе null (= авто-цвет) */
internal fun parseColorHex(s: String?): Int? {
    if (s == null || s.length != 7 || s[0] != '#') return null
    val hex = s.substring(1)
    if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return (0xFF shl 24) or hex.toInt(16)
}

/**
 * Стабильное назначение цветов устройствам: ids сортируются и получают цвета палитры
 * по порядку, циклично при >16 устройствах. В отличие от hash в colorFor не даёт
 * дублей цветов, пока устройств не больше 16. Устройства из [custom] фиксируют свой
 * цвет, остальным палитра раздаётся с пропуском занятого (уникальность сохраняется,
 * пока палитры хватает; вся палитра занята кастомными — цикл по полной палитре).
 */
internal fun colorMapFor(ids: Collection<String>, custom: Map<String, Int> = emptyMap()): Map<String, Int> {
    val free = PALETTE.filter { it !in custom.values.toSet() }
    val pool = if (free.isEmpty()) PALETTE.toList() else free
    val result = HashMap<String, Int>(ids.size)
    var i = 0
    for (id in ids.sorted()) {
        val c = custom[id]
        if (c != null) {
            result[id] = c
        } else {
            result[id] = pool[i % pool.size]
            i++
        }
    }
    return result
}

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
