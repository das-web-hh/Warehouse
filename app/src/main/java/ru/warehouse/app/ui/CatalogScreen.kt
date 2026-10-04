package ru.warehouse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState
import java.text.Collator
import java.util.Locale

/**
 * Full-screen product catalog corresponding to the HTML baseModal.
 * Product data and catalog mutations remain owned by the app.
 */
@Composable
fun CatalogScreen(
    state: WarehouseState,
    onBack: () -> Unit,
    onProductSelected: (Product) -> Unit,
    onEditProduct: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val light = !state.darkTheme

    val sortedProducts = remember(state.products) {
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
        state.products.sortedWith(Comparator { first, second ->
            collator.compare(first.name, second.name)
        })
    }
    val visibleProducts = remember(sortedProducts, query) {
        val normalizedQuery = query.trim().lowercase(Locale.getDefault())
        if (normalizedQuery.length < 3) {
            sortedProducts
        } else {
            val tokens = normalizedQuery.split(Regex("\\s+")).filter(String::isNotBlank)
            sortedProducts.filter { product ->
                val searchable = "${product.name} ${product.barcode} ${product.sku}"
                    .lowercase(Locale.getDefault())
                tokens.all { token -> searchable.contains(token) }
            }
        }
    }

    val surface = if (light) Color(0xFFF6F8FC) else Color(0xFF0F1114)
    val appBar = if (light) Color(0xFF23478F) else Color(0xFF1A1D23)
    val searchSurface = if (light) Color.White else Color(0xFF1E2229)
    val outline = if (light) Color(0xFFC9CFDB) else Color(0xFF2A2F37)
    val textColor = if (light) Color(0xFF171B22) else Color(0xFFE3E5EA)
    val subTextColor = if (light) Color(0xFF5F6672) else Color.White.copy(alpha = 0.5f)
    val mutedSearchHint = if (light) Color(0xFF7B8794) else Color.White.copy(alpha = 0.3f)

    BackHandler(onBack = onBack)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .testTag(WarehouseScreen.Catalog.toString()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp)
                .background(appBar)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 13.dp, end = 13.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Каталог",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }

        BasicTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 4.dp)
                .heightIn(min = 39.dp)
                .onFocusChanged { searchFocused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(
                color = if (light) Color(0xFF212529) else textColor,
                fontSize = 14.sp,
            ),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(searchSurface)
                        .border(
                            1.dp,
                            if (searchFocused) {
                                if (light) Color(0xFF2B5CB0) else Color(0xFF3F7BD6)
                            } else {
                                outline
                            },
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                text = "…",
                                color = mutedSearchHint,
                                fontSize = 14.sp,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )

        if (visibleProducts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Нет данных.",
                        color = subTextColor,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Text(
                        text = "Добавьте товары через Импорт",
                        color = subTextColor,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    top = 12.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(visibleProducts, key = { it.id }) { product ->
                    CatalogProductRow(
                        product = product,
                        light = light,
                        textColor = textColor,
                        subTextColor = subTextColor,
                        outline = if (light) Color(0xFFDEE2E6) else Color.White.copy(alpha = 0.08f),
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onProductSelected(product)
                        },
                        onEdit = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onEditProduct(product)
                        },
                        onDelete = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onDeleteProduct(product)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogProductRow(
    product: Product,
    light: Boolean,
    textColor: Color,
    subTextColor: Color,
    outline: Color,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowBackground = if (light) Color.White else Color(0xFF171A1F)
    val metadata = listOf(product.barcode, product.sku)
        .filter(String::isNotBlank)
        .joinToString("  ·  ")
        .ifBlank { "—" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(rowBackground)
            .border(1.5.dp, outline, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = product.name,
                modifier = Modifier.fillMaxWidth(),
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = metadata,
                    modifier = Modifier.weight(1f),
                    color = subTextColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                CatalogRowAction(
                    label = "✎",
                    description = "Редактировать ${product.name}",
                    color = if (light) Color(0xFF2B5CB0) else Color(0xFF3F7BD6),
                    onClick = onEdit,
                )
                CatalogRowAction(
                    label = "🗑",
                    description = "Удалить ${product.name}",
                    color = if (light) Color(0xFFDC3545) else Color(0xFFE57373),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun CatalogRowAction(
    label: String,
    description: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 22.dp, height = 19.dp)
            .clip(RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = if (label == "✎") 14.sp else 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}