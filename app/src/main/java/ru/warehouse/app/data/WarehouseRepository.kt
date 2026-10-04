package ru.warehouse.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class WarehouseRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): WarehouseState {
        val saved = preferences.getString(STATE_KEY, null)
        if (saved != null) {
            return decodeState(JSONObject(saved))
        }
        return seededState().also(::save)
    }

    fun save(state: WarehouseState) {
        preferences.edit().putString(STATE_KEY, encodeState(state).toString()).apply()
    }

    fun importJson(text: String, current: WarehouseState): Pair<WarehouseState, ImportResult> {
        val root = JSONObject(text)
        require(
            root.has("my_off_db") || root.has("products") || root.has("my_off_arr6") ||
                root.has("receipts") || root.has("name"),
        ) { "JSON не содержит данных склада" }
        val rawProducts = root.optJSONArray("my_off_db")
            ?: root.optJSONArray("products")
            ?: if (root.has("name")) JSONArray().put(root) else JSONArray()
        val mergedProducts = current.products.toMutableList()
        val imported = mutableListOf<Product>()
        val idRemap = mutableMapOf<String, String>()
        for (index in 0 until rawProducts.length()) {
            val row = rawProducts.optJSONObject(index) ?: continue
            val name = row.optString("name").trim()
            if (name.isBlank()) continue
            val barcode = row.optString("ean", row.optString("barcode", row.optString("ean2"))).trim()
            val product = Product(
                id = row.optString("id").ifBlank { "import-$barcode-$name" },
                barcode = barcode,
                sku = row.optString("artikel", row.optString("sku")).trim(),
                name = name,
                category = row.optString("category").trim(),
                unit = row.optString("unit").ifBlank { "шт" }.trim(),
                stock = row.optDouble("stock", 0.0),
                bwareStock = row.optDouble("bwareStock", 0.0),
                pendingApproval = row.optBoolean("pendingApproval"),
            )
            val existingIndex = mergedProducts.indexOfFirst {
                (barcode.isNotBlank() && it.barcode == barcode) || it.name.equals(name, ignoreCase = true)
            }
            val storedProduct = if (existingIndex >= 0) {
                val existing = mergedProducts[existingIndex]
                product.copy(
                    id = existing.id,
                    stock = if (row.has("stock")) product.stock else existing.stock,
                    bwareStock = if (row.has("bwareStock")) product.bwareStock else existing.bwareStock,
                    sku = product.sku.ifBlank { existing.sku },
                    category = product.category.ifBlank { existing.category },
                    unit = product.unit.ifBlank { existing.unit },
                ).also { mergedProducts[existingIndex] = it }
            } else {
                product.also(mergedProducts::add)
            }
            idRemap[product.id] = storedProduct.id
            imported.add(storedProduct)
        }

        val arrivals = root.optJSONArray("my_off_arr6")
        var addedReceipts = 0
        var next = current.copy(products = mergedProducts)
        if (arrivals != null) {
            val grouped = linkedMapOf<String, MutableList<ReceiptLine>>()
            val dates = mutableMapOf<String, String>()
            val suppliers = mutableMapOf<String, String>()
            val orderNumbers = mutableMapOf<String, String>()
            for (index in 0 until arrivals.length()) {
                val row = arrivals.optJSONObject(index) ?: continue
                val name = row.optString("name").trim()
                if (name.isBlank()) continue
                val product = next.products.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: Product(name = name, unit = "шт").also { next = next.copy(products = next.products + it) }
                val date = row.optString("date")
                val sender = row.optString("senderName")
                val orderNumber = row.optString("orderNumber")
                val fallbackKey = listOf(date, orderNumber, sender).joinToString("|")
                val groupId = row.optString("batchId").ifBlank {
                    if (fallbackKey.isBlank()) "legacy_$index" else "legacy_$fallbackKey"
                }
                grouped.getOrPut(groupId) { mutableListOf() }.add(
                    ReceiptLine(
                        productId = product.id,
                        barcode = product.barcode,
                        name = name,
                        quantity = row.optDouble("menge", row.optDouble("quantity", 0.0)),
                        unit = product.unit,
                    ),
                )
                dates.putIfAbsent(groupId, date.ifBlank { row.optString("receivedAt").ifBlank { today() } })
                suppliers.putIfAbsent(groupId, sender)
                orderNumbers.putIfAbsent(groupId, orderNumber)
            }
            val importedBatches = grouped.map { (id, lines) ->
                Receipt(
                    id = id,
                    date = dates[id] ?: today(),
                    orderNumber = orderNumbers[id].orEmpty(),
                    supplier = suppliers[id].orEmpty(),
                    lines = lines,
                )
            }
            val existingIds = next.receipts.mapTo(mutableSetOf()) { it.id }
            val freshBatches = importedBatches.filterNot { it.id in existingIds }
            next = next.copy(receipts = freshBatches + next.receipts)
            addedReceipts = freshBatches.size
            val totals = freshBatches.flatMap { it.lines }
                .groupBy { it.productId }
                .mapValues { (_, lines) -> lines.sumOf { it.quantity } }
            next = next.copy(products = next.products.map { product ->
                val fromImportedReceipts = totals[product.id] ?: 0.0
                if (fromImportedReceipts > 0.0) product.copy(stock = max(product.stock, fromImportedReceipts))
                else product
            })
        }

        val modernReceipts = root.optJSONArray("receipts")
        if (modernReceipts != null) {
            val importedReceipts = buildList {
                for (index in 0 until modernReceipts.length()) {
                    val row = modernReceipts.optJSONObject(index) ?: continue
                    val lines = row.optJSONArray("lines").toList { line ->
                        val barcode = line.optString("barcode")
                        val name = line.optString("name")
                        val originalId = line.optString("productId")
                        val productId = idRemap[originalId] ?: next.products.firstOrNull {
                            (barcode.isNotBlank() && it.barcode == barcode) || it.name.equals(name, true)
                        }?.id ?: originalId
                        val product = next.products.firstOrNull { it.id == productId }
                        ReceiptLine(
                            productId = productId,
                            barcode = barcode.ifBlank { product?.barcode.orEmpty() },
                            name = name.ifBlank { product?.name.orEmpty() },
                            quantity = line.optDouble("quantity", line.optDouble("menge")),
                            unit = line.optString("unit").ifBlank { product?.unit ?: "шт" },
                        )
                    }
                    val id = row.optString("id").ifBlank { "import-receipt-$index" }
                    add(
                        Receipt(
                            id = id,
                            date = row.optString("date").ifBlank { today() },
                            orderNumber = row.optString("orderNumber"),
                            supplier = row.optString("supplier"),
                            isBware = row.optBoolean("isBware"),
                            lines = lines,
                            createdAt = row.optLong("createdAt", System.currentTimeMillis()),
                        ),
                    )
                }
            }
            val existingIds = next.receipts.mapTo(mutableSetOf()) { it.id }
            val freshReceipts = importedReceipts.filterNot { it.id in existingIds }
            addedReceipts += freshReceipts.size
            next = next.copy(receipts = freshReceipts + next.receipts)
        }

        val importedLocations = root.optJSONArray("locations")
        if (importedLocations != null) {
            val locations = importedLocations.toList { row ->
                val countsObject = row.optJSONObject("counts") ?: JSONObject()
                val counts = buildMap {
                    countsObject.keys().forEach { key ->
                        val remappedId = idRemap[key] ?: key
                        put(remappedId, countsObject.optDouble(key))
                    }
                }
                InventoryLocation(
                    code = row.optString("code"),
                    counts = counts,
                    updatedAt = row.optLong("updatedAt", System.currentTimeMillis()),
                )
            }
            val existingCodes = next.locations.mapTo(mutableSetOf()) { it.code.lowercase(Locale.ROOT) }
            next = next.copy(locations = locations.filterNot { it.code.lowercase(Locale.ROOT) in existingCodes } + next.locations)
        }
        val importedTasks = root.optJSONArray("tasks")
        if (importedTasks != null) {
            val tasks = importedTasks.toList { row ->
                WarehouseTask(
                    id = row.optString("id").ifBlank { "import-task-${row.optLong("createdAt")}" },
                    title = row.optString("title"),
                    description = row.optString("description"),
                    done = row.optBoolean("done"),
                    createdAt = row.optLong("createdAt", System.currentTimeMillis()),
                )
            }
            val existingIds = next.tasks.mapTo(mutableSetOf()) { it.id }
            next = next.copy(tasks = tasks.filterNot { it.id in existingIds } + next.tasks)
        }
        if (root.has("darkTheme")) next = next.copy(darkTheme = root.optBoolean("darkTheme"))
        if (root.has("selectedLocation")) next = next.copy(selectedLocation = root.optString("selectedLocation"))

        val result = ImportResult(
            productCount = imported.size,
            receiptCount = addedReceipts,
            message = "Добавлено товаров: ${imported.size}; партий: $addedReceipts",
        )
        return next to result
    }

    private fun encodeState(state: WarehouseState) = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("products", JSONArray().apply {
            state.products.forEach { product ->
                put(JSONObject().apply {
                    put("id", product.id)
                    put("barcode", product.barcode)
                    put("sku", product.sku)
                    put("name", product.name)
                    put("category", product.category)
                    put("unit", product.unit)
                    put("stock", product.stock)
                    put("bwareStock", product.bwareStock)
                    put("pendingApproval", product.pendingApproval)
                })
            }
        })
        put("receipts", JSONArray().apply {
            state.receipts.forEach { receipt ->
                put(JSONObject().apply {
                    put("id", receipt.id)
                    put("date", receipt.date)
                    put("orderNumber", receipt.orderNumber)
                    put("supplier", receipt.supplier)
                    put("isBware", receipt.isBware)
                    put("createdAt", receipt.createdAt)
                    put("lines", JSONArray().apply {
                        receipt.lines.forEach { line ->
                            put(JSONObject().apply {
                                put("productId", line.productId)
                                put("barcode", line.barcode)
                                put("name", line.name)
                                put("quantity", line.quantity)
                                put("unit", line.unit)
                            })
                        }
                    })
                })
            }
        })
        put("locations", JSONArray().apply {
            state.locations.forEach { location ->
                put(JSONObject().apply {
                    put("code", location.code)
                    put("updatedAt", location.updatedAt)
                    put("counts", JSONObject().apply {
                        location.counts.forEach { (productId, count) -> put(productId, count) }
                    })
                })
            }
        })
        put("tasks", JSONArray().apply {
            state.tasks.forEach { task ->
                put(JSONObject().apply {
                    put("id", task.id)
                    put("title", task.title)
                    put("description", task.description)
                    put("done", task.done)
                    put("createdAt", task.createdAt)
                })
            }
        })
        put("darkTheme", state.darkTheme)
        put("selectedLocation", state.selectedLocation)
    }

    private fun decodeState(json: JSONObject): WarehouseState {
        val products = json.optJSONArray("products").toList { row ->
            Product(
                id = row.optString("id").ifBlank { "legacy-${row.optString("ean")}-${row.optString("name")}" },
                barcode = row.optString("barcode", row.optString("ean")),
                sku = row.optString("sku", row.optString("artikel")),
                name = row.optString("name"),
                category = row.optString("category"),
                unit = row.optString("unit").ifBlank { "шт" },
                stock = row.optDouble("stock", 0.0),
                bwareStock = row.optDouble("bwareStock", 0.0),
                pendingApproval = row.optBoolean("pendingApproval"),
            )
        }
        val receipts = json.optJSONArray("receipts").toList { row ->
            Receipt(
                id = row.optString("id").ifBlank { "receipt-${row.optLong("createdAt")}" },
                date = row.optString("date").ifBlank { today() },
                orderNumber = row.optString("orderNumber"),
                supplier = row.optString("supplier"),
                isBware = row.optBoolean("isBware"),
                createdAt = row.optLong("createdAt", System.currentTimeMillis()),
                lines = row.optJSONArray("lines").toList { line ->
                    ReceiptLine(
                        productId = line.optString("productId"),
                        barcode = line.optString("barcode"),
                        name = line.optString("name"),
                        quantity = line.optDouble("quantity"),
                        unit = line.optString("unit").ifBlank { "шт" },
                    )
                },
            )
        }
        val locations = json.optJSONArray("locations").toList { row ->
            val countsObject = row.optJSONObject("counts") ?: JSONObject()
            val counts = buildMap {
                countsObject.keys().forEach { key -> put(key, countsObject.optDouble(key)) }
            }
            InventoryLocation(
                code = row.optString("code"),
                counts = counts,
                updatedAt = row.optLong("updatedAt", System.currentTimeMillis()),
            )
        }
        val tasks = json.optJSONArray("tasks").toList { row ->
            WarehouseTask(
                id = row.optString("id").ifBlank { "task-${row.optLong("createdAt")}" },
                title = row.optString("title"),
                description = row.optString("description"),
                done = row.optBoolean("done"),
                createdAt = row.optLong("createdAt", System.currentTimeMillis()),
            )
        }
        return WarehouseState(
            products = products,
            receipts = receipts,
            locations = locations,
            tasks = tasks,
            darkTheme = json.optBoolean("darkTheme"),
            selectedLocation = json.optString("selectedLocation"),
        )
    }

    private fun seededState(): WarehouseState {
        val products = listOf(
            Product(barcode = "4031735112022", sku = "1202", name = "marstall Force 20kg", unit = "меш."),
            Product(barcode = "4005537021104", sku = "2110", name = "Zedan insektenschutz 500ml", unit = "шт"),
            Product(barcode = "1111111111111", sku = "9999", name = "marstall Huf-Regulator 10kg", unit = "меш."),
        )
        val receipt = Receipt(
            id = "demo1",
            date = "17.06.26",
            supplier = "Demo",
            lines = listOf(
                ReceiptLine(products[0].id, products[0].barcode, products[0].name, 40.0, products[0].unit),
                ReceiptLine(products[2].id, products[2].barcode, products[2].name, 21.0, products[2].unit),
            ),
        )
        return WarehouseState(
            products = products.mapIndexed { index, product ->
                when (index) {
                    0 -> product.copy(stock = 40.0)
                    2 -> product.copy(stock = 21.0)
                    else -> product
                }
            },
            receipts = listOf(receipt),
        )
    }

    private fun today(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yy", Locale.getDefault()))

    private inline fun <T> JSONArray?.toList(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val row = optJSONObject(index) ?: continue
                add(transform(row))
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "warehouse_native"
        const val STATE_KEY = "warehouse_state"
        const val SCHEMA_VERSION = 1
    }
}
