package com.superdash.esphome

import com.google.protobuf.ByteString
import org.esphome.api.CameraImageResponse

/** Max image bytes per CameraImageResponse. A whole message (proto overhead
 *  included) must fit one noise frame: NOISE_MAX_PAYLOAD (16 KiB) minus the
 *  4-byte inner header, so 15 KiB of data leaves ample headroom. aioesphomeapi
 *  reassembles chunks until `done = true`. */
internal const val CAMERA_CHUNK_BYTES: Int = 15 * 1024

internal fun cameraImageChunks(
    key: Int,
    jpeg: ByteArray,
    chunkBytes: Int = CAMERA_CHUNK_BYTES,
): List<CameraImageResponse> {
    require(chunkBytes > 0) { "chunkBytes must be positive" }
    if (jpeg.isEmpty()) {
        return emptyList()
    }
    val chunks = ArrayList<CameraImageResponse>((jpeg.size + chunkBytes - 1) / chunkBytes)
    var offset = 0
    while (offset < jpeg.size) {
        val end = minOf(offset + chunkBytes, jpeg.size)
        chunks.add(
            CameraImageResponse
                .newBuilder()
                .setKey(key)
                .setData(ByteString.copyFrom(jpeg, offset, end - offset))
                .setDone(end == jpeg.size)
                .build(),
        )
        offset = end
    }
    return chunks
}
