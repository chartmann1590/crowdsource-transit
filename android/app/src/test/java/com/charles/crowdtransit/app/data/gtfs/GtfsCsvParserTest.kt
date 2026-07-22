package com.charles.crowdtransit.app.data.gtfs

import org.junit.Assert.assertEquals
import org.junit.Test

class GtfsCsvParserTest {

    private fun parse(csv: String): List<Map<String, String>> =
        GtfsCsvParser.records(csv.reader().buffered()).toList()

    @Test
    fun `parses plain rows keyed by header`() {
        val rows = parse("stop_id,stop_name,stop_lat\n101,Main St,40.7\n102,Oak Ave,40.8\n")
        assertEquals(2, rows.size)
        assertEquals("Main St", rows[0]["stop_name"])
        assertEquals("40.8", rows[1]["stop_lat"])
    }

    @Test
    fun `handles quoted fields with commas, escaped quotes and newlines`() {
        val rows = parse(
            "stop_id,stop_name,stop_desc\n" +
                "1,\"Main St, at 5th\",\"He said \"\"hi\"\"\"\n" +
                "2,\"Two\nlines\",plain\n",
        )
        assertEquals(2, rows.size)
        assertEquals("Main St, at 5th", rows[0]["stop_name"])
        assertEquals("He said \"hi\"", rows[0]["stop_desc"])
        assertEquals("Two\nlines", rows[1]["stop_name"])
    }

    @Test
    fun `handles CRLF, missing trailing newline and short rows`() {
        val rows = parse("a,b,c\r\n1,2,3\r\n4,5")
        assertEquals(2, rows.size)
        assertEquals("3", rows[0]["c"])
        assertEquals("", rows[1]["c"])
    }

    @Test
    fun `strips UTF-8 BOM from the first header`() {
        val rows = parse("﻿stop_id,stop_name\n7,Elm\n")
        assertEquals("7", rows[0]["stop_id"])
    }
}
