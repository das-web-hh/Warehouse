@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ru.warehouse.app.data.BatchStatus
import ru.warehouse.app.data.ChatMessage
import ru.warehouse.app.data.CheckResult
import ru.warehouse.app.data.CheckType
import ru.warehouse.app.data.DraftLine
import ru.warehouse.app.data.ExtraSettings
import ru.warehouse.app.data.ExtraStore
import ru.warehouse.app.data.InvoiceBatch
import ru.warehouse.app.data.BatchItem
import ru.warehouse.app.data.PlanItem
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.ServerClient
import ru.warehouse.app.data.StoredDocument
import ru.warehouse.app.data.WarehouseScreen
import ru.warehouse.app.data.WarehouseState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Состояние и логика всех окон, которых ещё не было в Kotlin-версии:
 * «По заданию», «Товар+ШК», «Профиль», «Документы», «Чат Gemini»,
 * «По накладной», «Авто», «История 2».
 */
class ExtraViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ExtraStore(application)
    private val ioGate = Semaphore(10) // до 10 накладных одновременно

    private val _settings = MutableStateFlow(store.loadSettings())
    val settings: StateFlow<ExtraSettings> = _settings.asStateFlow()

    private val _documents = MutableStateFlow(store.loadDocuments())
    val documents: StateFlow<List<StoredDocument>> = _documents.asStateFlow()

    private val _chat = MutableStateFlow(store.loadChat())
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()
    private val _chatSending = MutableStateFlow(false)
    val chatSending: StateFlow<Boolean> = _chatSending.asStateFlow()

    private val _batches = MutableStateFlow(store.loadBatches())
    val batches: StateFlow<List<InvoiceBatch>> = _batches.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Запрос открыть окно (например, после «Поделиться» из другого приложения). */
    private val _openRequest = MutableStateFlow<WarehouseScreen?>(null)
    val openRequest: StateFlow<WarehouseScreen?> = _openRequest.asStateFlow()

    fun consumeOpenRequest() { _openRequest.value = null }
    fun dismissMessage() { _message.value = null }
    private fun say(text: String) { _message.value = text }

    // ═══════════ ПО ЗАДАНИЮ ═══════════
    private val _planItems = MutableStateFlow<List<PlanItem>>(emptyList())
    val planItems: StateFlow<List<PlanItem>> = _planItems.asStateFlow()

    private val _scanned = MutableStateFlow<Map<String, Int>>(linkedMapOf())
    val scanned: StateFlow<Map<String, Int>> = _scanned.asStateFlow()

    private val _scanStatus = MutableStateFlow("Нажмите кнопку сканирования, чтобы считать штрихкод из локальной базы.")
    val scanStatus: StateFlow<String> = _scanStatus.asStateFlow()

    private val _checkResults = MutableStateFlow<List<CheckResult>>(emptyList())
    val checkResults: StateFlow<List<CheckResult>> = _checkResults.asStateFlow()

    fun resetPlan() {
        _planItems.value = emptyList()
        _scanned.value = linkedMapOf()
        _checkResults.value = emptyList()
        _scanStatus.value = "Нажмите кнопку сканирования, чтобы считать штрихкод из локальной базы."
    }

    /** Разбор вставленного списка: «Название | кол-во | EAN» либо «Название 5». */
    fun applyPlanText(text: String) {
        _planItems.value = parsePlan(text)
    }

    fun deletePlanItem(index: Int) {
        _planItems.update { list -> list.filterIndexed { i, _ -> i != index } }
    }

    fun addScan(code: String, state: WarehouseState): Boolean {
        val normalized = normalizeBarcode(code)
        if (normalized.isEmpty()) {
            _scanStatus.value = "Не удалось прочитать штрихкод."
            return false
        }
        val next = LinkedHashMap(_scanned.value)
        next[normalized] = (next[normalized] ?: 0) + 1
        _scanned.value = next
        val local = findByBarcode(state, normalized)
        val expected = _planItems.value.firstOrNull { normalizeBarcode(it.ean) == normalized }?.quantity
        _scanStatus.value = when {
            local == null -> "Штрихкод $normalized не найден в локальной базе — он будет отмечен в сверке."
            expected != null -> "Считано ${next[normalized]} из $expected шт. для этого товара."
            else -> "Штрихкод $normalized найден в базе: ${local.name}."
        }
        return true
    }

    fun setScanQty(code: String, qty: Int) {
        val next = LinkedHashMap(_scanned.value)
        if (next.containsKey(code)) next[code] = qty.coerceIn(1, 99_999)
        _scanned.value = next
    }

    fun deleteScan(code: String) {
        val next = LinkedHashMap(_scanned.value)
        next.remove(code)
        _scanned.value = next
        _scanStatus.value = "Строка удалена: $code."
    }

    fun finishScanSession(state: WarehouseState) {
        _checkResults.value = buildCheckResults(state)
        _scanStatus.value = "Сверка завершена."
    }

    /** @return текст ошибки или null, если партия сохранена. */
    fun saveCheck(orderRaw: String, state: WarehouseState, warehouse: WarehouseViewModel): String? {
        val order = normalizeOrder(orderRaw)
        if (order.isEmpty()) return "Введите номер заказа."
        val drafts = _checkResults.value
            .filter { it.actualQty > 0 && it.product != null }
            .map { DraftLine(it.product!!, it.actualQty.toDouble()) }
        if (drafts.isEmpty()) return "Нет отсканированных товаров из локальной базы для сохранения."
        warehouse.saveReceipt(drafts, shortToday(), order, "", false)
        addDocument(
            type = "full",
            title = "Приём по заданию $order",
            subtitle = "${drafts.size} поз.",
            body = buildCheckReport(order),
        )
        resetPlan()
        return null
    }

    private fun buildCheckResults(state: WarehouseState): List<CheckResult> {
        val scanned = _scanned.value
        val codes = scanned.keys.toList()
        val used = HashSet<String>()
        val results = mutableListOf<CheckResult>()
        _planItems.value.forEach { planned ->
            var code = normalizeBarcode(planned.ean)
            var found = if (code.isNotEmpty()) findByBarcode(state, code) else null
            if (code.isEmpty()) {
                val candidate = codes.firstOrNull { sc ->
                    if (sc in used) return@firstOrNull false
                    val p = findByBarcode(state, sc)
                    p != null && cleanName(p.name) == cleanName(planned.name)
                }
                if (candidate != null) { code = candidate; found = findByBarcode(state, candidate) }
            }
            val actual = if (code.isNotEmpty()) scanned[code] ?: 0 else 0
            if (code.isNotEmpty() && scanned.containsKey(code)) used.add(code)
            val nameMatches = found != null && cleanName(planned.name) == cleanName(found.name)
            val type = when {
                actual > 0 && (found == null || !nameMatches) -> CheckType.MISMATCH
                actual > planned.quantity -> CheckType.EXTRA
                actual == planned.quantity && found != null && nameMatches -> CheckType.MATCH
                else -> CheckType.LESS
            }
            results += CheckResult(type, code, found, planned, actual, planned.quantity, found?.name ?: planned.name)
        }
        codes.forEach { code ->
            if (code in used) return@forEach
            val found = findByBarcode(state, code)
            results += CheckResult(
                CheckType.EXTRA, code, found, null, scanned[code] ?: 0, 0,
                found?.name ?: "Не найдено в локальной базе",
            )
        }
        return results
    }

    private fun buildCheckReport(order: String): String = buildString {
        appendLine("Заказ: $order")
        appendLine("Дата: ${shortToday()}")
        appendLine()
        _checkResults.value.forEach {
            appendLine("[${it.type.label}] ${it.name} — ${it.actualQty}/${it.expectedQty}  ${it.code}")
        }
    }

    // ═══════════ ТОВАР + ШТРИХКОД ═══════════
    /** @return сообщение об успехе или ошибке в виде Pair(ok, text). */
    fun saveLink(name: String, eanRaw: String, state: WarehouseState, warehouse: WarehouseViewModel): Pair<Boolean, String> {
        val cleanName = name.trim()
        val ean = normalizeBarcode(eanRaw)
        if (cleanName.isEmpty()) return false to "Введите наименование товара."
        if (ean.isEmpty()) return false to "Введите или отсканируйте штрихкод."
        val byBarcode = findByBarcode(state, ean)
        if (byBarcode != null && !byBarcode.name.trim().equals(cleanName, true)) {
            warehouse.saveProduct(byBarcode.copy(barcode = ""))
        }
        val target = state.products.firstOrNull { it.name.trim().equals(cleanName, true) }
        if (target == null) warehouse.saveProduct(Product(barcode = ean, name = cleanName))
        else warehouse.saveProduct(target.copy(barcode = ean))
        return true to "Товар «$cleanName» привязан к штрихкоду $ean."
    }

    // ═══════════ НАСТРОЙКИ / ПРОФИЛЬ ═══════════
    fun updateSettings(transform: (ExtraSettings) -> ExtraSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        store.saveSettings(next)
    }

    // ═══════════ ДОКУМЕНТЫ ═══════════
    fun addDocument(type: String, title: String, subtitle: String, body: String) {
        val now = Date()
        val doc = StoredDocument(
            type = type, title = title, subtitle = subtitle, body = body,
            date = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(now),
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now),
        )
        _documents.update { listOf(doc) + it }
        store.saveDocuments(_documents.value)
    }

    fun deleteDocument(id: String) {
        _documents.update { list -> list.filterNot { it.id == id } }
        store.saveDocuments(_documents.value)
    }

    // ═══════════ ЧАТ GEMINI ═══════════
    fun sendChat(text: String) {
        val clean = text.trim()
        if (clean.isEmpty() || _chatSending.value) return
        val history = _chat.value
        _chat.update { it + ChatMessage(role = "user", text = clean) }
        _chatSending.value = true
        val s = _settings.value
        viewModelScope.launch {
            val answer = withContext(Dispatchers.IO) {
                runCatching {
                    if (s.geminiKey.isNotBlank()) {
                        ServerClient.chatDirect(s.geminiModel, s.geminiKey, s.geminiInstruction, history, clean)
                    } else {
                        ServerClient.chat(s.pythonUrl, clean, s.geminiInstruction, history, "")
                    }
                }
            }
            val reply = answer.getOrElse { it.message ?: "Не удалось получить ответ Gemini." }
            _chat.update { it + ChatMessage(role = "assistant", text = reply) }
            store.saveChat(_chat.value)
            _chatSending.value = false
        }
    }

    fun clearChat() {
        _chat.value = emptyList()
        store.saveChat(emptyList())
    }

    // ═══════════ НАКЛАДНЫЕ: ПО НАКЛАДНОЙ / АВТО / ИСТОРИЯ 2 ═══════════
    fun enqueueUris(uris: List<Uri>, source: String) {
        uris.forEach { enqueueUri(it, source) }
    }

    /** Приём файлов из системного «Поделиться». */
    fun handleShareIntent(intent: Intent?) {
        intent ?: return
        val uris = extractSharedUris(intent)
        if (uris.isEmpty()) return
        enqueueUris(uris, "manual")
        _openRequest.value = WarehouseScreen.ByInvoice
    }

    @Suppress("DEPRECATION")
    private fun extractSharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE ->
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty()
        else -> emptyList()
    }

    private fun enqueueUri(uri: Uri, source: String) {
        val app = getApplication<Application>()
        val name = queryName(uri) ?: "file"
        val mime = app.contentResolver.getType(uri) ?: guessMime(name)
        val batch = InvoiceBatch(fileName = name, source = source, status = BatchStatus.QUEUED)
        _batches.update { listOf(batch) + it }
        persistBatches()
        viewModelScope.launch(Dispatchers.IO) {
            ioGate.withPermit {
                setBatch(batch.id) { it.copy(status = BatchStatus.PROCESSING) }
                val s = _settings.value
                val result = runCatching {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Не удалось прочитать файл")
                    ServerClient.processInvoice(s.pythonUrl, name, bytes, mime, s.geminiKey)
                }
                result.onSuccess { r ->
                    val date = r.date.ifBlank { shortToday() }
                    val existing = _batches.value.firstOrNull {
                        it.id != batch.id && r.orderNumber.isNotBlank() &&
                            it.orderNumber.equals(r.orderNumber, true) &&
                            it.status == BatchStatus.READY
                    }
                    if (existing != null) {
                        // Один и тот же номер заказа = одна партия: сливаем позиции.
                        val merged = mergeItems(existing.items, r.items)
                        _batches.update { list ->
                            list.filterNot { it.id == batch.id }
                                .map { if (it.id == existing.id) it.copy(items = merged) else it }
                        }
                    } else {
                        setBatch(batch.id) {
                            it.copy(
                                sender = r.sender, orderNumber = r.orderNumber, date = date,
                                items = r.items, status = BatchStatus.READY,
                                error = if (r.failedPages > 0) "Не распознано страниц: ${r.failedPages}" else "",
                            )
                        }
                    }
                }.onFailure { e ->
                    setBatch(batch.id) { it.copy(status = BatchStatus.ERROR, error = e.message ?: "Ошибка") }
                }
                persistBatches()
            }
        }
    }

    fun acceptBatch(batchId: String, state: WarehouseState, warehouse: WarehouseViewModel) {
        val batch = _batches.value.firstOrNull { it.id == batchId } ?: return
        if (batch.items.isEmpty()) { say("В накладной нет позиций"); return }
        var current = state
        val drafts = mutableListOf<DraftLine>()
        batch.items.forEach { item ->
            if (item.quantity <= 0.0) return@forEach
            var product = (if (item.ean.isNotEmpty()) findByBarcode(current, item.ean) else null)
                ?: current.products.firstOrNull { it.name.trim().equals(item.name.trim(), true) }
            if (product == null) {
                // Неизвестный товар: заводим в каталоге со статусом «ожидает подтверждения».
                val created = Product(barcode = item.ean, name = item.name, unit = item.unit, pendingApproval = true)
                warehouse.saveProduct(created)
                product = created
                current = current.copy(products = current.products + created)
            }
            drafts += DraftLine(product, item.quantity)
        }
        warehouse.saveReceipt(drafts, batch.date.ifBlank { shortToday() }, batch.orderNumber, batch.sender, false)
        setBatch(batchId) { it.copy(status = BatchStatus.ACCEPTED, acceptedAt = System.currentTimeMillis()) }
        persistBatches()
        addDocument("report", "Накладная ${batch.orderNumber.ifBlank { batch.fileName }}", batch.sender, buildBatchReport(batch))
    }

    fun updateBatchQty(batchId: String, index: Int, qty: Double) {
        setBatch(batchId) { b ->
            b.copy(items = b.items.mapIndexed { i, it -> if (i == index) it.copy(quantity = qty.coerceAtLeast(0.0)) else it })
        }
        persistBatches()
    }

    fun removeBatchItem(batchId: String, index: Int) {
        setBatch(batchId) { b -> b.copy(items = b.items.filterIndexed { i, _ -> i != index }) }
        persistBatches()
    }

    fun deleteBatch(batchId: String) {
        _batches.update { list -> list.filterNot { it.id == batchId } }
        persistBatches()
    }

    fun retryBatch(batchId: String, uri: Uri?) {
        if (uri == null) { say("Файл недоступен — добавьте его заново"); return }
        deleteBatch(batchId)
        enqueueUri(uri, "manual")
    }

    // ── Авто: папка ──
    fun setAutoFolder(uri: Uri?) {
        updateSettings { it.copy(autoFolderUri = uri?.toString().orEmpty()) }
        if (uri != null) scanAutoFolder()
    }

    /** Проверяет папку и ставит в очередь новые PDF/изображения. */
    fun scanAutoFolder() {
        val s = _settings.value
        if (s.autoFolderUri.isBlank()) return
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val tree = runCatching { DocumentFile.fromTreeUri(app, Uri.parse(s.autoFolderUri)) }.getOrNull()
            if (tree == null || !tree.canRead()) { say("Нет доступа к выбранной папке"); return@launch }
            val processed = store.loadProcessedFiles().toMutableSet()
            val fresh = tree.listFiles().filter { f ->
                f.isFile && f.uri.toString() !in processed &&
                    (f.type?.let { it == "application/pdf" || it.startsWith("image/") } == true ||
                        f.name.orEmpty().lowercase().let { it.endsWith(".pdf") || it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") })
            }
            if (fresh.isEmpty()) return@launch
            fresh.forEach { processed += it.uri.toString() }
            store.saveProcessedFiles(processed)
            withContext(Dispatchers.Main) { enqueueUris(fresh.map { it.uri }, "auto") }
        }
    }

    private fun setBatch(id: String, transform: (InvoiceBatch) -> InvoiceBatch) {
        _batches.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun persistBatches() { store.saveBatches(_batches.value) }

    private fun buildBatchReport(b: InvoiceBatch): String = buildString {
        appendLine("Отправитель: ${b.sender.ifBlank { "—" }}")
        appendLine("Заказ: ${b.orderNumber.ifBlank { "—" }}")
        appendLine("Дата: ${b.date}")
        appendLine()
        b.items.forEach { appendLine("${it.name} — ${formatQuantity(it.quantity)} ${it.unit}  ${it.ean}") }
    }

    private fun mergeItems(a: List<BatchItem>, b: List<BatchItem>): List<BatchItem> {
        val out = a.toMutableList()
        b.forEach { n ->
            val i = out.indexOfFirst { (n.ean.isNotEmpty() && it.ean == n.ean) || it.name.equals(n.name, true) }
            if (i >= 0) out[i] = out[i].copy(quantity = out[i].quantity + n.quantity) else out += n
        }
        return out
    }

    private fun queryName(uri: Uri): String? = runCatching {
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
    }.getOrNull() ?: uri.lastPathSegment

    private fun guessMime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        else -> "application/octet-stream"
    }

    companion object {
        fun normalizeBarcode(value: String?): String {
            val raw = value.orEmpty().trim()
            if (raw.isNotEmpty() && raw.all(Char::isDigit)) return raw
            return raw.uppercase().replace(Regex("[\\s-]+"), "")
        }

        fun normalizeOrder(value: String?): String {
            var raw = value.orEmpty().trim().uppercase().replace(Regex("\\s+"), "")
            raw = raw.replace(Regex("^(?:EB)+"), "").replace(Regex("^[-:#]+"), "")
            return if (raw.isEmpty()) "" else "EB$raw"
        }

        fun cleanName(value: String?): String =
            value.orEmpty().lowercase().replace('ё', 'е')
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

        fun findByBarcode(state: WarehouseState, code: String): Product? {
            val n = normalizeBarcode(code)
            if (n.isEmpty()) return null
            return state.products.firstOrNull { normalizeBarcode(it.barcode) == n }
        }

        fun parsePlan(text: String): List<PlanItem> =
            text.split(Regex("\\r?\\n")).map(String::trim).filter(String::isNotEmpty).mapNotNull { line ->
                val columns = line.split(Regex("[|;\\t]+")).map(String::trim).filter(String::isNotEmpty)
                var name = columns.getOrNull(0).orEmpty()
                var quantity = columns.getOrNull(1).orEmpty().filter(Char::isDigit).toIntOrNull() ?: 0
                var ean = normalizeBarcode(columns.getOrNull(2).orEmpty())
                if (quantity == 0) {
                    val m = Regex("\\s+(\\d+)\\s*$").find(name)
                    if (m != null) {
                        quantity = m.groupValues[1].toIntOrNull() ?: 1
                        name = name.substring(0, m.range.first).trim()
                    }
                }
                if (ean.isEmpty()) {
                    Regex("\\b\\d{8,14}\\b").find(line)?.let { ean = normalizeBarcode(it.value) }
                }
                if (name.isEmpty()) null else PlanItem(name, maxOf(1, quantity), ean)
            }

        fun shortToday(): String = SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date())

        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ExtraViewModel::class.java))
                    return ExtraViewModel(application) as T
                }
            }
    }
}
