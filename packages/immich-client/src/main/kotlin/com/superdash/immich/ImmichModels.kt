package com.superdash.immich

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ImmichAsset(
    val id: String,
    val type: String,
    val originalFileName: String,
    val fileCreatedAt: Instant,
    val dateTimeOriginal: Instant? = null,
    val isFavorite: Boolean = false,
    val exifInfo: ImmichExif? = null,
)

@Serializable
data class ImmichExif(
    val city: String? = null,
    val state: String? = null,
    val country: String? = null,
    val make: String? = null,
    val model: String? = null,
    val exposureTime: String? = null,
    val fNumber: Double? = null,
    val iso: Int? = null,
    val exifImageWidth: Int? = null,
    val exifImageHeight: Int? = null,
    @SerialName("orientation")
    val exifOrientation: String? = null,
)

val ImmichExif.orientation: ImmichAssetOrientation
    get() =
        when {
            exifImageWidth == null || exifImageHeight == null -> ImmichAssetOrientation.Unknown
            displayWidth > displayHeight -> ImmichAssetOrientation.Landscape
            displayHeight > displayWidth -> ImmichAssetOrientation.Portrait
            else -> ImmichAssetOrientation.Unknown
        }

private val ImmichExif.displayWidth: Int
    get() =
        if (rotatesQuarterTurn) {
            exifImageHeight ?: 0
        } else {
            exifImageWidth ?: 0
        }

private val ImmichExif.displayHeight: Int
    get() =
        if (rotatesQuarterTurn) {
            exifImageWidth ?: 0
        } else {
            exifImageHeight ?: 0
        }

private val ImmichExif.rotatesQuarterTurn: Boolean
    get() {
        val normalized = exifOrientation?.trim()?.lowercase() ?: return false
        return normalized == "6" ||
            normalized == "8" ||
            normalized.contains("90") ||
            normalized.contains("270")
    }

enum class ImmichAssetOrientation {
    Landscape,
    Portrait,
    Unknown,
}

val ImmichExif.formattedLocation: String?
    get() =
        listOfNotNull(city, state, country)
            .joinToString(", ")
            .takeIf { it.isNotBlank() }

@Serializable
data class ImmichAlbum(
    val id: String,
    val albumName: String,
)

@Serializable
data class ImmichAlbumDetails(
    val id: String,
    val albumName: String,
    val assets: List<ImmichAsset> = emptyList(),
)

@Serializable
data class ImmichPingResponse(
    val res: String,
)

@Serializable
data class ImmichSearchAssetsBucket(
    val total: Int = 0,
    val count: Int = 0,
    val items: List<ImmichAsset> = emptyList(),
    val nextPage: String? = null,
)

@Serializable
data class ImmichSearchPage(
    val assets: ImmichSearchAssetsBucket = ImmichSearchAssetsBucket(),
)
