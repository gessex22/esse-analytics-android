package com.esseanalytics.android.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "pending_catalog_registrations",
    indices = [Index(value = ["userKey", "serverUrl", "clientFileId"], unique = true)],
)
data class PendingCatalogRegistrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userKey: String,
    val serverUrl: String,
    val deviceId: String,
    val clientFileId: String,
    val remoteLibraryVideoId: String?,
    val contentId: String?,
    val fileName: String,
    val sha256: String,
    val durationSeconds: Int?,
    val resolution: String?,
    val format: String?,
    val createdAt: String?,
    val thumbnailPath: String? = null,
    @ColumnInfo(defaultValue = "0") val thumbnailUploaded: Boolean = false,
    @ColumnInfo(defaultValue = "0") val attempts: Int = 0,
    @ColumnInfo(defaultValue = "0") val nextAttemptAtEpochMs: Long = 0,
    @ColumnInfo(defaultValue = "'pending'") val state: String = "pending",
    val lastError: String? = null,
)
