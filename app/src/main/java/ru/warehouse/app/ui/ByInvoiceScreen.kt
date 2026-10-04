package ru.warehouse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.warehouse.app.data.Invoice
import ru.warehouse.app.data.InvoiceItem
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Warehouse invoice receiving screen.
 *
 * File reading, camera capture, persistence, printing, and export remain owned
 * by the host app. This screen only renders the existing Invoice models and
 * reports user actions through callbacks.
 */
@Composable
fun ByInvoiceScreen(
    invoices: List<Invoice>,
    onSelectFile: () -> Unit,
    onTakePhoto: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    initialInvoice: Invoice? = null,
    isProcessing: Boolean = false,
    processingMessage: String = "Обработка накладной…",
    onActualQuantityChange: (invoice: Invoice, item: InvoiceItem, quantity: Double) -> Unit = { _, _, _ -> },
    onAddItem: (invoice: Invoice) -> Unit = {},
    onSave: (invoice: Invoice) -> Unit = {},
    onExport: (invoice: Invoice) -> Unit = {},
    onPrint: (invoice: Invoice) -> Unit = {},
    onExit: () -> Unit = {},
) {
    val darkTheme = isSystemInDarkTheme()
    val colors = invoiceColors(darkTheme)
    var selectedInvoiceId by remember(initialInvoice?.id) {
        mutableStateOf(initialInvoice?.id?.takeIf { it.isNotBlank() })
    }
    var currentPage by remember(initialInvoice?.id) {
        mutableStateOf(if (initialInvoice == null) InvoicePage.Start else InvoicePage.Items)
    }
    var searchText by remember { mutableStateOf("") }
    var showUnverifiedOnly by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InvoiceItem?>(null) }
    var editingQuantity by remember { mutableStateOf("") }
    val quantityOverrides = remember { mutableStateMapOf<String, Double>() }

    val activeInvoice = invoices.firstOrNull { it.id == selectedInvoiceId }
        ?: initialInvoice?.takeIf { it.id == selectedInvoiceId }

    fun currentQuantity(invoice: Invoice, item: InvoiceItem): Double =
        quantityOverrides[invoiceItemKey(invoice, item)] ?: item.scannedQty

    fun changeQuantity(invoice: Invoice, item: InvoiceItem, quantity: Double) {
        quantityOverrides[invoiceItemKey(invoice, item)] = quantity
        onActualQuantityChange(invoice, item, quantity)
    }

    fun returnToPreviousPage() {
        when (currentPage) {
            InvoicePage.Report -> currentPage = InvoicePage.Items
            InvoicePage.Items -> {
                currentPage = InvoicePage.Start
                searchText = ""
            }
            InvoicePage.Start -> onExit()
        }
    }

    BackHandler(enabled = currentPage != InvoicePage.Start || editingItem != null) {
        if (editingItem != null) {
            editingItem = null
        } else {
            returnToPreviousPage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        InvoiceHeader(
            title = "Приём товаров",
            invoice = activeInvoice,
            showReportButton = currentPage == InvoicePage.Items &&
                activeInvoice?.items?.isNotEmpty() == true,
            onOpenReport = {
                showUnverifiedOnly = false
                currentPage = InvoicePage.Report
            },
        )

        when (currentPage) {
            InvoicePage.Start -> Box(Modifier.weight(1f).fillMaxWidth()) {
                StartPage(
                    invoices = invoices,
                    colors = colors,
                    isProcessing = isProcessing,
                    processingMessage = processingMessage,
                    onSelectFile = onSelectFile,
                    onOpenHistory = onOpenHistory,
                    onInvoiceClick = { invoice ->
                        selectedInvoiceId = invoice.id
                        searchText = ""
                        currentPage = InvoicePage.Items
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                FloatingActionButton(
                    onClick = onTakePhoto,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 24.dp)
                        .size(68.dp),
                    shape = CircleShape,
                    containerColor = colors.camera,
                    contentColor = Color.White,
                ) {
                    Text("📷", fontSize = 28.sp)
                }
            }

            InvoicePage.Items -> {
                if (activeInvoice == null) {
                    MissingInvoicePage(
                        colors = colors,
                        onReturn = { currentPage = InvoicePage.Start },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val filteredItems = remember(activeInvoice.id, activeInvoice.items, searchText) {
                        activeInvoice.items.filter {
                            it.name.contains(searchText.trim(), ignoreCase = true)
                        }
                    }
                    ItemListPage(
                        invoice = activeInvoice,
                        items = filteredItems,
                        query = searchText,
                        colors = colors,
                        quantityFor = { item -> currentQuantity(activeInvoice, item) },
                        onSearchChange = { searchText = it },
                        onAddItem = { onAddItem(activeInvoice) },
                        onItemTap = { item ->
                            changeQuantity(
                                activeInvoice,
                                item,
                                currentQuantity(activeInvoice, item) + 1.0,
                            )
                        },
                        onEditQuantity = { item ->
                            editingItem = item
                            editingQuantity = formatQuantity(currentQuantity(activeInvoice, item))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            InvoicePage.Report -> {
                if (activeInvoice == null) {
                    MissingInvoicePage(
                        colors = colors,
                        onReturn = { currentPage = InvoicePage.Start },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    ReportPage(
                        invoice = activeInvoice,
                        colors = colors,
                        quantityFor = { item -> currentQuantity(activeInvoice, item) },
                        showUnverifiedOnly = showUnverifiedOnly,
                        onFilterChange = { showUnverifiedOnly = it },
                        onSave = { onSave(activeInvoice) },
                        onExport = { onExport(activeInvoice) },
                        onPrint = { onPrint(activeInvoice) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    val itemBeingEdited = editingItem
    if (itemBeingEdited != null && activeInvoice != null) {
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = { Text("Количество товара") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        itemBeingEdited.name,
                        color = colors.muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "По накладной: ${formatQuantity(itemBeingEdited.quantity)} ${itemBeingEdited.unit}",
                        color = colors.muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = editingQuantity,
                        onValueChange = { value ->
                            if (value.all { it.isDigit() || it == '.' || it == ',' }) {
                                editingQuantity = value.replace(',', '.')
                            }
                        },
                        label = { Text("Фактическое количество") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newQuantity = editingQuantity.toDoubleOrNull()
                        if (newQuantity != null && newQuantity >= 0.0) {
                            changeQuantity(activeInvoice, itemBeingEdited, newQuantity)
                            editingItem = null
                        }
                    },
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun InvoiceHeader(
    title: String,
    invoice: Invoice?,
    showReportButton: Boolean,
    onOpenReport: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F1F38), Color(0xFF1A2F4A)),
                ),
            )
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .heightIn(min = 54.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (invoice != null && (invoice.number.isNotBlank() || invoice.date.isNotBlank())) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "№ ${invoice.number}",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    invoice.date.ifBlank { "${invoice.items.size} позиций" },
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showReportButton) {
            Surface(
                modifier = Modifier
                    .size(34.dp)
                    .clickable(onClick = onOpenReport),
                shape = RoundedCornerShape(9.dp),
                color = Color.White.copy(alpha = 0.12f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("›", color = Color.White, fontSize = 24.sp, lineHeight = 24.sp)
                }
            }
        }
    }
}

@Composable
private fun StartPage(
    invoices: List<Invoice>,
    colors: InvoiceColors,
    isProcessing: Boolean,
    processingMessage: String,
    onSelectFile: () -> Unit,
    onOpenHistory: () -> Unit,
    onInvoiceClick: (Invoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(colors.background)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelectFile),
            shape = RoundedCornerShape(16.dp),
            color = colors.card,
            border = BorderStroke(2.dp, colors.dropZone),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("📁", fontSize = 28.sp)
                Text(
                    "Выбрать файл",
                    color = colors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Excel или PDF", color = colors.muted, fontSize = 12.sp)
            }
        }

        if (isProcessing) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = colors.processingBackground,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text("◌", color = colors.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(
                        processingMessage,
                        color = colors.primary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "ТЕКУЩАЯ СЕССИЯ ПРИЁМА",
                color = colors.muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
            Surface(
                shape = CircleShape,
                color = colors.border,
            ) {
                Text(
                    invoices.size.toString(),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    color = colors.muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (invoices.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = colors.card,
                border = BorderStroke(1.dp, colors.dropZone),
            ) {
                Text(
                    "Здесь появятся успешно распознанные накладные.",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                    color = colors.muted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                itemsIndexed(
                    items = invoices,
                    key = { index, invoice ->
                        invoice.id.ifBlank { "invoice-$index-${invoice.number}" }
                    },
                ) { _, invoice ->
                    InvoiceRegistryCard(
                        invoice = invoice,
                        colors = colors,
                        onClick = { onInvoiceClick(invoice) },
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenHistory),
            shape = RoundedCornerShape(16.dp),
            color = colors.card,
            border = BorderStroke(1.dp, colors.border),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text("🕒", fontSize = 17.sp)
                Spacer(Modifier.size(8.dp))
                Text(
                    "История сканирований",
                    color = colors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun InvoiceRegistryCard(
    invoice: Invoice,
    colors: InvoiceColors,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        invoice.supplier.ifBlank { "Поставщик не указан" },
                        color = colors.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        invoice.number.ifBlank { "Без номера" },
                        color = colors.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                }
                StatusPill(
                    text = if (invoice.isCompleted) "Завершено" else "В работе",
                    complete = invoice.isCompleted,
                    colors = colors,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    invoice.date.ifBlank { "Дата не указана" },
                    color = colors.muted,
                    fontSize = 12.sp,
                )
                Text(
                    "${invoice.items.size} позиций",
                    color = colors.muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    complete: Boolean,
    colors: InvoiceColors,
) {
    Surface(
        shape = CircleShape,
        color = if (complete) colors.completeBackground else colors.pendingBackground,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = if (complete) colors.completeText else colors.pendingText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun ItemListPage(
    invoice: Invoice,
    items: List<InvoiceItem>,
    query: String,
    colors: InvoiceColors,
    quantityFor: (InvoiceItem) -> Double,
    onSearchChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onItemTap: (InvoiceItem) -> Unit,
    onEditQuantity: (InvoiceItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.searchBar)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InvoiceSearchField(
                value = query,
                onValueChange = onSearchChange,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            Surface(
                modifier = Modifier
                    .size(width = 40.dp, height = 42.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onAddItem),
                shape = RoundedCornerShape(9.dp),
                color = colors.addButton,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+", color = Color.White, fontSize = 24.sp, lineHeight = 24.sp)
                }
            }
        }

        if (items.isEmpty()) {
            Text(
                if (invoice.items.isEmpty()) "В накладной пока нет товаров" else "Ничего не найдено",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                color = colors.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 10.dp),
            ) {
                itemsIndexed(
                    items = items,
                    key = { index, item ->
                        invoiceItemKey(invoice, item).ifBlank { "row-$index" }
                    },
                ) { index, item ->
                    InvoiceItemRow(
                        item = item,
                        actualQuantity = quantityFor(item),
                        colors = colors,
                        zebra = index % 2 == 1,
                        onTap = { onItemTap(item) },
                        onEditQuantity = { onEditQuantity(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    colors: InvoiceColors,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(9.dp),
        color = colors.searchField,
        border = BorderStroke(1.dp, colors.searchBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = colors.text,
                    fontSize = 14.sp,
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text("Поиск товара…", color = colors.placeholder, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}

@Composable
private fun InvoiceItemRow(
    item: InvoiceItem,
    actualQuantity: Double,
    colors: InvoiceColors,
    zebra: Boolean,
    onTap: () -> Unit,
    onEditQuantity: () -> Unit,
) {
    val completed = actualQuantity > 0.0 && actualQuantity == item.quantity
    val mismatch = actualQuantity > 0.0 && actualQuantity != item.quantity
    val rowColor = when {
        completed -> colors.rowComplete
        mismatch -> colors.rowMismatch
        zebra -> colors.rowAlternate
        else -> colors.card
    }
    val textColor = if (completed || mismatch) Color.White else colors.text
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .background(rowColor)
            .clickable(onClick = onTap)
            .padding(start = if (completed || mismatch) 8.dp else 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (completed || mismatch) 0.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                item.name,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Пл: ${formatQuantity(item.quantity)} ${item.unit}",
                color = if (completed || mismatch) Color.White.copy(alpha = 0.78f) else colors.muted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        Surface(
            modifier = Modifier
                .heightIn(min = 38.dp)
                .clickable(onClick = onEditQuantity),
            shape = RoundedCornerShape(10.dp),
            color = if (completed) colors.completeQuantity else if (mismatch) colors.mismatchQuantity else colors.quantityButton,
            border = BorderStroke(
                1.dp,
                if (completed) colors.completeBorder else if (mismatch) colors.mismatchBorder else colors.quantityBorder,
            ),
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    formatQuantity(actualQuantity),
                    color = if (completed || mismatch) Color.White else colors.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

@Composable
private fun ReportPage(
    invoice: Invoice,
    colors: InvoiceColors,
    quantityFor: (InvoiceItem) -> Double,
    showUnverifiedOnly: Boolean,
    onFilterChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val matchedCount = invoice.items.count { quantityFor(it) == it.quantity }
    val mismatchCount = invoice.items.size - matchedCount
    val displayedItems = if (showUnverifiedOnly) {
        invoice.items.filter { quantityFor(it) == 0.0 }
    } else {
        invoice.items
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.searchBar),
        ) {
            if (maxWidth < 460.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ReportActions(colors, onPrint, onExport, onSave)
                    Text(
                        "Совпало: $matchedCount · Расхождений: $mismatchCount",
                        modifier = Modifier.align(Alignment.End),
                        color = colors.muted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    ReportActions(colors, onPrint, onExport, onSave)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Совпало: $matchedCount · Расхождений: $mismatchCount",
                        modifier = Modifier.weight(1f, fill = false),
                        color = colors.muted,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.searchBar)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ReportFilterButton(
                text = "Все",
                selected = !showUnverifiedOnly,
                colors = colors,
                onClick = { onFilterChange(false) },
            )
            ReportFilterButton(
                text = "Не сверялось",
                selected = showUnverifiedOnly,
                colors = colors,
                onClick = { onFilterChange(true) },
            )
        }

        if (displayedItems.isEmpty()) {
            Text(
                if (invoice.items.isEmpty()) "В накладной пока нет товаров" else "Нет товаров для отображения",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                color = colors.muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                itemsIndexed(
                    items = displayedItems,
                    key = { index, item -> invoiceItemKey(invoice, item).ifBlank { "report-$index" } },
                ) { index, item ->
                    ReportItemRow(
                        item = item,
                        actualQuantity = quantityFor(item),
                        colors = colors,
                        zebra = index % 2 == 1,
                    )
                }
            }
        }

        Text(
            "Приёмка по накладной · Warehouse",
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.searchBar)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = colors.placeholder,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReportActions(
    colors: InvoiceColors,
    onPrint: () -> Unit,
    onExport: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReportActionButton("🖨️ Печать", colors, onClick = onPrint)
        ReportActionButton("📊 Экспорт", colors, onClick = onExport)
        ReportActionButton("💾 Сохранить", colors, onClick = onSave, primary = true)
    }
}

@Composable
private fun ReportActionButton(
    text: String,
    colors: InvoiceColors,
    onClick: () -> Unit,
    primary: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (primary) colors.saveButton else colors.toolbarButton,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReportFilterButton(
    text: String,
    selected: Boolean,
    colors: InvoiceColors,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .heightIn(min = 34.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.primary else colors.toolbarButton,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (selected) Color.White else colors.text,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ReportItemRow(
    item: InvoiceItem,
    actualQuantity: Double,
    colors: InvoiceColors,
    zebra: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (zebra) colors.rowAlternate else colors.card)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "План: ${formatQuantity(item.quantity)} ${item.unit}",
                color = colors.placeholder,
                fontSize = 10.sp,
            )
        }
        Text(
            "Факт: ${formatQuantity(actualQuantity)}",
            color = colors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun MissingInvoicePage(
    colors: InvoiceColors,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Накладная не выбрана", color = colors.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Button(onClick = onReturn) { Text("К списку накладных") }
    }
}

private enum class InvoicePage {
    Start,
    Items,
    Report,
}

private data class InvoiceColors(
    val background: Color,
    val card: Color,
    val text: Color,
    val muted: Color,
    val placeholder: Color,
    val border: Color,
    val dropZone: Color,
    val primary: Color,
    val searchBar: Color,
    val searchField: Color,
    val searchBorder: Color,
    val addButton: Color,
    val quantityButton: Color,
    val quantityBorder: Color,
    val rowAlternate: Color,
    val rowComplete: Color,
    val rowMismatch: Color,
    val completeBackground: Color,
    val completeText: Color,
    val pendingBackground: Color,
    val pendingText: Color,
    val completeQuantity: Color,
    val completeBorder: Color,
    val mismatchQuantity: Color,
    val mismatchBorder: Color,
    val toolbarButton: Color,
    val saveButton: Color,
    val camera: Color,
    val processingBackground: Color,
)

private fun invoiceColors(dark: Boolean) = if (dark) {
    InvoiceColors(
        background = Color(0xFF0B1622),
        card = Color(0xFF14263A),
        text = Color(0xFFF3F6F8),
        muted = Color(0xFF9FB0C3),
        placeholder = Color(0xFF73879A),
        border = Color(0xFF243A52),
        dropZone = Color(0xFF3A5675),
        primary = Color(0xFF74A8FF),
        searchBar = Color(0xFF0D1A2D),
        searchField = Color(0xFF1A2F4A),
        searchBorder = Color(0xFF344B64),
        addButton = Color(0xFF2B8A3E),
        quantityButton = Color(0xFF0D1A2D),
        quantityBorder = Color(0xFF4B7095),
        rowAlternate = Color(0xFF10263E),
        rowComplete = Color(0xFF267A45),
        rowMismatch = Color(0xFF8F641F),
        completeBackground = Color(0xFF183E2B),
        completeText = Color(0xFF69DB7C),
        pendingBackground = Color(0xFF46391E),
        pendingText = Color(0xFFFFD166),
        completeQuantity = Color(0xFF267A45),
        completeBorder = Color(0xFF69DB7C),
        mismatchQuantity = Color(0xFF8F641F),
        mismatchBorder = Color(0xFFFFD166),
        toolbarButton = Color(0xFF1A2F4A),
        saveButton = Color(0xFF2B8A3E),
        camera = Color(0xFFF07818),
        processingBackground = Color(0xFF183454),
    )
} else {
    InvoiceColors(
        background = Color(0xFFF8FAFC),
        card = Color.White,
        text = Color(0xFF1E293B),
        muted = Color(0xFF64748B),
        placeholder = Color(0xFF94A3B8),
        border = Color(0xFFE2E8F0),
        dropZone = Color(0xFFCBD5E1),
        primary = Color(0xFF2563EB),
        searchBar = Color(0xFFE8EEF6),
        searchField = Color.White,
        searchBorder = Color(0xFFCBD5E1),
        addButton = Color(0xFF2B8A3E),
        quantityButton = Color.White,
        quantityBorder = Color(0xFF93C5FD),
        rowAlternate = Color(0xFFF1F5F9),
        rowComplete = Color(0xFF267A45),
        rowMismatch = Color(0xFF8F641F),
        completeBackground = Color(0xFFDCFCE7),
        completeText = Color(0xFF15803D),
        pendingBackground = Color(0xFFFEF3C7),
        pendingText = Color(0xFFA16207),
        completeQuantity = Color(0xFF267A45),
        completeBorder = Color(0xFF69DB7C),
        mismatchQuantity = Color(0xFF8F641F),
        mismatchBorder = Color(0xFFFFD166),
        toolbarButton = Color(0xFFCBD5E1),
        saveButton = Color(0xFF2B8A3E),
        camera = Color(0xFFF07818),
        processingBackground = Color(0xFFDBEAFE),
    )
}

private fun invoiceItemKey(invoice: Invoice, item: InvoiceItem): String =
    "${invoice.id}:${item.id.ifBlank { item.sku.ifBlank { item.name } }}"

private fun formatQuantity(value: Double): String {
    val nearestInteger = value.roundToLong()
    return if (abs(value - nearestInteger) < 0.000001) {
        nearestInteger.toString()
    } else {
        value.toString().trimEnd('0').trimEnd('.')
    }
}
