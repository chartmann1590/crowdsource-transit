package com.charles.crowdtransit.app.ai.model

/**
 * The Gemma 4 E2B model Hopper downloads. Size and SHA-256 digest are pinned from the
 * Hugging Face LFS metadata for litert-community/gemma-4-E2B-it-litert-lm (fetched
 * 2026-08-06 via the repo's /api/models/.../tree/main endpoint) — never trust a
 * Content-Length header alone for a multi-GB file; a truncated or corrupted download
 * that "mostly works" is worse than one that fails loudly.
 *
 * There is only one file: gemma-4-E2B-it.litertlm. It was originally split into a
 * "TextOnly" variant pointing at gemma-4-E2B-it-web.litertlm and a "Multimodal" variant
 * pointing at this file, on the assumption that "-web" meant "smaller, text-only build
 * for Android". Confirmed wrong on a real device (2026-08-07): the "-web" file is built
 * for a browser/WASM runtime and throws "TF_LITE_PREFILL_DECODE not found" on Android's
 * native Engine, regardless of backend. This file works for both text-only and
 * multimodal use — text-only mode is simply never invoking the vision tool (see
 * LiteRtAssistantEngine, which now passes null for visionBackend/audioBackend so the
 * engine never even validates that submodel unless an image is actually sent).
 */
object AssistantModelCatalog {

    enum class Variant {
        /** Chat only — camera scanning hidden in the UI. Recommended for Basic/Standard tiers. */
        TextOnly,

        /** Chat plus "scan a stop sign" with the camera. Recommended for Advanced tier. */
        Multimodal,
    }

    data class ModelInfo(
        val variant: Variant,
        val fileName: String,
        val downloadUrl: String,
        val sizeBytes: Long,
        val sha256: String,
        val label: String,
        val description: String,
    )

    private const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    private const val DOWNLOAD_URL =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/$FILE_NAME"
    private const val SIZE_BYTES = 2_588_147_712L
    private const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"

    val TEXT_ONLY = ModelInfo(
        variant = Variant.TextOnly,
        fileName = FILE_NAME,
        downloadUrl = DOWNLOAD_URL,
        sizeBytes = SIZE_BYTES,
        sha256 = SHA256,
        label = "Hopper (Text)",
        description = "Chat about routes and trips.",
    )

    val MULTIMODAL = ModelInfo(
        variant = Variant.Multimodal,
        fileName = FILE_NAME,
        downloadUrl = DOWNLOAD_URL,
        sizeBytes = SIZE_BYTES,
        sha256 = SHA256,
        label = "Hopper (Text + Camera)",
        description = "Chat about routes and trips, plus scan a stop or station sign with your camera.",
    )

    val all: List<ModelInfo> = listOf(TEXT_ONLY, MULTIMODAL)

    fun byVariant(variant: Variant): ModelInfo = when (variant) {
        Variant.TextOnly -> TEXT_ONLY
        Variant.Multimodal -> MULTIMODAL
    }
}
