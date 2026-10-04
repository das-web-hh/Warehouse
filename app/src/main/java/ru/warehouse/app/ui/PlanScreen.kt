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
    ReceiveHub("Приём товаров"),
    ByInvoice("По накладной"),
    Auto("Автоприём"),
    History2("История 2"),
    Plan("Приём по заданию"),
    LinkTool("Товар + штрихкод"),
    Profile("Профиль"),
    Info("Инфо"),
    Documents("Документы"),
    Gemini("Чат Gemini"),
    Integrations("Gemini и сервер"),
}

enum class CheckStatus {
    MATCH,
    SHORTAGE,
    OVERAGE,
    MISMATCH,
    PENDING
}

data class Product(
    val id: String = UUID.randomUUID().toString(),
    val barcode: String = "",
    val sku: String = "",
    val name: String = "",
    val category: String = "",
    val unit: String = "шт",
    val stock: Double = 0.0,
    val bwareStock: Double = 0.0,
    val pendingApproval: Boolean = false,
)

data class PlanItem(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String = "",
    val name: String = "",
    val barcode: String = "",
    val ean: String = "",
    val sku: String = "",
    val code: String = "",
    val product: String = "",
    val quantity: Double = 0.0,
    val planned: Double = 0.0,
    val expectedQty: Double = 0.0,
    val scannedQty: Double = 0.0,
    val unit: String = "шт",
    val type: String = "",
    val isBware: Boolean = false,
)

data class CheckResult(
    val id: String = UUID.randomUUID().toString(),
    val itemId: String = "",
    val name: String = "",
    val barcode: String = "",
    val ean: String = "",
    val sku: String = "",
    val expectedQty: Double = 0.0,
    val actualQty: Double = 0.0,
    val scannedQty: Double = 0.0,
    val quantity: Double = 0.0,
    val difference: Double = 0.0,
    val status: CheckStatus = CheckStatus.PENDING,
    val unit: String = "шт",
    val type: String = "",
    val code: String = "",
    val product: String = "",
    val planned: Double = 0.0,
)

data class InvoiceItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val barcode: String = "",
    val sku: String = "",
    val quantity: Double = 0.0,
    val scannedQty: Double = 0.0,
    val unit: String = "шт",
    val price: Double = 0.0,
)

data class Invoice(
    val id: String = UUID.randomUUID().toString(),
    val number: String = "",
    val date: String = "",
    val supplier: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val isCompleted: Boolean = false,
)

data class ReceiptLine(
    val productId: String = "",
    val barcode: String = "",
    val name: String = "",
    val quantity: Double = 0.0,
    val unit: String = "шт",
)

data class Receipt(
    val id: String = UUID.randomUUID().toString(),
    val date: String = "",
    val orderNumber: String = "",
    val supplier: String = "",
    val isBware: Boolean = false,
    val lines: List<ReceiptLine> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class InventoryLocation(
    val code: String = "",
    val counts: Map<String, Double> = emptyMap(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class WarehouseTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
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
    val product: Product = Product(),
    val quantity: Double = 1.0,
)

data class ImportResult(
    val productCount: Int = 0,
    val receiptCount: Int = 0,
    val message: String = "",
)
