package com.example.calmsource.core.model

fun List<StremioVideo>.displayableEpisodes(): List<StremioVideo> {
    return asSequence()
        .filter { video ->
            val season = video.season
            season != null &&
                season >= 0 &&
                (
                    !video.id.isNullOrBlank() ||
                        !video.title.isNullOrBlank() ||
                        (video.episode ?: 0) > 0
                    )
        }
        .distinctBy { video ->
            video.id?.takeIf { it.isNotBlank() }
                ?: "${video.season}:${video.episode}:${video.title.orEmpty().trim().lowercase()}"
        }
        .sortedWith(
            compareBy<StremioVideo> { it.season ?: Int.MAX_VALUE }
                .thenBy { it.episode ?: Int.MAX_VALUE }
                .thenBy { it.title.orEmpty() }
        )
        .toList()
}

fun List<StremioVideo>.displayableSeasons(): List<Int> {
    return displayableEpisodes()
        .mapNotNull { it.season }
        .distinct()
        .sortedWith(compareBy<Int> { if (it == 0) Int.MAX_VALUE else it })
}

fun seasonDisplayLabel(season: Int): String {
    return if (season == 0) "Specials" else "Season $season"
}
