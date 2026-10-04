@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.warehouse.app.data.BatchStatus
import ru.warehouse.app.data.InvoiceBatch
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ═══════════ Хаб «Приём товаров» ═══════════
@Composable
fun ReceiveHubScreen(onOpen: (WarehouseScreen) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HubRow("Вручную", "Ввод позиций и количества вручную", Icons.Default.MoveToInbox, Color(0xFF7C8CF8)) {
            onOpen(WarehouseScreen.Receive)
        }
        HubRow("По накладной", "Распознавание PDF / фото накладной", Icons.Default.ReceiptLong, Color(0xFF52C4C1)) {
            onOpen(WarehouseScreen.ByInvoice)
        }
        HubRow("Автоприём", "Автоматическая обработка файлов из папки", Icons.Default.Bolt, Color(0xFFE5A038)) {
            onOpen(WarehouseScreen.Auto)
        }
    }
}

@Composable
private fun HubRow(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    InfoCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ═══════════ «По накладной» ═══════════
@Composable
fun ByInvoiceScreen(
    state: WarehouseState,
    extra: ExtraViewModel,
    warehouse: WarehouseViewModel,
    onHistory2: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val batches by extra.batches.collectAsState()
    val active = batches.filter { it.source == "manual" && it.status != BatchStatus.ACCEPTED }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) extra.enqueueUris(uris, "manual")
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                modifier = Modifier.weight(1f).height(50.dp),
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Файл накладной")
            }
            OutlinedButton(onClick = onHistory2, modifier = Modifier.height(50.dp)) {
                Icon(Icons.Default.History, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("История 2")
            }
        }
        BatchList(active, state, extra, warehouse, emptyHint = "Выберите PDF или фото накладной — или отправьте файл в приложение через «Поделиться».")
    }
}

// ═══════════ «Авто» ═══════════
@Composable
fun AutoReceiveScreen(
    state: WarehouseState,
    extra: ExtraViewModel,
    warehouse: WarehouseViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by extra.settings.collectAsState()
    val batches by extra.batches.collectAsState()
    val context = LocalContext.current
    val auto = batches.filter { it.source == "auto" && it.status != BatchStatus.ACCEPTED }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            extra.setAutoFolder(uri)
        }
    }

    // Проверка папки сразу и затем каждые N минут, пока окно открыто.
    LaunchedEffect(settings.autoFolderUri, settings.autoIntervalMinutes) {
        if (settings.autoFolderUri.isNotBlank()) {
            while (true) {
                extra.scanAutoFolder()
                delay(settings.autoIntervalMinutes * 60_000L)
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        // Выбор папки — вверху, сразу под ним счётчики.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f).height(50.dp)) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (settings.autoFolderUri.isBlank()) "Выбрать папку" else "Сменить папку", maxLines = 1)
            }
            IconButton(onClick = extra::scanAutoFolder, enabled = settings.autoFolderUri.isNotBlank()) {
                Icon(Icons.Default.Refresh, contentDescription = "Проверить сейчас")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Counter("Ожидают", batches.count { it.source == "auto" && it.status == BatchStatus.QUEUED }, Modifier.weight(1f))
            Counter("Обработка", batches.count { it.source == "auto" && it.status == BatchStatus.PROCESSING }, Modifier.weight(1f))
            Counter("Готово", batches.count { it.source == "auto" && it.status == BatchStatus.READY }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        BatchList(auto, state, extra, warehouse, emptyHint = "Выберите папку с накладными — новые PDF и фото будут обработаны автоматически.")
    }
}

/** Счётчик: подпись не обрезается и при пяти цифрах (число уменьшается, подпись остаётся целой). */
@Composable
private fun Counter(label: String, value: Int, modifier: Modifier = Modifier) {
    val text = value.toString()
    InfoCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text,
                fontSize = when {
                    text.length <= 2 -> 24.sp
                    text.length == 3 -> 20.sp
                    text.length == 4 -> 17.sp
                    else -> 14.sp
                },
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ═══════════ Список партий (общий для «По накладной» и «Авто») ═══════════
@Composable
private fun BatchList(
    batches: List<InvoiceBatch>,
    state: WarehouseState,
    extra: ExtraViewModel,
    warehouse: WarehouseViewModel,
    emptyHint: String,
) {
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    if (batches.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(18.dp)) { EmptyState("Реестр сканирований пуст", emptyHint) }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(batches, key = { it.id }) { b ->
            val expanded = expandedId == b.id
            InfoCard(Modifier.clickable { expandedId = if (expanded) null else b.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.sender.ifBlank { b.fileName }, fontWeight = FontWeight.SemiBold)
                        Text(
                            listOf(b.orderNumber, b.date, if (b.items.isNotEmpty()) "${b.items.size} поз." else "")
                                .filter(String::isNotBlank).joinToString(" · ").ifBlank { b.fileName },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    StatusChip(b.status)
                }
                if (b.status == BatchStatus.PROCESSING || b.status == BatchStatus.QUEUED) {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  ${b.status.label}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (b.error.isNotBlank()) {
                    Text(b.error, color = AlertRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    b.items.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                val known = (item.ean.isNotBlank() && ExtraViewModel.findByBarcode(state, item.ean) != null) ||
                                    state.products.any { it.name.trim().equals(item.name.trim(), true) }
                                Text(
                                    (item.ean.ifBlank { "без EAN" }) + if (known) "" else " · новый товар",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (known) MaterialTheme.colorScheme.onSurfaceVariant else WarnAmber,
                                )
                            }
                            QuantityStepper(item.quantity, item.unit, { extra.updateBatchQty(b.id, index, it) })
                            IconButton(onClick = { extra.removeBatchItem(b.id, index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Убрать позицию")
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (b.status == BatchStatus.READY) {
                        Button(onClick = { extra.acceptBatch(b.id, state, warehouse) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.size(6.dp))
                            Text("Принять")
                        }
                    }
                    OutlinedButton(onClick = { extra.deleteBatch(b.id) }) { Text("Удалить") }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(status: BatchStatus) {
    val color = when (status) {
        BatchStatus.READY -> WarnAmber
        BatchStatus.ACCEPTED -> OkGreen
        BatchStatus.ERROR -> AlertRed
        else -> InfoBlue
    }
    Text(
        status.label,
        color = color,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.background(color.copy(alpha = 0.14f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

// ═══════════ «История 2» ═══════════
@Composable
fun History2Screen(extra: ExtraViewModel, modifier: Modifier = Modifier) {
    val batches by extra.batches.collectAsState()
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = batches.firstOrNull { it.id == detailId }
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    if (batches.isEmpty()) {
        Box(modifier.fillMaxSize().padding(18.dp)) { EmptyState("Пока ничего нет", "Здесь появятся все обработанные накладные.") }
    } else {
        LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(batches, key = { it.id }) { b ->
                InfoCard(Modifier.clickable { detailId = b.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.sender.ifBlank { b.fileName }, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(b.orderNumber, dateFmt.format(Date(b.createdAt)), if (b.source == "auto") "авто" else "вручную")
                                    .filter(String::isNotBlank).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusChip(b.status)
                    }
                }
            }
        }
    }

    detail?.let { b ->
        AlertDialog(
            onDismissRequest = { detailId = null },
            title = { Text(b.sender.ifBlank { b.fileName }) },
            text = {
                Column {
                    KeyValueRow("Заказ", b.orderNumber)
                    KeyValueRow("Дата", b.date)
                    KeyValueRow("Файл", b.fileName)
                    KeyValueRow("Статус", b.status.label)
                    b.items.forEach {
                        Text("${it.name} — ${formatQuantity(it.quantity)} ${it.unit}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (b.error.isNotBlank()) Text(b.error, color = AlertRed, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { detailId = null }) { Text("Закрыть") } },
            dismissButton = { TextButton(onClick = { extra.deleteBatch(b.id); detailId = null }) { Text("Удалить") } },
        )
    }
}
