@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** «Документы»: список созданных документов, группировка по дням, просмотр, удаление, отправка текстом. */
@Composable
fun DocumentsScreen(extra: ExtraViewModel, modifier: Modifier = Modifier) {
    val docs by extra.documents.collectAsState()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val opened = docs.firstOrNull { it.id == openId }

    androidx.activity.compose.BackHandler(enabled = opened != null) { openId = null }

    if (opened != null) {
        Column(modifier.fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(opened.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${opened.date} · ${opened.time}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, opened.title)
                        putExtra(Intent.EXTRA_TEXT, opened.body)
                    }
                    context.startActivity(Intent.createChooser(send, "Отправить документ"))
                }) { Icon(Icons.Default.Share, contentDescription = "Отправить") }
            }
            InfoCard(Modifier.padding(top = 10.dp)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(opened.body.ifBlank { "Документ пуст." }, fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        return
    }

    if (docs.isEmpty()) {
        Column(modifier.fillMaxSize().padding(18.dp)) {
            EmptyState("Документов пока нет", "Здесь появятся документы, созданные при приёме товаров.")
        }
        return
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var last = ""
        docs.forEach { doc ->
            if (doc.date != last) {
                last = doc.date
                val label = dayLabel(doc.date)
                item(key = "h-${doc.date}") {
                    Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item(key = doc.id) {
                InfoCard(Modifier.clickable { openId = doc.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(doc.title, fontWeight = FontWeight.SemiBold)
                            Text(listOf(doc.subtitle, doc.time).filter(String::isNotBlank).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (confirmId == doc.id) {
                            TextButton(onClick = { extra.deleteDocument(doc.id); confirmId = null }) { Text("Удалить?") }
                        } else {
                            IconButton(onClick = { confirmId = doc.id }) { Icon(Icons.Default.Delete, contentDescription = "Удалить") }
                        }
                    }
                }
            }
        }
    }
}

private fun dayLabel(date: String): String {
    val fmt = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val now = Calendar.getInstance()
    val today = fmt.format(now.time)
    now.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = fmt.format(now.time)
    return when (date) {
        today -> "$date · Сегодня"
        yesterday -> "$date · Вчера"
        else -> date
    }
}
