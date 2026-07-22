package com.charles.crowdtransit.app.data.trip

import com.charles.crowdtransit.model.TRIP_PLAN_VERSION
import com.charles.crowdtransit.model.TripPlan
import com.squareup.moshi.Moshi
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encodes/decodes [TripPlan] to the wire format from docs/routing/itinerary-spec.md:
 * base64url_no_padding(deflate_raw(minified_json_utf8)). Used for share links and
 * RTDB saved-trip blobs. java.util.Base64 is available on minSdk 24 via core library
 * desugaring (and on the JVM in unit tests).
 */
class UnsupportedTripPlanVersion(val version: Int?) :
    Exception("Unsupported trip plan version: $version")

@Singleton
class TripPlanCodec @Inject constructor() {

    private val moshi: Moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(TripPlan::class.java)

    fun toJson(plan: TripPlan): String = adapter.toJson(plan)

    fun fromJson(json: String): TripPlan {
        val plan = adapter.fromJson(json) ?: throw UnsupportedTripPlanVersion(null)
        if (plan.v < 1 || plan.v > TRIP_PLAN_VERSION) throw UnsupportedTripPlanVersion(plan.v)
        return plan
    }

    fun encode(plan: TripPlan): String {
        val json = toJson(plan).toByteArray(Charsets.UTF_8)
        val deflated = deflateRaw(json)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(deflated)
    }

    fun decode(blob: String): TripPlan {
        val deflated = Base64.getUrlDecoder().decode(blob.trim())
        val json = inflateRaw(deflated).toString(Charsets.UTF_8)
        return fromJson(json)
    }

    private fun deflateRaw(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, /* nowrap = */ true)
        try {
            deflater.setInput(input)
            deflater.finish()
            val out = java.io.ByteArrayOutputStream(input.size / 2 + 16)
            val buffer = ByteArray(8192)
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer))
            }
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflateRaw(input: ByteArray): ByteArray {
        val inflater = Inflater(/* nowrap = */ true)
        try {
            inflater.setInput(input)
            val out = java.io.ByteArrayOutputStream(input.size * 4 + 16)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && inflater.needsInput()) break // truncated input
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
