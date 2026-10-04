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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarcodeReader
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val screen: WarehouseScreen,
    val color: Color,
)

@Composable
fun HomeScreen(
    state: WarehouseState,
    search: String,
    onSearchChange: (String) -> Unit,
    onOpen: (WarehouseScreen) -> Unit,
    onScan: () -> Unit,
    onProduct: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        QuickAction("Приём товаров", Icons.Default.LocalShipping, WarehouseScreen.Receive, Color(0xFF2C7B59)),
        QuickAction("Инвентаризация", Icons.Default.Inventory2, WarehouseScreen.Inventory, Color(0xFFBA4B45)),
        QuickAction("B-Ware", Icons.Default.Warehouse, WarehouseScreen.Bware, Color(0xFFF07818)),
        QuickAction("По заданию", Icons.Default.Assignment, WarehouseScreen.Tasks, Color(0xFF1685A2)),
        QuickAction("История", Icons.Default.ReceiptLong, WarehouseScreen.History, Color(0xFF5576C5)),
        QuickAction("Каталог", Icons.Default.Storage, WarehouseScreen.Catalog, Color(0xFFB38A2E)),
        QuickAction("Импорт / экспорт", Icons.Default.UploadFile, WarehouseScreen.Transfer, Color(0xFF7651A3)),
        QuickAction("Задачи", Icons.Default.TaskAlt, WarehouseScreen.Tasks, Color(0xFF2C7B59)),
    )
    val matchingProducts = if (search.isBlank()) emptyList() else state.products
        .filter { product ->
            product.name.contains(search, true) ||
                product.barcode.contains(search, true) ||
                product.sku.contains(search, true)
        }
        .take(5)
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale("ru")))
        .replaceFirstChar { it.uppercase() }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                Text("Склад под контролем", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Приёмка, остатки и инвентаризация — в одном месте.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = Color(0xFF153558),
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFFFFAE69))
                        Text("Быстрый поиск", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    OutlinedTextField(
                        value = search,
                        onValueChange = onSearchChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Название, артикул или штрихкод", color = Color.White.copy(alpha = 0.65f)) },
                        singleLine = true,
                        trailingIcon = {
                            androidx.compose.material3.IconButton(onClick = onScan) {
                                Icon(Icons.Default.BarcodeReader, contentDescription = "Сканировать", tint = Color.White)
                            }
                        },
                        shape = MaterialTheme.shapes.large,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFFFAE69),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.42f),
                            cursorColor = Color.White,
                        ),
                    )
                    if (matchingProducts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            matchingProducts.forEach { product ->
                                Card(
                                    onClick = { onProduct(product) },
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(product.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${formatQuantity(product.stock)} ${product.unit}", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    } else if (search.isNotBlank()) {
                        Text("Совпадений не найдено", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                val totalPieces = state.products.sumOf { it.stock }
                MetricCard(
                    label = "Товаров в каталоге",
                    value = state.products.size.toString(),
                    supporting = "Разных позиций",
                    accent = Color(0xFF4188D2),
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    label = "На складе",
                    value = formatQuantity(totalPieces),
                    supporting = "Единиц товара",
                    accent = Color(0xFF3A9A6A),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item { SectionHeading("Быстрые операции") }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(254.dp),
                userScrollEnabled = false,
                contentPadding = PaddingValues(0.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(actions) { action ->
                    Card(
                        onClick = { onOpen(action.screen) },
                        modifier = Modifier.fillMaxWidth().height(118.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Surface(shape = MaterialTheme.shapes.medium, color = action.color.copy(alpha = 0.12f)) {
                                Icon(
                                    action.icon,
                                    contentDescription = null,
                                    modifier = Modifier.padding(9.dp),
                                    tint = action.color,
                                )
                            }
                            Text(action.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        item {
            SectionHeading(
                "Недавние приёмки",
                actionLabel = "Все",
                onAction = { onOpen(WarehouseScreen.History) },
            )
        }
        if (state.receipts.isEmpty()) {
            item { EmptyState("Пока нет приёмок", "Сохранённые партии появятся здесь.") }
        } else {
            items(state.receipts.take(3), key = { it.id }) { receipt ->
                Card(
                    onClick = { onOpen(WarehouseScreen.History) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (receipt.isBware) "B-Ware · ${receipt.date}" else "Приёмка · ${receipt.date}", fontWeight = FontWeight.SemiBold)
                            Text(
                                listOf(receipt.supplier, receipt.orderNumber).filter(String::isNotBlank).joinToString(" · ")
                                    .ifBlank { "${receipt.lines.size} позиций" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${receipt.lines.size}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}