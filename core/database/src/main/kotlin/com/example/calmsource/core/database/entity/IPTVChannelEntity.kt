package com.example.calmsource.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

@Entity(
    tableName = "iptv_channels",
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
        Index("tvgId"),
        Index("groupTitle")
    ]
)
class IPTVChannelEntity {
    @PrimaryKey
    var id: String = ""
    var tvgId: String? = null
    var tvgName: String? = null
    var tvgLogo: String? = null
    var groupTitle: String? = null
    var name: String = ""
    var streamUrl: String = ""
    var providerId: String = ""
    var rawAttributesJson: String = ""
    var language: String? = null
    var country: String? = null
}
