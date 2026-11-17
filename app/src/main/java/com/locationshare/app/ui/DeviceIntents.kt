package com.locationshare.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Интенты системных настроек.
 *
 * Xiaomi/MIUI/HyperOS убивают фоновые приложения несколькими собственными
 * механизмами поверх AOSP (PowerKeeper, «Контроль активности», автозапуск).
 * Стандартный whitelist оптимизации батареи их не закрывает — ведём
 * пользователя на фирменные экраны настроек.
 */
internal object DeviceIntents {

    /** Redmi/POCO/Xiaomi — у всех производитель «Xiaomi» */
    fun isXiaomi(): Boolean = isXiaomi(android.os.Build.MANUFACTURER)

    /** Карточка приложения в системных настройках (разрешения — рядом) */
    fun openAppDetails(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Экран «Местоположение: доступ для этого приложения» — сразу с выбором
     * «Разрешить в любом режиме» (MIUI-название «Всё время»). Скрытое действие
     * permissioncontroller: работает на AOSP-подобных прошивках (MIUI/HyperOS
     * подтвердлено); если активити нет — fallback на карточку приложения.
     */
    fun openLocationPermission(ctx: Context) {
        try {
            ctx.startActivity(
                Intent("android.intent.action.MANAGE_APP_PERMISSION")
                    .putExtra("android.intent.extra.PACKAGE_NAME", ctx.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            openAppDetails(ctx)
        }
    }

    /** Экран «Автозапуск» MIUI: без него не работает ни BOOT, ни рестарты после килла */
    fun openXiaomiAutostart(ctx: Context) {
        try {
            ctx.startActivity(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity",
                    )
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            openAppDetails(ctx) // Security Center нет/переименован — хотя бы карточка приложения
        }
    }

    /** «Контроль активности» конкретного приложения (PowerKeeper): нужен «Нет ограничений» */
    fun openXiaomiBatterySaver(ctx: Context) {
        try {
            ctx.startActivity(
                Intent().setComponent(
                    ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                    )
                )
                    .putExtra("package_name", ctx.packageName)
                    .putExtra("package_label", ctx.applicationInfo.loadLabel(ctx.packageManager).toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            openAppDetails(ctx)
        }
    }
}

/** Чистая функция для тестов: производитель Xiaomi → нужны MIUI-экраны настроек */
internal fun isXiaomi(manufacturer: String?): Boolean =
    manufacturer?.equals("Xiaomi", ignoreCase = true) == true
