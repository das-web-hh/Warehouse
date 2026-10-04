@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
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

            // Заглушки для остальных экранов (будут обновляться по мере разработки)
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

        // Диалог редактирования / создания товара
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

        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(enabled = screen != WarehouseScreen.Home || scannerOpen || editorProduct != null) {
        when {
            scannerOpen -> scannerOpen = false
            editorProduct != null -> editorProduct = null
            else -> goBack()
        }
    }

    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.takeIf(String::isNotBlank)?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            WarehouseTopBar(
                screen = screen,
                onBack = goBack,
                onSettings = { screen = WarehouseScreen.Settings },
            )
        },
        bottomBar = {
            WarehouseBottomBar(
                selected = screen,
                onSelect = { target ->
                    screen = if (target == WarehouseScreen.Receive) WarehouseScreen.ReceiveHub else target
                    scanResult = null
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost, modifier = Modifier.padding(bottom = 3.dp)) },
    ) { innerPadding ->
        when (screen) {
            WarehouseScreen.Home -> HomeScreen(
                state = state,
                search = homeSearch,
                onSearchChange = { homeSearch = it },
                onOpen = { screen = it; scanResult = null },
                onScan = openScanner,
                onProduct = { editorProduct = it },
                page = homePage,
                onPageChange = { homePage = it },
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.Catalog -> CatalogScreen(
                state = state,
                scanResult = scanResult,
                onScan = openScanner,
                onEdit = { editorProduct = it },
                onAdd = { editorProduct = Product(name = "") },
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.Receive, WarehouseScreen.Bware -> ReceiveScreen(
                state = state,
                viewModel = viewModel,
                isBware = screen == WarehouseScreen.Bware,
                scanResult = scanResult,
                onScan = openScanner,
                onCreateProduct = { barcode -> editorProduct = Product(barcode = barcode, name = "") },
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.Inventory -> InventoryScreen(
                state = state,
                viewModel = viewModel,
                scanResult = scanResult,
                onScan = openScanner,
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.History -> HistoryScreen(
                state = state,
                onDeleteReceipt = viewModel::deleteReceipt,
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.Tasks -> TasksScreen(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.Transfer -> TransferScreen(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.Settings -> SettingsScreen(
                state = state,
                onThemeChange = viewModel::setDarkTheme,
                onTransfer = { screen = WarehouseScreen.Transfer },
                modifier = Modifier.padding(innerPadding),
                onIntegrations = { screen = WarehouseScreen.Integrations },
            )
            WarehouseScreen.ReceiveHub -> ReceiveHubScreen(
                onOpen = { screen = it },
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.ByInvoice -> ByInvoiceScreen(
                state = state,
                extra = extra,
                warehouse = viewModel,
                onHistory2 = { screen = WarehouseScreen.History2 },
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.Auto -> AutoReceiveScreen(
                state = state,
                extra = extra,
                warehouse = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.History2 -> History2Screen(extra, Modifier.padding(innerPadding))
            WarehouseScreen.Plan -> PlanScreen(
                state = state,
                extra = extra,
                warehouse = viewModel,
                onBackToHome = { screen = WarehouseScreen.Home },
                modifier = Modifier.padding(innerPadding),
            )
            WarehouseScreen.LinkTool -> LinkToolScreen(state, extra, viewModel, Modifier.padding(innerPadding))
            WarehouseScreen.Profile -> ProfileScreen(extra, Modifier.padding(innerPadding))
            WarehouseScreen.Info -> InfoStatsScreen(state, Modifier.padding(innerPadding))
            WarehouseScreen.Documents -> DocumentsScreen(extra, Modifier.padding(innerPadding))
            WarehouseScreen.Gemini -> GeminiChatScreen(extra, Modifier.padding(innerPadding))
            WarehouseScreen.Integrations -> IntegrationSettingsScreen(extra, Modifier.padding(innerPadding))
        }
    }

    if (scannerOpen) {
        BarcodeScannerDialog(
            onDismiss = { scannerOpen = false },
            onBarcodeScanned = { code ->
                scanResult = code
                if (screen == WarehouseScreen.Home) homeSearch = code
                scannerOpen = false
                viewModel.showTransferMessage("Считан код: $code")
            },
        )
    }

    editorProduct?.let { product ->
        ProductEditorDialog(
            product = product,
            onDismiss = { editorProduct = null },
            onSave = {
                viewModel.saveProduct(it)
                editorProduct = null
            },
            onDelete = { id ->
                viewModel.deleteProduct(id)
                editorProduct = null
            },
        )
    }
}

@Composable
private fun WarehouseTopBar(
    screen: WarehouseScreen,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (screen == WarehouseScreen.Home) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.padding(9.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("WAREHOUSE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
                        Text("Управление складом", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "На главную")
                    }
                    Text(screen.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            if (screen == WarehouseScreen.Home) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun WarehouseBottomBar(
    selected: WarehouseScreen,
    onSelect: (WarehouseScreen) -> Unit,
) {
    val selectedTab = when (selected) {
        WarehouseScreen.Catalog -> WarehouseScreen.Catalog
        WarehouseScreen.Receive, WarehouseScreen.Bware, WarehouseScreen.ReceiveHub,
        WarehouseScreen.ByInvoice, WarehouseScreen.Auto, WarehouseScreen.Plan -> WarehouseScreen.Receive
        WarehouseScreen.History, WarehouseScreen.History2 -> WarehouseScreen.History
        WarehouseScreen.Transfer, WarehouseScreen.Settings, WarehouseScreen.Tasks, WarehouseScreen.Inventory,
        WarehouseScreen.LinkTool, WarehouseScreen.Profile, WarehouseScreen.Info, WarehouseScreen.Documents,
        WarehouseScreen.Gemini, WarehouseScreen.Integrations -> WarehouseScreen.Settings
        else -> WarehouseScreen.Home
    }
    NavigationBar(
        windowInsets = WindowInsets.navigationBars,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        val tabs = listOf(
            Triple(WarehouseScreen.Home, "Главная", Icons.Default.Home),
            Triple(WarehouseScreen.Catalog, "Каталог", Icons.Default.Inventory2),
            Triple(WarehouseScreen.Receive, "Приёмка", Icons.Default.Inventory2),
            Triple(WarehouseScreen.History, "История", Icons.Default.ReceiptLong),
            Triple(WarehouseScreen.Settings, "Ещё", Icons.Default.MoreHoriz),
        )
        tabs.forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = selectedTab == route,
                onClick = {
                    onSelect(route)
                },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}
