package com.superdash.camera

/** Rotates NV21 buffers so JPEG encoding produces upright images. Pure
 *  byte-shuffling; no Android APIs, so it stays JVM-testable. */
object Nv21Rotator {
    class Rotated(
        val nv21: ByteArray,
        val width: Int,
        val height: Int,
    )

    fun rotate(
        nv21: ByteArray,
        width: Int,
        height: Int,
        degrees: Int,
    ): Rotated {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) {
            return Rotated(nv21, width, height)
        }
        val ySize = width * height
        val out = ByteArray(nv21.size)
        val (outW, outH) =
            when (normalized) {
                90, 270 -> height to width
                else -> width to height
            }
        // Luma.
        for (y in 0 until height) {
            for (x in 0 until width) {
                val (nx, ny) =
                    when (normalized) {
                        90 -> (height - 1 - y) to x
                        180 -> (width - 1 - x) to (height - 1 - y)
                        else -> y to (width - 1 - x) // 270
                    }
                out[ny * outW + nx] = nv21[y * width + x]
            }
        }
        // Chroma: one interleaved VU pair per 2x2 block; rotate block-wise.
        val chromaW = width / 2
        val chromaH = height / 2
        val outChromaW = outW / 2
        for (cy in 0 until chromaH) {
            for (cx in 0 until chromaW) {
                val (ncx, ncy) =
                    when (normalized) {
                        90 -> (chromaH - 1 - cy) to cx
                        180 -> (chromaW - 1 - cx) to (chromaH - 1 - cy)
                        else -> cy to (chromaW - 1 - cx) // 270
                    }
                val src = ySize + (cy * chromaW + cx) * 2
                val dst = ySize + (ncy * outChromaW + ncx) * 2
                out[dst] = nv21[src]
                out[dst + 1] = nv21[src + 1]
            }
        }
        return Rotated(out, outW, outH)
    }
}
