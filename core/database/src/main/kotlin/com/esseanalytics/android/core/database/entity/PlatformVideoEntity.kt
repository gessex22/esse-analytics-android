package com.esseanalytics.android.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Mirror de local-backend/src/models/platform-video.model.ts. Índice único
// (platform, platformId) — mismo que el desktop, evita duplicar el link de una
// plataforma a dos archivos distintos.
@Entity(
    tableName = "platform_videos",
    indices = [Index(value = ["platform", "platformId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedFileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class PlatformVideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,          // Platform.apiValue
    val platformId: String,
    val platformUrl: String? = null,
    val publishedAtEpochMs: Long? = null,
    val linkedFileId: Long? = null,
    val matchStatus: String = "manual",
    val title: String? = null,
    val description: String? = null,
    val views: Int = 0,
    val likes: Int = 0,
    val comments: Int = 0,
)
