package ru.warehouse.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.warehouse.app.data.WarehouseState
import ru.warehouse.app.data.WarehouseTransfer
import ru.warehouse.app.ui.WarehouseViewModel
import java.io.IOException
import java.util.Locale

@Composable
fun TransferScreen(
    state: WarehouseState,
    viewModel: WarehouseViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val latestState by rememberUpdatedState(state)
    val mimeXlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    val mimeCsv = "text/csv"
    val mimeJson = "application/json"

    val xlsxExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeXlsx)) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    output.write(WarehouseTransfer.encodeXlsx(latestState))
                } ?: throw IOException("Не удалось открыть файл для записи")
            }.onSuccess { viewModel.showTransferMessage("Excel-файл сохранён") }
                .onFailure { viewModel.showTransferMessage("Не удалось сохранить Excel-файл") }
        }
    }
    val csvExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeCsv)) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    output.write(WarehouseTransfer.encodeCsv(latestState))
                } ?: throw IOException("Не удалось открыть файл для записи")
            }.onSuccess { viewModel.showTransferMessage("CSV-файл сохранён") }
                .onFailure { viewModel.showTransferMessage("Не удалось сохранить CSV-файл") }
        }
    }
    val jsonExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeJson)) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.openOutputStream(it)?.use { output ->
                    output.write(WarehouseTransfer.encodeJson(latestState))
                } ?: throw IOException("Не удалось открыть файл для записи")
            }.onSuccess { viewModel.showTransferMessage("Резервная копия сохранена") }
                .onFailure { viewModel.showTransferMessage("Не удалось сохранить резервную копию") }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("Не удалось прочитать файл")
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else ""
                }.orEmpty().lowercase(Locale.ROOT)
                when {
                    fileName.endsWith(".json") || String(bytes, Charsets.UTF_8).trimStart().startsWith("{") ->
                        viewModel.importJson(String(bytes, Charsets.UTF_8)) {}
                    fileName.endsWith(".xlsx") || (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) ->
                        viewModel.importProducts(WarehouseTransfer.readXlsx(bytes))
                    fileName.endsWith(".csv") || fileName.endsWith(".txt") ->
                        viewModel.importProducts(WarehouseTransfer.readCsv(bytes))
                    else -> throw IOException("Поддерживаются файлы XLSX, CSV и JSON")
                }
            }.onFailure { viewModel.showTransferMessage(it.message ?: "Не удалось прочитать файл") }
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Данные склада", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Переносите каталог и создавайте резервные копии на устройстве.")
                Text(
                    "Восстановление исходного Warehouse поддерживает JSON с ключами my_off_db и my_off_arr6.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Экспорт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("${state.products.size} товаров · ${state.receipts.size} партий")
                Button(
                    onClick = { xlsxExporter.launch("warehouse.xlsx") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.TableView, contentDescription = null)
                    Text("Таблица Excel (.xlsx)")
                }
                OutlinedButton(
                    onClick = { csvExporter.launch("warehouse.csv") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Таблица CSV (.csv)") }
                OutlinedButton(
                    onClick = { jsonExporter.launch("warehouse-backup.json") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Полная резервная копия (.json)") }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text("Импорт", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    "XLSX и CSV обновляют каталог по штрихкоду или названию. JSON можно использовать для переноса резервной копии или старых данных браузерной версии.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Выбрать файл")
                }
            }
        }
    }
}