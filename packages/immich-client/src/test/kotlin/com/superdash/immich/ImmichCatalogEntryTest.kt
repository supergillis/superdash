package com.superdash.immich

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmichCatalogEntryTest {
    @Test
    fun `projects ImmichAsset to slim CatalogEntry`() {
        val asset =
            ImmichAsset(
                id = "id-1",
                type = "IMAGE",
                originalFileName = "x.jpg",
                fileCreatedAt = Instant.fromEpochMilliseconds(0),
                exifInfo = ImmichExif(exifImageWidth = 1200, exifImageHeight = 800),
            )
        val entry = asset.toCatalogEntry()
        assertEquals(ImmichCatalogEntry("id-1", "IMAGE", ImmichAssetOrientation.Landscape), entry)
    }

    @Test
    fun `unknown orientation when exif is missing`() {
        val asset =
            ImmichAsset(
                id = "id-1",
                type = "IMAGE",
                originalFileName = "x.jpg",
                fileCreatedAt = Instant.fromEpochMilliseconds(0),
                exifInfo = null,
            )
        assertEquals(ImmichAssetOrientation.Unknown, asset.toCatalogEntry().orientation)
    }

    @Test
    fun `portrait orientation when height exceeds width`() {
        val asset =
            ImmichAsset(
                id = "id-1",
                type = "IMAGE",
                originalFileName = "x.jpg",
                fileCreatedAt = Instant.fromEpochMilliseconds(0),
                exifInfo = ImmichExif(exifImageWidth = 800, exifImageHeight = 1200),
            )
        assertEquals(ImmichAssetOrientation.Portrait, asset.toCatalogEntry().orientation)
    }

    @Test
    fun `unknown orientation when dimensions are equal`() {
        val asset =
            ImmichAsset(
                id = "id-1",
                type = "IMAGE",
                originalFileName = "x.jpg",
                fileCreatedAt = Instant.fromEpochMilliseconds(0),
                exifInfo = ImmichExif(exifImageWidth = 1000, exifImageHeight = 1000),
            )
        assertEquals(ImmichAssetOrientation.Unknown, asset.toCatalogEntry().orientation)
    }

    @Test
    fun `portrait orientation for landscape-shaped image with quarter-turn rotation`() {
        // width=4032, height=3024 is landscape-shaped in storage, but exifOrientation="6"
        // (90° CW) means the display dimensions are swapped → Portrait.
        val asset =
            ImmichAsset(
                id = "id-1",
                type = "IMAGE",
                originalFileName = "x.jpg",
                fileCreatedAt = Instant.fromEpochMilliseconds(0),
                exifInfo = ImmichExif(exifImageWidth = 4032, exifImageHeight = 3024, exifOrientation = "6"),
            )
        assertEquals(ImmichAssetOrientation.Portrait, asset.toCatalogEntry().orientation)
    }
}
