@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.warehouse.app.data.CheckResult
import ru.warehouse.app.data.CheckStatus
import ru.warehouse.app.data.PlanItem
import ru.warehouse.app.data.WarehouseState

@Composable
fun PlanScreen(
    state: WarehouseState,
    onBack: () -> Unit = {},
    onScanClick: () -> Unit = {},
    onSavePlan: (List<CheckResult>) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var planItems by remember(state.products) {
        mutableStateOf(
            state.products.map { product ->
                PlanItem(
                    product = product,
                    scannedQuantity = 0.0,
                    status = CheckStatus.PENDING
                )
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Инвентаризация / План") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onScanClick) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Сканировать")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск по наименованию или штрихкоду") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            val filteredItems = remember(planItems, searchQuery) {
                if (searchQuery.isBlank()) {
                    planItems
                } else {
                    planItems.filter {
                        it.product.name.contains(searchQuery, ignoreCase = true) ||
                                it.product.barcode.contains(searchQuery, ignoreCase = true)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredItems, key = { it.product.id }) { item ->
                    PlanItemCard(
                        item = item,
                        onQuantityChange = { newQty ->
                            planItems = planItems.map {
                                if (it.product.id == item.product.id) {
                                    val status = when {
                                        newQty == 0.0 -> CheckStatus.PENDING
                                        newQty == it.product.quantity -> CheckStatus.MATCH
                                        else -> CheckStatus.MISMATCH
                                    }
                                    it.copy(scannedQuantity = newQty, status = status)
                                } else {
                                    it
                                }
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val results = planItems.map {
                        CheckResult(
                            productId = it.product.id,
                            expectedQty = it.product.quantity,
                            scannedQty = it.scannedQuantity,
                            status = it.status
                        )
                    }
                    onSavePlan(results)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить результат")
            }
        }
    }
}

@Composable
private fun PlanItemCard(
    item: PlanItem,
    onQuantityChange: (Double) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (item.status) {
                CheckStatus.MATCH -> Color(0xFFE8F5E9)
                CheckStatus.MISMATCH -> Color(0xFFFFEBEE)
                CheckStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Штрихкод: ${item.product.barcode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "План: ${item.product.quantity} ${item.product.unit}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (item.status) {
                        CheckStatus.MATCH -> Icons.Default.CheckCircle
                        CheckStatus.MISMATCH -> Icons.Default.Warning
                        CheckStatus.PENDING -> Icons.Default.QrCodeScanner
                    },
                    contentDescription = null,
                    tint = when (item.status) {
                        CheckStatus.MATCH -> Color(0xFF2E7D32)
                        CheckStatus.MISMATCH -> Color(0xFFC62828)
                        CheckStatus.PENDING -> Color.Gray
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = formatQuantity(item.scannedQuantity),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onQuantityChange(item.scannedQuantity + 1.0) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
