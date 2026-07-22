package com.charles.crowdtransit.app.util

import com.charles.crowdtransit.app.domain.routing.LngLat
import kotlin.math.roundToLong

/** Google encoded polyline, precision 5 (docs/routing/itinerary-spec.md). */
object PolylineCodec {

    fun encode(points: List<LngLat>): String {
        val out = StringBuilder()
        var prevLat = 0L
        var prevLng = 0L
        for ((lng, lat) in points) {
            val latE5 = (lat * 1e5).roundToLong()
            val lngE5 = (lng * 1e5).roundToLong()
            encodeSigned(latE5 - prevLat, out)
            encodeSigned(lngE5 - prevLng, out)
            prevLat = latE5
            prevLng = lngE5
        }
        return out.toString()
    }

    fun decode(encoded: String): List<LngLat> {
        val points = mutableListOf<LngLat>()
        var index = 0
        var lat = 0L
        var lng = 0L

        fun decodeSigned(): Long {
            var result = 0L
            var shift = 0
            var byte: Int
            do {
                byte = encoded[index++].code - 63
                result = result or ((byte and 0x1f).toLong() shl shift)
                shift += 5
            } while (byte >= 0x20)
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }

        while (index < encoded.length) {
            lat += decodeSigned()
            lng += decodeSigned()
            points.add(LngLat(lng / 1e5, lat / 1e5))
        }
        return points
    }

    private fun encodeSigned(value: Long, out: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            out.append((((0x20L or (v and 0x1f)) + 63).toInt()).toChar())
            v = v shr 5
        }
        out.append(((v + 63).toInt()).toChar())
    }
}
