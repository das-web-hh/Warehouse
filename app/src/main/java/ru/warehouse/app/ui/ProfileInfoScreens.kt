@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.warehouse.app.data.WarehouseState
import java.text.SimpleDateFormat
import java.util.Locale

/** «Профиль»: локальные данные пользователя (без Firebase-входа). */
@Composable
fun ProfileScreen(extra: ExtraViewModel, modifier: Modifier = Modifier) {
    val s by extra.settings.collectAsState()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(s.firstName, { v -> extra.updateSettings { it.copy(firstName = v) } },
                modifier = Modifier.fillMaxWidth(), label = { Text("Имя") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        }
        item {
            OutlinedTextField(s.lastName, { v -> extra.updateSettings { it.copy(lastName = v) } },
                modifier = Modifier.fillMaxWidth(), label = { Text("Фамилия") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        }
        item {
            OutlinedTextField(s.email, { v -> extra.updateSettings { it.copy(email = v) } },
                modifier = Modifier.fillMaxWidth(), label = { Text("Эмайл / логин") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        }
        item {
            InfoCard {
                KeyValueRow("ID аккаунта", s.accountId)
            }
        }
        item {
            Text(
                "Профиль хранится только на этом устройстве. Вход через Firebase в нативной версии пока не подключён.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** «Инфо»: сводка по каталогу и приёмкам. */
@Composable
fun InfoStatsScreen(state: WarehouseState, modifier: Modifier = Modifier) {
    val perDay = state.receipts.groupBy { it.date.ifBlank { "—" } }.map { (date, list) ->
        Triple(date, list.sumOf { it.lines.size }, list.sumOf { r -> r.lines.sumOf { it.quantity } })
    }.sortedByDescending { dayKey(it.first) }
    val totalQty = state.receipts.sumOf { r -> r.lines.sumOf { it.quantity } }
    val approxBytes = runCatching {
        state.products.size * 160L + state.receipts.sumOf { 120L + it.lines.size * 140L }
    }.getOrDefault(0L)

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionLabel("Общая сводка") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Товаров в каталоге", state.products.size.toString(), Modifier.weight(1f))
                StatCard("Всего приёмок", state.receipts.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Всего принято (шт.)", formatQuantity(totalQty), Modifier.weight(1f))
                StatCard("Дней с приёмками", perDay.size.toString(), Modifier.weight(1f))
            }
        }
        item { SectionLabel("Данные") }
        item {
            InfoCard {
                KeyValueRow("Каталог + история (≈)", formatBytes(approxBytes))
                KeyValueRow("Адресов инвентаризации", state.locations.size.toString())
                KeyValueRow("Задач", state.tasks.size.toString())
            }
        }
        item { SectionLabel("По дням") }
        if (perDay.isEmpty()) {
            item { EmptyState("Нет данных", "Данные о приёмках появятся после первой партии.") }
        } else {
            items(perDay, key = { it.first }) { (date, positions, qty) ->
                InfoCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(date, fontWeight = FontWeight.Bold)
                            Text("$positions позиц. · ${formatQuantity(qty)} шт.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${formatQuantity(qty)} шт.", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
    }
}

private fun dayKey(date: String): Long = runCatching {
    val pattern = if (date.length <= 8) "dd.MM.yy" else "dd.MM.yyyy"
    SimpleDateFormat(pattern, Locale.getDefault()).parse(date)?.time ?: 0L
}.getOrDefault(0L)

private fun formatBytes(b: Long): String = when {
    b <= 0 -> "0 Б"
    b < 1024 -> "$b Б"
    b < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f КБ", b / 1024.0)
    else -> String.format(Locale.getDefault(), "%.2f МБ", b / (1024.0 * 1024.0))
}
