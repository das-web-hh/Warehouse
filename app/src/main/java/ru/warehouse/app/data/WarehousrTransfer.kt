package ru.warehouse.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

object WarehouseTransfer {
    private val columns = listOf("Barcode", "Article", "Name", "Category", "Unit", "Stock", "B-Ware")

    fun encodeJson(state: WarehouseState): ByteArray {
        val root = JSONObject().apply {
            put("schemaVersion", 1)
            put("products", JSONArray().apply {
                state.products.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("barcode", item.barcode)
                        put("sku", item.sku)
                        put("name", item.name)
                        put("category", item.category)
                        put("unit", item.unit)
                        put("stock", item.stock)
                        put("bwareStock", item.bwareStock)
                        put("pendingApproval", item.pendingApproval)
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
        return root.toString(2).toByteArray(StandardCharsets.UTF_8)
    }

    fun encodeCsv(state: WarehouseState): ByteArray {
        val text = buildString {
            append('\uFEFF')
            append(columns.joinToString(",") { quoteCsv(it) })
            append("\r\n")
            state.products.forEach { product ->
                val row = listOf(
                    product.barcode,
                    product.sku,
                    product.name,
                    product.category,
                    product.unit,
                    product.stock.toString(),
                    product.bwareStock.toString(),
                )
                append(row.joinToString(",") { quoteCsv(it) })
                append("\r\n")
            }
        }
        return text.toByteArray(StandardCharsets.UTF_8)
    }

    fun encodeXlsx(state: WarehouseState): ByteArray {
        val rows = buildList {
            add(columns)
            state.products.forEach { product ->
                add(
                    listOf(
                        product.barcode,
                        product.sku,
                        product.name,
                        product.category,
                        product.unit,
                        product.stock.toString(),
                        product.bwareStock.toString(),
                    ),
                )
            }
        }
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { rowIndex, row ->
                val excelRow = rowIndex + 1
                append("""<row r="$excelRow">""")
                row.forEachIndexed { columnIndex, value ->
                    val ref = "${columnName(columnIndex)}$excelRow"
                    if (rowIndex > 0 && columnIndex >= 5) {
                        val number = value.replace(',', '.').toDoubleOrNull() ?: 0.0
                        append("""<c r="$ref"><v>${number}</v></c>""")
                    } else {
                        append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escapeXml(value)}</t></is></c>""")
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val entries = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Warehouse" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to sheet,
        )
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun readCsv(bytes: ByteArray): List<List<String>> =
        parseCsv(String(bytes, StandardCharsets.UTF_8).removePrefix("\uFEFF"))

    fun readXlsx(bytes: ByteArray): List<List<String>> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && (entry.name == "xl/sharedStrings.xml" || entry.name.startsWith("xl/worksheets/sheet"))) {
                    val data = ByteArrayOutputStream()
                    zip.copyTo(data)
                    entries[entry.name] = data.toByteArray()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val sheet = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") }?.value
            ?: error("В книге не найден лист с товарами")
        val shared = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val xml = parseXml(sheet)
        val rowNodes = elements(xml, "row")
        return buildList {
            for (rowIndex in 0 until rowNodes.length) {
                val row = rowNodes.item(rowIndex) as? Element ?: continue
                val cellNodes = elements(row, "c")
                val cells = sortedMapOf<Int, String>()
                for (cellIndex in 0 until cellNodes.length) {
                    val cell = cellNodes.item(cellIndex) as? Element ?: continue
                    val column = columnIndex(cell.getAttribute("r"))
                    if (column < 0) continue
                    val type = cell.getAttribute("t")
                    val raw = firstText(elements(cell, "v")) ?: ""
                    val value = when (type) {
                        "s" -> shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                        "inlineStr" -> allText(elements(cell, "t"))
                        else -> raw
                    }
                    cells[column] = value
                }
                if (cells.isNotEmpty()) {
                    val maxColumn = cells.lastKey()
                    add((0..maxColumn).map { cells[it].orEmpty() })
                }
            }
        }
    }

    fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                character == '"' && quoted && index + 1 < text.length && text[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    row.add(field.toString())
                    field.setLength(0)
                }
                (character == '\n' || character == '\r') && !quoted -> {
                    row.add(field.toString())
                    field.setLength(0)
                    if (row.any(String::isNotBlank)) rows.add(row.toList())
                    row.clear()
                    if (character == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
                }
                else -> field.append(character)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any(String::isNotBlank)) rows.add(row)
        }
        return rows
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val xml = parseXml(bytes)
        val items = elements(xml, "si")
        return buildList {
            for (index in 0 until items.length) {
                val item = items.item(index) as? Element ?: continue
                add(allText(elements(item, "t")))
            }
        }
    }

    private fun parseXml(bytes: ByteArray) =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setXIncludeAware(false)
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

    private fun elements(node: Node, name: String) =
        (node as? org.w3c.dom.Document)?.getElementsByTagNameNS("*", name)
            ?: (node as Element).getElementsByTagNameNS("*", name)

    private fun firstText(nodes: org.w3c.dom.NodeList): String? =
        nodes.item(0)?.textContent

    private fun allText(nodes: org.w3c.dom.NodeList): String =
        buildString {
            for (index in 0 until nodes.length) append(nodes.item(index)?.textContent.orEmpty())
        }

    private fun columnIndex(reference: String): Int {
        var result = 0
        var found = false
        for (character in reference) {
            if (!character.isLetter()) break
            found = true
            result = result * 26 + (character.uppercaseChar() - 'A' + 1)
        }
        return if (found) result - 1 else -1
    }

    private fun columnName(index: Int): String {
        var number = index + 1
        val result = StringBuilder()
        while (number > 0) {
            val remainder = (number - 1) % 26
            result.append(('A'.code + remainder).toChar())
            number = (number - 1) / 26
        }
        return result.reverse().toString()
    }

    private fun quoteCsv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
