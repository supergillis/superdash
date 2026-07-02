package com.superdash.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BrowseMediaSource(
    val title: String,
    @SerialName("media_class") val mediaClass: String,
    @SerialName("media_content_id") val mediaContentId: String,
    @SerialName("media_content_type") val mediaContentType: String,
    val thumbnail: String? = null,
    @SerialName("can_play") val canPlay: Boolean = false,
    @SerialName("can_expand") val canExpand: Boolean = false,
    @SerialName("children_media_class") val childrenMediaClass: String? = null,
    val children: List<BrowseMediaSource> = emptyList(),
)

@Serializable
data class ResolvedMedia(
    val url: String,
    @SerialName("mime_type") val mimeType: String,
)
