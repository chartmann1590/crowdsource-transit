package com.charles.crowdtransit.app.ai.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantModelCatalogTest {

    @Test
    fun `every variant has a well-formed https url pointing at the expected file`() {
        AssistantModelCatalog.all.forEach { info ->
            assertTrue(info.downloadUrl.startsWith("https://huggingface.co/"))
            assertTrue(info.downloadUrl.endsWith(info.fileName))
        }
    }

    @Test
    fun `every variant has a positive size and a 64-character hex sha256`() {
        AssistantModelCatalog.all.forEach { info ->
            assertTrue(info.sizeBytes > 0)
            assertEquals(64, info.sha256.length)
            assertTrue(info.sha256.all { it.isDigit() || it in 'a'..'f' })
        }
    }

    @Test
    fun `variants are distinct and byVariant round-trips`() {
        assertEquals(2, AssistantModelCatalog.all.map { it.variant }.distinct().size)
        AssistantModelCatalog.all.forEach { info ->
            assertEquals(info, AssistantModelCatalog.byVariant(info.variant))
        }
    }

    @Test
    fun `both variants share the same underlying model file`() {
        // gemma-4-E2B-it.litertlm serves both modes; text-only just never invokes the
        // vision tool. See AssistantModelCatalog's doc comment for why the file is shared.
        assertEquals(AssistantModelCatalog.TEXT_ONLY.fileName, AssistantModelCatalog.MULTIMODAL.fileName)
        assertEquals(AssistantModelCatalog.TEXT_ONLY.sizeBytes, AssistantModelCatalog.MULTIMODAL.sizeBytes)
        assertEquals(AssistantModelCatalog.TEXT_ONLY.sha256, AssistantModelCatalog.MULTIMODAL.sha256)
    }
}
