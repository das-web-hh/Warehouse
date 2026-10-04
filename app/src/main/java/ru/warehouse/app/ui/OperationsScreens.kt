package ru.warehouse.app.ui

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
import ru.warehouse.app.ui.BarcodeScannerDialog
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.warehouse.app.data.DraftLine
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseState
import ru.warehouse.app.ui.WarehouseViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ReceiveScreen(
    state: WarehouseState,
    viewModel: WarehouseViewModel,
    isBware: Boolean,
    scanResult: String?,
    onScan: () -> Unit,
    onCreateProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(todayForForm()) }
    var order by rememberSaveable { mutableStateOf("") }
    var supplier by rememberSaveable { mutableStateOf("") }
    var lines by remember { mutableStateOf(emptyList<DraftLine>()) }

    val addProduct: (Product) -> Unit = { product ->
        val existing = lines.firstOrNull { it.product.id == product.id }
        lines = if (existing == null) lines + DraftLine(product)
        else lines.map { if (it.product.id == product.id) it.copy(quantity = it.quantity + 1.0) else it }
        search = ""
    }
    LaunchedEffect(scanResult) {
        if (!scanResult.isNullOrBlank()) {
            search = scanResult
            state.products.firstOrNull {
                it.barcode == scanResult.trim() || it.name.equals(scanResult.trim(), true)
            }?.let(addProduct)
        }
    }

    val matches = if (search.isBlank()) emptyList() else state.products
        .filter {
            it.name.contains(search, true) || it.barcode.contains(search, true) || it.sku.contains(search, true)
        }
        .take(6)
    val total = lines.sumOf { it.quantity }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = if (isBware) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        if (isBware) "Повреждённый товар" else "Новая приёмка",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (isBware) "Отдельный учёт B-Ware, не смешивается с основным остатком."
                        else "Добавьте товары вручную или отсканируйте их штрихкоды.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Дата") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = order,
                    onValueChange = { order = it.filter(Char::isDigit) },
                    modifier = Modifier.weight(1f),
                    label = { Text("№ заказа") },
                    singleLine = true,
                )
            }
        }
        item {
            OutlinedTextField(
                value = supplier,
                onValueChange = { supplier = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Поставщик") },
                singleLine = true,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Название или штрихкод") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
                FilledTonalIconButton(onClick = onScan) {
                    Icon(Icons.Default.BarcodeReader, contentDescription = "Сканировать")
                }
            }
        }
        if (search.isNotBlank()) {
            if (matches.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Товар не найден", fontWeight = FontWeight.SemiBold)
                            Text("Можно создать карточку по этому штрихкоду.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            OutlinedButton(onClick = { onCreateProduct(search) }) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("Создать товар")
                            }
                        }
                    }
                }
            } else {
                items(matches, key = { "suggest-${it.id}" }) { product ->
                    ProductPickerRow(
                        product = product,
                        action = "Добавить",
                        onClick = { addProduct(product) },
                    )
                }
            }
        }
        item {
            SectionHeading("Позиции · ${lines.size} · ${formatQuantity(total)}")
        }
        if (lines.isEmpty()) {
            item { EmptyState("Список пока пуст", "Найдите товар или отсканируйте его код, чтобы добавить.") }
        } else {
            items(lines, key = { it.product.id }) { line ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(line.product.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(line.product.sku, line.product.barcode).filter(String::isNotBlank).joinToString(" · ")
                                    .ifBlank { line.product.unit },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        QuantityStepper(
                            value = line.quantity,
                            unit = line.product.unit,
                            onValueChange = { quantity ->
                                lines = if (quantity <= 0.0) lines.filterNot { it.product.id == line.product.id }
                                else lines.map { if (it.product.id == line.product.id) it.copy(quantity = quantity) else it }
                            },
                        )
                        IconButton(onClick = { lines = lines.filterNot { it.product.id == line.product.id } }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Убрать позицию", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(3.dp))
            Button(
                onClick = {
                    viewModel.saveReceipt(lines, date, order, supplier, isBware)
                    if (lines.isNotEmpty()) {
                        lines = emptyList()
                        order = ""
                        supplier = ""
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                enabled = lines.isNotEmpty(),
                shape = MaterialTheme.shapes.large,
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Text("Сохранить приёмку · ${lines.size}")
            }
        }
    }
}

@Composable
fun InventoryScreen(
    state: WarehouseState,
    viewModel: WarehouseViewModel,
    scanResult: String?,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var location by rememberSaveable { mutableStateOf(state.selectedLocation) }
    var search by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.selectedLocation) {
        if (state.selectedLocation.isNotBlank()) location = state.selectedLocation
    }
    LaunchedEffect(scanResult) {
        if (!scanResult.isNullOrBlank()) {
            val matched = state.products.firstOrNull { it.barcode == scanResult.trim() }
            if (matched != null && location.isNotBlank()) {
                val count = state.locations.firstOrNull { it.code.equals(location.trim(), true) }?.counts?.get(matched.id) ?: 0.0
                viewModel.updateInventoryCount(location, matched, count + 1.0)
                search = ""
            } else if (matched != null) {
                search = matched.name
            } else {
                location = scanResult
                viewModel.selectLocation(scanResult)
            }
        }
    }

    val activeLocation = state.locations.firstOrNull { it.code.equals(location.trim(), true) }
    val currentCounts = activeLocation?.counts.orEmpty()
    val matchedProducts = if (search.isBlank()) emptyList() else state.products
        .filter { it.name.contains(search, true) || it.barcode.contains(search, true) || it.sku.contains(search, true) }
        .take(6)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Пересчёт по адресам", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Отсканируйте QR-код ячейки, затем внесите фактическое количество товаров.")
                }
            }
        }
        item {
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Адрес склада / ячейки") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                singleLine = true,
                supportingText = { Text("Например: A-01-03") },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Товар или штрихкод") },
                    singleLine = true,
                )
                FilledTonalIconButton(onClick = onScan) {
                    Icon(Icons.Default.BarcodeReader, contentDescription = "Сканировать адрес или товар")
                }
            }
        }
        if (search.isNotBlank()) {
            if (matchedProducts.isEmpty()) {
                item { Text("В каталоге не найден товар «$search».", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(matchedProducts, key = { "inventory-${it.id}" }) { product ->
                    ProductPickerRow(
                        product = product,
                        action = "Внести",
                        onClick = {
                            val current = currentCounts[product.id] ?: 0.0
                            viewModel.updateInventoryCount(location, product, current + 1.0)
                            search = ""
                        },
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionHeading("Пересчёт ${location.ifBlank { "не выбран" }}", modifier = Modifier.weight(1f))
                OutlinedButton(
                    onClick = {
                        viewModel.selectLocation(location)
                    },
                    enabled = location.isNotBlank(),
                ) { Text("Выбрать адрес") }
            }
        }
        if (location.isBlank()) {
            item { EmptyState("Выберите ячейку", "Укажите адрес склада вручную или отсканируйте QR-код.") }
        } else if (currentCounts.isEmpty()) {
            item { EmptyState("Пока нет позиций", "Добавьте товар — количество будет сохранено автоматически.") }
        } else {
            val counted = currentCounts.entries.mapNotNull { entry ->
                state.products.firstOrNull { it.id == entry.key }?.let { it to entry.value }
            }.sortedBy { it.first.name.lowercase() }
            items(counted, key = { it.first.id }) { (product, count) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(product.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Остаток системы: ${formatQuantity(product.stock)} ${product.unit}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        QuantityStepper(
                            value = count,
                            unit = product.unit,
                            onValueChange = { viewModel.updateInventoryCount(location, product, it) },
                        )
                        IconButton(onClick = { viewModel.updateInventoryCount(location, product, 0.0) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Сбросить количество", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
        if (state.locations.isNotEmpty()) {
            item { SectionHeading("Недавние адреса") }
            items(state.locations.sortedByDescending { it.updatedAt }.take(6), key = { "location-${it.code}" }) { saved ->
                OutlinedButton(
                    onClick = {
                        location = saved.code
                        viewModel.selectLocation(saved.code)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Text("${saved.code} · ${saved.counts.size} поз.")
                }
            }
        }
    }
}

private fun todayForForm(): String =
    LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yy"))
