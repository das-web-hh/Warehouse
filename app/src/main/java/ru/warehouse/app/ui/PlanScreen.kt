@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.warehouse.app.data.CheckResult
import ru.warehouse.app.data.CheckType
import ru.warehouse.app.data.WarehouseState

private const val STAGE_INPUT = 0
private const val STAGE_SCAN = 1
private const val STAGE_RESULT = 2

/** «Приём по заданию»: список → сканирование → результат сверки → сохранение с номером заказа. */
@Composable
fun PlanScreen(
    state: WarehouseState,
    extra: ExtraViewModel,
    warehouse: WarehouseViewModel,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var stage by rememberSaveable { mutableStateOf(STAGE_INPUT) }
    var text by rememberSaveable { mutableStateOf("") }
    val plan by extra.planItems.collectAsState()
    val scanned by extra.scanned.collectAsState()
    val status by extra.scanStatus.collectAsState()
    val results by extra.checkResults.collectAsState()
    val clipboard = LocalClipboardManager.current
    var orderDialog by remember { mutableStateOf(false) }
    var orderText by remember { mutableStateOf("") }
    var orderError by remember { mutableStateOf<String?>(null) }

    val scan = rememberScanner { code -> extra.addScan(code, state) }

    androidx.activity.compose.BackHandler(enabled = stage != STAGE_INPUT) {
        stage = if (stage == STAGE_RESULT) STAGE_SCAN else STAGE_INPUT
    }

    Column(modifier.fillMaxSize()) {
        when (stage) {
            STAGE_INPUT -> {
                Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it; extra.applyPlanText(it) },
                            modifier = Modifier.weight(1f).heightIn(min = 64.dp, max = 140.dp),
                            placeholder = { Text("Добавить задачу: название | кол-во | EAN") },
                            shape = RoundedCornerShape(16.dp),
                        )
                        IconButton(onClick = {
                            val pasted = clipboard.getText()?.text.orEmpty()
                            if (pasted.isNotBlank()) { text = pasted; extra.applyPlanText(pasted) }
                        }) { Icon(Icons.Default.ContentPaste, contentDescription = "Вставить из буфера") }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (plan.isEmpty()) {
                        EmptyState("Список пока пуст", "Вставьте список товаров из буфера обмена — по одному на строку.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(plan) { index, item ->
                                InfoCard {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (item.ean.isNotEmpty()) "Штрихкод: ${item.ean}" else "Штрихкод не указан",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Text("${item.quantity} шт.", fontWeight = FontWeight.Bold)
                                        IconButton(onClick = { extra.deletePlanItem(index) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Удалить строку")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        if (plan.isEmpty()) return@Button
                        stage = STAGE_SCAN
                        scan()
                    },
                    enabled = plan.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Сканировать товары")
                }
            }

            STAGE_SCAN -> {
                Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(scanned.keys.toList(), key = { it }) { code ->
                            val planned = plan.firstOrNull { ExtraViewModel.normalizeBarcode(it.ean) == code }
                            val local = ExtraViewModel.findByBarcode(state, code)
                            val name = local?.name ?: planned?.name ?: "Товар по штрихкоду"
                            InfoCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(name, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "Штрихкод: $code",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    QuantityStepper(
                                        value = (scanned[code] ?: 1).toDouble(),
                                        unit = "шт",
                                        onValueChange = { extra.setScanQty(code, it.toInt().coerceAtLeast(1)) },
                                    )
                                    IconButton(onClick = { extra.deleteScan(code) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить строку")
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(onClick = scan, modifier = Modifier.weight(1f).height(52.dp)) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Скан")
                    }
                    Button(
                        onClick = { extra.finishScanSession(state); stage = STAGE_RESULT },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { Text("Далее") }
                }
            }

            else -> {
                Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CheckType.values().forEach { type ->
                            val count = results.count { it.type == type }
                            Column(
                                Modifier.weight(1f)
                                    .background(colorFor(type).copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                                    .padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("$count", fontWeight = FontWeight.ExtraBold, color = colorFor(type))
                                Text(type.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (results.isEmpty()) {
                        EmptyState("Список для сверки пуст", "Вернитесь назад и добавьте товары.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(results) { r -> CheckRow(r) }
                        }
                    }
                }
                Button(
                    onClick = { orderText = ""; orderError = null; orderDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Сохранить")
                }
            }
        }
    }

    if (orderDialog) {
        AlertDialog(
            onDismissRequest = { orderDialog = false },
            title = { Text("Номер заказа") },
            text = {
                Column {
                    OutlinedTextField(
                        value = orderText,
                        onValueChange = { orderText = it; orderError = null },
                        singleLine = true,
                        placeholder = { Text("например 12345") },
                    )
                    orderError?.let { Text(it, color = AlertRed, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val error = extra.saveCheck(orderText, state, warehouse)
                    if (error == null) {
                        orderDialog = false
                        stage = STAGE_INPUT
                        text = ""
                    } else orderError = error
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { orderDialog = false }) { Text("Отмена") } },
        )
    }
}

private fun colorFor(type: CheckType): Color = when (type) {
    CheckType.MATCH -> OkGreen
    CheckType.MISMATCH -> AlertRed
    CheckType.EXTRA -> WarnAmber
    CheckType.LESS -> InfoBlue
}

@Composable
private fun CheckRow(r: CheckResult) {
    val color = colorFor(r.type)
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp)))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, fontWeight = FontWeight.SemiBold)
                Text(
                    r.code.ifBlank { r.planned?.ean?.takeIf { it.isNotBlank() } ?: "—" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("${r.actualQty}/${r.expectedQty}", fontWeight = FontWeight.ExtraBold, color = if (r.actualQty == 0) AlertRed else color)
        }
    }
}
