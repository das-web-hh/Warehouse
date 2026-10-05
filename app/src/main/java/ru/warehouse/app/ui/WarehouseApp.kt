@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ru.warehouse.app.data.DraftLine
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseScreen

@Composable
fun WarehouseApp(
    viewModel: WarehouseViewModel,
    extra: ExtraViewModel,
) {
    val state by viewModel.state.collectAsState()
    val warehouseMessage by viewModel.message.collectAsState()
    val extraMessage by extra.message.collectAsState()
    val openRequest by extra.openRequest.collectAsState()
    val chat by extra.chat.collectAsState()
    val chatSending by extra.chatSending.collectAsState()

    val stack = remember { mutableStateListOf(WarehouseScreen.Home) }
    val currentScreen = stack.last()
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var isAddingProduct by remember { mutableStateOf(false) }
    var newProductBarcode by remember { mutableStateOf("") }
    var scanResult by remember { mutableStateOf<String?>(null) }
    var chatDraft by remember { mutableStateOf("") }
    val snackbarHost = remember { SnackbarHostState() }

    fun navigate(screen: WarehouseScreen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun openRoot(screen: WarehouseScreen) {
        stack.clear()
        stack.add(WarehouseScreen.Home)
        if (screen != WarehouseScreen.Home) stack.add(screen)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    val scan = rememberScanner { code -> scanResult = code }

    BackHandler(enabled = stack.size > 1) { back() }

    LaunchedEffect(openRequest) {
        openRequest?.let {
            navigate(it)
            extra.consumeOpenRequest()
        }
    }
    LaunchedEffect(warehouseMessage) {
        warehouseMessage?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(extraMessage) {
        extraMessage?.let {
            snackbarHost.showSnackbar(it)
            extra.dismissMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            if (currentScreen != WarehouseScreen.Home) {
                TopAppBar(
                    title = { Text(currentScreen.title) },
                    navigationIcon = {
                        IconButton(onClick = { back() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                    },
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == WarehouseScreen.Home,
                    onClick = { openRoot(WarehouseScreen.Home) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Главная") },
                    label = { Text("Главная") },
                )
                NavigationBarItem(
                    selected = currentScreen == WarehouseScreen.Catalog,
                    onClick = { openRoot(WarehouseScreen.Catalog) },
                    icon = { Icon(Icons.Default.List, contentDescription = "Каталог") },
                    label = { Text("Каталог") },
                )
                NavigationBarItem(
                    selected = currentScreen == WarehouseScreen.Settings,
                    onClick = { openRoot(WarehouseScreen.Settings) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Настройки") },
                    label = { Text("Настройки") },
                )
            }
        },
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)

        Box(modifier = modifier.fillMaxSize()) {
            when (currentScreen) {
                WarehouseScreen.Home -> HomeScreen(
                    state = state,
                    onScreenSelected = { navigate(it) },
                    onScanClick = scan,
                    onProductSelected = { product ->
                        editingProduct = product
                        navigate(WarehouseScreen.Catalog)
                    },
                    onMenuClick = { navigate(WarehouseScreen.Settings) },
                )

                WarehouseScreen.Catalog -> CatalogScreen(
                    state = state,
                    scanResult = scanResult,
                    onScan = scan,
                    onEdit = { product -> editingProduct = product },
                    onAdd = { isAddingProduct = true },
                )

                WarehouseScreen.ReceiveHub -> ReceiveHubScreen(onOpen = { navigate(it) })

                WarehouseScreen.Receive -> ReceiveScreen(
                    state = state,
                    viewModel = viewModel,
                    isBware = false,
                    scanResult = scanResult,
                    onScan = scan,
                    onCreateProduct = { barcode ->
                        newProductBarcode = barcode
                        isAddingProduct = true
                    },
                )

                WarehouseScreen.Bware -> ReceiveScreen(
                    state = state,
                    viewModel = viewModel,
                    isBware = true,
                    scanResult = scanResult,
                    onScan = scan,
                    onCreateProduct = { barcode ->
                        newProductBarcode = barcode
                        isAddingProduct = true
                    },
                )

                WarehouseScreen.ByInvoice -> ByInvoiceScreen(
                    state = state,
                    extra = extra,
                    warehouse = viewModel,
                    onHistory2 = { navigate(WarehouseScreen.History2) },
                )

                WarehouseScreen.Auto -> AutoReceiveScreen(
                    state = state,
                    extra = extra,
                    warehouse = viewModel,
                )

                WarehouseScreen.History2 -> History2Screen(extra = extra)

                WarehouseScreen.Inventory -> InventoryScreen(
                    state = state,
                    viewModel = viewModel,
                    scanResult = scanResult,
                    onScan = scan,
                )

                WarehouseScreen.History -> HistoryScreen(
                    state = state,
                    onDeleteReceipt = viewModel::deleteReceipt,
                )

                WarehouseScreen.Tasks -> TasksScreen(state = state, viewModel = viewModel)

                WarehouseScreen.Transfer -> TransferScreen(state = state, viewModel = viewModel)

                WarehouseScreen.Settings -> SettingsScreen(
                    state = state,
                    onThemeChange = viewModel::setDarkTheme,
                    onTransfer = { navigate(WarehouseScreen.Transfer) },
                    onIntegrations = { navigate(WarehouseScreen.Integrations) },
                )

                WarehouseScreen.Plan -> PlanScreen(
                    state = state,
                    onBack = { back() },
                    onScanClick = scan,
                    onSavePlan = { results ->
                        val drafts = results
                            .filter { it.scannedQty > 0.0 }
                            .mapNotNull { r ->
                                state.products.firstOrNull { it.id == r.productId }
                                    ?.let { DraftLine(it, r.scannedQty) }
                            }
                        viewModel.saveReceipt(
                            draftLines = drafts,
                            date = "",
                            orderNumber = "",
                            supplier = "",
                            isBware = false,
                        )
                    },
                )

                WarehouseScreen.LinkTool -> LinkToolScreen(
                    state = state,
                    extra = extra,
                    warehouse = viewModel,
                )

                WarehouseScreen.Profile -> ProfileScreen(extra = extra)

                WarehouseScreen.Info -> InfoStatsScreen(state = state)

                WarehouseScreen.Documents -> DocumentsScreen(extra = extra)

                WarehouseScreen.Integrations -> IntegrationSettingsScreen(extra = extra)

                WarehouseScreen.Gemini -> GeminiChatScreen(
                    messages = chat,
                    draftText = chatDraft,
                    pendingAttachments = emptyList(),
                    onDraftTextChange = { chatDraft = it },
                    onAddAttachments = {},
                    onRemovePendingAttachment = {},
                    onSendMessage = { text, _ ->
                        extra.sendChat(text)
                        chatDraft = ""
                    },
                    onBack = { back() },
                    onRetryMessage = { message -> extra.sendChat(message.text) },
                    isSending = chatSending,
                )
            }
        }

        if (editingProduct != null || isAddingProduct) {
            val targetProduct = editingProduct ?: Product(
                id = System.currentTimeMillis().toString(),
                barcode = newProductBarcode.ifBlank { scanResult.orEmpty() },
            )

            ProductEditorDialog(
                product = targetProduct,
                onDismiss = {
                    editingProduct = null
                    isAddingProduct = false
                    newProductBarcode = ""
                },
                onSave = { updatedProduct ->
                    viewModel.saveProduct(updatedProduct)
                    editingProduct = null
                    isAddingProduct = false
                    newProductBarcode = ""
                },
                onDelete = { productId ->
                    viewModel.deleteProduct(productId)
                    editingProduct = null
                    isAddingProduct = false
                    newProductBarcode = ""
                },
            )
        }
    }
}
