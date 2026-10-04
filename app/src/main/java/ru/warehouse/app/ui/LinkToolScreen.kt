@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseState

/** «Товар + Штрихкод»: привязка штрихкода к товару каталога. */
@Composable
fun LinkToolScreen(
    state: WarehouseState,
    extra: ExtraViewModel,
    warehouse: WarehouseViewModel,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var ean by rememberSaveable { mutableStateOf("") }
    var hint by remember { mutableStateOf("" to InfoBlue) }
    var dropdown by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<String?>(null) }
    var confirmRebind by remember { mutableStateOf<Product?>(null) }

    fun onEanChanged(value: String) {
        ean = value
        val normalized = ExtraViewModel.normalizeBarcode(value)
        if (normalized.length < 6) { if (normalized.isEmpty()) hint = "" to InfoBlue; return }
        val product = ExtraViewModel.findByBarcode(state, normalized)
        hint = if (product != null) {
            name = product.name
            dropdown = false
            "Штрихкод найден в базе: «${product.name}»" to OkGreen
        } else "Такого штрихкода в базе нет — можно сохранить как новый." to WarnAmber
    }

    val scan = rememberScanner { code -> onEanChanged(code) }

    val suggestions = remember(name, state.products, dropdown) {
        val q = name.trim().lowercase()
        if (!dropdown || q.length < 3) emptyList()
        else state.products.filter {
            val hay = "${it.name} ${it.barcode} ${it.sku}".lowercase()
            q.split(' ').filter(String::isNotBlank).all(hay::contains)
        }.take(12)
    }

    fun doSave() {
        val (ok, text) = extra.saveLink(name, ean, state, warehouse)
        if (ok) { name = ""; ean = ""; hint = "" to InfoBlue }
        result = text
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Наименование товара", fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; dropdown = true },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Начните вводить название…") },
            shape = RoundedCornerShape(16.dp),
        )
        suggestions.forEach { item ->
            InfoCard(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = {
                        name = item.name
                        dropdown = false
                        if (item.barcode.isNotBlank()) {
                            ean = item.barcode
                            hint = "Товар уже есть в базе. Текущий штрихкод: ${item.barcode}" to OkGreen
                        } else hint = "Товар есть в базе, но штрихкод пока не указан." to WarnAmber
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(item.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (item.barcode.isNotBlank()) "Штрихкод: ${item.barcode}" else "Штрихкод не указан",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Text("Штрихкод (EAN)", fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = ean,
                onValueChange = { onEanChanged(it) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                placeholder = { Text("Введите код или отсканируйте") },
                shape = RoundedCornerShape(16.dp),
            )
            IconButton(onClick = scan) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Сканировать штрихкод", tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (hint.first.isNotBlank()) Text(hint.first, color = hint.second, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                val byBarcode = ExtraViewModel.findByBarcode(state, ean)
                if (byBarcode != null && !byBarcode.name.trim().equals(name.trim(), true) && name.isNotBlank()) {
                    confirmRebind = byBarcode
                } else doSave()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Сохранить")
        }
    }

    confirmRebind?.let { other ->
        AlertDialog(
            onDismissRequest = { confirmRebind = null },
            title = { Text("Перепривязать штрихкод?") },
            text = { Text("Штрихкод ${ExtraViewModel.normalizeBarcode(ean)} уже привязан к товару «${other.name}». Перепривязать его к «${name.trim()}»?") },
            confirmButton = { TextButton(onClick = { confirmRebind = null; doSave() }) { Text("Перепривязать") } },
            dismissButton = { TextButton(onClick = { confirmRebind = null }) { Text("Отмена") } },
        )
    }
    result?.let {
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(if (it.startsWith("Товар «")) "Готово" else "Ошибка") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { result = null }) { Text("OK") } },
        )
    }
}
