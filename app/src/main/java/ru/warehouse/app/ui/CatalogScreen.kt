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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseState

@Composable
fun CatalogScreen(
    state: WarehouseState,
    scanResult: String?,
    onScan: () -> Unit,
    onEdit: (Product) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(scanResult) {
        if (!scanResult.isNullOrBlank()) search = scanResult
    }
    val filtered = state.products
        .filter {
            search.isBlank() ||
                it.name.contains(search, true) ||
                it.barcode.contains(search, true) ||
                it.sku.contains(search, true) ||
                it.category.contains(search, true)
        }
        .sortedBy { it.name.lowercase() }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Поиск товара") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = MaterialTheme.shapes.large,
            )
            FilledTonalIconButton(onClick = onScan) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Сканировать штрихкод")
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${filtered.size} позиций", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Добавить")
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                title = if (search.isBlank()) "Каталог пуст" else "Ничего не найдено",
                body = if (search.isBlank()) "Добавьте товар вручную или импортируйте список." else "Проверьте название, артикул или штрихкод.",
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(filtered, key = { it.id }) { product ->
                    ProductRow(product = product, onClick = { onEdit(product) })
                }
            }
        }
    }
}

@Composable
fun ProductEditorDialog(
    product: Product,
    onDismiss: () -> Unit,
    onSave: (Product) -> Unit,
    onDelete: ((String) -> Unit)? = null,
) {
    var name by remember(product.id) { mutableStateOf(product.name) }
    var barcode by remember(product.id) { mutableStateOf(product.barcode) }
    var sku by remember(product.id) { mutableStateOf(product.sku) }
    var category by remember(product.id) { mutableStateOf(product.category) }
    var unit by remember(product.id) { mutableStateOf(product.unit) }
    var stock by remember(product.id) { mutableStateOf(formatQuantity(product.stock)) }
    var bware by remember(product.id) { mutableStateOf(formatQuantity(product.bwareStock)) }
    var confirmDelete by remember(product.id) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product.name.isBlank()) "Новый товар" else "Карточка товара") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Название *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(barcode, { barcode = it }, label = { Text("Штрихкод") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(sku, { sku = it }, label = { Text("Артикул") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(unit, { unit = it }, label = { Text("Ед.") }, singleLine = true, modifier = Modifier.weight(0.6f))
                }
                OutlinedTextField(category, { category = it }, label = { Text("Категория") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(stock, { stock = it }, label = { Text("Остаток") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(bware, { bware = it }, label = { Text("B-Ware") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (confirmDelete) {
                    Text("Удалить товар из каталога?", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null && product.name.isNotBlank()) {
                    TextButton(
                        onClick = {
                            if (confirmDelete) onDelete(product.id) else confirmDelete = true
                        },
                    ) {
                        Text(if (confirmDelete) "Подтвердить" else "Удалить", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(
                    onClick = {
                        onSave(
                            product.copy(
                                name = name.trim(),
                                barcode = barcode.trim(),
                                sku = sku.trim(),
                                category = category.trim(),
                                unit = unit.trim().ifBlank { "шт" },
                                stock = stock.replace(',', '.').toDoubleOrNull() ?: product.stock,
                                bwareStock = bware.replace(',', '.').toDoubleOrNull() ?: product.bwareStock,
                            ),
                        )
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Сохранить") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ProductPickerRow(
    product: Product,
    action: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(product.barcode, product.sku).filter(String::isNotBlank).joinToString(" · ").ifBlank { "Нет штрихкода" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(action, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}