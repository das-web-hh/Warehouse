package ru.warehouse.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Ответ /process-invoice сервера server.py (Python, порт 8050). */
data class InvoiceResponse(
    val sender: String,
    val orderNumber: String,
    val date: String,
    val items: List<BatchItem>,
    val totalPages: Int,
    val failedPages: Int,
)

class ServerException(message: String) : Exception(message)

/**
 * Клиент для уже существующего Python-сервера (assets/server.py).
 * Никаких ответов не подделывает: если сервер недоступен — бросает ServerException.
 * Вызывать только из фонового потока (Dispatchers.IO).
 */
object ServerClient {

    fun health(baseUrl: String): Boolean = runCatching {
        val c = open(baseUrl, "/health", "GET", 4_000)
        c.responseCode == 200
    }.getOrDefault(false)

    fun processInvoice(
        baseUrl: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
        geminiKey: String,
    ): InvoiceResponse {
        val fields = if (geminiKey.isNotBlank()) mapOf("api_key" to geminiKey) else emptyMap()
        val json = postMultipart(baseUrl, "/process-invoice", fields, "file", fileName, mimeType, bytes, 300_000)
        val items = json.optJSONArray("items").let { arr ->
            val out = mutableListOf<BatchItem>()
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isBlank()) continue
                out += BatchItem(
                    name = name,
                    quantity = o.optDouble("menge", o.optDouble("quantity", 0.0)),
                    unit = o.optString("unit").ifBlank { "шт" },
                    ean = o.optString("ean").filter { it.isLetterOrDigit() },
                )
            }
            out
        }
        return InvoiceResponse(
            sender = json.optString("senderName").trim(),
            orderNumber = json.optString("orderNumber").trim(),
            date = json.optString("date").trim(),
            items = items,
            totalPages = json.optInt("totalPages", 0),
            failedPages = json.optJSONArray("failedPages")?.length() ?: 0,
        )
    }

    /** Чат Gemini через сервер (/gemini-chat). Возвращает текст ответа. */
    fun chat(baseUrl: String, text: String, instruction: String, history: List<ChatMessage>, geminiKey: String): String {
        val hist = JSONArray()
        history.takeLast(2).forEach { m ->
            hist.put(
                JSONObject().put("role", if (m.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text))),
            )
        }
        val fields = mutableMapOf("text" to text, "instruction" to instruction, "history" to hist.toString())
        if (geminiKey.isNotBlank()) fields["api_key"] = geminiKey
        val json = postMultipart(baseUrl, "/gemini-chat", fields, null, null, null, null, 120_000)
        return json.optString("text").ifBlank { throw ServerException("Gemini не вернул текстовый ответ.") }
    }

    /** Прямой запрос к Gemini API (без Python), как в warehouse.html. */
    fun chatDirect(model: String, apiKey: String, instruction: String, history: List<ChatMessage>, text: String): String {
        if (apiKey.isBlank()) throw ServerException("Не указан API-ключ Gemini (Настройки → Gemini).")
        val contents = JSONArray()
        history.takeLast(10).forEach { m ->
            contents.put(
                JSONObject().put("role", if (m.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.text))),
            )
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text))))
        val body = JSONObject().put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", 8192))
        if (instruction.isNotBlank()) {
            body.put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instruction))))
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 120_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", apiKey)
        c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val code = c.responseCode
        val raw = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val msg = runCatching { JSONObject(raw).getJSONObject("error").optString("message") }.getOrNull()
            throw ServerException(msg?.takeIf { it.isNotBlank() } ?: "Gemini вернул ошибку HTTP $code")
        }
        val payload = JSONObject(raw)
        val parts = payload.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) {
            val t = parts.optJSONObject(i)?.optString("text").orEmpty()
            if (t.isNotBlank()) { if (sb.isNotEmpty()) sb.append('\n'); sb.append(t) }
        }
        val answer = sb.toString().trim()
        if (answer.isBlank()) {
            val block = payload.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw ServerException(if (block.isNotBlank()) "Gemini заблокировал запрос: $block." else "Gemini не вернул текстовый ответ.")
        }
        return answer
    }

    // ───────── низкий уровень ─────────
    private fun open(baseUrl: String, path: String, method: String, timeoutMs: Int): HttpURLConnection {
        val c = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 8_000
        c.readTimeout = timeoutMs
        return c
    }

    private fun postMultipart(
        baseUrl: String,
        path: String,
        fields: Map<String, String>,
        fileField: String?,
        fileName: String?,
        mime: String?,
        bytes: ByteArray?,
        timeoutMs: Int,
    ): JSONObject {
        val boundary = "----wh" + UUID.randomUUID().toString().replace("-", "")
        val body = ByteArrayOutputStream()
        DataOutputStream(body).use { out ->
            fun line(s: String) = out.write((s + "\r\n").toByteArray(Charsets.UTF_8))
            fields.forEach { (k, v) ->
                line("--$boundary")
                line("Content-Disposition: form-data; name=\"$k\"")
                line("")
                line(v)
            }
            if (fileField != null && bytes != null) {
                line("--$boundary")
                line("Content-Disposition: form-data; name=\"$fileField\"; filename=\"${(fileName ?: "file").replace("\"", "_")}\"")
                line("Content-Type: ${mime ?: "application/octet-stream"}")
                line("")
                out.write(bytes)
                line("")
            }
            line("--$boundary--")
        }
        val data = body.toByteArray()
        val c = try {
            open(baseUrl, path, "POST", timeoutMs)
        } catch (e: Exception) {
            throw ServerException("Неверный адрес сервера: $baseUrl")
        }
        c.doOutput = true
        c.setFixedLengthStreamingMode(data.size)
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        try {
            c.outputStream.use { it.write(data) }
            val code = c.responseCode
            val raw = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            if (code !in 200..299 || json?.optString("status") == "error") {
                throw ServerException(json?.optString("message")?.takeIf { it.isNotBlank() } ?: "Сервер вернул ошибку HTTP $code")
            }
            return json ?: throw ServerException("Сервер вернул ответ в неизвестном формате.")
        } catch (e: ServerException) {
            throw e
        } catch (e: java.net.ConnectException) {
            throw ServerException("Не удалось подключиться к Python-серверу ($baseUrl). Запустите server.py.")
        } catch (e: java.net.SocketTimeoutException) {
            throw ServerException("Python-сервер не ответил вовремя.")
        } catch (e: Exception) {
            throw ServerException(e.message ?: "Ошибка связи с сервером.")
        }
    }
}
