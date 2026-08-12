package com.charles.crowdtransit.app.ai.model

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where Hopper's model files live on disk. filesDir (not cacheDir) so the OS never evicts
 * a 2+ GB file mid-use — nothing else in this app writes files today, so this is the
 * first user of that directory (see GtfsDownloadManager, which streams a zip straight
 * into Room without ever touching disk).
 */
@Singleton
class AssistantModelStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val aiDir: File get() = File(context.filesDir, "ai").apply { mkdirs() }

    /** Inference cache dir LiteRT-LM is allowed to use for faster subsequent loads. */
    val cacheDir: File get() = File(context.cacheDir, "ai_cache").apply { mkdirs() }

    fun finalFile(info: AssistantModelCatalog.ModelInfo): File = File(aiDir, info.fileName)

    fun partialFile(info: AssistantModelCatalog.ModelInfo): File = File(aiDir, "${info.fileName}.part")

    fun isInstalled(info: AssistantModelCatalog.ModelInfo): Boolean =
        finalFile(info).let { it.exists() && it.length() == info.sizeBytes }

    fun installedVariant(): AssistantModelCatalog.Variant? =
        AssistantModelCatalog.all.firstOrNull { isInstalled(it) }?.variant

    fun freeStorageBytes(): Long = aiDir.usableSpace

    fun delete(info: AssistantModelCatalog.ModelInfo) {
        finalFile(info).delete()
        partialFile(info).delete()
    }

    fun deleteAll() {
        aiDir.listFiles()?.forEach { it.delete() }
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /** SHA-256 of the given file, streamed — never loads a multi-GB file into memory. */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
