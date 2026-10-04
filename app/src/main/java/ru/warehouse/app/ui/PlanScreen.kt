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
    var planItems by remember {
        mutableStateOf(
            state.products.map { product ->
                PlanItem(
                    id = product.id,
                    name = product.name,
                    barcode = product.barcode,
                    sku = product.sku,
                    expectedQty = product.stock,
                    scannedQty = 0.0,
                    unit = product.unit
                )
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Приём по заданию") },
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
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Поиск по названию или штрихкоду") },
                modifier = Modifier.fillMaxWidth()
            )

            val filteredItems = planItems.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.barcode.contains(searchQuery, ignoreCase = true) ||
                        it.sku.contains(searchQuery, ignoreCase = true)
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredItems) { item ->
                    PlanItemRow(
                        item = item,
                        onQuantityChange = { newQty ->
                            planItems = planItems.map {
                                if (it.id == item.id) it.copy(scannedQty = newQty) else it
                            }
                        }
                    )
                }
            }

            Button(
                onClick = {
                    val results = planItems.map { item ->
                        val diff = item.scannedQty - item.expectedQty
                        val status = when {
                            diff == 0.0 -> CheckStatus.MATCH
                            diff < 0 -> CheckStatus.SHORTAGE
                            else -> CheckStatus.OVERAGE
                        }
                        CheckResult(
                            itemId = item.id,
                            name = item.name,
                            barcode = item.barcode,
                            expectedQty = item.expectedQty,
                            actualQty = item.scannedQty,
                            difference = diff,
                            status = status,
                            unit = item.unit
                        )
                    }
                    onSavePlan(results)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Завершить и сохранить сверку")
            }
        }
    }
}

@Composable
private fun PlanItemRow(
    item: PlanItem,
    onQuantityChange: (Double) -> Unit
) {
    val diff = item.scannedQty - item.expectedQty
    val statusColor = when {
        item.scannedQty == 0.0 -> Color.Gray
        diff == 0.0 -> Color(0xFF2E7D32)
        diff < 0 -> Color(0xFFE65100)
        else -> Color(0xFFC62828)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name.ifEmpty { "Товар без названия" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Штрихкод: ${item.barcode.ifEmpty { "—" }} | SKU: ${item.sku.ifEmpty { "—" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "План: ${item.expectedQty} ${item.unit} | Факт: ${item.scannedQty} ${item.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (item.scannedQty > 0) onQuantityChange(item.scannedQty - 1) }
                ) {
                    Text("-", style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    text = "${item.scannedQty.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                IconButton(
                    onClick = { onQuantityChange(item.scannedQty + 1) }
                ) {
                    Text("+", style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
    }
}
