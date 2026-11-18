package com.locationshare.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationshare.app.R
import com.locationshare.app.data.Prefs
import com.locationshare.app.data.Repo
import com.locationshare.app.data.Updater
import com.locationshare.app.service.EcoWorker
import com.locationshare.app.service.LocationService
import com.locationshare.app.service.RingPlayer
import com.locationshare.app.service.WatchdogReceiver
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.ScreenPoint
import com.yandex.mapkit.ScreenRect
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.runtime.image.ImageProvider
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.MapObjectCollection
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.PolylineMapObject
import com.yandex.mapkit.map.TextStyle
import com.yandex.mapkit.mapview.MapView
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.tan
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val ctx = LocalContext.current
    val group = Prefs.groupCode ?: return
    val eco by Prefs.ecoFlow.collectAsState()
    var devices by remember { mutableStateOf<List<Repo.DeviceState>>(emptyList()) }
    var showSettings by remember { mutableStateOf(false) }
    var ringTarget by remember { mutableStateOf<Repo.DeviceState?>(null) }
    var ringingBy by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<Updater.Update?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<List<String>?>(null) }
    // Затухающий трек выбранного устройства (история точек из Firebase)
    var track by remember { mutableStateOf<List<Repo.Pt>>(emptyList()) }
    var focusTrigger by remember { mutableStateOf(0) }
    var focusId by remember { mutableStateOf<String?>(null) }
    var adminId by remember { mutableStateOf<String?>(null) }
    var fitTrigger by remember { mutableStateOf(0) }
    var centerTrigger by remember { mutableStateOf(0) }
    var follow by remember { mutableStateOf(true) }
    var deleteTarget by remember { mutableStateOf<Repo.DeviceState?>(null) }
    var tick by remember { mutableStateOf(0) }
    var onboardShown by remember { mutableStateOf(Prefs.onboardingShown) }
    val myId = Prefs.deviceId

    DisposableEffect(group) {
        val listener = Repo.observe(group) { devices = it }
        val adminL = Repo.observeAdmin(group) { adminId = it }
        onDispose {
            Repo.stop(group, listener)
            Repo.stopAdmin(group, adminL)
        }
    }

    // Трек слушаем, только пока устройство выбрано: тап по карте/крестику — отписка
    DisposableEffect(group, selectedId) {
        val id = selectedId
        if (id == null) {
            onDispose { }
        } else {
            val l = Repo.observeTrack(group, id) { track = it }
            onDispose {
                Repo.stopTrack(group, id, l)
                track = emptyList()
            }
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

    // Раз в день проверяем обновления: найдено — скачиваем и сами открываем установку.
    // Без права «неизвестные источники» автоустановка невозможна — показываем баннер
    // (кнопка «Установить» откроет системные настройки и установщик)
    LaunchedEffect(Unit) {
        val today = System.currentTimeMillis() / 86_400_000L
        if (Prefs.lastUpdateCheck != today) {
            Prefs.lastUpdateCheck = today
            val r = Updater.check()
            if (r is Updater.CheckResult.Found) {
                if (ctx.packageManager.canRequestPackageInstalls()) {
                    Updater.downloadAndInstall(ctx, r.u.url, r.u.tag)
                } else {
                    update = r.u
                }
            }
        }
    }

    // Тикер: раз в 30 сек пересобираем «обновлено N мин»
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }

    LaunchedEffect(eco) {
        if (!eco) LocationService.start(ctx)
        else LocationService.stop(ctx)
        // Watchdog-аларм: точка не реже чем раз в 10 мин, даже если систему убила сервис
        if (!eco) WatchdogReceiver.schedule(ctx)
        else WatchdogReceiver.cancel(ctx)
        if (eco) EcoWorker.schedule(ctx)
        else EcoWorker.cancel(ctx)
    }

    // Системный «Назад» закрывает настройки, а не всё приложение
    BackHandler(enabled = showSettings) { showSettings = false }

    val selected = devices.firstOrNull { it.id == selectedId }
    val selectedDevices = selectedGroup?.mapNotNull { id -> devices.firstOrNull { it.id == id } }
    // Стабильные цвета устройств без коллизий (hash из colorFor мог дублировать цвета)
    val colorMap = remember(devices) { colorMapFor(devices.map { it.id }, customColors(devices)) }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Баннер и карточки вынесены в лямбды: в портрете рендерим их в нижнем столбце
    // (как раньше), в ландшафте — у верхнего края, чтобы нижний стек не съедал экран
    val updateBanner: @Composable () -> Unit = {
        update?.let { u ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 14.dp),
                ) {
                    Text("Доступна версия ${u.version}", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = {
                        Updater.downloadAndInstall(ctx, u.url, u.tag); update = null
                    }) { Text(stringResource(R.string.btn_install)) }
                    TextButton(onClick = { update = null }) { Text(stringResource(R.string.btn_later)) }
                }
            }
        }
    }

    val groupCard: @Composable () -> Unit = {
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
                        IconButton(onClick = { selectedGroup = null }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), modifier = Modifier.size(18.dp))
                        }
                    }
                    // Список ограничен по высоте: 6+ устройств не должны съедать экран
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        for (d in selectedDevices) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedId = d.id; selectedGroup = null; focusId = d.id; focusTrigger++ }
                                    .padding(vertical = 4.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .background(androidx.compose.ui.graphics.Color(colorMap[d.id] ?: colorFor(d.id)), CircleShape)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        d.name + if (d.id == myId) " (Вы)" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    d.current?.let {
                                        tick // тикер: пересчитать «обновлено»
                                        Text(
                                            "обновлено ${timeAgo(it.ts)} · точность ±${d.accuracy.toInt()} м",
                                            style = MaterialTheme.typography.bodySmall,
                                            // Молчит дольше 10 мин — красным: устройство устарело
                                            color = if (System.currentTimeMillis() - it.ts > WatchdogReceiver.INTERVAL_MS) MaterialTheme.colorScheme.error
                                            else Color.Unspecified,
                                        )
                                    }
                                }
                                d.battery?.let { Text("$it%", style = MaterialTheme.typography.bodySmall) }
                                if (d.id != myId) {
                                    IconButton(onClick = { ringTarget = d }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.cd_ring), modifier = Modifier.size(20.dp))
                                    }
                                    if (adminId == myId) {
                                        IconButton(onClick = { deleteTarget = d }, modifier = Modifier.size(40.dp)) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = stringResource(R.string.cd_remove_from_group),
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val deviceCard: @Composable () -> Unit = {
        if (selectedDevices.isNullOrEmpty()) selected?.let { d ->
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
                                .background(androidx.compose.ui.graphics.Color(colorMap[d.id] ?: colorFor(d.id)), CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            d.name + if (d.id == myId) " (Вы)" else "",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        d.battery?.let { Text("$it%", style = MaterialTheme.typography.bodyMedium) }
                        IconButton(onClick = { selectedId = null }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), modifier = Modifier.size(18.dp))
                        }
                    }
                    d.current?.let {
                        tick // тикер: пересчитать «обновлено»
                        Text(
                            "обновлено ${timeAgo(it.ts)} · точность ±${d.accuracy.toInt()} м",
                            style = MaterialTheme.typography.bodySmall,
                            // Молчит дольше 10 мин — красным: устройство устарело
                            color = if (System.currentTimeMillis() - it.ts > WatchdogReceiver.INTERVAL_MS) MaterialTheme.colorScheme.error
                            else Color.Unspecified,
                        )
                    }
                    if (d.id != myId) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp),
                        ) {
                            OutlinedButton(onClick = { ringTarget = d }) { Text(stringResource(R.string.btn_ring)) }
                            if (adminId == myId) {
                                TextButton(onClick = { deleteTarget = d }) {
                                    Text(stringResource(R.string.btn_remove_from_group), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

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
            follow = follow,
            onFollowChange = { follow = it },
            selectedId = selectedId,
            track = track,
        )

        if (landscape) {
            // Ландшафт: баннер и карточки — у верхнего края, внизу только кнопки и чипы
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                updateBanner()
                groupCard()
                deviceCard()
            }
        } else {
            // Портрет: баннер обновления сверху
            Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)) {
                updateBanner()
            }
        }

        // Нижний столбец: (портрет: карточки →) кнопки → панель чипов (не перекрываются)
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!landscape) {
                groupCard()
                deviceCard()
            }

            // Кнопки карты: «Все» и «центрировать на мне»
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
            ) {
                val showAllCd = stringResource(R.string.cd_show_all_devices)
                Surface(
                    onClick = { fitTrigger++ },
                    shape = CircleShape,
                    tonalElevation = 3.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = showAllCd },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.btn_all), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(10.dp))
                // В режиме следования кнопка выделена (дефолт FAB — primaryContainer)
                FloatingActionButton(
                    onClick = { centerTrigger++ },
                    containerColor = if (follow) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (follow) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.cd_center_on_me))
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
                        if (devices.isEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.chip_waiting_for_points),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                )
                            }
                        }
                        items(devices, key = { it.id }) { d ->
                            // Тап по чипу — открыть карточку, долгий тап — прозвон
                            DeviceChip(
                                d,
                                color = colorMap[d.id] ?: colorFor(d.id),
                                tick = tick,
                                onClick = { selectedId = d.id; selectedGroup = null },
                                onLongClick = { ringTarget = d },
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.cd_settings))
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
            title = { Text(stringResource(R.string.dialog_ring_title)) },
            text = {
                Text("Прозвонить «${if (t.id == Prefs.deviceId) "Вы" else t.name}» громким сигналом?")
            },
            confirmButton = {
                TextButton(onClick = {
                    Repo.ring(group, t.id, Prefs.deviceName)
                    Toast.makeText(ctx, ctx.getString(R.string.toast_command_sent), Toast.LENGTH_SHORT).show()
                    ringTarget = null
                }) {
                    Text(stringResource(R.string.btn_ring))
                }
            },
            dismissButton = {
                TextButton(onClick = { ringTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    // Меня прозвонили: кто ищет + кнопки «Нашёл»/«Заглушить»
    if (ringingBy != null) {
        val by = ringingBy ?: ""
        AlertDialog(
            onDismissRequest = { RingPlayer.stop(ctx); ringingBy = null },
            title = { Text(if (by.isNotBlank()) "«$by» ищет ваш телефон" else stringResource(R.string.dialog_phone_searched_title)) },
            text = { Text(stringResource(R.string.dialog_ringing_body)) },
            confirmButton = {
                TextButton(onClick = { RingPlayer.stop(ctx); ringingBy = null }) {
                    Text(stringResource(R.string.btn_found))
                }
            },
            dismissButton = {
                TextButton(onClick = { RingPlayer.stop(ctx); ringingBy = null }) { Text(stringResource(R.string.btn_mute)) }
            },
        )
    }

    // Подтверждение удаления устройства из группы
    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_device_title)) },
            text = { Text("Удалить «${t.name}» из группы? Ему придётся присоединяться заново.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.removeDevice(group, t.id)
                    Toast.makeText(ctx, ctx.getString(R.string.toast_device_removed), Toast.LENGTH_SHORT).show()
                    selectedId = null
                    deleteTarget = null
                }) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    // Онбординг после входа в группу: один раз предлагаем позвать близких по QR
    if (!onboardShown && devices.isNotEmpty() && devices.size <= 1) {
        AlertDialog(
            onDismissRequest = { onboardShown = true; Prefs.onboardingShown = true },
            title = { Text(stringResource(R.string.dialog_invite_title)) },
            text = { Text(stringResource(R.string.dialog_invite_body)) },
            confirmButton = {
                TextButton(onClick = { onboardShown = true; Prefs.onboardingShown = true }) { Text(stringResource(R.string.btn_got_it)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    onClose: () -> Unit,
    adminId: String?,
    devices: List<Repo.DeviceState>,
) {
    val eco by Prefs.ecoFlow.collectAsState()
    val group = Prefs.groupCode ?: ""
    var showPerms by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var updState by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var delTarget by remember { mutableStateOf<Repo.DeviceState?>(null) }
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
                    stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).padding(top = 12.dp),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close))
                }
            }
            ToggleRow(
                title = stringResource(R.string.settings_eco_title),
                subtitle = stringResource(R.string.settings_eco_subtitle),
                checked = eco,
            ) { Prefs.eco = it }
            var name by remember { mutableStateOf(Prefs.deviceName) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; Prefs.deviceName = it },
                label = { Text(stringResource(R.string.settings_device_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            // Администрирование группы
            Text(
                stringResource(R.string.settings_admin_section),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 20.dp),
            )
            when {
                adminId == null -> {
                    Text(stringResource(R.string.settings_admin_none), fontSize = 12.sp)
                    OutlinedButton(onClick = { Repo.claimAdmin(group, myId) }) {
                        Text(stringResource(R.string.btn_become_admin))
                    }
                }
                isAdmin -> Text(stringResource(R.string.settings_admin_you), fontSize = 12.sp)
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
                            TextButton(onClick = { delTarget = d }) {
                                Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text(
                    "Группа: $group",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    copyToClipboard(ctx, group)
                    Toast.makeText(ctx, ctx.getString(R.string.toast_code_copied), Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.btn_copy)) }
            }
            // FlowRow: на узких экранах кнопки переносятся на следующую строку,
            // а не сплющиваются в вытянутую «пустую» кнопку (артефакт на маленьких экранах)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(onClick = { showQr = true }) { Text(stringResource(R.string.btn_qr_group)) }
                OutlinedButton(onClick = { confirmLeave = true }) {
                    Text(stringResource(R.string.btn_change_group))
                }
                OutlinedButton(onClick = { showPerms = true }) { Text(stringResource(R.string.btn_permissions)) }
            }

            // Индикатор «Всё время»: без фонового разрешения трекер после закрытия
            // приложения теряет GPS (FGS из фона — без while-in-use доступа).
            // Обновляем по ON_RESUME: пермишен выдаётся в системных настройках
            var bgLocOk by remember { mutableStateOf(hasBackgroundLocation(ctx)) }
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val obs = LifecycleEventObserver { _, e ->
                    if (e == Lifecycle.Event.ON_RESUME) bgLocOk = hasBackgroundLocation(ctx)
                }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs) }
            }
            if (!bgLocOk) {
                Text(
                    stringResource(R.string.settings_bgloc_missing),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(onClick = { DeviceIntents.openLocationPermission(ctx) }) {
                    Text(stringResource(R.string.btn_grant_always))
                }
            }

            // Обновления
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 16.dp),
            ) {
                OutlinedButton(onClick = {
                    updState = ctx.getString(R.string.update_state_checking)
                    scope.launch {
                        val r = Updater.check()
                        updState = when (r) {
                            is Updater.CheckResult.Found -> {
                                Updater.downloadAndInstall(ctx, r.u.url, r.u.tag)
                                "скачивается ${r.u.version}…"
                            }
                            Updater.CheckResult.UpToDate -> ctx.getString(R.string.update_state_up_to_date)
                            Updater.CheckResult.NotConfigured -> ctx.getString(R.string.update_state_not_configured)
                            is Updater.CheckResult.Error -> "не удалось: ${r.msg}"
                        }
                    }
                }) { Text(stringResource(R.string.btn_check_updates)) }
                updState?.let {
                    // weight(1f): длинный текст ошибки («не удалось: …») переносится,
                    // а не выталкивает кнопку за экран
                    Text(
                        it,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
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
                Text(stringResource(R.string.btn_open_release_page), fontSize = 12.sp)
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
            confirmButton = { TextButton(onClick = { showQr = false }) { Text(stringResource(R.string.btn_close)) } },
            title = { Text("QR группы $group") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = Qr.bitmap(Qr.link(group)).asImageBitmap(),
                        contentDescription = stringResource(R.string.cd_qr_group),
                        modifier = Modifier.size(280.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.dialog_qr_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
    }
    if (showPerms) PermissionsDialog { showPerms = false }

    // Подтверждение выхода из группы: код копируем в буфер, иначе обратно не попасть
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.dialog_leave_title)) },
            text = { Text("Код группы: $group. Без кода вы не сможете вернуться в неё.") },
            confirmButton = {
                TextButton(onClick = {
                    copyToClipboard(ctx, group)
                    confirmLeave = false
                    onClose()
                    Prefs.groupCode = null
                }) { Text(stringResource(R.string.btn_copy_and_leave)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    // Подтверждение удаления устройства из списка в настройках
    delTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { delTarget = null },
            title = { Text(stringResource(R.string.dialog_delete_device_title)) },
            text = { Text("Удалить «${t.name}» из группы? Ему придётся присоединяться заново.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.removeDevice(group, t.id)
                    Toast.makeText(ctx, ctx.getString(R.string.toast_device_removed), Toast.LENGTH_SHORT).show()
                    delTarget = null
                }) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { delTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
}

// Копирует код группы в буфер обмена
private fun copyToClipboard(ctx: Context, group: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("group", group))
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
private fun DeviceChip(d: Repo.DeviceState, color: Int, tick: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    val me = d.id == Prefs.deviceId
    Surface(
        shape = RoundedCornerShape(50),
        color = if (d.online) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .alpha(if (d.online) 1f else 0.5f),
        ) {
            Box(Modifier.size(10.dp).background(androidx.compose.ui.graphics.Color(color), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (me) "Вы" else d.name, style = MaterialTheme.typography.bodySmall)
            d.current?.let {
                tick // тикер: пересчитать «N мин» (и цвет устаревания)
                Text(
                    "  ${timeAgo(it.ts)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (System.currentTimeMillis() - it.ts > WatchdogReceiver.INTERVAL_MS) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.outline,
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

// Разрешения: последовательный мастер — ui/PermissionsDialog.kt
// (вызов из настроек и автозапрос из AppRoot после первой выдачи локации)

// ---------- Карта ----------
// colorFor/timeAgo/PALETTE вынесены в Format.kt (тот же пакет)

/**
 * Прозрачность сегмента трека по возрасту точки: свежая — непрозрачная,
 * у края окна истории — притушенная, но не ниже пола 0.5 (трек видно целиком).
 * Квантование в 14 ступеней — чтобы соседние точки сливались в одну полилинию.
 */
internal fun trackAlpha(ageMs: Long, windowMs: Long = Repo.HIST_WINDOW_MS): Int {
    val t = (1f - ageMs.toFloat() / windowMs).coerceIn(0f, 1f)
    val q = (t * 14f).toInt() / 14f
    return (255f * (0.5f + 0.5f * q)).toInt().coerceIn(0, 255)
}

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
    follow: Boolean = true,
    onFollowChange: (Boolean) -> Unit = {},
    selectedId: String? = null,
    track: List<Repo.Pt> = emptyList(),
) {
    val myId = Prefs.deviceId
    var centered by remember { mutableStateOf(false) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    val placemarks = remember { mutableStateMapOf<String, PlacemarkMapObject>() }
    // Затухающий трек: слой под пинами + кэш-ключ отрисованного (не перестраиваем на каждый recompose)
    var trackLayer by remember { mutableStateOf<MapObjectCollection?>(null) }
    var trackRenderedKey by remember { mutableStateOf<String?>(null) }
    val trackLines = remember { mutableListOf<PolylineMapObject>() }
    // Текущий цвет пина: при сдвиге палитры (colorMapFor) перерисовываем иконку
    val pinColors = remember { mutableStateMapOf<String, Int>() }
    // Показана ли подпись у пина (и с какой темой карты): кэш, чтобы не звать setText лишний раз
    val labelShown = remember { mutableStateMapOf<String, Pair<Boolean, Boolean>>() }
    // Схлопнутые группы: ключ «grp:<id первого>» → общий кружок с числом
    val groupPins = remember { mutableStateMapOf<String, PlacemarkMapObject>() }
    val groupMembers = remember { mutableStateMapOf<String, List<String>>() }
    val tapListeners = remember { mutableStateMapOf<String, MapObjectTapListener>() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val devicesNow by rememberUpdatedState(devices)
    val darkTheme = isSystemInDarkTheme()
    // Стабильные цвета устройств без коллизий
    val colorMap = remember(devices) { colorMapFor(devices.map { it.id }, customColors(devices)) }

    // Стили подписей пинов (объекты переиспользуются, в update не создаём).
    // Текст с контрастной обводкой: белым по тёмной карте, тёмным по светлой.
    val labelStyleDark = remember {
        TextStyle().apply {
            size = 12f
            color = 0xFFFFFFFF.toInt()
            outlineColor = 0xFF000000.toInt()
            outlineWidth = 2f
            placement = TextStyle.Placement.BOTTOM
            offsetFromIcon = true
        }
    }
    val labelStyleLight = remember {
        TextStyle().apply {
            size = 12f
            color = 0xFF212121.toInt()
            outlineColor = 0xFFFFFFFF.toInt()
            outlineWidth = 2f
            placement = TextStyle.Placement.BOTTOM
            offsetFromIcon = true
        }
    }

    // Держим сильные ссылки: MapKit принимает WeakReference
    val inputListener = remember {
        object : InputListener {
            override fun onMapTap(m: com.yandex.mapkit.map.Map, p: Point) {
                onMapTap()
            }

            override fun onMapLongTap(m: com.yandex.mapkit.map.Map, p: Point) {}
        }
    }

    // Пользователь сам двигает камеру — выключаем следование
    // (GESTURES = жест пользователя, программные move приходят как APPLICATION)
    val setFollow by rememberUpdatedState(onFollowChange)
    // Подписи пинов зависят от зума, а жесты зума не вызывают рекомпозицию:
    // тикаем zoomTick при изменившемся зуме, update-блок его читает и пересчитывает подписи.
    // По позиции не тикаем — иначе follow-режим (программные move при том же зуме) зациклит.
    var lastZoom by remember { mutableStateOf(Float.MIN_VALUE) }
    var zoomTick by remember { mutableStateOf(0) }
    val cameraListener = remember {
        object : CameraListener {
            override fun onCameraPositionChanged(
                m: com.yandex.mapkit.map.Map,
                p: CameraPosition,
                reason: CameraUpdateReason,
                finished: Boolean,
            ) {
                if (finished && reason == CameraUpdateReason.GESTURES) setFollow(false)
                if (finished && abs(p.zoom - lastZoom) > 0.01f) {
                    lastZoom = p.zoom
                    zoomTick++
                }
            }
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
        onFollowChange(false)
        val minLat = pts.minOf { it.lat }; val maxLat = pts.maxOf { it.lat }
        val minLon = pts.minOf { it.lon }; val maxLon = pts.maxOf { it.lon }
        // Зум считает сам MapKit: ручная формула завышала зум, т.к. width/height —
        // физические пиксели, а шкала зума MapKit определена в dp (расхождение ~log2(density))
        val bb = BoundingBox(Point(minLat, minLon), Point(maxLat, maxLon))
        // ScreenRect — в физических пикселях; снизу поле больше: там карточки и панель чипов
        val w = mv.width.toFloat()
        val h = mv.height.toFloat()
        val rect = ScreenRect(
            ScreenPoint(w * 0.12f, h * 0.10f),
            ScreenPoint(w * 0.88f, h * 0.82f),
        )
        val pos = mv.map.cameraPosition(Geometry.fromBoundingBox(bb), rect)
        mv.map.move(
            CameraPosition(pos.target, pos.zoom.coerceIn(3f, 18f), 0f, 0f),
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
            onFollowChange(true)
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
        onFollowChange(false)
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
                    map.addCameraListener(WeakReference(cameraListener))
                }
            },
            update = { mv ->
                mapView = mv
                zoomTick // зум изменился жестами без рекомпозиции — пересчитать подписи пинов
                // Ночная тема карты вслед за системной (MapKit 4.45: setNightModeEnabled)
                if (mv.map.isNightModeEnabled != darkTheme) mv.map.setNightModeEnabled(darkTheme)
                val objs = mv.map.mapObjects

                // --- Затухающий трек выбранного устройства ---
                // Слой создаём до пинов: добавленный раньше — рисуется ниже.
                val layer = trackLayer ?: objs.addCollection().also { trackLayer = it }
                val owner = selectedId
                val cur = owner?.let { o -> devices.firstOrNull { it.id == o }?.current }
                val trackKey = if (owner != null && cur != null && track.size >= 2)
                    "$owner|${track.size}|${track.last().ts}|${cur.ts}|${colorMap[owner]}" else null
                if (trackKey != trackRenderedKey) {
                    trackRenderedKey = trackKey
                    trackLines.forEach { layer.remove(it) }
                    trackLines.clear()
                    if (trackKey != null && owner != null && cur != null) {
                        val now = System.currentTimeMillis()
                        val color = colorMap[owner] ?: colorFor(owner)
                        // Возраст точки → прозрачность: старый хвост тусклее, но не ниже пола 0.5 —
                        // трек читается целиком. Квантование в 14 ступеней: соседние точки
                        // одной прозрачности сливаем в одну полилинию (см. trackAlpha).
                        fun alphaOf(ts: Long): Int = trackAlpha(now - ts)
                        // Последняя точка трека может отставать от current — дотягиваем до пина
                        val ptsAll: List<Pair<Point, Int>> = buildList {
                            track.forEach { add(Point(it.lat, it.lon) to alphaOf(it.ts)) }
                            add(Point(cur.lat, cur.lon) to alphaOf(cur.ts))
                        }
                        var start = 0
                        while (start < ptsAll.size - 1) {
                            val a = ptsAll[start].second
                            var end = start + 1
                            while (end < ptsAll.size && ptsAll[end].second == a) end++
                            if (a > 0) { // страховка: сегмент с нулевой прозрачностью не рисуем
                                val lastIdx = min(end, ptsAll.size - 1)
                                val line = layer.addPolyline(
                                    Polyline((start..lastIdx).map { ptsAll[it].first })
                                )
                                line.setStrokeColor((a shl 24) or (color and 0xFFFFFF))
                                line.setStrokeWidth(3f + 2f * (a / 255f))
                                trackLines.add(line)
                            }
                            start = end
                        }
                    }
                }

                val zoom = mv.map.cameraPosition.zoom
                val seen = HashSet<String>()
                for (d in devices) {
                    seen.add(d.id)
                    val color = colorMap[d.id] ?: colorFor(d.id)
                    if (!tapListeners.contains(d.id)) {
                        tapListeners[d.id] = MapObjectTapListener { _, _ ->
                            onMarkerTap(d.id); true
                        }
                    }
                    val pl = placemarks.getOrPut(d.id) {
                        pinColors[d.id] = color
                        objs.addPlacemark().apply {
                            setIcon(
                                ImageProvider.fromBitmap(pinBitmap(labelFor(d, myId), color)),
                                IconStyle().apply { scale = 0.55f },
                            )
                            addTapListener(WeakReference(tapListeners[d.id]))
                        }
                    }
                    if (pinColors[d.id] != color) {
                        pinColors[d.id] = color
                        pl.setIcon(
                            ImageProvider.fromBitmap(pinBitmap(labelFor(d, myId), color)),
                            IconStyle().apply { scale = 0.55f },
                        )
                    }
                    d.current?.let { pl.geometry = Point(it.lat, it.lon) }
                    // Подпись с именем под пином: при близком зуме, малом числе устройств
                    // или для выбранного. setText("") прячет, повторы отсекает кэш labelShown.
                    val showLabel = zoom >= 14f || devices.size <= 4 || d.id == selectedId
                    val cur = labelShown[d.id]
                    if (cur == null || cur.first != showLabel || cur.second != darkTheme) {
                        pl.setText(
                            if (showLabel) labelFor(d, myId) else "",
                            if (darkTheme) labelStyleDark else labelStyleLight,
                        )
                        labelShown[d.id] = showLabel to darkTheme
                    }
                }
                for (k in placemarks.keys - seen) {
                    placemarks.remove(k)?.let { objs.remove(it) }
                    pinColors.remove(k)
                    labelShown.remove(k)
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
                // Выбранное устройство не сворачиваем в кластер: его пин должен быть виден
                val sorted = withPoints.filter { it.id != selectedId }.sortedByDescending { it.current?.ts ?: 0L }
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
                // одиночные — видимы, в группе — скрыты; выбранное устройство всегда видно
                // (иначе трек выбранного из кластера рисуется без пина)
                for (d in withPoints) {
                    placemarks[d.id]?.setVisible(d.id !in groupedIds || d.id == selectedId)
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
