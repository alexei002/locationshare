package com.locationshare.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.locationshare.app.data.Prefs

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasLocation(ctx)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasLocation(ctx)
    }
    val group by Prefs.groupCodeFlow.collectAsState()

    when {
        !granted -> PermissionsScreen {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            launcher.launch(perms.toTypedArray())
        }
        group == null -> GroupScreen()
        else -> MainScreen()
    }
}

private fun hasLocation(ctx: Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionsScreen(onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("LocationShare", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Показывает местоположение вашей группы на Яндекс.Картах. Нужен доступ к геолокации — только для этого.")
        Spacer(Modifier.height(8.dp))
        Text(
            "Батарея: GPS используется по минимуму — активнее только пока открыта карта.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) { Text("Разрешить геолокацию") }
    }
}

@Composable
fun GroupScreen() {
    var code by remember { mutableStateOf("") }
    val scanner = rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract()
    ) { result -> Qr.parseJoinLink(result.contents)?.let { Prefs.groupCode = it } }

    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Группа", style = MaterialTheme.typography.headlineMedium)
        Text("Введите один и тот же код на всех устройствах, чтобы видеть друг друга.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(10) },
            label = { Text("Код группы") },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = code.length >= 4, onClick = { Prefs.groupCode = code.trim() }) {
                Text("Войти")
            }
            OutlinedButton(onClick = { Prefs.groupCode = Prefs.newGroupCode() }) {
                Text("Создать группу")
            }
        }
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = {
            scanner.launch(
                com.journeyapps.barcodescanner.ScanOptions().apply {
                    setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                    setPrompt("Наведите камеру на QR-код группы")
                    setBeepEnabled(false)
                }
            )
        }) {
            Text("📷  Присоединиться по QR")
        }
    }
}
