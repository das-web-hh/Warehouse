@file:OptIn(ExperimentalMaterial3Api::class)

package ru.warehouse.app.ui

import androidx.compose.material3.ExperimentalMaterial3Api

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
import ru.warehouse.app.data.DraftLine
import ru.warehouse.app.data.ImportResult
import ru.warehouse.app.data.InventoryLocation
import ru.warehouse.app.data.Product
import ru.warehouse.app.data.Receipt
import ru.warehouse.app.data.ReceiptLine
import ru.warehouse.app.data.WarehouseRepository
import ru.warehouse.app.data.WarehouseState
import ru.warehouse.app.data.WarehouseTask

class WarehouseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WarehouseRepository(application)
    private val persistenceMutex = Mutex()
    private val _state = MutableStateFlow(repository.load())
    val state: StateFlow<WarehouseState> = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun dismissMessage() {
        _message.value = null
    }

    fun showTransferMessage(value: String) {
        _message.value = value
    }

    fun setDarkTheme(enabled: Boolean) = update { it.copy(darkTheme = enabled) }

    fun saveProduct(product: Product) {
        val name = product.name.trim()
        if (name.isBlank()) {
            showMessage("Укажите название товара")
            return
        }
        val current = _state.value
        val existing = current.products.firstOrNull {
            it.id == product.id ||
                (product.barcode.isNotBlank() && it.barcode == product.barcode && it.id != product.id)
        }
        val saved = if (existing != null && existing.id != product.id) {
            existing.copy(
                sku = product.sku.trim(),
                name = name,
                category = product.category.trim(),
                unit = product.unit.ifBlank { "шт" },
                stock = product.stock,
                bwareStock = product.bwareStock,
            )
        } else product.copy(name = name, barcode = product.barcode.trim(), sku = product.sku.trim())
        val products = if (existing == null) current.products + saved
        else current.products.map { if (it.id == existing.id) saved.copy(id = existing.id) else it }
        persist(current.copy(products = products))
        showMessage(if (existing == null) "Товар добавлен" else "Изменения сохранены")
    }

    fun deleteProduct(productId: String) {
        val current = _state.value
        persist(current.copy(products = current.products.filterNot { it.id == productId }))
        showMessage("Товар удалён")
    }

    fun saveReceipt(
        draftLines: List<DraftLine>,
        date: String,
        orderNumber: String,
        supplier: String,
        isBware: Boolean,
    ) {
        val validLines = draftLines.filter { it.quantity > 0.0 }
        if (validLines.isEmpty()) {
            showMessage("Добавьте хотя бы одну позицию")
            return
        }
        val current = _state.value
        val lines = validLines.map { draft ->
            ReceiptLine(
                productId = draft.product.id,
                barcode = draft.product.barcode,
                name = draft.product.name,
                quantity = draft.quantity,
                unit = draft.product.unit,
            )
        }
        val receipt = Receipt(
            date = date.ifBlank { today() },
            orderNumber = orderNumber.trim().let { if (it.isBlank() || it.startsWith("EB", true)) it else "EB$it" },
            supplier = supplier.trim(),
            isBware = isBware,
            lines = lines,
        )
        val quantities = lines.groupBy { it.productId }.mapValues { (_, items) -> items.sumOf { it.quantity } }
        val updatedProducts = current.products.map { product ->
            val amount = quantities[product.id] ?: return@map product
            if (isBware) product.copy(bwareStock = product.bwareStock + amount)
            else product.copy(stock = product.stock + amount)
        }
        persist(current.copy(products = updatedProducts, receipts = listOf(receipt) + current.receipts))
        showMessage("Приёмка сохранена")
    }

    fun updateInventoryCount(locationCode: String, product: Product, count: Double) {
        val code = locationCode.trim()
        if (code.isBlank()) {
            showMessage("Сначала укажите адрес склада")
            return
        }
        val current = _state.value
        val previous = current.locations.firstOrNull { it.code.equals(code, ignoreCase = true) }
        val location = InventoryLocation(
            code = code,
            counts = (previous?.counts.orEmpty() + (product.id to count.coerceAtLeast(0.0))),
        )
        val locations = current.locations.filterNot { it.code.equals(code, ignoreCase = true) } + location
        persist(current.copy(locations = locations, selectedLocation = code))
        showMessage("${product.name}: внесено $count ${product.unit}")
    }

    fun selectLocation(code: String) {
        val clean = code.trim()
        update { it.copy(selectedLocation = clean) }
    }

    fun addTask(title: String, description: String) {
        if (title.isBlank()) {
            showMessage("Введите название задачи")
            return
        }
        update {
            it.copy(tasks = listOf(WarehouseTask(title = title.trim(), description = description.trim())) + it.tasks)
        }
        showMessage("Задача добавлена")
    }

    fun toggleTask(taskId: String) {
        update { current ->
            current.copy(tasks = current.tasks.map { if (it.id == taskId) it.copy(done = !it.done) else it })
        }
    }

    fun deleteTask(taskId: String) {
        update { current -> current.copy(tasks = current.tasks.filterNot { it.id == taskId }) }
    }

    fun deleteReceipt(receiptId: String) {
        val current = _state.value
        val receipt = current.receipts.firstOrNull { it.id == receiptId } ?: return
        val quantities = receipt.lines.groupBy { it.productId }.mapValues { (_, items) -> items.sumOf { it.quantity } }
        val updatedProducts = current.products.map { product ->
            val amount = quantities[product.id] ?: return@map product
            if (receipt.isBware) product.copy(bwareStock = (product.bwareStock - amount).coerceAtLeast(0.0))
            else product.copy(stock = (product.stock - amount).coerceAtLeast(0.0))
        }
        persist(current.copy(products = updatedProducts, receipts = current.receipts.filterNot { it.id == receiptId }))
        showMessage("Партия удалена, остатки пересчитаны")
    }

    fun importJson(json: String, onComplete: (ImportResult?) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.importJson(json, _state.value) }
            }
            result.onSuccess { (next, summary) ->
                persist(next)
                _message.value = summary.message
                onComplete(summary)
            }.onFailure { error ->
                _message.value = error.message?.let { "Не удалось прочитать файл: $it" }
                    ?: "Не удалось прочитать файл. Проверьте формат JSON."
                onComplete(null)
            }
        }
    }

    fun importProducts(rows: List<List<String>>) {
        val current = _state.value
        val first = rows.firstOrNull()?.firstOrNull().orEmpty()
        val hasHeader = first.contains("barcode", true) || first.contains("ean", true) ||
            first.contains("штрих", true) || first.contains("name", true) || first.contains("назв", true)
        val dataRows = if (hasHeader) rows.drop(1) else rows
        if (dataRows.isEmpty()) {
            showMessage("В файле нет строк с товарами")
            return
        }
        val products = current.products.toMutableList()
        var imported = 0
        dataRows.forEach { row ->
            if (row.all(String::isBlank)) return@forEach
            val barcode = row.getOrNull(0).orEmpty().trim()
            val sku = row.getOrNull(1).orEmpty().trim()
            val name = row.getOrNull(2).orEmpty().trim()
            if (name.isBlank()) return@forEach
            val category = row.getOrNull(3).orEmpty().trim()
            val unit = row.getOrNull(4).orEmpty().ifBlank { "шт" }.trim()
            val stock = row.getOrNull(5)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val bwareStock = row.getOrNull(6)?.replace(',', '.')?.toDoubleOrNull() ?: 0.0
            val index = products.indexOfFirst {
                (barcode.isNotBlank() && it.barcode == barcode) || it.name.equals(name, true)
            }
            if (index >= 0) {
                products[index] = products[index].copy(
                    sku = sku.ifBlank { products[index].sku },
                    name = name,
                    category = category.ifBlank { products[index].category },
                    unit = unit,
                    stock = stock,
                    bwareStock = bwareStock,
                )
            } else {
                products.add(Product(barcode = barcode, sku = sku, name = name, category = category, unit = unit, stock = stock, bwareStock = bwareStock))
            }
            imported++
        }
        persist(current.copy(products = products))
        showMessage("Импортировано товаров: $imported")
    }

    private fun update(transform: (WarehouseState) -> WarehouseState) {
        persist(transform(_state.value))
    }

    private fun persist(next: WarehouseState) {
        _state.value = next
        viewModelScope.launch(Dispatchers.IO) {
            persistenceMutex.withLock { repository.save(next) }
        }
    }

    private fun showMessage(value: String) {
        _message.value = value
    }

    private fun today(): String =
        java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yy"))

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(WarehouseViewModel::class.java))
                    return WarehouseViewModel(application) as T
                }
            }
    }
}