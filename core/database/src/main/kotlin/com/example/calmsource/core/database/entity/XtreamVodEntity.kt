package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "xtream_vod",
    foreignKeys = [
        ForeignKey(
            entity = IPTVProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("providerId"), 
        Index("categoryId")
    ]
)
class XtreamVodEntity {
    @PrimaryKey
    var id: String = ""
    var name: String = ""
    var streamId: String = ""
    var categoryId: String = ""
    var categoryName: String = ""
    var poster: String = ""
    var rating: Double = 0.0
    var containerExtension: String = "mp4"
    var addedTimestamp: Long = 0
    var providerId: String = ""
}
