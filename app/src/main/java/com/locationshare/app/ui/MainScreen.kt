package com.locationshare.app.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationshare.app.data.Prefs
import com.locationshare.app.data.Repo
import com.locationshare.app.data.Updater
import com.locationshare.app.service.EcoWorker
import com.locationshare.app.service.LocationService
import com.locationshare.app.service.RingPlayer
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.runtime.image.ImageProvider
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.mapview.MapView
import java.lang.ref.WeakReference
import kotlin.math.ln
import kotlin.math.min
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val group = Prefs.groupCode ?: return
    val sharing by Prefs.sharingFlow.collectAsState()
    val eco by Prefs.ecoFlow.collectAsState()
    var devices by remember { mutableStateOf<List<Repo.DeviceState>>(emptyList()) }
    var showSettings by remember { mutableStateOf(false) }
    var ringTarget by remember { mutableStateOf<Repo.DeviceState?>(null) }
    var ringingBy by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<Updater.Update?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<List<String>?>(null) }
    var focusTrigger by remember { mutableStateOf(0) }
    var focusId by remember { mutableStateOf<String?>(null) }
    var adminId by remember { mutableStateOf<String?>(null) }
    var fitTrigger by remember { mutableStateOf(0) }
    var centerTrigger by remember { mutableStateOf(0) }
    val myId = Prefs.deviceId

    DisposableEffect(group) {
        val listener = Repo.observe(group) { devices = it }
        val adminL = Repo.observeAdmin(group) { adminId = it }
        onDispose {
            Repo.stop(group, listener)
            Repo.stopAdmin(group, adminL)
        }
    }

    // Входящая команда «прозвонить меня» от другого устройства группы
    DisposableEffect(group, myId) {
        val l = Repo.observeRing(group, myId) { by ->
            ringingBy = by
            RingPlayer.start(ctx, by) { ringingBy = null }
        }
        onDispose { Repo.stopRing(group, myId, l) }
    }

    // Раз в день тихо проверяем обновления
    LaunchedEffect(Unit) {
        val today = System.currentTimeMillis() / 86_400_000L
        if (Prefs.lastUpdateCheck != today) {
            Prefs.lastUpdateCheck = today
            val r = Updater.check()
            if (r is Updater.CheckResult.Found) update = r.u
        }
    }

    LaunchedEffect(sharing, eco) {
        if (sharing && !eco) LocationService.start(ctx)
        else LocationService.stop(ctx)
        if (sharing && eco) EcoWorker.schedule(ctx)
        else EcoWorker.cancel(ctx)
    }

    val selected = devices.firstOrNull { it.id == selectedId }
    val selectedDevices = selectedGroup?.mapNotNull { id -> devices.firstOrNull { it.id == id } }

    Box(Modifier.fillMaxSize()) {
        MapArea(
            devices = devices,
            onMarkerTap = { selectedId = it; selectedGroup = null },
            onMapTap = { selectedId = null; selectedGroup = null },
            onGroupTap = { selectedGroup = it; selectedId = null },
            fitTrigger = fitTrigger,
            centerTrigger = centerTrigger,
            focusTrigger = focusTrigger,
            focusId = focusId,
        )

        // Баннер обновления сверху
        update?.let { u ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp),
                ) {
                    Text("Доступна версия ${u.version}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        Updater.downloadAndInstall(ctx, u.url, u.tag); update = null
                    }) { Text("Установить") }
                    TextButton(onClick = { update = null }) { Text("Позже") }
                }
            }
        }

        // Единый нижний столбец: карточка → кнопки → панель чипов (не перекрываются)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!selectedDevices.isNullOrEmpty()) {
                // Карточка группы: тап по кружку близких устройств
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier.padding(bottom = 6.dp).fillMaxWidth(0.92f),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "В этой точке: ${selectedDevices.size}",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { selectedGroup = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", modifier = Modifier.size(18.dp))
                            }
                        }
                        for (d in selectedDevices) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { focusId = d.id; focusTrigger++ }
                                    .padding(vertical = 4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .background(androidx.compose.ui.graphics.Color(colorFor(d.id)), CircleShape)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        d.name + if (d.id == myId) " (Вы)" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    d.current?.let {
                                        Text(
                                            "обновлено ${timeAgo(it.ts)} · точность ±${d.accuracy.toInt()} м",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                                d.battery?.let { Text("$it%", style = MaterialTheme.typography.bodySmall) }
                                if (d.id != myId) {
                                    TextButton(onClick = { ringTarget = d }) { Text("🔔") }
                                    if (adminId == myId) {
                                        TextButton(onClick = {
                                            Repo.removeDevice(group, d.id)
                                        }) { Text("🗑", color = MaterialTheme.colorScheme.error) }
                                    }
                                }
                            }
                        }
                    }
                }
            } else selected?.let { d ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp,
                    modifier = Modifier.padding(bottom = 6.dp).fillMaxWidth(0.92f),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .background(androidx.compose.ui.graphics.Color(colorFor(d.id)), CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                d.name + if (d.id == myId) " (Вы)" else "",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            d.battery?.let { Text("$it%", style = MaterialTheme.typography.bodyMedium) }
                            IconButton(onClick = { selectedId = null }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", modifier = Modifier.size(18.dp))
                            }
                        }
                        d.current?.let {
                            Text(
                                "обновлено ${timeAgo(it.ts)} · точность ±${d.accuracy.toInt()} м",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (d.id != myId) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                OutlinedButton(onClick = { ringTarget = d }) { Text("Прозвонить") }
                                if (adminId == myId) {
                                    TextButton(onClick = {
                                        Repo.removeDevice(group, d.id)
                                        selectedId = null
                                    }) { Text("Удалить из группы", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }

            // Кнопки карты: «Все» и «центрировать на мне»
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
            ) {
                Surface(
                    onClick = { fitTrigger++ },
                    shape = CircleShape,
                    tonalElevation = 3.dp,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("Все", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                FloatingActionButton(onClick = { centerTrigger++ }) {
                    Icon(Icons.Filled.LocationOn, contentDescription = "Центрировать на мне")
                }
            }

            // Компактная нижняя панель: чипы устройств + кнопка настроек
            Surface(
                tonalElevation = 3.dp,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (!sharing) {
                            item {
                                Text(
                                    "гео выключено",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                )
                            }
                        }
                        if (devices.isEmpty() && sharing) {
                            item {
                                Text(
                                    "ждём точки…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                )
                            }
                        }
                        items(devices, key = { it.id }) { d -> DeviceChip(d) { selectedId = d.id; ringTarget = d } }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                }
            }
        }
    }

    if (showSettings) SettingsScreen(
        onClose = { showSettings = false },
        adminId = adminId,
        devices = devices,
    )

    ringTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { ringTarget = null },
            title = { Text("Найти устройство") },
            text = {
                Text(
                    "Прозвонить «${if (t.id == Prefs.deviceId) "Вы" else t.name}» громким сигналом?\n\n" +
                        "(нажать и удерживать чип — меню)"
                )
            },
            confirmButton = {
                TextButton(onClick = { Repo.ring(group, t.id, Prefs.deviceName); ringTarget = null }) {
                    Text("Прозвонить")
                }
            },
            dismissButton = {
                TextButton(onClick = { ringTarget = null }) { Text("Отмена") }
            },
        )
    }

    // Меня прозвонили: кто ищет + кнопка «Нашёл»
    if (ringingBy != null) {
        val by = ringingBy ?: ""
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (by.isNotBlank()) "«$by» ищет ваш телефон" else "Телефон ищут") },
            text = { Text("Громкий сигнал продлится до 90 секунд. Если вы рядом — нажмите «Нашёл».") },
            confirmButton = {
                TextButton(onClick = { RingPlayer.stop(ctx); ringingBy = null }) {
                    Text("Нашёл")
                }
            },
        )
    }
}

@Composable
private fun SettingsScreen(
    onClose: () -> Unit,
    adminId: String?,
    devices: List<Repo.DeviceState>,
) {
    val sharing by Prefs.sharingFlow.collectAsState()
    val eco by Prefs.ecoFlow.collectAsState()
    val group = Prefs.groupCode ?: ""
    var showBat by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var updState by remember { mutableStateOf<String?>(null) }
    val myId = Prefs.deviceId
    val isAdmin = adminId == myId

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Настройки",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                }
            }
            ToggleRow(
                title = "Передавать гео",
                subtitle = if (eco) "Эко: обновление раз в 15 мин"
                else "Адаптивно: точнее в движении, экономнее на месте",
                checked = sharing,
            ) { Prefs.sharing = it }
            ToggleRow(
                title = "Эко-режим",
                subtitle = "Минимум батареи: без постоянного сервиса, раз в 15 мин",
                checked = eco,
            ) { Prefs.eco = it }
            var name by remember { mutableStateOf(Prefs.deviceName) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; Prefs.deviceName = it },
                label = { Text("Имя устройства") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            // Администрирование группы
            Text(
                "Администрирование",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp),
            )
            when {
                adminId == null -> {
                    Text("Администратор не назначен", fontSize = 12.sp)
                    OutlinedButton(onClick = { Repo.claimAdmin(group, myId) }) {
                        Text("Стать администратором")
                    }
                }
                isAdmin -> Text("Вы администратор группы", fontSize = 12.sp)
                else -> Text(
                    "Администратор: ${devices.firstOrNull { it.id == adminId }?.name ?: adminId}",
                    fontSize = 12.sp,
                )
            }
            if (isAdmin && devices.size > 1) {
                Text("Устройства (${devices.size}):", fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                for (d in devices) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 2.dp),
                    ) {
                        Text(
                            d.name + if (d.id == myId) " (Вы)" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (d.id != myId) {
                            TextButton(onClick = { Repo.removeDevice(group, d.id) }) {
                                Text("Удалить", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Text(
                "Группа: $group",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(onClick = { showQr = true }) { Text("QR группы") }
                OutlinedButton(onClick = { onClose(); Prefs.groupCode = null }) {
                    Text("Сменить")
                }
                OutlinedButton(onClick = { showBat = true }) { Text("Батарея") }
            }

            // Обновления
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                OutlinedButton(onClick = {
                    updState = "проверяю…"
                    scope.launch {
                        val r = Updater.check()
                        updState = when (r) {
                            is Updater.CheckResult.Found -> {
                                Updater.downloadAndInstall(ctx, r.u.url, r.u.tag)
                                "скачивается ${r.u.version}…"
                            }
                            Updater.CheckResult.UpToDate -> "у вас последняя версия"
                            Updater.CheckResult.NotConfigured -> "не настроено"
                            is Updater.CheckResult.Error -> "не удалось: ${r.msg}"
                        }
                    }
                }) { Text("Проверить обновления") }
                updState?.let {
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            TextButton(onClick = {
                try {
                    ctx.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/alexei002/locationshare/releases/latest"),
                        )
                    )
                } catch (_: Exception) {
                }
            }) {
                Text("Открыть страницу релиза в браузере", fontSize = 12.sp)
            }

            Text(
                "Версия ${com.locationshare.app.BuildConfig.VERSION_NAME} (сборка ${com.locationshare.app.BuildConfig.VERSION_CODE})",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Закрыть") } },
            title = { Text("QR группы $group") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = Qr.bitmap(Qr.link(group)).asImageBitmap(),
                        contentDescription = "QR группы",
                        modifier = Modifier.size(280.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Наведите камеру другого устройства на этот QR (кнопка «Присоединиться по QR») — группа подставится автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
    }
    if (showBat) BatteryDialog { showBat = false }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceChip(d: Repo.DeviceState, onLongPress: () -> Unit) {
    val me = d.id == Prefs.deviceId
    val color = androidx.compose.ui.graphics.Color(colorFor(d.id))
    Surface(
        shape = RoundedCornerShape(50),
        color = if (d.online) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .alpha(if (d.online) 1f else 0.5f),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (me) "Вы" else d.name, style = MaterialTheme.typography.bodySmall)
            d.current?.let {
                Text(
                    "  ${timeAgo(it.ts)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            d.battery?.let {
                Text(
                    "  $it%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun BatteryDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Экономия батареи") },
        text = {
            Column {
                Text("1. Сейчас откроется системный запрос — разрешите «Не оптимизировать».")
                Text("2. MIUI: Настройки → Приложения → LocationShare → Контроль активности → «Нет ограничений»; Автозапуск — вкл.")
                Text("3. MIUI: в меню недавних приложений задержите карточку и нажмите замочек 🔒.")
            }
        },
    )
    LaunchedEffect(Unit) {
        try {
            val pm = ctx.getSystemService(android.os.PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                ctx.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:${ctx.packageName}"),
                    )
                )
            }
        } catch (_: Exception) {
        }
    }
}

// ---------- Карта ----------
// colorFor/timeAgo/PALETTE вынесены в Format.kt (тот же пакет)

private fun pinBitmap(label: String, color: Int): Bitmap {
    val size = 128
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    c.drawCircle(size / 2f, size / 2f, size / 2f - 8f, fill)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    c.drawCircle(size / 2f, size / 2f, size / 2f - 8f, border)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        textSize = 60f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val y = size / 2f - (text.ascent() + text.descent()) / 2f
    c.drawText(label.take(1).uppercase(), size / 2f, y, text)
    return b
}

/** Кластер: синий кружок с количеством устройств */
private fun clusterBitmap(count: Int): Bitmap {
    val size = 128
    val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1E88E5.toInt() }
    c.drawCircle(size / 2f, size / 2f, size / 2f - 6f, fill)
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    c.drawCircle(size / 2f, size / 2f, size / 2f - 6f, border)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFFFFFFFF.toInt()
        textSize = 58f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val y = size / 2f - (text.ascent() + text.descent()) / 2f
    c.drawText(count.coerceAtMost(99).toString(), size / 2f, y, text)
    return b
}

@Composable
fun MapArea(
    devices: List<Repo.DeviceState>,
    onMarkerTap: (String) -> Unit,
    onMapTap: () -> Unit,
    onGroupTap: (List<String>) -> Unit = {},
    fitTrigger: Int = 0,
    centerTrigger: Int = 0,
    focusTrigger: Int = 0,
    focusId: String? = null,
) {
    val myId = Prefs.deviceId
    var follow by remember { mutableStateOf(true) }
    var centered by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val placemarks = remember { mutableStateMapOf<String, PlacemarkMapObject>() }
    // Схлопнутые группы: ключ «grp:<id первого>» → общий кружок с числом
    val groupPins = remember { mutableStateMapOf<String, PlacemarkMapObject>() }
    val groupMembers = remember { mutableStateMapOf<String, List<String>>() }
    val tapListeners = remember { mutableStateMapOf<String, MapObjectTapListener>() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val devicesNow by rememberUpdatedState(devices)

    // Держим сильные ссылки: MapKit принимает WeakReference
    val inputListener = remember {
        object : InputListener {
            override fun onMapTap(m: com.yandex.mapkit.map.Map, p: Point) {
                onMapTap()
            }

            override fun onMapLongTap(m: com.yandex.mapkit.map.Map, p: Point) {}
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mv = mapView
            when (event) {
                Lifecycle.Event.ON_START -> {
                    // MapKit 4.x: без onStart() инстанса тайлы не грузятся
                    MapKitFactory.getInstance().onStart()
                    mv?.onStart()
                }
                Lifecycle.Event.ON_STOP -> {
                    mv?.onStop()
                    MapKitFactory.getInstance().onStop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onStop()
        }
    }

    // «Показать всех»
    LaunchedEffect(fitTrigger) {
        if (fitTrigger == 0) return@LaunchedEffect
        val mv = mapView ?: return@LaunchedEffect
        val pts = devicesNow.mapNotNull { it.current }
        if (pts.isEmpty()) return@LaunchedEffect
        follow = false
        val minLat = pts.minOf { it.lat }; val maxLat = pts.maxOf { it.lat }
        val minLon = pts.minOf { it.lon }; val maxLon = pts.maxOf { it.lon }
        val center = Point((minLat + maxLat) / 2, (minLon + maxLon) / 2)
        val latSpan = (maxLat - minLat).coerceAtLeast(0.003)
        val lonSpan = (maxLon - minLon).coerceAtLeast(0.003)
        val wPx = kotlin.math.max(mv.width, 256)
        val hPx = kotlin.math.max(mv.height, 256)
        val zLat = ln(360.0 / latSpan * (hPx * 0.8 / 256.0)) / ln(2.0)
        val zLon = ln(360.0 / lonSpan * (wPx * 0.8 / 256.0)) / ln(2.0)
        val zoom = min(zLat, zLon).coerceIn(3.0, 18.0).toFloat()
        mv.map.move(
            CameraPosition(center, zoom, 0f, 0f),
            Animation(Animation.Type.SMOOTH, 0.6f),
            null,
        )
    }

    // «Центрировать на мне»
    LaunchedEffect(centerTrigger) {
        if (centerTrigger == 0) return@LaunchedEffect
        val mv = mapView ?: return@LaunchedEffect
        val mePoint: Point? = devicesNow.firstOrNull { it.id == myId }?.current
            ?.let { Point(it.lat, it.lon) }
            ?: LocationService.lastLocation?.let { Point(it.latitude, it.longitude) }
        if (mePoint != null) {
            follow = true
            centered = true
            mv.map.move(
                CameraPosition(mePoint, mv.map.cameraPosition.zoom.coerceAtLeast(14f), 0f, 0f),
                Animation(Animation.Type.SMOOTH, 0.5f),
                null,
            )
        }
    }

    // Фокус на устройстве из карточки группы
    LaunchedEffect(focusTrigger) {
        if (focusTrigger == 0) return@LaunchedEffect
        val id = focusId ?: return@LaunchedEffect
        val mv = mapView ?: return@LaunchedEffect
        val target = devicesNow.firstOrNull { it.id == id }?.current ?: return@LaunchedEffect
        follow = false
        centered = true
        mv.map.move(
            CameraPosition(
                Point(target.lat, target.lon),
                mv.map.cameraPosition.zoom.coerceAtLeast(16f),
                0f, 0f,
            ),
            Animation(Animation.Type.SMOOTH, 0.5f),
            null,
        )
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    map.isRotateGesturesEnabled = false
                    map.addInputListener(WeakReference(inputListener))
                }
            },
            update = { mv ->
                mapView = mv
                val objs = mv.map.mapObjects
                val seen = HashSet<String>()
                for (d in devices) {
                    seen.add(d.id)
                    val color = colorFor(d.id)
                    if (!tapListeners.contains(d.id)) {
                        tapListeners[d.id] = MapObjectTapListener { _, _ ->
                            onMarkerTap(d.id); true
                        }
                    }
                    val pl = placemarks.getOrPut(d.id) {
                        objs.addPlacemark().apply {
                            setIcon(
                                ImageProvider.fromBitmap(pinBitmap(labelFor(d, myId), color)),
                                IconStyle().apply { scale = 0.55f },
                            )
                            addTapListener(WeakReference(tapListeners[d.id]))
                        }
                    }
                    d.current?.let { pl.geometry = Point(it.lat, it.lon) }
                }
                for (k in placemarks.keys - seen) {
                    placemarks.remove(k)?.let { objs.remove(it) }
                    tapListeners.remove(k)
                }

                // --- Ручное схлопывание близких маркеров ---
                // Встроенная кластеризация MapKit не сработала на слипшихся точках,
                // поэтому сами считаем экранную близость (<48 px) и рисуем общий кружок с числом.
                val withPoints = devices.filter { it.current != null }
                val screenPos = HashMap<String, Pair<Float, Float>>()
                for (d in withPoints) {
                    val sp = mv.mapWindow.worldToScreen(Point(d.current!!.lat, d.current!!.lon))
                    if (sp != null) screenPos[d.id] = Pair(sp.x, sp.y)
                }
                val assigned = HashSet<String>()
                val groups = mutableListOf<List<Repo.DeviceState>>()
                val sorted = withPoints.sortedByDescending { it.current?.ts ?: 0L }
                for (d in sorted) {
                    if (d.id in assigned || d.id !in screenPos) continue
                    val (sx, sy) = screenPos[d.id]!!
                    val grp = mutableListOf(d)
                    assigned.add(d.id)
                    for (other in sorted) {
                        if (other.id in assigned || other.id !in screenPos) continue
                        val (ox, oy) = screenPos[other.id]!!
                        val dx = ox - sx; val dy = oy - sy
                        if (dx * dx + dy * dy < 48f * 48f) {
                            grp.add(other)
                            assigned.add(other.id)
                        }
                    }
                    if (grp.size > 1) groups.add(grp)
                }

                val groupedIds = groups.flatten().map { it.id }.toHashSet()
                // одиночные — видимы, в группе — скрыты
                for (d in withPoints) {
                    placemarks[d.id]?.setVisible(d.id !in groupedIds)
                }
                // общие кружки
                val groupKeys = HashSet<String>()
                for (g in groups) {
                    val head = g.first() // самый «свежий» в группе
                    val key = "grp:${head.id}"
                    groupKeys.add(key)
                    groupMembers[key] = g.map { it.id }
                    if (!tapListeners.contains(key)) {
                        tapListeners[key] = MapObjectTapListener { _, _ ->
                            onGroupTap(groupMembers[key] ?: listOf(head.id)); true
                        }
                    }
                    val gp = groupPins.getOrPut(key) {
                        objs.addPlacemark().apply {
                            setIcon(
                                ImageProvider.fromBitmap(clusterBitmap(g.size)),
                                IconStyle().apply { scale = 0.6f },
                            )
                            addTapListener(WeakReference(tapListeners[key]))
                        }
                    }
                    gp.geometry = Point(head.current!!.lat, head.current!!.lon)
                }
                for (k in groupPins.keys - groupKeys) {
                    groupPins.remove(k)?.let { objs.remove(it) }
                    tapListeners.remove(k)
                    groupMembers.remove(k)
                }

                val me = devices.firstOrNull { it.id == myId && it.current != null }?.current
                if (!centered) {
                    val first = me ?: devices.firstOrNull { it.current != null }?.current
                    if (first != null) {
                        centered = true
                        mv.map.move(CameraPosition(Point(first.lat, first.lon), 14f, 0f, 0f))
                    }
                } else if (follow && me != null) {
                    mv.map.move(
                        CameraPosition(Point(me.lat, me.lon), mv.map.cameraPosition.zoom, 0f, 0f),
                        Animation(Animation.Type.SMOOTH, 0.5f),
                        null,
                    )
                }
            },
        )
    }
}

private fun labelFor(d: Repo.DeviceState, myId: String): String =
    if (d.id == myId) "Я" else d.name
