package ru.warehouse.app.data

import java.util.UUID

/** Строка списка в окне «По заданию». */
data class PlanItem(
    val name: String,
    val quantity: Int,
    val ean: String,
)

enum class CheckType(val label: String) {
    MATCH("Совпало"),
    MISMATCH("Не совпало"),
    EXTRA("Лишнее"),
    LESS("Мало"),
}

/** Результат сверки плана и отсканированного. */
data class CheckResult(
    val type: CheckType,
    val code: String,
    val product: Product?,
    val planned: PlanItem?,
    val actualQty: Int,
    val expectedQty: Int,
    val name: String,
)

/** Напечатанный/созданный документ (окно «Документы»). */
data class StoredDocument(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "report",
    val title: String,
    val subtitle: String = "",
    val date: String,
    val time: String,
    val ts: Long = System.currentTimeMillis(),
    val body: String = "",
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" | "assistant"
    val text: String,
    val ts: Long = System.currentTimeMillis(),
)

data class InvoiceItem(
    val name: String,
    val quantity: Double,
    val unit: String = "шт",
    val ean: String = "",
)

enum class BatchStatus(val label: String) {
    QUEUED("В очереди"),
    PROCESSING("Распознаётся…"),
    READY("Ожидает приёма"),
    ACCEPTED("Принято"),
    ERROR("Ошибка"),
}

/** Распознанная накладная (партия) — окна «По накладной», «Авто», «История 2». */
data class InvoiceBatch(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val source: String = "manual", // manual | auto
    val sender: String = "",
    val orderNumber: String = "",
    val date: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val status: BatchStatus = BatchStatus.QUEUED,
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long = 0L,
)

data class ExtraSettings(
    val pythonUrl: String = "http://localhost:8050",
    val geminiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",
    val geminiInstruction: String = "",
    val autoFolderUri: String = "",
    val autoIntervalMinutes: Int = 15,
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val accountId: String = UUID.randomUUID().toString(),
)
