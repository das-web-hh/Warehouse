@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Tile(val title: String, val icon: ImageVector, val color: Color, val target: WarehouseScreen)

private val firstPageTiles = listOf(
    Tile("Приём товаров", Icons.Default.MoveToInbox, Color(0xFF7C8CF8), WarehouseScreen.ReceiveHub),
    Tile("Инвентар.", Icons.Default.AssignmentTurnedIn, Color(0xFF52C4C1), WarehouseScreen.Inventory),
    Tile("B-Ware", Icons.Default.Warning, Color(0xFFE5A038), WarehouseScreen.Bware),
    Tile("По заданию", Icons.Default.Assignment, Color(0xFF3FB6C9), WarehouseScreen.Plan),
)

private val secondPageTiles = listOf(
    Tile("История", Icons.Default.History, Color(0xFF3FB6C9), WarehouseScreen.History),
    Tile("Каталог", Icons.Default.Folder, Color(0xFFE9C03A), WarehouseScreen.Catalog),
    Tile("Импорт / Экспорт", Icons.Default.SwapVert, Color(0xFF8B6CF0), WarehouseScreen.Transfer),
    Tile("Настройки", Icons.Default.Settings, Color(0xFF7A8794), WarehouseScreen.Settings),
    Tile("Товар+ШК", Icons.Default.Link, Color(0xFF3B82F6), WarehouseScreen.LinkTool),
    Tile("Задачи", Icons.Default.TaskAlt, Color(0xFF4CAF7A), WarehouseScreen.Tasks),
    Tile("Профиль", Icons.Default.Person, Color(0xFFE05252), WarehouseScreen.Profile),
    Tile("Инфо", Icons.Default.Info, Color(0xFFE5A038), WarehouseScreen.Info),
    Tile("Документы", Icons.Default.Description, Color(0xFFE86AA6), WarehouseScreen.Documents),
    Tile("Чат Gemini", Icons.Default.AutoAwesome, Color(0xFF8B6CF0), WarehouseScreen.Gemini),
)

/** Главный экран: страница 1 (часы, поиск, 4 плитки) и страница 2 (10 плиток) со свайпом и точками. */
@Composable
fun HomeScreen(
    state: WarehouseState,
    search: String,
    onSearchChange: (String) -> Unit,
    onOpen: (WarehouseScreen) -> Unit,
    onScan: () -> Unit,
    onProduct: (Product) -> Unit,
    page: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = page, pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // Запоминаем, на какой странице пользователь, чтобы «Назад» из окна возвращал на ту же.
    LaunchedEffect(pagerState.currentPage) { onPageChange(pagerState.currentPage) }
    LaunchedEffect(page) {
        if (page != pagerState.currentPage) pagerState.scrollToPage(page)
    }

    Column(modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            if (index == 0) FirstPage(state, search, onSearchChange, onOpen, onScan, onProduct)
            else SecondPage(onOpen)
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(2) { i ->
                Box(
                    Modifier.padding(4.dp).size(if (pagerState.currentPage == i) 10.dp else 8.dp)
                        .background(
                            if (pagerState.currentPage == i) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
private fun FirstPage(
    state: WarehouseState,
    search: String,
    onSearchChange: (String) -> Unit,
    onOpen: (WarehouseScreen) -> Unit,
    onScan: () -> Unit,
    onProduct: (Product) -> Unit,
) {
    val searchResults = if (search.isBlank()) emptyList() else state.products.filter {
        it.name.contains(search, ignoreCase = true) ||
            it.barcode.contains(search, ignoreCase = true) ||
            it.sku.contains(search, ignoreCase = true)
    }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    val currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val currentDate = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(currentTime, fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text(currentDate, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск товара") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = onScan) { Icon(Icons.Default.QrCodeScanner, contentDescription = "Сканировать") }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
        if (search.isNotBlank()) {
            item { SectionHeading("Результаты поиска (${searchResults.size})") }
            if (searchResults.isEmpty()) {
                item { EmptyState("Ничего не найдено", "Попробуйте изменить запрос или отсканировать штрихкод.") }
            } else {
                items(searchResults, key = { it.id }) { product -> ProductRow(product, { onProduct(product) }) }
            }
        } else {
            item { TileGrid(firstPageTiles, onOpen) }
        }
    }
}

@Composable
private fun SecondPage(onOpen: (WarehouseScreen) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
    ) {
        item { TileGrid(secondPageTiles, onOpen) }
    }
}

@Composable
private fun TileGrid(tiles: List<Tile>, onOpen: (WarehouseScreen) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    QuickActionButton(tile.title, tile.icon, tile.color, { onOpen(tile.target) }, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickActionButton(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(48.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = title, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
    }
}
