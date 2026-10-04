package ru.warehouse.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import ru.warehouse.app.data.CheckResult
import ru.warehouse.app.data.CheckStatus
import ru.warehouse.app.data.PlanItem
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.WarehouseState
import java.util.Locale

private enum class PlanStage {
    PLAN,
    SCANNING,
    RESULTS,
}

/**
 * Assignment receipt: paste a plan, scan the received products, reconcile,
 * then hand the recognized items and results to the host app for persistence.
 */
@Composable
fun PlanScreen(
    state: WarehouseState,
    onBack: () -> Unit,
    onOpenScanner: (onBarcodeScanned: (String) -> Unit) -> Unit,
    onSave: suspend (
        orderNumber: String,
        receivedItems: List<PlanItem>,
        results: List<CheckResult>,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var stage by remember { mutableStateOf(PlanStage.PLAN) }
    var planItems by remember { mutableStateOf<List<PlanItem>>(emptyList()) }
    val scannedQuantities = remember { mutableStateMapOf<String, Int>() }
    var checkResults by remember { mutableStateOf<List<CheckResult>>(emptyList()) }
    var resultCodes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var statusMessage by remember { mutableStateOf("") }
    var deletePlanId by remember { mutableStateOf<String?>(null) }
    var deleteScanCode by remember { mutableStateOf<String?>(null) }
    var showOrderDialog by remember { mutableStateOf(false) }
    var orderNumber by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val light = !state.darkTheme
    val surface = if (light) Color(0xFFF6F8FC) else Color(0xFF0F1114)
    val appBar = if (light) Color(0xFF23478F) else Color(0xFF1A1D23)
    val textColor = if (light) Color(0xFF171B22) else Color(0xFFE3E5EA)
    val subTextColor = if (light) Color(0xFF5F6672) else Color.White.copy(alpha = 0.55f)
    val rowDivider = if (light) Color(0x2E868E96) else Color.White.copy(alpha = 0.10f)

    val barcodeHandler: (String) -> Unit = { rawCode ->
        val code = normalizePlanBarcode(rawCode)
        if (code.isBlank()) {
            statusMessage = "Не удалось прочитать штрихкод."
        } else {
            val quantity = (scannedQuantities[code] ?: 0) + 1
            scannedQuantities[code] = quantity
            planItems = planItems.map { item ->
                if (normalizePlanBarcode(item.barcode) == code) {
                    item.copy(scannedQty = quantity.toDouble())
                } else {
                    item
                }
            }
            val product = state.products.firstOrNull {
                normalizePlanBarcode(it.barcode) == code
            }
            val planned = planItems.firstOrNull {
                normalizePlanBarcode(it.barcode) == code
            }
            statusMessage = when {
                product == null ->
                    "Штрихкод $code не найден в локальной базе — он будет отмечен в сверке."
                planned != null ->
                    "Считано $quantity из ${formatPlanQuantity(planned.expectedQty)} ${planned.unit} для ${product.name}."
                else ->
                    "Штрихкод $code найден в базе: ${product.name}."
            }
        }
    }
    val latestBarcodeHandler by rememberUpdatedState(barcodeHandler)

    fun requestScanner() {
        try {
            onOpenScanner { scannedCode -> latestBarcodeHandler(scannedCode) }
        } catch (exception: Exception) {
            statusMessage = "Не удалось открыть сканер. Попробуйте ещё раз."
        }
    }

    fun startScanning() {
        if (planItems.isEmpty()) {
            statusMessage = "Сначала вставьте список товаров."
            return
        }
        scannedQuantities.clear()
        planItems = planItems.map { it.copy(scannedQty = 0.0) }
        checkResults = emptyList()
        resultCodes = emptyMap()
        deleteScanCode = null
        statusMessage = "Нажмите кнопку сканирования, чтобы считать штрихкод из локальной базы."
        stage = PlanStage.SCANNING
        requestScanner()
    }

    fun finishScanning() {
        val (results, codes) = buildPlanCheckResults(
            planItems = planItems,
            scannedQuantities = scannedQuantities.toMap(),
            products = state.products,
        )
        checkResults = results
        resultCodes = codes
        statusMessage = "Сверка завершена."
        stage = PlanStage.RESULTS
    }

    fun closeOrderDialog() {
        if (!isSaving) {
            showOrderDialog = false
            saveError = ""
        }
    }

    BackHandler {
        when {
            showOrderDialog -> closeOrderDialog()
            stage == PlanStage.RESULTS -> stage = PlanStage.SCANNING
            stage == PlanStage.SCANNING -> stage = PlanStage.PLAN
            else -> onBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        PlanHeader(
            stage = stage,
            appBar = appBar,
            onBack = {
                when (stage) {
                    PlanStage.PLAN -> onBack()
                    PlanStage.SCANNING -> stage = PlanStage.PLAN
                    PlanStage.RESULTS -> stage = PlanStage.SCANNING
                }
            },
            onPaste = {
                try {
                    val text = clipboard.getText()?.text.orEmpty()
                    if (text.isBlank()) {
                        statusMessage = "Буфер обмена пуст."
                    } else {
                        planItems = parsePlanItems(text, state.products)
                        scannedQuantities.clear()
                        checkResults = emptyList()
                        resultCodes = emptyMap()
                        deletePlanId = null
                        statusMessage = ""
                    }
                } catch (exception: Exception) {
                    statusMessage = "Не удалось прочитать буфер обмена. Разрешите доступ и попробуйте ещё раз."
                }
            },
            onStartScanning = ::startScanning,
            onFinishScanning = ::finishScanning,
            onSaveClick = {
                orderNumber = ""
                saveError = ""
                showOrderDialog = true
            },
        )

        when (stage) {
            PlanStage.PLAN -> {
                if (statusMessage.isNotBlank()) {
                    PlanStatusMessage(
                        message = statusMessage,
                        light = light,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                if (planItems.isEmpty()) {
                    PlanEmptyState(
                        text = "Список пока пуст.",
                        subtext = "Вставьте задание из буфера обмена.",
                        textColor = subTextColor,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        items(planItems, key = { it.id }) { item ->
                            PlanItemRow(
                                item = item,
                                deleteReady = deletePlanId == item.id,
                                light = light,
                                textColor = textColor,
                                subTextColor = subTextColor,
                                divider = rowDivider,
                                onLongPress = { deletePlanId = item.id },
                                onDismissDelete = { deletePlanId = null },
                                onDelete = {
                                    planItems = planItems.filterNot { it.id == item.id }
                                    deletePlanId = null
                                },
                            )
                        }
                    }
                }
            }

            PlanStage.SCANNING -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(surface),
                ) {
                    if (scannedQuantities.isEmpty()) {
                        PlanEmptyState(
                            text = "Пока ничего не отсканировано.",
                            subtext = "Нажмите кнопку сканирования, чтобы считать штрихкод.",
                            textColor = subTextColor,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 10.dp,
                                bottom = 100.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(scannedQuantities.keys.toList(), key = { it }) { code ->
                                ScanItemRow(
                                    code = code,
                                    quantity = scannedQuantities[code] ?: 0,
                                    productName = state.products.firstOrNull {
                                        normalizePlanBarcode(it.barcode) == code
                                    }?.name ?: planItems.firstOrNull {
                                        normalizePlanBarcode(it.barcode) == code
                                    }?.name ?: "Товар по штрихкоду",
                                    deleteReady = deleteScanCode == code,
                                    textColor = textColor,
                                    subTextColor = subTextColor,
                                    divider = rowDivider,
                                    onLongPress = { deleteScanCode = code },
                                    onDismissDelete = { deleteScanCode = null },
                                    onDelete = {
                                        scannedQuantities.remove(code)
                                        planItems = planItems.map { item ->
                                            if (normalizePlanBarcode(item.barcode) == code) {
                                                item.copy(scannedQty = 0.0)
                                            } else {
                                                item
                                            }
                                        }
                                        deleteScanCode = null
                                        statusMessage = "Строка удалена: $code."
                                    },
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 22.dp)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF28C28))
                            .clickable(onClick = ::requestScanner)
                            .semantics { contentDescription = "Сканировать товар" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("▦", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (statusMessage.isNotBlank()) {
                    PlanStatusMessage(
                        message = statusMessage,
                        light = light,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            PlanStage.RESULTS -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0F1F38))
                        .padding(horizontal = 16.dp),
                ) {
                    PlanResultSummary(
                        results = checkResults,
                        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    )
                    if (checkResults.isEmpty()) {
                        PlanEmptyState(
                            text = "Список для сверки пуст.",
                            subtext = "",
                            textColor = Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            items(checkResults, key = { it.itemId }) { result ->
                                PlanResultRow(
                                    result = result,
                                    code = resultCodes[result.itemId].orEmpty().ifBlank { "—" },
                                    divider = Color.White.copy(alpha = 0.12f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOrderDialog) {
        Dialog(onDismissRequest = ::closeOrderDialog) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .padding(20.dp),
            ) {
                Text(
                    text = "Номер заказа",
                    color = Color(0xFF171B22),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Введите номер заказа, к которому нужно привязать приёмку.",
                    modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                    color = Color(0xFF6C757D),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                BasicTextField(
                    value = orderNumber,
                    onValueChange = {
                        orderNumber = it
                        saveError = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFFD5D9DE), RoundedCornerShape(10.dp))
                        .padding(horizontal = 13.dp, vertical = 12.dp),
                    singleLine = true,
                    textStyle = TextStyle(color = Color(0xFF171B22), fontSize = 17.sp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (orderNumber.isEmpty()) {
                                Text("Например, 12345", color = Color(0xFF9AA1A9), fontSize = 16.sp)
                            }
                            innerTextField()
                        }
                    },
                )
                if (saveError.isNotBlank()) {
                    Text(
                        text = saveError,
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(0xFFDC3545),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlanDialogButton(
                        text = "Отмена",
                        color = Color(0xFFEDF2F7),
                        textColor = Color(0xFF495057),
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        onClick = ::closeOrderDialog,
                    )
                    PlanDialogButton(
                        text = if (isSaving) "Сохранение…" else "Сохранить",
                        color = Color(0xFF2B8A3E),
                        textColor = Color.White,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val normalizedOrder = normalizePlanOrderNumber(orderNumber)
                            if (normalizedOrder.isBlank()) {
                                saveError = "Введите номер заказа."
                                return@PlanDialogButton
                            }
                            val receivedItems = buildReceivedPlanItems(
                                scannedQuantities = scannedQuantities.toMap(),
                                products = state.products,
                            )
                            if (receivedItems.isEmpty()) {
                                saveError = "Нет отсканированных товаров из локальной базы для сохранения."
                                return@PlanDialogButton
                            }

                            keyboardController?.hide()
                            isSaving = true
                            scope.launch {
                                try {
                                    onSave(normalizedOrder, receivedItems, checkResults)
                                    showOrderDialog = false
                                    onBack()
                                } catch (exception: CancellationException) {
                                    throw exception
                                } catch (exception: Exception) {
                                    saveError = "Не удалось сохранить партию. Попробуйте ещё раз."
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanHeader(
    stage: PlanStage,
    appBar: Color,
    onBack: () -> Unit,
    onPaste: () -> Unit,
    onStartScanning: () -> Unit,
    onFinishScanning: () -> Unit,
    onSaveClick: () -> Unit,
) {
    val title = if (stage == PlanStage.RESULTS) "Результат сверки" else "Приём по заданию"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp)
            .background(appBar)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (stage != PlanStage.PLAN) {
            HeaderAction(
                label = "‹",
                description = "Назад",
                background = Color.White.copy(alpha = 0.12f),
                onClick = onBack,
                width = 34.dp,
            )
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        when (stage) {
            PlanStage.PLAN -> {
                HeaderAction(
                    label = "Вставить",
                    description = "Вставить задание из буфера обмена",
                    background = Color.White.copy(alpha = 0.14f),
                    onClick = onPaste,
                    width = 76.dp,
                )
                HeaderAction(
                    label = "▦",
                    description = "Сканировать товары",
                    background = Color(0xFF2B8A3E),
                    onClick = onStartScanning,
                    width = 36.dp,
                )
            }

            PlanStage.SCANNING -> {
                HeaderAction(
                    label = "Далее",
                    description = "Перейти к сверке",
                    background = Color(0xFF2B8A3E),
                    onClick = onFinishScanning,
                    width = 60.dp,
                )
            }

            PlanStage.RESULTS -> {
                HeaderAction(
                    label = "💾",
                    description = "Сохранить приёмку",
                    background = Color(0xFF2B8A3E),
                    onClick = onSaveClick,
                    width = 36.dp,
                )
            }
        }
    }
}

@Composable
private fun HeaderAction(
    label: String,
    description: String,
    background: Color,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp,
) {
    Box(
        modifier = Modifier
            .size(width = width, height = 34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (label == "💾") 19.sp else 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlanItemRow(
    item: PlanItem,
    deleteReady: Boolean,
    light: Boolean,
    textColor: Color,
    subTextColor: Color,
    divider: Color,
    onLongPress: () -> Unit,
    onDismissDelete: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (deleteReady) Color(0x14DC3545) else Color.Transparent)
            .border(width = 0.dp, color = Color.Transparent)
            .combinedClickable(
                onClick = { if (deleteReady) onDismissDelete() },
                onLongClick = onLongPress,
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
        ) {
            Text(
                text = item.name,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
            )
            Text(
                text = if (item.barcode.isBlank()) "Штрихкод не указан" else "Штрихкод: ${item.barcode}",
                modifier = Modifier.padding(top = 3.dp),
                color = subTextColor,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        Text(
            text = "${formatPlanQuantity(item.expectedQty)} ${item.unit}",
            color = if (light) Color(0xFF2B5CB0) else Color(0xFF77A7F7),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
        if (deleteReady) {
            Box(
                modifier = Modifier
                    .padding(start = 7.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFDC3545))
                    .clickable(onClick = onDelete)
                    .semantics { contentDescription = "Удалить ${item.name}" },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(divider),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScanItemRow(
    code: String,
    quantity: Int,
    productName: String,
    deleteReady: Boolean,
    textColor: Color,
    subTextColor: Color,
    divider: Color,
    onLongPress: () -> Unit,
    onDismissDelete: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (deleteReady) Color(0x14DC3545) else Color.Transparent)
            .combinedClickable(
                onClick = { if (deleteReady) onDismissDelete() },
                onLongClick = onLongPress,
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp),
        ) {
            Text(
                text = productName,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
            )
            Text(
                text = "Штрихкод: $code",
                modifier = Modifier.padding(top = 3.dp),
                color = subTextColor,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
        if (deleteReady) {
            Box(
                modifier = Modifier
                    .padding(end = 7.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFDC3545))
                    .clickable(onClick = onDelete)
                    .semantics { contentDescription = "Удалить строку $code" },
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            }
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White)
                .border(1.5.dp, Color(0xFF2B5CB0), RoundedCornerShape(9.dp))
                .padding(horizontal = 13.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = quantity.toString(),
                color = Color(0xFF2B5CB0),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(divider),
    )
}

@Composable
private fun PlanResultSummary(
    results: List<CheckResult>,
    modifier: Modifier = Modifier,
) {
    val counts = listOf(
        CheckStatus.MATCH to "Совпало",
        CheckStatus.MISMATCH to "Не совпало",
        CheckStatus.OVERAGE to "Лишнее",
        CheckStatus.SHORTAGE to "Мало",
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        counts.forEach { (status, label) ->
            val color = when (status) {
                CheckStatus.MATCH -> Color(0xFF2B8A3E)
                CheckStatus.MISMATCH -> Color(0xFFDC3545)
                CheckStatus.OVERAGE -> Color(0xFFE67700)
                CheckStatus.SHORTAGE -> Color(0xFFE03131)
                CheckStatus.PENDING -> Color(0xFF6C757D)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = results.count { it.status == status }.toString(),
                    color = color,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 20.sp,
                )
                Text(
                    text = label,
                    color = Color(0xFF6C757D),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlanResultRow(
    result: CheckResult,
    code: String,
    divider: Color,
) {
    val scanColor = when {
        result.actualQty == 0.0 -> Color(0xFFFF6B6B)
        result.status == CheckStatus.MATCH -> Color(0xFF69DB7C)
        result.status == CheckStatus.SHORTAGE -> Color(0xFFFFD43B)
        else -> Color(0xFFFF6B6B)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 7.dp),
    ) {
        Text(
            text = result.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 20.sp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = code,
                modifier = Modifier.weight(1f),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
            Text(
                text = "${formatPlanQuantity(result.actualQty)}/${formatPlanQuantity(result.expectedQty)}",
                color = scanColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(divider),
    )
}

@Composable
private fun PlanEmptyState(
    text: String,
    subtext: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (subtext.isNotBlank()) {
            Text(
                text = subtext,
                modifier = Modifier.padding(top = 4.dp),
                color = textColor.copy(alpha = 0.8f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlanStatusMessage(
    message: String,
    light: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (light) Color(0xFFF1F3F5) else Color(0xFF20242B))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        color = if (light) Color(0xFF5F6672) else Color.White.copy(alpha = 0.75f),
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

@Composable
private fun PlanDialogButton(
    text: String,
    color: Color,
    textColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) color else color.copy(alpha = 0.6f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun parsePlanItems(text: String, products: List<Product>): List<PlanItem> {
    return text.lineSequence()
        .mapIndexedNotNull { lineIndex, rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@mapIndexedNotNull null

            val columns = line.split(Regex("[|;\\t]+"))
                .map(String::trim)
                .filter(String::isNotEmpty)
            var name = columns.firstOrNull().orEmpty()
            if (name.isBlank()) return@mapIndexedNotNull null

            var quantity = columns.getOrNull(1)
                ?.replace(Regex("[^\\d]"), "")
                ?.toDoubleOrNull() ?: 0.0
            var barcode = normalizePlanBarcode(columns.getOrNull(2).orEmpty())

            if (quantity <= 0.0) {
                val trailingQuantity = Regex("\\s+(\\d+)\\s*$").find(name)
                if (trailingQuantity != null) {
                    quantity = trailingQuantity.groupValues[1].toDoubleOrNull() ?: 1.0
                    name = name.substring(0, trailingQuantity.range.first).trim()
                }
            }
            if (barcode.isBlank()) {
                barcode = Regex("\\b\\d{8,14}\\b").find(line)?.value
                    ?.let(::normalizePlanBarcode)
                    .orEmpty()
            }
            if (name.isBlank()) return@mapIndexedNotNull null

            val localProduct = barcode.takeIf(String::isNotBlank)?.let { code ->
                products.firstOrNull { normalizePlanBarcode(it.barcode) == code }
            }
            val stablePart = barcode.ifBlank { name.hashCode().toString() }
            PlanItem(
                id = "assignment-${lineIndex + 1}-$stablePart",
                name = name,
                barcode = barcode,
                sku = localProduct?.sku.orEmpty(),
                expectedQty = quantity.coerceAtLeast(1.0),
            )
        }
        .toList()
}

private fun buildPlanCheckResults(
    planItems: List<PlanItem>,
    scannedQuantities: Map<String, Int>,
    products: List<Product>,
): Pair<List<CheckResult>, Map<String, String>> {
    val scannedCodes = scannedQuantities.keys.toList()
    val usedCodes = mutableSetOf<String>()
    val results = mutableListOf<CheckResult>()
    val resultCodes = mutableMapOf<String, String>()

    planItems.forEachIndexed { index, planned ->
        var code = normalizePlanBarcode(planned.barcode)
        var foundProduct = code.takeIf(String::isNotBlank)?.let { scannedCode ->
            products.firstOrNull { normalizePlanBarcode(it.barcode) == scannedCode }
        }

        if (code.isBlank()) {
            val candidate = scannedCodes.firstOrNull { scannedCode ->
                if (scannedCode in usedCodes) {
                    false
                } else {
                    val candidateProduct = products.firstOrNull {
                        normalizePlanBarcode(it.barcode) == scannedCode
                    }
                    candidateProduct != null &&
                        cleanPlanName(candidateProduct.name) == cleanPlanName(planned.name)
                }
            }
            if (candidate != null) {
                code = candidate
                foundProduct = products.firstOrNull {
                    normalizePlanBarcode(it.barcode) == candidate
                }
            }
        }

        val actual = if (code.isBlank()) 0 else scannedQuantities[code] ?: 0
        if (code.isNotBlank() && actual > 0) usedCodes += code
        val namesMatch = foundProduct != null &&
            cleanPlanName(planned.name) == cleanPlanName(foundProduct.name)
        val status = when {
            actual > 0 && (foundProduct == null || !namesMatch) -> CheckStatus.MISMATCH
            actual.toDouble() > planned.expectedQty -> CheckStatus.OVERAGE
            actual.toDouble() == planned.expectedQty && foundProduct != null && namesMatch ->
                CheckStatus.MATCH
            else -> CheckStatus.SHORTAGE
        }
        val itemId = planned.id.ifBlank { "assignment-${index + 1}" }
        results += CheckResult(
            itemId = itemId,
            name = foundProduct?.name ?: planned.name,
            expectedQty = planned.expectedQty,
            actualQty = actual.toDouble(),
            difference = actual.toDouble() - planned.expectedQty,
            status = status,
            unit = planned.unit,
        )
        resultCodes[itemId] = code.ifBlank { planned.barcode }
    }

    scannedCodes.forEach { code ->
        if (code in usedCodes) return@forEach
        val product = products.firstOrNull { normalizePlanBarcode(it.barcode) == code }
        val itemId = "extra:$code"
        val actual = (scannedQuantities[code] ?: 0).toDouble()
        results += CheckResult(
            itemId = itemId,
            name = product?.name ?: "Не найдено в локальной базе",
            expectedQty = 0.0,
            actualQty = actual,
            difference = actual,
            status = CheckStatus.OVERAGE,
        )
        resultCodes[itemId] = code
    }

    return results to resultCodes
}

private fun buildReceivedPlanItems(
    scannedQuantities: Map<String, Int>,
    products: List<Product>,
): List<PlanItem> {
    return scannedQuantities.mapNotNull { (code, quantity) ->
        val product = products.firstOrNull {
            normalizePlanBarcode(it.barcode) == code
        } ?: return@mapNotNull null
        PlanItem(
            id = product.id,
            name = product.name,
            barcode = product.barcode.ifBlank { code },
            sku = product.sku,
            scannedQty = quantity.toDouble(),
        )
    }
}

private fun normalizePlanBarcode(value: String): String =
    value.trim().uppercase(Locale.ROOT).replace(Regex("[\\s-]+"), "")

private fun cleanPlanName(value: String): String =
    value.lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

private fun normalizePlanOrderNumber(value: String): String {
    var raw = value.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), "")
    raw = raw.replace(Regex("^(?:EB)+"), "").replace(Regex("^[-:#]+"), "")
    return if (raw.isBlank()) "" else "EB$raw"
}

private fun formatPlanQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()