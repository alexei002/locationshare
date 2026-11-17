package com.locationshare.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationshare.app.R
import com.locationshare.app.data.Prefs

/**
 * Мастер разрешений: последовательно проводит по незакрытым пунктам —
 * уведомления → геолокация «Всё время» → оптимизация батареи → настройки Xiaomi.
 *
 * Состояние пересчитывается по ON_RESUME: вернулся из системного экрана —
 * мастер сам показывает следующий шаг. Служит и автозапросом после первой
 * выдачи локации (AppRoot), и экраном «Разрешения» в настройках.
 */
@Composable
fun PermissionsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(Step.computeFirst(ctx)) }

    // Возврат из системных настроек = пересчёт: закрытый шаг сменяется следующим
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) step = Step.computeFirst(ctx)
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { step = Step.computeFirst(ctx) }

    when (step) {
        Step.NOTIFICATIONS -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text(stringResource(R.string.btn_allow)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_later)) } },
            title = { Text(stringResource(R.string.dialog_perm_notifications_title)) },
            text = { Text(stringResource(R.string.dialog_perm_notifications_body)) },
        )

        Step.BACKGROUND_LOCATION -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    DeviceIntents.openLocationPermission(ctx)
                }) { Text(stringResource(R.string.btn_grant_always)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_later)) } },
            title = { Text(stringResource(R.string.dialog_bgloc_title)) },
            text = { Text(stringResource(R.string.dialog_bgloc_body)) },
        )

        Step.BATTERY -> {
            // После первого отказа система прячет прямой запрос — дальше ведём в список
            val firstAsk = !Prefs.batteryRequestShown
            AlertDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        if (firstAsk) {
                            Prefs.batteryRequestShown = true
                            openBatteryRequest(ctx)
                        } else {
                            openBatteryList(ctx)
                        }
                    }) { Text(stringResource(R.string.btn_allow)) }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_later)) } },
                title = { Text(stringResource(R.string.dialog_battery_title)) },
                text = { Text(stringResource(if (firstAsk) R.string.dialog_battery_body else R.string.dialog_battery_body_list)) },
            )
        }

        // Автозапуск/«Контроль активности» программно не проверяются —
        // показываем кнопки и закрываем по «Готово»
        Step.XIAOMI -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_done)) } },
            title = { Text(stringResource(R.string.dialog_perm_xiaomi_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.dialog_perm_xiaomi_body))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { DeviceIntents.openXiaomiAutostart(ctx) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.btn_xiaomi_autostart)) }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { DeviceIntents.openXiaomiBatterySaver(ctx) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.btn_xiaomi_activity)) }
                }
            },
        )

        Step.DONE -> AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) } },
            title = { Text(stringResource(R.string.dialog_perm_done_title)) },
            text = { Text(stringResource(R.string.dialog_perm_done_body)) },
        )
    }
}

internal enum class Step { NOTIFICATIONS, BACKGROUND_LOCATION, BATTERY, XIAOMI, DONE;

    companion object {
        /** Первый незакрытый пункт; DONE — всё выдано */
        fun computeFirst(ctx: Context): Step {
            if (Build.VERSION.SDK_INT >= 33 &&
                ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return NOTIFICATIONS
            if (!hasBackgroundLocation(ctx)) return BACKGROUND_LOCATION
            if (!ignoringBattery(ctx)) return BATTERY
            if (DeviceIntents.isXiaomi()) return XIAOMI
            return DONE
        }
    }
}

/** До Android 10 фоновая геолокация выдаётся вместе с FINE — отдельного разрешения нет */
internal fun hasBackgroundLocation(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 29 ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun ignoringBattery(ctx: Context): Boolean = try {
    ctx.getSystemService(android.os.PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(ctx.packageName) == true
} catch (_: Exception) {
    false
}

private fun openBatteryRequest(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${ctx.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
        openBatteryList(ctx)
    }
}

private fun openBatteryList(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {
    }
}
