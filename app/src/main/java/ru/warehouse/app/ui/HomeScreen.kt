@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Плитка главного экрана — цвета взяты из warehouse.html (режим «С фоном»). */
private data class AppTile(
    val screen: WarehouseScreen,
    val label: String,
    val icon: ImageVector,
    val from: Color,
    val to: Color,
    val badge: String? = null,
)

private val BLUE = Color(0xFF7FA9E8) to Color(0xFF6B97DC)
private val PURPLE = Color(0xFFA08AE2) to Color(0xFF8B74D8)
private val GREEN = Color(0xFF7EC8A2) to Color(0xFF64B88B)
private val ORANGE = Color(0xFFF6AB78) to Color(0xFFF0955C)
private val PINK = Color(0xFFF28FA8) to Color(0xFFEA7894)
private val TEAL = Color(0xFF6CC3CF) to Color(0xFF52B2BF)
private val INDIGO = Color(0xFF6F93F0) to Color(0xFF5679E2)
private val AMBER = Color(0xFFE9A23B) to Color(0xFFDD8F1F)
private val VIOLET = Color(0xFF8F7BE0) to Color(0xFF775FD2)
private val CYAN = Color(0xFF5FB3C9) to Color(0xFF459FB7)

private fun tile(
    screen: WarehouseScreen,
    label: String,
    icon: ImageVector,
    colors: Pair<Color, Color>,
    badge: String? = null,
) = AppTile(screen, label, icon, colors.first, colors.second, badge)

private val firstPageTiles = listOf(
    tile(WarehouseScreen.ReceiveHub, "Приём товаров", Icons.Default.MoveToInbox, INDIGO),
    tile(WarehouseScreen.Inventory, "Инвентар.", Icons.Default.AssignmentTurnedIn, TEAL),
    tile(WarehouseScreen.Bware, "B-Ware", Icons.Default.Inventory2, AMBER),
    tile(WarehouseScreen.Plan, "По заданию", Icons.Default.Assignment, CYAN),
)

private val secondPageTiles = listOf(
    tile(WarehouseScreen.History, "История", Icons.Default.History, BLUE),
    tile(WarehouseScreen.Catalog, "Каталог", Icons.Default.Folder, PURPLE),
    tile(WarehouseScreen.Transfer, "Импорт / Экспорт", Icons.Default.SwapVert, GREEN),
    tile(WarehouseScreen.Settings, "Настройки", Icons.Default.Settings, ORANGE),
    tile(WarehouseScreen.LinkTool, "Товар+ШК", Icons.Default.Link, PINK, badge = "ШК"),
    tile(WarehouseScreen.Tasks, "Задачи", Icons.Default.Check, BLUE),
    tile(WarehouseScreen.Profile, "Профиль", Icons.Default.Person, PURPLE),
    tile(WarehouseScreen.Info, "Инфо", Icons.Default.Info, GREEN),
    tile(WarehouseScreen.Documents, "Документы", Icons.Default.Description, ORANGE),
    tile(WarehouseScreen.Gemini, "Чат Gemini", Icons.Default.Chat, VIOLET),
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
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val time = remember(now) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = time,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = date,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(18.dp))

            SearchBox(
                query = query,
                onQueryChange = { query = it },
                onScanClick = onScanClick,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (searching) {
                SearchResults(
                    state = state,
                    query = query.trim(),
                    onProductSelected = onProductSelected,
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    if (page == 0) {
                        // Первая страница: крупные кнопки внизу, как в HTML
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            TileRow(firstPageTiles, onScreenSelected)
                            Spacer(Modifier.height(34.dp))
                        }
                    } else {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Spacer(Modifier.height(10.dp))
                            secondPageTiles.chunked(4).forEach { row ->
                                TileRow(row, onScreenSelected)
                            }
                        }
                    }
                }
            }
        }

        if (!searching) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(2) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        Modifier
                            .size(if (active) 9.dp else 7.dp)
                            .background(
                                color = if (active) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape,
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { scope.launch { pagerState.animateScrollToPage(index) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .shadow(6.dp, RoundedCornerShape(27.dp)),
        shape = RoundedCornerShape(27.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Поиск товара",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Очистить",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            Box(
                Modifier
                    .size(44.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFFFF9D43), Color(0xFFD45B0B))),
                        CircleShape,
                    )
                    .clickable(onClick = onScanClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = "Сканировать",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: WarehouseState,
    query: String,
    onProductSelected: (Product) -> Unit,
) {
    val matches = remember(state.products, query) {
        state.products.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.barcode.contains(query, ignoreCase = true) ||
                it.sku.contains(query, ignoreCase = true)
        }
    }
    if (matches.isEmpty()) {
        Text(
            "Ничего не найдено",
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(matches, key = { it.id }) { product ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onProductSelected(product) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            product.name,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val sub = listOf(product.sku, product.barcode).filter { it.isNotBlank() }.joinToString(" · ")
                        if (sub.isNotEmpty()) {
                            Text(
                                sub,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        formatQuantity(product.stock),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TileRow(tiles: List<AppTile>, onSelect: (WarehouseScreen) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.forEach { tile ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                TileItem(tile) { onSelect(tile.screen) }
            }
        }
        repeat(4 - tiles.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun TileItem(tile: AppTile, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(top = 10.dp, start = 3.dp, end = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .shadow(6.dp, RoundedCornerShape(20.dp), ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
                .background(
                    Brush.linearGradient(listOf(tile.from, tile.to)),
                    RoundedCornerShape(20.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                tile.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(34.dp)
                    .padding(bottom = if (tile.badge != null) 8.dp else 0.dp),
            )
            tile.badge?.let { Badge(it) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = tile.label,
            fontSize = 13.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
        )
    }
}

@Composable
private fun BoxScope.Badge(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 8.dp),
    )
}
