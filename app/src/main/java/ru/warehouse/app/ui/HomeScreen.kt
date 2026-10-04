package ru.warehouse.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState

@OptIn(ExperimentalMaterial3Api::class)
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
    val searchResults = if (search.isBlank()) {
        emptyList()
    } else {
        state.products.filter {
            it.name.contains(search, true) ||
                it.barcode.contains(search, true) ||
                it.sku.contains(search, true)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("Быстрый поиск товара") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    shape = MaterialTheme.shapes.large,
                )
                FilledTonalIconButton(onClick = onScan) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Сканер")
                }
            }
        }

        if (search.isNotBlank()) {
            item {
                SectionHeading("Результаты поиска (${searchResults.size})")
            }
            if (searchResults.isEmpty()) {
                item {
                    EmptyState(
                        title = "Ничего не найдено",
                        body = "Проверьте введенный текст или штрихкод.",
                    )
                }
            } else {
                items(searchResults, key = { it.id }) { product ->
                    ProductRow(product = product, onClick = { onProduct(product) })
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    HomeStatCard(
                        title = "Всего позиций",
                        value = state.products.size.toString(),
                        subtitle = "в каталоге",
                        modifier = Modifier.weight(1f),
                    )
                    HomeStatCard(
                        title = "Документов",
                        value = state.receipts.size.toString(),
                        subtitle = "проведено",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                SectionHeading("Быстрые действия")
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ActionCard(
                            title = "Приёмка",
                            subtitle = "Новое поступление",
                            icon = Icons.Default.MoveToInbox,
                            onClick = { onOpen(WarehouseScreen.Receive) },
                            modifier = Modifier.weight(1f),
                        )
                        ActionCard(
                            title = "B-Ware",
                            subtitle = "Уценка и уценка",
                            icon = Icons.Default.Warning,
                            onClick = { onOpen(WarehouseScreen.Bware) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ActionCard(
                            title = "Инвентаризация",
                            subtitle = "Сверка остатков",
                            icon = Icons.Default.Inventory,
                            onClick = { onOpen(WarehouseScreen.Inventory) },
                            modifier = Modifier.weight(1f),
                        )
                        ActionCard(
                            title = "Задачи",
                            subtitle = "Списки и задания",
                            icon = Icons.Default.Assignment,
                            onClick = { onOpen(WarehouseScreen.Tasks) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ActionCard(
                            title = "Каталог",
                            subtitle = "База товаров",
                            icon = Icons.Default.Inventory2,
                            onClick = { onOpen(WarehouseScreen.Catalog) },
                            modifier = Modifier.weight(1f),
                        )
                        ActionCard(
                            title = "История",
                            subtitle = "Все операции",
                            icon = Icons.Default.History,
                            onClick = { onOpen(WarehouseScreen.History) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
