package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.MediaStreamEntity
import com.example.calmsource.core.model.StreamSource

internal fun MediaStreamEntity.toStreamSource(): StreamSource {
    val sizeHint = sizeInBytes?.takeIf { it > 0L }?.let { "${it / (1024 * 1024)}MB" }
    val combinedTitle = listOfNotNull(
        title,
        resolution,
        codec,
        quality,
        sizeHint,
    ).joinToString(" ").ifBlank { title }
    return StreamSource(
        id = id,
        name = title,
        url = url.orEmpty(),
        extensionId = source.orEmpty(),
        resolution = resolution.orEmpty(),
        videoCodec = codec,
        audioCodec = null,
        sizeBytes = sizeInBytes,
        seeds = null,
        language = language.orEmpty(),
        isSubbed = isSubbed,
        isDubbed = isDubbed,
        isDualAudio = false,
        headers = emptyMap(),
        rawTitle = combinedTitle,
    )
}
