@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState

@Composable
fun WarehouseApp(
    state: WarehouseState,
    onStateChange: (WarehouseState) -> Unit,
    onScanRequest: () -> Unit = {},
    scanResult: String? = null,
) {
    var currentScreen by remember { mutableStateOf<WarehouseScreen>(WarehouseScreen.Home) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var isAddingProduct by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == WarehouseScreen.Home,
                    onClick = { currentScreen = WarehouseScreen.Home },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
                    label = { Text("Главная") }
                )
                NavigationBarItem(
                    selected = currentScreen == WarehouseScreen.Catalog,
                    onClick = { currentScreen = WarehouseScreen.Catalog },
                    icon = { Icon(Icons.Default.List, contentDescription = "Каталог") },
                    label = { Text("Каталог") }
                )
                NavigationBarItem(
                    selected = currentScreen == WarehouseScreen.Settings,
                    onClick = { currentScreen = WarehouseScreen.Settings },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки") }
                )
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)

        when (currentScreen) {
            WarehouseScreen.Home -> {
                HomeScreen(
                    state = state,
                    onScreenSelected = { screen -> currentScreen = screen },
                    onScanClick = onScanRequest,
                    onProductSelected = { product ->
                        editingProduct = product
                        currentScreen = WarehouseScreen.Catalog
                    },
                    onMenuClick = { currentScreen = WarehouseScreen.Settings },
                    modifier = modifier,
                )
            }

            WarehouseScreen.Catalog -> {
                CatalogScreen(
                    state = state,
                    scanResult = scanResult,
                    onScan = onScanRequest,
                    onEdit = { product -> editingProduct = product },
                    onAdd = { isAddingProduct = true },
                    modifier = modifier,
                )
            }

            else -> {
                HomeScreen(
                    state = state,
                    onScreenSelected = { screen -> currentScreen = screen },
                    onScanClick = onScanRequest,
                    onProductSelected = { product ->
                        editingProduct = product
                        currentScreen = WarehouseScreen.Catalog
                    },
                    onMenuClick = { currentScreen = WarehouseScreen.Settings },
                    modifier = modifier,
                )
            }
        }

        if (editingProduct != null || isAddingProduct) {
            val targetProduct = editingProduct ?: Product(
                id = System.currentTimeMillis().toString(),
                name = "",
                barcode = scanResult ?: "",
                sku = "",
                category = "",
                unit = "шт",
                stock = 0.0,
                bwareStock = 0.0,
            )

            ProductEditorDialog(
                product = targetProduct,
                onDismiss = {
                    editingProduct = null
                    isAddingProduct = false
                },
                onSave = { updatedProduct ->
                    val updatedList = if (editingProduct != null) {
                        state.products.map { if (it.id == updatedProduct.id) updatedProduct else it }
                    } else {
                        state.products + updatedProduct
                    }
                    onStateChange(state.copy(products = updatedList))
                    editingProduct = null
                    isAddingProduct = false
                },
                onDelete = { productId ->
                    val updatedList = state.products.filterNot { it.id == productId }
                    onStateChange(state.copy(products = updatedList))
                    editingProduct = null
                    isAddingProduct = false
                },
            )
        }
    }
}
