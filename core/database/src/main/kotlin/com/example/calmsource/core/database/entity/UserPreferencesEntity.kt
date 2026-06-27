package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_preferences")
class UserPreferencesEntity {
    @PrimaryKey
    var id: Int = 1
    var primaryLanguage: String = "Hindi"
    var secondaryLanguage: String = "English"
    var subtitleLanguage: String = "English"
    var sourcePriority: String = "Auto-pick best source"
    var preferCachedDebrid: Boolean = true
    var preferIptvExactMatch: Boolean = true
    var preferFhdOrBetter: Boolean = true
    var hideLowQuality: Boolean = true
    var hideDuplicates: Boolean = true
    var preferOriginalAudio: Boolean = false
    var preferDubbedAudio: Boolean = false
    var preferDualAudio: Boolean = true
    var preferHighestQuality: Boolean = true
    var preferLowerDataUsage: Boolean = false
    var askBeforeChoosingSource: Boolean = false
    var askBeforeDebrid: Boolean = false
    var hideNonCached: Boolean = false
    var showDebridStatusInStreamPicker: Boolean = true
    var primaryDebridProvider: String = ""
    var allowCleartextUserSources: Boolean = false
    var separateIptvCategoriesByProvider: Boolean = false
}
