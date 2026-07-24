package com.charles.crowdtransit.app.data.gtfs

import java.io.BufferedReader

/**
 * Streaming RFC-4180 CSV reader for GTFS text files (quoted fields, embedded commas,
 * doubled quotes, embedded newlines inside quotes; mirrors scripts/lib/gtfs-parser.js
 * semantics). Pull-based: [records] yields each data row as a header-keyed map, so
 * callers can interleave suspend DB inserts between rows without buffering the file.
 */
object GtfsCsvParser {

    fun records(reader: BufferedReader): Sequence<Map<String, String>> = sequence {
        var header: List<String>? = null
        for (fields in rawRecords(reader)) {
            val h = header
            if (h == null) {
                // Strip a UTF-8 BOM from the first CSV field (GTFS files often start with one).
                header = fields.map { it.trim().removePrefix("\uFEFF") }
            } else {
                if (fields.size == 1 && fields[0].isBlank()) continue
                val record = HashMap<String, String>(h.size)
                for (i in h.indices) record[h[i]] = fields.getOrElse(i) { "" }
                yield(record)
            }
        }
    }

    private fun rawRecords(reader: BufferedReader): Sequence<List<String>> = sequence {
        val field = StringBuilder()
        var fields = mutableListOf<String>()
        var inQuotes = false
        var sawAny = false

        while (true) {
            val c = reader.read()
            if (c == -1) {
                if (sawAny || field.isNotEmpty() || fields.isNotEmpty()) {
                    fields.add(field.toString())
                    yield(fields)
                }
                return@sequence
            }
            val ch = c.toChar()
            if (inQuotes) {
                if (ch == '"') {
                    reader.mark(1)
                    val next = reader.read()
                    if (next == '"'.code) {
                        field.append('"')
                    } else {
                        inQuotes = false
                        reader.reset()
                    }
                } else {
                    field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> {
                        inQuotes = true
                        sawAny = true
                    }

                    ',' -> {
                        fields.add(field.toString())
                        field.setLength(0)
                        sawAny = true
                    }

                    '\r' -> Unit // swallow; \n ends the record
                    '\n' -> {
                        fields.add(field.toString())
                        field.setLength(0)
                        yield(fields)
                        fields = mutableListOf()
                        sawAny = false
                    }

                    else -> {
                        field.append(ch)
                        sawAny = true
                    }
                }
            }
        }
    }
}
