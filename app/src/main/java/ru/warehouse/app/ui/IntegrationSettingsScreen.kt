@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import ru.warehouse.app.data.ServerClient

/** Настройки Python-сервера и Gemini (аналог раздела «Gemini / Python» в warehouse.html). */
@Composable
fun IntegrationSettingsScreen(extra: ExtraViewModel, modifier: Modifier = Modifier) {
    val s by extra.settings.collectAsState()
    var url by remember(s.pythonUrl) { mutableStateOf(s.pythonUrl) }
    var key by remember(s.geminiKey) { mutableStateOf(s.geminiKey) }
    var model by remember(s.geminiModel) { mutableStateOf(s.geminiModel) }
    var instruction by remember(s.geminiInstruction) { mutableStateOf(s.geminiInstruction) }
    var interval by remember(s.autoIntervalMinutes) { mutableStateOf(s.autoIntervalMinutes.toString()) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Python-сервер", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Адрес (server.py)") },
            singleLine = true, shape = RoundedCornerShape(16.dp))
        Text("Gemini", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API-ключ") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), shape = RoundedCornerShape(16.dp))
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Модель") },
            singleLine = true, shape = RoundedCornerShape(16.dp))
        OutlinedTextField(instruction, { instruction = it }, Modifier.fillMaxWidth(), label = { Text("Системная инструкция чата") },
            minLines = 3, maxLines = 6, shape = RoundedCornerShape(16.dp))
        Text("Автоприём", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(interval, { interval = it.filter(Char::isDigit).take(3) }, Modifier.fillMaxWidth(),
            label = { Text("Проверка папки, минут") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), shape = RoundedCornerShape(16.dp))

        Button(
            onClick = {
                extra.updateSettings {
                    it.copy(
                        pythonUrl = url.trim().trimEnd('/').ifBlank { it.pythonUrl },
                        geminiKey = key.trim(),
                        geminiModel = model.trim().ifBlank { it.geminiModel },
                        geminiInstruction = instruction,
                        autoIntervalMinutes = (interval.toIntOrNull() ?: 15).coerceIn(1, 240),
                    )
                }
                status = "Сохранено."
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить") }
        Button(
            onClick = {
                status = "Проверяю подключение…"
                val target = url.trim()
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { ServerClient.health(target) }
                    status = if (ok) "Python-сервер отвечает." else "Python-сервер недоступен: $target"
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Проверить подключение к серверу") }
        if (status.isNotBlank()) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
