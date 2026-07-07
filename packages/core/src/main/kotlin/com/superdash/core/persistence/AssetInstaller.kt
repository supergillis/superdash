package com.superdash.core.persistence

import java.io.File
import java.io.InputStream

/**
 * Copies a bundled asset to internal storage exactly once, using
 * [AtomicFileWriter] so callers never see a half-written file.
 *
 * Callers supply [openAsset] (typically `context.assets::open`). The asset's
 * `available()` byte length is used as the freshness check: if [targetFile]
 * already has that exact length, the install is a no-op. Otherwise the asset
 * is streamed to a temp file and atomically moved into place. After the move
 * the final file length is verified to equal the asset's expected length —
 * a truncated stream therefore fails the install instead of silently
 * producing a corrupt model file.
 */
class AssetInstaller(
    private val openAsset: (String) -> InputStream,
) {
    /**
     * Ensures [targetFile] mirrors the asset at [assetPath]. Returns
     * [targetFile] (created if needed) or `null` if the asset cannot be opened
     * or the copied length does not match the asset's expected length.
     */
    fun installIfMissing(
        assetPath: String,
        targetFile: File,
    ): File? {
        val expectedLength =
            runCatching { openAsset(assetPath).use { input -> input.available().toLong() } }
                .getOrElse {
                    return null
                }
        if (targetFile.length() == expectedLength && targetFile.exists()) {
            return targetFile
        }
        targetFile.parentFile?.mkdirs()
        return runCatching {
            openAsset(assetPath).use { input ->
                AtomicFileWriter.writeFromStream(targetFile, input)
            }
            check(targetFile.length() == expectedLength) {
                "AssetInstaller: copied length mismatch for $assetPath " +
                    "(expected $expectedLength, got ${targetFile.length()})"
            }
            targetFile
        }.getOrNull()
    }
}
