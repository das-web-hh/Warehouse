package ru.warehouse.app.data

import java.util.UUID

enum class WarehouseScreen(val title: String) {
    Home("Warehouse"),
    Catalog("Каталог"),
    Receive("Приём товаров"),
    Bware("Приёмка B-Ware"),
    Inventory("Инвентаризация"),
    History("История"),
    Tasks("Задачи"),
    Transfer("Импорт / экспорт"),
    Settings("Настройки"),
}

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val barcode: String = "",
    val sku: String = "",
    val name: String,
    val category: String = "",
    val unit: String = "шт",
    val stock: Double = 0.0,
    val bwareStock: Double = 0.0,
    val pendingApproval: Boolean = false,
)

data class ReceiptLine(
    val productId: String,
    val barcode: String,
    val name: String,
    val quantity: Double,
    val unit: String,
)

data class Receipt(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val orderNumber: String = "",
    val supplier: String = "",
    val isBware: Boolean = false,
    val lines: List<ReceiptLine>,
    val createdAt: Long = System.currentTimeMillis(),
)

data class InventoryLocation(
    val code: String,
    val counts: Map<String, Double> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class WarehouseTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

data class WarehouseState(
    val products: List<Product> = emptyList(),
    val receipts: List<Receipt> = emptyList(),
    val locations: List<InventoryLocation> = emptyList(),
    val tasks: List<WarehouseTask> = emptyList(),
    val darkTheme: Boolean = false,
    val selectedLocation: String = "",
)

data class DraftLine(
    val product: Product,
    val quantity: Double = 1.0,
)

data class ImportResult(
    val productCount: Int,
    val receiptCount: Int,
    val message: String,
)
