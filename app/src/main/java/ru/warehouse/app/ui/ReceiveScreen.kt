@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Presentation data for the product suggestion list in manual receiving.
 * The host app remains the source of product/catalog data.
 */
data class ReceiveProductUiState(
    val id: String,
    val name: String,
    val barcode: String = "",
    val article: String = "",
)

/**
 * Presentation data for an item already added to the current receive batch.
 * This is not a replacement for the host app's persisted product model.
 */
data class ReceiveItemUiState(
    val id: String,
    val name: String,
    val quantity: Int,
    val barcode: String = "",
    val article: String = "",
    val senderName: String = "",
    val orderNumber: String = "",
)

/**
 * Manual receiving screen. Search/catalog access, barcode scanning, creating
 * unknown products, and committing the batch are supplied by the host app.
 */
@Composable
fun ReceiveScreen(
    items: List<ReceiveItemUiState>,
    searchQuery: String,
    suggestions: List<ReceiveProductUiState>,
    onSearchQueryChange: (String) -> Unit,
    onParsePastedItems: (String) -> Unit,
    onScanBarcode: () -> Unit,
    onAddProduct: (ReceiveProductUiState, Int) -> Unit,
    onQuantityChange: (itemId: String, quantity: Int) -> Unit,
    onRemoveItem: (itemId: String) -> Unit,
    onAccept: () -> Unit,
    onCreateUnknownProduct: suspend (barcode: String, name: String) -> ReceiveProductUiState,
    onUnknownProductNameChange: (String) -> Unit,
    onDismissUnknownProduct: () -> Unit,
    modifier: Modifier = Modifier,
    isSearching: Boolean = false,
    searchMessage: String? = null,
    unknownBarcode: String? = null,
    unknownNameSuggestions: List<ReceiveProductUiState> = emptyList(),
    unknownProductStatus: String? = null,
    isAccepting: Boolean = false,
) {
    val colors = receiveColors(isSystemInDarkTheme())
    val coroutineScope = rememberCoroutineScope()
    val itemCount = items.size
    val totalQuantity = items.sumOf { it.quantity.coerceAtLeast(0) }
    val isReadyToAccept = items.isNotEmpty() && items.all { it.quantity > 0 } && !isAccepting

    var selectedProduct by remember { mutableStateOf<ReceiveProductUiState?>(null) }
    var editingItem by remember { mutableStateOf<ReceiveItemUiState?>(null) }
    var quantityText by remember { mutableStateOf("") }
    var unknownProductName by remember(unknownBarcode) { mutableStateOf("") }
    var localUnknownProductError by remember(unknownBarcode) { mutableStateOf<String?>(null) }
    var isCreatingUnknownProduct by remember { mutableStateOf(false) }
    var dismissedUnknownBarcode by remember(unknownBarcode) { mutableStateOf<String?>(null) }
    var hideSearchDropdown by remember { mutableStateOf(false) }

    fun startAddingProduct(product: ReceiveProductUiState) {
        hideSearchDropdown = true
        selectedProduct = product
        editingItem = null
        quantityText = ""
    }

    fun startEditingQuantity(item: ReceiveItemUiState) {
        selectedProduct = null
        editingItem = item
        quantityText = item.quantity.toString()
    }

    fun closeQuantityDialog() {
        selectedProduct = null
        editingItem = null
        quantityText = ""
    }

    fun updateSearch(value: String) {
        hideSearchDropdown = false
        if (value.contains('\n')) {
            onParsePastedItems(value)
            onSearchQueryChange("")
        } else {
            onSearchQueryChange(value)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        ReceiveHeader()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(start = 14.dp, top = 7.dp, end = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = ::updateSearch,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Название или штрихкод") },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        suggestions.firstOrNull()?.let(::startAddingProduct)
                    },
                ),
            )
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .semantics { contentDescription = "Сканировать EAN" }
                    .clickable(role = Role.Button, onClick = onScanBarcode),
                shape = RoundedCornerShape(14.dp),
                color = colors.scanButton,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("▦", color = colors.primary, fontSize = 25.sp)
                }
            }
        }

        if (!hideSearchDropdown &&
            searchQuery.trim().length >= 3 &&
            (suggestions.isNotEmpty() || isSearching || !searchMessage.isNullOrBlank())
        ) {
            SearchSuggestions(
                suggestions = suggestions,
                isSearching = isSearching,
                searchMessage = searchMessage,
                colors = colors,
                onSelect = ::startAddingProduct,
            )
        }

        Text(
            text = if (itemCount == 0) {
                "Список пуст — найдите товар выше"
            } else {
                "$itemCount поз. · $totalQuantity шт"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 7.dp),
            color = colors.muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Добавленные товары появятся здесь.",
                    color = colors.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 14.dp,
                    top = 2.dp,
                    end = 14.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = items,
                    key = { index, item ->
                        item.id.ifBlank { "receive-$index-${item.name}" }
                    },
                ) { _, item ->
                    ReceiveItemCard(
                        item = item,
                        colors = colors,
                        onDecrement = {
                            onQuantityChange(item.id, (item.quantity - 1).coerceAtLeast(0))
                        },
                        onIncrement = {
                            onQuantityChange(item.id, item.quantity + 1)
                        },
                        onEditQuantity = { startEditingQuantity(item) },
                        onRemove = { onRemoveItem(item.id) },
                    )
                }
            }
        }

        if (items.isNotEmpty()) {
            ReceiveFooter(
                itemCount = itemCount,
                totalQuantity = totalQuantity,
                enabled = isReadyToAccept,
                isAccepting = isAccepting,
                colors = colors,
                onAccept = onAccept,
            )
        }
    }

    val productForQuantity = selectedProduct
    val itemForQuantity = editingItem
    if (productForQuantity != null || itemForQuantity != null) {
        val title = productForQuantity?.name ?: itemForQuantity?.name.orEmpty()
        AlertDialog(
            onDismissRequest = ::closeQuantityDialog,
            title = { Text("Количество товара") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = title,
                        color = colors.muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { input ->
                            quantityText = input.filter { it.isDigit() }.take(5)
                        },
                        label = { Text("Количество") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = quantityText.toIntOrNull()?.let { it > 0 } == true,
                    onClick = {
                        val quantity = quantityText.toIntOrNull()
                        if (quantity != null && quantity > 0) {
                            if (productForQuantity != null) {
                                onAddProduct(productForQuantity, quantity)
                                onSearchQueryChange("")
                            } else if (itemForQuantity != null) {
                                onQuantityChange(itemForQuantity.id, quantity)
                            }
                            closeQuantityDialog()
                        }
                    },
                ) {
                    Text(if (productForQuantity != null) "Добавить" else "Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = ::closeQuantityDialog) {
                    Text("Отмена")
                }
            },
        )
    }

    val barcode = unknownBarcode
        ?.takeIf { it.isNotBlank() }
        ?.takeUnless { it == dismissedUnknownBarcode }
    if (barcode != null) {
        fun dismissUnknownProduct() {
            dismissedUnknownBarcode = barcode
            localUnknownProductError = null
            onDismissUnknownProduct()
        }

        AlertDialog(
            onDismissRequest = ::dismissUnknownProduct,
            title = { Text("Товар не найден") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = "EAN: $barcode",
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = unknownProductName,
                        onValueChange = {
                            unknownProductName = it
                            localUnknownProductError = null
                            onUnknownProductNameChange(it)
                        },
                        label = { Text("Название товара") },
                        singleLine = true,
                    )
                    if (unknownNameSuggestions.isNotEmpty()) {
                        UnknownNameSuggestions(
                            suggestions = unknownNameSuggestions,
                            colors = colors,
                            onSelect = { product ->
                                unknownProductName = product.name
                                localUnknownProductError = null
                                onUnknownProductNameChange(product.name)
                            },
                        )
                    }
                    val status = localUnknownProductError ?: unknownProductStatus
                    if (!status.isNullOrBlank()) {
                        Text(
                            text = status,
                            color = colors.error,
                            fontSize = 12.sp,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = unknownProductName.isNotBlank() && !isCreatingUnknownProduct,
                    onClick = {
                        val name = unknownProductName.trim()
                        if (name.isNotEmpty() && !isCreatingUnknownProduct) {
                            coroutineScope.launch {
                                isCreatingUnknownProduct = true
                                localUnknownProductError = null
                                try {
                                    val product = onCreateUnknownProduct(barcode, name)
                                        .copy(barcode = barcode)
                                    dismissUnknownProduct()
                                    startAddingProduct(product)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    localUnknownProductError = error.message
                                        ?.takeIf { it.isNotBlank() }
                                        ?: "Не удалось сохранить товар"
                                } finally {
                                    isCreatingUnknownProduct = false
                                }
                            }
                        }
                    },
                ) {
                    Text(if (isCreatingUnknownProduct) "Сохраняем…" else "Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = ::dismissUnknownProduct) {
                    Text("Пропустить")
                }
            },
        )
    }
}

@Composable
private fun ReceiveHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F1F38), Color(0xFF1A2F4A)),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .height(54.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Принять товар",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun SearchSuggestions(
    suggestions: List<ReceiveProductUiState>,
    isSearching: Boolean,
    searchMessage: String?,
    colors: ManualReceiveColors,
    onSelect: (ReceiveProductUiState) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 4.dp,
    ) {
        when {
            isSearching -> Text(
                text = "Поиск товара…",
                modifier = Modifier.padding(14.dp),
                color = colors.muted,
                fontSize = 13.sp,
            )
            suggestions.isEmpty() -> Text(
                text = searchMessage?.takeIf { it.isNotBlank() } ?: "Товар не найден",
                modifier = Modifier.padding(14.dp),
                color = colors.muted,
                fontSize = 13.sp,
            )
            else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(
                    items = suggestions.take(12),
                    key = { product -> product.id },
                ) { product ->
                    SuggestionRow(
                        product = product,
                        colors = colors,
                        onClick = { onSelect(product) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UnknownNameSuggestions(
    suggestions: List<ReceiveProductUiState>,
    colors: ManualReceiveColors,
    onSelect: (ReceiveProductUiState) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 170.dp),
        shape = RoundedCornerShape(12.dp),
        color = colors.background,
        border = BorderStroke(1.dp, colors.border),
    ) {
        LazyColumn {
            items(
                items = suggestions.take(8),
                key = { product -> product.id },
            ) { product ->
                SuggestionRow(
                    product = product,
                    colors = colors,
                    onClick = { onSelect(product) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    product: ReceiveProductUiState,
    colors: ManualReceiveColors,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = product.name,
            color = colors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        val details = listOf(product.barcode, product.article)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (details.isNotBlank()) {
            Text(
                text = details,
                color = colors.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReceiveItemCard(
    item: ReceiveItemUiState,
    colors: ManualReceiveColors,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onEditQuantity: () -> Unit,
    onRemove: () -> Unit,
) {
    val isZero = item.quantity < 1
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = BorderStroke(
            width = if (isZero) 1.5.dp else 1.dp,
            color = if (isZero) colors.error else colors.border,
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 13.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (item.senderName.isNotBlank()) {
                    Text(
                        text = item.senderName,
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = item.name,
                    color = colors.text,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                val details = buildList {
                    if (item.barcode.isNotBlank()) add(item.barcode)
                    if (item.article.isNotBlank()) add(item.article)
                    if (item.orderNumber.isNotBlank()) add("Заказ: ${item.orderNumber}")
                }.joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        color = colors.muted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
            StepButton(
                label = "−",
                description = "Уменьшить количество",
                colors = colors,
                onClick = onDecrement,
            )
            Surface(
                modifier = Modifier
                    .width(38.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .semantics { contentDescription = "Изменить количество: ${item.quantity}" }
                    .clickable(role = Role.Button, onClick = onEditQuantity),
                shape = RoundedCornerShape(11.dp),
                color = Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = item.quantity.toString(),
                        color = if (isZero) colors.error else colors.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            StepButton(
                label = "+",
                description = "Увеличить количество",
                colors = colors,
                onClick = onIncrement,
            )
            Surface(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = "Удалить ${item.name}" }
                    .clickable(role = Role.Button, onClick = onRemove),
                shape = CircleShape,
                color = Color.Transparent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("✕", color = colors.muted, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun StepButton(
    label: String,
    description: String,
    colors: ManualReceiveColors,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onClick),
        shape = CircleShape,
        color = colors.stepButton,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun ReceiveFooter(
    itemCount: Int,
    totalQuantity: Int,
    enabled: Boolean,
    isAccepting: Boolean,
    colors: ManualReceiveColors,
    onAccept: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$itemCount поз.",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "$totalQuantity шт",
                    color = colors.muted,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = onAccept,
                enabled = enabled,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 17.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (isAccepting) "Принимаем…" else "Принять",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private data class ManualReceiveColors(
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val primary: Color,
    val border: Color,
    val scanButton: Color,
    val stepButton: Color,
    val error: Color,
)

private fun receiveColors(darkTheme: Boolean) = if (darkTheme) {
    ManualReceiveColors(
        background = Color(0xFF101318),
        card = Color(0xFF171A1F),
        text = Color(0xFFE8EAED),
        muted = Color(0xFF9AA3AF),
        primary = Color(0xFF8AB4F8),
        border = Color(0xFF363C46),
        scanButton = Color(0xFF20252D),
        stepButton = Color(0xFF252B34),
        error = Color(0xFFFF8A80),
    )
} else {
    ManualReceiveColors(
        background = Color(0xFFF6F8FC),
        card = Color.White,
        text = Color(0xFF171B22),
        muted = Color(0xFF414853),
        primary = Color(0xFF2B5CB0),
        border = Color(0xFFC9CFDB),
        scanButton = Color(0xFFE9EDF5),
        stepButton = Color(0xFFE9EDF5),
        error = Color(0xFFC92A2A),
    )
}
