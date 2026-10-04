@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * «Чат Gemini». Если в Настройках → Gemini указан ключ — запрос идёт напрямую в Gemini API,
 * иначе через Python-сервер (/gemini-chat). Подмены ответов нет: при ошибке показывается её текст.
 */
@Composable
fun GeminiChatScreen(extra: ExtraViewModel, modifier: Modifier = Modifier) {
    val messages by extra.chat.collectAsState()
    val sending by extra.chatSending.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = extra::clearChat, enabled = messages.isNotEmpty()) {
                Icon(Icons.Default.Delete, contentDescription = "Очистить чат")
            }
        }
        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                EmptyState("Чат с Gemini", "Задайте вопрос. Ключ API и модель задаются в разделе «Настройки».")
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    val mine = m.role == "user"
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Box(
                            Modifier.widthIn(max = 300.dp)
                                .background(
                                    if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(18.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                m.text,
                                color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (sending) {
                    item(key = "typing") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                            Text("  Gemini печатает…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение") },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
            )
            IconButton(
                onClick = { extra.sendChat(input); input = "" },
                enabled = input.isNotBlank() && !sending,
            ) { Icon(Icons.Default.Send, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}
