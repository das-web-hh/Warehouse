package ru.warehouse.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Warehouse home: product search and the two quick-action pages from the HTML
 * home screen. Product data and navigation remain owned by the existing app.
 */
@Composable
fun HomeScreen(
    state: WarehouseState,
    onScreenSelected: (WarehouseScreen) -> Unit,
    onScanClick: () -> Unit,
    onProductSelected: (Product) -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var clockTick by remember { mutableStateOf(System.currentTimeMillis()) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val activePage by remember(scrollState) {
        derivedStateOf {
            if (scrollState.maxValue == 0) 0
            else if (scrollState.value > scrollState.maxValue / 2) 1 else 0
        }
    }
    val matchingProducts = remember(state.products, query) {
        val normalized = query.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) emptyList()
        else state.products.filter { product ->
            product.name.contains(normalized, ignoreCase = true) ||
                product.barcode.contains(normalized, ignoreCase = true) ||
                product.sku.contains(normalized, ignoreCase = true) ||
                product.category.contains(normalized, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            clockTick = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val light = !state.darkTheme
    val backgroundColor = if (light) Color(0xFFF6F8FC) else Color.Black
    val mainTextColor = if (light) Color(0xFF171B22) else Color.White
    val mutedTextColor = if (light) Color(0xFF5F6672) else Color.White.copy(alpha = 0.62f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        val screenWidth = maxWidth

        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(scrollState),
        ) {
            Box(
                modifier = Modifier
                    .width(screenWidth)
                    .fillMaxHeight(),
            ) {
                HomeSearchPage(
                    state = state,
                    query = query,
                    onQueryChange = { query = it },
                    matchingProducts = matchingProducts,
                    clockTick = clockTick,
                    onScanClick = onScanClick,
                    onProductSelected = onProductSelected,
                    onScreenSelected = onScreenSelected,
                    textColor = mainTextColor,
                    mutedTextColor = mutedTextColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .width(screenWidth)
                    .fillMaxHeight(),
            ) {
                QuickActionsPage(
                    state = state,
                    onScreenSelected = onScreenSelected,
                    textColor = mainTextColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // The original menu control floats near the upper-right side of the page.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 14.dp)
                .padding(top = maxHeight * 0.20f),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        if (light) Color(0x1F1C2735) else Color.White.copy(alpha = 0.13f),
                    )
                    .border(
                        1.5.dp,
                        if (light) Color(0x33212B3A) else Color.White.copy(alpha = 0.28f),
                        CircleShape,
                    )
                    .clickable(onClick = onMenuClick)
                    .semantics { contentDescription = "Открыть меню" },
                contentAlignment = Alignment.Center,
            ) {
                MenuGlyph(color = if (light) Color(0xFF212529) else Color.White)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) { page ->
                Box(
                    modifier = Modifier
                        .size(if (activePage == page) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (activePage == page) mainTextColor
                            else mainTextColor.copy(alpha = 0.28f),
                        )
                        .clickable {
                            scope.launch {
                                val width = with(density) { screenWidth.roundToPx() }
                                scrollState.animateScrollTo(if (page == 0) 0 else width)
                            }
                        }
                        .semantics {
                            contentDescription = "Экран ${page + 1}"
                        },
                )
            }
        }
    }
}

@Composable
private fun HomeSearchPage(
    state: WarehouseState,
    query: String,
    onQueryChange: (String) -> Unit,
    matchingProducts: List<Product>,
    clockTick: Long,
    onScanClick: () -> Unit,
    onProductSelected: (Product) -> Unit,
    onScreenSelected: (WarehouseScreen) -> Unit,
    textColor: Color,
    mutedTextColor: Color,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 54.dp, bottom = 34.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(clockTick)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            color = textColor,
            fontSize = 36.sp,
            lineHeight = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(clockTick)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            color = mutedTextColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(if (query.isBlank()) 22.dp else 18.dp))
        HomeSearchField(
            query = query,
            onQueryChange = onQueryChange,
            onScanClick = onScanClick,
            onClear = {
                onQueryChange("")
                keyboardController?.hide()
                focusManager.clearFocus()
            },
            darkTheme = state.darkTheme,
        )

        if (query.isBlank()) {
            Spacer(Modifier.weight(1f))
            QuickActionGrid(
                tiles = firstPageTiles,
                darkTheme = state.darkTheme,
                textColor = textColor,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                onScreenSelected = onScreenSelected,
            )
        } else {
            ProductResults(
                products = matchingProducts,
                darkTheme = state.darkTheme,
                textColor = textColor,
                onProductSelected = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onProductSelected(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 4.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun HomeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onScanClick: () -> Unit,
    onClear: () -> Unit,
    darkTheme: Boolean,
) {
    val fieldBackground = if (darkTheme) Color(0xFF171A1F) else Color.White
    val fieldText = if (darkTheme) Color(0xFFE3E5EA) else Color(0xFF212529)
    val fieldHint = if (darkTheme) Color(0xFFA6ACB6) else Color(0xFF7B8794)
    val outline = if (darkTheme) Color(0xFF2A2F37) else Color(0xFFC9CFDB)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 54.dp)
            .shadow(8.dp, RoundedCornerShape(27.dp))
            .clip(RoundedCornerShape(27.dp))
            .background(fieldBackground)
            .border(1.dp, outline, RoundedCornerShape(27.dp))
            .padding(start = 20.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            singleLine = true,
            textStyle = TextStyle(
                color = fieldText,
                fontSize = 16.sp,
                lineHeight = 20.sp,
            ),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search,
            ),
            decorationBox = { innerTextField ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "",
                            color = fieldHint,
                            fontSize = 16.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (darkTheme) Color(0xFF2A2F37) else Color(0xFFE9ECEF))
                    .clickable(onClick = onClear)
                    .semantics { contentDescription = "Очистить поиск" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "×",
                    color = if (darkTheme) Color(0xFFE3E5EA) else Color(0xFF495057),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(if (darkTheme) Color.Black else Color.White)
                .border(1.dp, if (darkTheme) Color.Black else Color.White, CircleShape)
                .clickable(onClick = onScanClick)
                .semantics { contentDescription = "Сканировать товар" },
            contentAlignment = Alignment.Center,
        ) {
            ScanGlyph(color = if (darkTheme) Color.White else Color(0xFF111827))
        }
    }
}

@Composable
private fun ProductResults(
    products: List<Product>,
    darkTheme: Boolean,
    textColor: Color,
    onProductSelected: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = if (darkTheme) Color(0xF70A1628) else Color.White.copy(alpha = 0.97f)
    val borderColor = if (darkTheme) Color(0xFF26394F) else Color(0xFFDEE2E6)
    val secondaryText = if (darkTheme) Color(0xFFADB8C5) else Color(0xFF495057)

    Column(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(cardColor)
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
    ) {
        if (products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Ничего не найдено",
                    color = if (darkTheme) Color(0xFF8B929C) else Color(0xFF868E96),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(products, key = { it.id }) { product ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (darkTheme) Color(0xFF0F1114) else Color.White)
                            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                            .clickable { onProductSelected(product) }
                            .padding(vertical = 11.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = product.sku.ifBlank { product.barcode }.ifBlank { "—" },
                            modifier = Modifier
                                .weight(0.15f)
                                .padding(start = 6.dp, end = 4.dp),
                            color = secondaryText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = product.name,
                            modifier = Modifier.weight(0.73f),
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatStock(product.stock),
                            modifier = Modifier.weight(0.12f),
                            color = if (darkTheme) Color(0xFFA9C7FF) else Color(0xFF1C7ED6),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

private fun formatStock(stock: Double): String =
    if (stock % 1.0 == 0.0) stock.toLong().toString()
    else String.format(Locale.getDefault(), "%.2f", stock).trimEnd('0').trimEnd(',')

@Composable
private fun QuickActionsPage(
    state: WarehouseState,
    onScreenSelected: (WarehouseScreen) -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 124.dp, bottom = 34.dp),
    ) {
        QuickActionGrid(
            tiles = secondPageTiles,
            darkTheme = state.darkTheme,
            textColor = textColor,
            onScreenSelected = onScreenSelected,
        )
    }
}

@Composable
private fun QuickActionGrid(
    tiles: List<HomeTile>,
    darkTheme: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
    onScreenSelected: (WarehouseScreen) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        tiles.chunked(4).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowTiles.forEach { tile ->
                    QuickActionTile(
                        tile = tile,
                        darkTheme = darkTheme,
                        textColor = textColor,
                        modifier = Modifier.weight(1f),
                        onClick = { onScreenSelected(tile.destination) },
                    )
                }
                repeat(4 - rowTiles.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    tile: HomeTile,
    darkTheme: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val iconColor = if (darkTheme) Color.White else tile.lightIconColor

    Column(
        modifier = modifier
            .height(116.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 10.dp)
            .semantics { contentDescription = tile.label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        HomeActionIcon(kind = tile.icon, color = iconColor)
        Spacer(Modifier.height(8.dp))
        Text(
            text = tile.label,
            modifier = Modifier
                .fillMaxWidth()
                .height(29.dp),
            color = textColor,
            fontSize = 12.sp,
            lineHeight = 14.4.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Visible,
        )
    }
}

@Composable
private fun HomeActionIcon(
    kind: HomeIcon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(45.dp)
            .semantics { contentDescription = kind.name },
    ) {
        withTransform({
            scale(
                scaleX = size.width / 24f,
                scaleY = size.height / 24f,
                pivot = Offset.Zero,
            )
        }) {
            val stroke = 1.8f
            when (kind) {
                HomeIcon.Receive -> {
                    drawOutlinePath(
                        listOf(Offset(3f, 7.5f), Offset(10f, 4f), Offset(17f, 7.5f), Offset(17f, 14.5f), Offset(10f, 18f), Offset(3f, 14.5f)),
                        color, stroke, closed = true,
                    )
                    drawPolyline(listOf(Offset(3f, 7.5f), Offset(10f, 11f), Offset(17f, 7.5f)), color, stroke)
                    drawLine(color, Offset(10f, 11f), Offset(10f, 18f), stroke)
                    drawLine(color, Offset(20f, 7f), Offset(20f, 13f), 2.2f)
                    drawLine(color, Offset(17f, 10f), Offset(23f, 10f), 2.2f)
                }
                HomeIcon.Inventory -> {
                    drawRoundRect(color, Offset(4f, 4f), androidx.compose.ui.geometry.Size(16f, 18f), CornerRadius(2f), style = Stroke(stroke))
                    drawRoundRect(color, Offset(8f, 2f), androidx.compose.ui.geometry.Size(8f, 4f), CornerRadius(1f), style = Stroke(stroke))
                    drawLine(color, Offset(8f, 10f), Offset(12f, 10f), stroke)
                    drawLine(color, Offset(8f, 14f), Offset(11f, 14f), stroke)
                    drawPolyline(listOf(Offset(13f, 15f), Offset(16f, 18f), Offset(22f, 11f)), color, 2.2f)
                }
                HomeIcon.Bware -> {
                    drawOutlinePath(
                        listOf(Offset(3f, 7.5f), Offset(12f, 3f), Offset(21f, 7.5f), Offset(21f, 16.5f), Offset(12f, 21f), Offset(3f, 16.5f)),
                        color, stroke, closed = true,
                    )
                    drawPolyline(listOf(Offset(3f, 7.5f), Offset(12f, 12f), Offset(21f, 7.5f)), color, stroke)
                    drawLine(color, Offset(12f, 12f), Offset(12f, 21f), stroke)
                    drawLine(color, Offset(7.5f, 5.3f), Offset(16.5f, 9.8f), stroke)
                }
                HomeIcon.Plan -> {
                    drawRoundRect(color, Offset(5f, 4f), androidx.compose.ui.geometry.Size(14f, 17f), CornerRadius(2f), style = Stroke(stroke))
                    drawLine(color, Offset(9f, 4f), Offset(9f, 3f), stroke)
                    drawLine(color, Offset(15f, 4f), Offset(15f, 3f), stroke)
                    drawLine(color, Offset(9f, 3f), Offset(15f, 3f), stroke)
                    drawPolyline(listOf(Offset(9f, 13f), Offset(11f, 15f), Offset(15f, 11f)), color, stroke)
                }
                HomeIcon.History -> {
                    drawCircle(color, 8f, Offset(12f, 12f), style = Stroke(stroke))
                    drawLine(color, Offset(12f, 8f), Offset(12f, 13f), stroke)
                    drawLine(color, Offset(12f, 13f), Offset(15f, 15f), stroke)
                }
                HomeIcon.Catalog -> {
                    drawOutlinePath(
                        listOf(Offset(3f, 6f), Offset(3f, 6f), Offset(5f, 4f), Offset(10f, 4f), Offset(12f, 6f), Offset(19f, 6f), Offset(21f, 8f), Offset(21f, 18f), Offset(19f, 20f), Offset(5f, 20f), Offset(3f, 18f)),
                        color, stroke, closed = true,
                    )
                }
                HomeIcon.Transfer -> {
                    drawLine(color, Offset(7f, 3f), Offset(7f, 16f), stroke)
                    drawPolyline(listOf(Offset(3f, 7f), Offset(7f, 3f), Offset(11f, 7f)), color, stroke)
                    drawLine(color, Offset(17f, 21f), Offset(17f, 8f), stroke)
                    drawPolyline(listOf(Offset(13f, 17f), Offset(17f, 21f), Offset(21f, 17f)), color, stroke)
                }
                HomeIcon.Settings -> {
                    drawCircle(color, 7.5f, Offset(12f, 12f), style = Stroke(stroke))
                    drawCircle(color, 3f, Offset(12f, 12f), style = Stroke(stroke))
                    repeat(8) { index ->
                        val angle = Math.toRadians(index * 45.0)
                        val inner = Offset(12f + kotlin.math.cos(angle).toFloat() * 8.2f, 12f + kotlin.math.sin(angle).toFloat() * 8.2f)
                        val outer = Offset(12f + kotlin.math.cos(angle).toFloat() * 10.5f, 12f + kotlin.math.sin(angle).toFloat() * 10.5f)
                        drawLine(color, inner, outer, stroke)
                    }
                }
                HomeIcon.Link -> {
                    drawLine(color, Offset(9f, 12f), Offset(15f, 12f), stroke)
                    drawPolyline(listOf(Offset(8f, 7f), Offset(6f, 7f), Offset(3f, 10f), Offset(3f, 14f), Offset(6f, 17f), Offset(8f, 17f)), color, stroke)
                    drawPolyline(listOf(Offset(16f, 7f), Offset(18f, 7f), Offset(21f, 10f), Offset(21f, 14f), Offset(18f, 17f), Offset(16f, 17f)), color, stroke)
                }
                HomeIcon.Tasks -> {
                    drawRoundRect(color, Offset(3f, 3f), androidx.compose.ui.geometry.Size(18f, 18f), CornerRadius(2f), style = Stroke(stroke))
                    drawPolyline(listOf(Offset(8f, 11f), Offset(11f, 14f), Offset(17f, 7f)), color, stroke)
                }
                HomeIcon.Profile -> {
                    drawCircle(color, 4f, Offset(12f, 8f), style = Stroke(stroke))
                    val shoulders = Path().apply {
                        moveTo(4f, 21f)
                        cubicTo(4f, 16.6f, 7.6f, 13f, 12f, 13f)
                        cubicTo(16.4f, 13f, 20f, 16.6f, 20f, 21f)
                    }
                    drawPath(shoulders, color, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                HomeIcon.Info -> {
                    drawCircle(color, 9f, Offset(12f, 12f), style = Stroke(stroke))
                    drawLine(color, Offset(12f, 11f), Offset(12f, 16f), stroke)
                    drawCircle(color, 0.8f, Offset(12f, 7.7f))
                }
                HomeIcon.Documents -> {
                    drawOutlinePath(
                        listOf(Offset(9f, 3f), Offset(15f, 3f), Offset(19f, 7f), Offset(19f, 18f), Offset(9f, 18f)),
                        color, stroke,
                    )
                    drawPolyline(listOf(Offset(15f, 3f), Offset(15f, 7f), Offset(19f, 7f)), color, stroke)
                    drawPolyline(listOf(Offset(5f, 7f), Offset(5f, 21f), Offset(16f, 21f)), color, stroke)
                    drawLine(color, Offset(12f, 12f), Offset(16f, 12f), stroke)
                    drawLine(color, Offset(12f, 15f), Offset(16f, 15f), stroke)
                }
                HomeIcon.Gemini -> {
                    val bubble = Path().apply {
                        moveTo(21f, 12f)
                        cubicTo(21f, 16.4f, 17f, 20f, 12f, 20f)
                        cubicTo(10f, 20f, 8.5f, 19.5f, 7f, 18.8f)
                        lineTo(4f, 20f)
                        lineTo(5f, 16f)
                        cubicTo(2f, 12f, 4f, 5f, 10f, 4f)
                        cubicTo(16f, 2.8f, 21f, 7f, 21f, 12f)
                        close()
                    }
                    drawPath(bubble, color, style = Stroke(stroke, join = StrokeJoin.Round))
                    drawStar(color, center = Offset(12f, 11.5f), outerRadius = 4f, innerRadius = 1.8f)
                }
            }
        }
    }
}

@Composable
private fun ScanGlyph(color: Color) {
    Canvas(Modifier.size(24.dp)) {
        val s = size.width / 24f
        withTransform({ scale(s, s, Offset.Zero) }) {
            val stroke = 2f
            drawPolyline(listOf(Offset(3f, 7f), Offset(3f, 4f), Offset(4f, 3f), Offset(7f, 3f)), color, stroke)
            drawPolyline(listOf(Offset(17f, 3f), Offset(20f, 3f), Offset(21f, 4f), Offset(21f, 7f)), color, stroke)
            drawPolyline(listOf(Offset(21f, 17f), Offset(21f, 20f), Offset(20f, 21f), Offset(17f, 21f)), color, stroke)
            drawPolyline(listOf(Offset(7f, 21f), Offset(4f, 21f), Offset(3f, 20f), Offset(3f, 17f)), color, stroke)
            for (x in listOf(7f, 10f, 13f, 16f)) {
                drawLine(color, Offset(x, 8f), Offset(x, 16f), stroke)
            }
        }
    }
}

@Composable
private fun MenuGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val left = size.width * 0.2f
        val right = size.width * 0.8f
        for (fraction in listOf(0.3f, 0.5f, 0.7f)) {
            drawLine(
                color = color,
                start = Offset(left, size.height * fraction),
                end = Offset(right, size.height * fraction),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawPolyline(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    closed: Boolean = false,
) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        if (closed) close()
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

private fun DrawScope.drawOutlinePath(
    points: List<Offset>,
    color: Color,
    strokeWidth: Float,
    closed: Boolean = false,
) {
    drawPolyline(points, color, strokeWidth, closed)
}

private fun DrawScope.drawStar(
    color: Color,
    center: Offset,
    outerRadius: Float,
    innerRadius: Float,
) {
    val points = (0 until 8).map { index ->
        val angle = Math.PI * index / 4.0 - Math.PI / 2.0
        val radius = if (index % 2 == 0) outerRadius else innerRadius
        Offset(
            center.x + kotlin.math.cos(angle).toFloat() * radius,
            center.y + kotlin.math.sin(angle).toFloat() * radius,
        )
    }
    drawPolyline(points, color, 1.6f, closed = true)
}

private enum class HomeIcon {
    Receive,
    Inventory,
    Bware,
    Plan,
    History,
    Catalog,
    Transfer,
    Settings,
    Link,
    Tasks,
    Profile,
    Info,
    Documents,
    Gemini,
}

/**
 * Presentation-only tile metadata; all app destinations use WarehouseScreen.
 */
private data class HomeTile(
    val label: String,
    val destination: WarehouseScreen,
    val icon: HomeIcon,
    val lightIconColor: Color,
)

private val firstPageTiles = listOf(
    HomeTile("Приём товаров", WarehouseScreen.ReceiveHub, HomeIcon.Receive, Color(0xFF1B7F45)),
    HomeTile("Инвентар.", WarehouseScreen.Inventory, HomeIcon.Inventory, Color(0xFFB3261E)),
    HomeTile("B-Ware", WarehouseScreen.Bware, HomeIcon.Bware, Color(0xFFC4600B)),
    HomeTile("По заданию", WarehouseScreen.Plan, HomeIcon.Plan, Color(0xFF0B7C85)),
)

private val secondPageTiles = listOf(
    HomeTile("История", WarehouseScreen.History, HomeIcon.History, Color(0xFF0B7C85)),
    HomeTile("Каталог", WarehouseScreen.Catalog, HomeIcon.Catalog, Color(0xFF8A6A00)),
    HomeTile("Импорт / Экспорт", WarehouseScreen.Transfer, HomeIcon.Transfer, Color(0xFF7B3FA6)),
    HomeTile("Настройки", WarehouseScreen.Settings, HomeIcon.Settings, Color(0xFF5A6270)),
    HomeTile("Товар+ШК", WarehouseScreen.LinkTool, HomeIcon.Link, Color(0xFF1F62B8)),
    HomeTile("Задачи", WarehouseScreen.Tasks, HomeIcon.Tasks, Color(0xFF1B7F45)),
    HomeTile("Профиль", WarehouseScreen.Profile, HomeIcon.Profile, Color(0xFFB3261E)),
    HomeTile("Инфо", WarehouseScreen.Info, HomeIcon.Info, Color(0xFFC4600B)),
    HomeTile("Документы", WarehouseScreen.Documents, HomeIcon.Documents, Color(0xFFF783AC)),
    HomeTile("Чат Gemini", WarehouseScreen.Gemini, HomeIcon.Gemini, Color(0xFF7B3FA6)),
)