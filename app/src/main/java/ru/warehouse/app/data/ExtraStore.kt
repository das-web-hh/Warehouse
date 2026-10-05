package ru.warehouse.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Локальное хранилище для того, что не входит в WarehouseState:
 * документы, чат Gemini, партии накладных (История 2), настройки сервера/Gemini, профиль.
 * Лежит в отдельном SharedPreferences-файле, поэтому старые данные не затрагиваются.
 */
class ExtraStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("warehouse_extra", Context.MODE_PRIVATE)

    // ───────── настройки / профиль ─────────
    fun loadSettings(): ExtraSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return ExtraSettings().also(::saveSettings)
        return runCatching {
            val o = JSONObject(raw)
            val d = ExtraSettings()
            ExtraSettings(
                pythonUrl = o.optString("pythonUrl", d.pythonUrl).ifBlank { d.pythonUrl },
                geminiKey = o.optString("geminiKey", ""),
                geminiModel = o.optString("geminiModel", d.geminiModel).ifBlank { d.geminiModel },
                geminiInstruction = o.optString("geminiInstruction", ""),
                autoFolderUri = o.optString("autoFolderUri", ""),
                autoIntervalMinutes = o.optInt("autoIntervalMinutes", 15).coerceIn(1, 240),
                firstName = o.optString("firstName", ""),
                lastName = o.optString("lastName", ""),
                email = o.optString("email", ""),
                accountId = o.optString("accountId", d.accountId).ifBlank { d.accountId },
            )
        }.getOrElse { ExtraSettings() }
    }

    fun saveSettings(s: ExtraSettings) {
        val o = JSONObject()
            .put("pythonUrl", s.pythonUrl)
            .put("geminiKey", s.geminiKey)
            .put("geminiModel", s.geminiModel)
            .put("geminiInstruction", s.geminiInstruction)
            .put("autoFolderUri", s.autoFolderUri)
            .put("autoIntervalMinutes", s.autoIntervalMinutes)
            .put("firstName", s.firstName)
            .put("lastName", s.lastName)
            .put("email", s.email)
            .put("accountId", s.accountId)
        prefs.edit().putString(KEY_SETTINGS, o.toString()).apply()
    }

    // ───────── документы ─────────
    fun loadDocuments(): List<StoredDocument> = readArray(KEY_DOCS) { o ->
        StoredDocument(
            id = o.optString("id"),
            type = o.optString("type", "report"),
            title = o.optString("title"),
            subtitle = o.optString("subtitle"),
            date = o.optString("date"),
            time = o.optString("time"),
            ts = o.optLong("ts"),
            body = o.optString("body"),
        )
    }

    fun saveDocuments(list: List<StoredDocument>) = writeArray(KEY_DOCS, list.take(MAX_DOCS)) { d ->
        JSONObject().put("id", d.id).put("type", d.type).put("title", d.title)
            .put("subtitle", d.subtitle).put("date", d.date).put("time", d.time)
            .put("ts", d.ts).put("body", d.body)
    }

    // ───────── чат Gemini ─────────
    fun loadChat(): List<ChatMessage> = readArray(KEY_CHAT) { o ->
        ChatMessage(o.optString("id"), o.optString("role", "user"), o.optString("text"), o.optLong("ts"))
    }

    fun saveChat(list: List<ChatMessage>) = writeArray(KEY_CHAT, list.takeLast(MAX_CHAT)) { m ->
        JSONObject().put("id", m.id).put("role", m.role).put("text", m.text).put("ts", m.ts)
    }

    // ───────── партии накладных / История 2 ─────────
    fun loadBatches(): List<InvoiceBatch> = readArray(KEY_BATCHES) { o ->
        InvoiceBatch(
            id = o.optString("id"),
            fileName = o.optString("fileName"),
            source = o.optString("source", "manual"),
            sender = o.optString("sender"),
            orderNumber = o.optString("orderNumber"),
            date = o.optString("date"),
            items = o.optJSONArray("items").mapObjects { i ->
                BatchItem(i.optString("name"), i.optDouble("quantity", 0.0), i.optString("unit", "шт"), i.optString("ean"))
            },
            status = runCatching { BatchStatus.valueOf(o.optString("status")) }.getOrDefault(BatchStatus.READY),
            error = o.optString("error"),
            createdAt = o.optLong("createdAt"),
            acceptedAt = o.optLong("acceptedAt"),
        )
    }

    fun saveBatches(list: List<InvoiceBatch>) = writeArray(KEY_BATCHES, list.take(MAX_BATCHES)) { b ->
        JSONObject().put("id", b.id).put("fileName", b.fileName).put("source", b.source)
            .put("sender", b.sender).put("orderNumber", b.orderNumber).put("date", b.date)
            .put("status", b.status.name).put("error", b.error)
            .put("createdAt", b.createdAt).put("acceptedAt", b.acceptedAt)
            .put("items", JSONArray().apply {
                b.items.forEach { i ->
                    put(JSONObject().put("name", i.name).put("quantity", i.quantity)
                        .put("unit", i.unit).put("ean", i.ean))
                }
            })
    }

    /** Имена уже обработанных файлов папки «Авто» (чтобы не обрабатывать повторно). */
    fun loadProcessedFiles(): Set<String> =
        prefs.getStringSet(KEY_PROCESSED, emptySet())?.toSet() ?: emptySet()

    fun saveProcessedFiles(value: Set<String>) {
        prefs.edit().putStringSet(KEY_PROCESSED, value).apply()
    }

    // ───────── helpers ─────────
    private fun <T> readArray(key: String, map: (JSONObject) -> T): List<T> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { JSONArray(raw).mapObjects(map) }.getOrDefault(emptyList())
    }

    private fun <T> writeArray(key: String, list: List<T>, map: (T) -> JSONObject) {
        val arr = JSONArray()
        list.forEach { arr.put(map(it)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    private fun <T> JSONArray?.mapObjects(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>(length())
        for (i in 0 until length()) optJSONObject(i)?.let { out.add(map(it)) }
        return out
    }

    private companion object {
        const val KEY_SETTINGS = "settings"
        const val KEY_DOCS = "documents"
        const val KEY_CHAT = "chat"
        const val KEY_BATCHES = "batches"
        const val KEY_PROCESSED = "processed_files"
        const val MAX_DOCS = 500
        const val MAX_CHAT = 200
        const val MAX_BATCHES = 500
    }
}
