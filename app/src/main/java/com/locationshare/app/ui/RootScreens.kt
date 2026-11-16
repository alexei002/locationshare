package com.locationshare.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.locationshare.app.R
import com.locationshare.app.data.Prefs

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasLocation(ctx)) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasLocation(ctx)
        // «Больше не спрашивать»: после отказа система не показывает диалог — надо вести в настройки
        permanentlyDenied = !granted &&
            (ctx as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) == false
    }
    val group by Prefs.groupCodeFlow.collectAsState()

    // После возврата из системных настроек разрешение могло быть выдано вручную
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = hasLocation(ctx)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    when {
        !granted -> PermissionsScreen(
            onRequest = {
                val perms = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                launcher.launch(perms.toTypedArray())
            },
            permanentlyDenied = permanentlyDenied,
        )
        group == null -> GroupScreen()
        else -> MainScreen()
    }
}

private fun hasLocation(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionsScreen(onRequest: () -> Unit, permanentlyDenied: Boolean = false) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.permissions_body))
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.permissions_battery_hint),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text(stringResource(R.string.btn_allow_location)) }
        if (permanentlyDenied) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.permissions_denied_body))
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { openAppSettings(ctx) }) { Text(stringResource(R.string.btn_open_settings)) }
        }
    }
}

private fun openAppSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:com.locationshare.app"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {}
}

@Composable
fun GroupScreen() {
    val ctx = LocalContext.current
    var code by remember { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        val parsed = Qr.parseJoinLink(result.contents)
        if (parsed != null) {
            Prefs.groupCode = parsed
        } else if (result.contents != null) {
            // Скан был, но QR чужой/битый — отмену (contents == null) молчим
            Toast.makeText(ctx, ctx.getString(R.string.toast_qr_invalid), Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.group_screen_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.group_screen_hint))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(10) },
            label = { Text(stringResource(R.string.group_code_label)) },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = code.length >= 4, onClick = { Prefs.groupCode = code.trim() }) {
                Text(stringResource(R.string.btn_join))
            }
            OutlinedButton(onClick = {
                val c = Prefs.newGroupCode()
                Prefs.groupCode = c
                // Создатель группы становится её администратором
                com.locationshare.app.data.Repo.claimAdmin(c, Prefs.deviceId)
            }) {
                Text(stringResource(R.string.btn_create_group))
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = {
            scanner.launch(
                com.journeyapps.barcodescanner.ScanOptions().apply {
                    setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                    setPrompt(ctx.getString(R.string.qr_scan_prompt))
                    setBeepEnabled(false)
                }
            )
        }) {
            Text(stringResource(R.string.btn_join_by_qr))
        }
    }
}
