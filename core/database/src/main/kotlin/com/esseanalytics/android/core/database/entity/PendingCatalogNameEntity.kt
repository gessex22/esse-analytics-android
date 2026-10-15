package com.esseanalytics.android.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "pending_catalog_names", indices = [Index(value = ["userKey", "serverUrl", "clientFileId"], unique = true)])
data class PendingCatalogNameEntity(
    @PrimaryKey val operationId: String,
    val userKey: String,
    val serverUrl: String,
    val clientFileId: String,
    val contentId: String? = null,
    // displayName/baseRevision are immutable once a request is marked sending.
    val displayName: String,
    @ColumnInfo(defaultValue = "''") val desiredName: String = displayName,
    val baseRevision: Int,
    val attempts: Int = 0,
    val nextAttemptAtEpochMs: Long = 0,
    val state: String = "pending",
    val currentName: String? = null,
    val currentRevision: Int? = null,
    val lastError: String? = null,
)
