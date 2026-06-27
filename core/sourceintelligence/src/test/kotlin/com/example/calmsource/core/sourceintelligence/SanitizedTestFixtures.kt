package com.example.calmsource.core.sourceintelligence

/**
 * Provides heavily sanitized test fixtures for source intelligence parsers.
 * No real URLs, piracy identifiers, or copyrighted hashes are used.
 */
object SanitizedTestFixtures {

    // 1. Simple 1080p English
    const val SIMPLE_1080P_ENGLISH = "Movie.Name.2023.1080p.BluRay.x264-MockGroup.mkv"

    // 2. 4K Dolby Vision Atmos
    const val HDR_4K_DV_ATMOS = "Movie.Name.2023.2160p.WEB-DL.DV.HDR10+.DDP5.1.Atmos.x265-MockGroup.mkv"

    // 3. Hindi-English dual audio
    const val HINDI_ENGLISH_DUAL = "Movie.Name.2023.1080p.WEB-DL.Dual-Audio.Hindi.English.AAC.2.0.x264-MockGroup.mp4"

    // 4. CAM/TS low-quality
    const val CAM_TS_LOW_QUALITY = "Movie.Name.2023.CAMRip.TS.XviD.MP3-MockGroup.avi"

    // 5. Huge REMUX
    const val HUGE_REMUX = "Movie.Name.2023.2160p.BluRay.REMUX.HEVC.DTS-HD.MA.TrueHD.7.1.Atmos-MockGroup.mkv"

    // 6. Malformed/weird filename
    const val MALFORMED_FILENAME = " [ www.MockSite.com ] - Movie Name (2023) [1080p] {x264} (Multi_Audio) @MockUser"

    val ALL_FIXTURES = listOf(
        SIMPLE_1080P_ENGLISH,
        HDR_4K_DV_ATMOS,
        HINDI_ENGLISH_DUAL,
        CAM_TS_LOW_QUALITY,
        HUGE_REMUX,
        MALFORMED_FILENAME
    )
}
