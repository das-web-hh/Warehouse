@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.warehouse.app.data.Receipt
import ru.warehouse.app.data.WarehouseState

@Composable
fun HistoryScreen(
    state: WarehouseState,
    onDeleteReceipt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteCandidate by remember { mutableStateOf<Receipt?>(null) }
    if (deleteCandidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Удалить партию?") },
            text = { Text("Количество этой приёмки будет вычтено из остатка товара.") },
            confirmButton = {
                Button(onClick = {
                    deleteCandidate?.let { onDeleteReceipt(it.id) }
                    deleteCandidate = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Отмена") } },
        )
    }
    val receipts = state.receipts.sortedByDescending { it.createdAt }
    if (receipts.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EmptyState("История пуста", "Здесь будут сохранённые партии приёмки.")
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Всего партий: ${receipts.size}",
                    modifier = Modifier.padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(receipts, key = { it.id }) { receipt ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    if (receipt.isBware) "B-Ware · ${receipt.date}" else "Приёмка · ${receipt.date}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    listOf(receipt.supplier, receipt.orderNumber).filter(String::isNotBlank).joinToString(" · ")
                                        .ifBlank { "Без поставщика и номера заказа" },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            IconButton(onClick = { deleteCandidate = receipt }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить партию", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        receipt.lines.forEach { line ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(line.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Text("${formatQuantity(line.quantity)} ${line.unit}", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Text(
                            "${receipt.lines.size} позиций · ${formatQuantity(receipt.lines.sumOf { it.quantity })} ед.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TasksScreen(
    state: WarehouseState,
    viewModel: WarehouseViewModel,
    modifier: Modifier = Modifier,
) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Новая задача") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true)
                    OutlinedTextField(description, { description = it }, label = { Text("Описание") }, minLines = 2)
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addTask(title, description)
                    title = ""
                    description = ""
                    showAdd = false
                }) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Рабочие задачи", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${state.tasks.count { !it.done }} активных · ${state.tasks.count { it.done }} выполнено")
                    }
                }
            }
        }
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Добавить задачу")
            }
        }
        if (state.tasks.isEmpty()) {
            item { EmptyState("Задач пока нет", "Создайте рабочую задачу, чтобы отметить её по завершении.") }
        } else {
            items(state.tasks.sortedWith(compareBy({ it.done }, { -it.createdAt })), key = { it.id }) { task ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = task.done, onCheckedChange = { viewModel.toggleTask(task.id) })
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(task.title, fontWeight = FontWeight.SemiBold)
                            if (task.description.isNotBlank()) {
                                Text(task.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                            if (task.done) {
                                Text("Выполнено", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(onClick = { viewModel.deleteTask(task.id) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Удалить задачу", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    state: WarehouseState,
    onThemeChange: (Boolean) -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier,
    onIntegrations: () -> Unit = {},
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("Внешний вид", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Тёмная тема")
                            Text("Сохраняется на этом устройстве.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.darkTheme, onCheckedChange = onThemeChange)
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Данные на устройстве", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("${state.products.size} товаров · ${state.receipts.size} партий · ${state.locations.size} адресов")
                    }
                    Text(
                        "Каталог, партии, инвентаризация и задачи хранятся локально. Создавайте резервную копию перед переносом на другое устройство.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onTransfer, modifier = Modifier.fillMaxWidth()) {
                        Text("Резервная копия и перенос")
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Gemini и сервер", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Адрес Python-сервера, ключ и модель Gemini, интервал проверки папки автоприёма.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onIntegrations, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть настройки")
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Warehouse", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Android · Kotlin · Jetpack Compose", color = MaterialTheme.colorScheme.primary)
                    Text("Локальная версия складского приложения. Версия 1.0.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}