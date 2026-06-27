package com.example.calmsource.feature.extensions

import com.example.calmsource.core.model.ExtensionProvider

data class RecommendedStremioAddon(
    val manifestId: String,
    val name: String,
    val manifestUrl: String,
    val description: String
)

object RecommendedStremioAddons {
    val presets = listOf(
        RecommendedStremioAddon(
            manifestId = "com.stremio.torrentio.addon",
            name = "Torrentio",
            manifestUrl = "https://torrentio.strem.fun/manifest.json",
            description = "Torrent streams from Stremio-compatible providers."
        ),
        RecommendedStremioAddon(
            manifestId = "com.aiostreams.viren070",
            name = "AIOStreams",
            manifestUrl = "https://aiostreams.elfhosted.com/stremio/manifest.json",
            description = "All-in-one Stremio stream hub with debrid-aware configuration."
        )
    )

    fun installedProvider(
        preset: RecommendedStremioAddon,
        extensions: List<ExtensionProvider>
    ): ExtensionProvider? {
        val presetBase = manifestBase(preset.manifestUrl)
        return extensions.firstOrNull { provider ->
            provider.id == preset.manifestId ||
                provider.manifest?.id == preset.manifestId ||
                manifestBase(provider.url).equals(presetBase, ignoreCase = true)
        }
    }

    private fun manifestBase(url: String): String {
        return ExtensionInstallValidator.normalizeUrl(url)
            .substringBefore("/manifest.json")
            .trimEnd('/')
    }
}
