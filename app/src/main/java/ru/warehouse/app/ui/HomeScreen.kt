@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

private data class HomeTile(
    val screen: WarehouseScreen,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

@Composable
fun HomeScreen(
    state: WarehouseState,
    onScreenSelected: (WarehouseScreen) -> Unit,
    onScanClick: () -> Unit,
    onProductSelected: (Product) -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(
        HomeTile(WarehouseScreen.Catalog, "Каталог", "${state.products.size} товаров", Icons.Default.List),
        HomeTile(WarehouseScreen.ReceiveHub, "Приём товаров", "Вручную · по накладной · авто", Icons.Default.MoveToInbox),
        HomeTile(WarehouseScreen.Bware, "Приёмка B-Ware", "Уценённый товар", Icons.Default.Inventory2),
        HomeTile(WarehouseScreen.Inventory, "Инвентаризация", "Подсчёт по адресам", Icons.Default.CheckCircle),
        HomeTile(WarehouseScreen.Plan, "По заданию", "Сверка плана и факта", Icons.Default.Assignment),
        HomeTile(WarehouseScreen.LinkTool, "Товар + ШК", "Привязка штрихкода", Icons.Default.Link),
        HomeTile(WarehouseScreen.History, "История", "${state.receipts.size} приёмок", Icons.Default.History),
        HomeTile(WarehouseScreen.History2, "История 2", "Накладные в деталях", Icons.Default.History),
        HomeTile(WarehouseScreen.Tasks, "Задачи", "${state.tasks.count { !it.done }} активных", Icons.Default.CheckCircle),
        HomeTile(WarehouseScreen.Documents, "Документы", "Отчёты и печать", Icons.Default.Description),
        HomeTile(WarehouseScreen.Gemini, "Чат Gemini", "Помощник по складу", Icons.Default.Chat),
        HomeTile(WarehouseScreen.Info, "Инфо", "Статистика", Icons.Default.Info),
        HomeTile(WarehouseScreen.Profile, "Профиль", "Данные пользователя", Icons.Default.Person),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Склад",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Row {
                IconButton(onClick = onScanClick) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Сканер")
                }
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки")
                }
            }
        }

        tiles.chunked(2).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowTiles.forEach { tile ->
                    Card(
                        onClick = { onScreenSelected(tile.screen) },
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(tile.icon, contentDescription = null)
                            Text(tile.title, fontWeight = FontWeight.Bold)
                            Text(tile.subtitle, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (rowTiles.size == 1) {
                    Column(Modifier.weight(1f)) {}
                }
            }
        }
    }
}
