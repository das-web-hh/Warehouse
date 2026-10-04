@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState

@Composable
fun WarehouseApp(
    viewModel: WarehouseViewModel,
    state: WarehouseState = viewModel.state.collectAsState().value,
) {
    val context = LocalContext.current
    val snackbarHost = remember { SnackbarHostState() }
    var screen by rememberSaveable { mutableStateOf(WarehouseScreen.Home) }
    var homeSearch by rememberSaveable { mutableStateOf("") }
    var scanResult by rememberSaveable { mutableStateOf<String?>(null) }
    var scannerOpen by rememberSaveable { mutableStateOf(false) }
    var editorProduct by remember { mutableStateOf<Product?>(null) }

    val cameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            if (granted) scannerOpen = true
            else viewModel.showTransferMessage("Для сканирования разрешите доступ к камере")
        },
    )
    val openScanner = {
        scanResult = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scannerOpen = true
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler(enabled = screen != WarehouseScreen.Home || scannerOpen || editorProduct != null) {
        when {
            scannerOpen -> scannerOpen = false
            editorProduct != null -> editorProduct = null
            else -> screen = WarehouseScreen.Home
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
                onBack = { screen = WarehouseScreen.Home },
                onSettings = { screen = WarehouseScreen.Settings },
            )
        },
        bottomBar = {
            WarehouseBottomBar(
                selected = screen,
                onSelect = { target ->
                    screen = target
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
            )
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
        WarehouseScreen.Receive, WarehouseScreen.Bware -> WarehouseScreen.Receive
        WarehouseScreen.History -> WarehouseScreen.History
        WarehouseScreen.Transfer, WarehouseScreen.Settings, WarehouseScreen.Tasks, WarehouseScreen.Inventory -> WarehouseScreen.Settings
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
