package com.esseanalytics.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Outbox persistente para SyncApi.recordPublish -- hallazgo SYNC-02#4 de la
// auditoría de sincronización 2026-08-30 (ver
// content-automation-dashboard/docs/SYNC-01-audit-2026-08-30.md). Antes, si
// los 3 reintentos EN MEMORIA de UploadWorker.reportPublish/RemoteUploadWorker
// fallaban (corte de red justo al publicar), el evento se perdía para
// siempre sin ningún aviso -- la subida real ya había quedado bien
// registrada en Room (platform_videos/files, este dispositivo), pero la
// central nunca se enteraba, así que Estadísticas/Historial/Calendario en
// cualquier OTRO dispositivo (o la web) jamás mostraban ese evento. Mismo
// patrón que ya usa local-backend (`history_outbox`, SQLite) para su propio
// camino a la central -- esto es el equivalente para el camino DIRECTO
// Android→central (modo remoto, sin PC). Mirror de PendingHistoryEvent.swift
// (iOS, mismos campos).
@Entity(tableName = "pending_history_events")
data class PendingHistoryEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,
    val platformId: String,
    val platformUrl: String?,
    val fileName: String?,
    val remoteLibraryVideoId: String?,
    val title: String?,
    val publishedAt: String?,
    val operationId: String?,
    val deviceId: String?,
    val deviceName: String?,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)
