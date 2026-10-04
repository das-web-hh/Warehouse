@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Запуск камеры-сканера для окон, которым нужен свой обработчик кода.
 * Один код = один результат (диалог закрывается сразу после считывания),
 * как и в warehouse.html. Возвращает функцию «начать сканирование».
 */
@Composable
fun rememberScanner(onCode: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    val callback = rememberUpdatedState(onCode)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { locked = false; open = true }
    }
    if (open) {
        BarcodeScannerDialog(
            onDismiss = { open = false },
            onBarcodeScanned = { code ->
                if (!locked) {
                    locked = true
                    open = false
                    callback.value(code)
                }
            },
        )
    }
    return {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) { locked = false; open = true } else permission.launch(Manifest.permission.CAMERA)
    }
}

val OkGreen = Color(0xFF2E9E5B)
val WarnAmber = Color(0xFFE5A038)
val AlertRed = Color(0xFFE05252)
val InfoBlue = Color(0xFF3B82F6)

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) { content() }
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Text(value.ifBlank { "—" }, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
