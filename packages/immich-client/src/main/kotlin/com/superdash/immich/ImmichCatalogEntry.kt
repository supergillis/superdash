package com.superdash.immich

import kotlinx.serialization.Serializable

/** Slim per-asset row held in the slideshow catalog. Just enough to schedule
 *  the next picture without a network call — overlay metadata is fetched
 *  lazily by the slideshow when an item is about to be displayed. */
@Serializable
data class ImmichCatalogEntry(
    val id: String,
    val type: String,
    val orientation: ImmichAssetOrientation,
)

fun ImmichAsset.toCatalogEntry(): ImmichCatalogEntry =
    ImmichCatalogEntry(
        id = id,
        type = type,
        orientation = exifInfo?.orientation ?: ImmichAssetOrientation.Unknown,
    )
