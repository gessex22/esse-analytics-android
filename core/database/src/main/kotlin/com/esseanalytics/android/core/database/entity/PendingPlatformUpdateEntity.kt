package com.esseanalytics.android.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Outbox persistente para SyncApi.updateFilePlatforms -- SYNC-01 #4 parte b
// (content-automation-dashboard/docs/SYNC-01-audit-2026-08-30.md): a
// diferencia de PendingHistoryEventEntity (SYNC-02#4), esta llamada no tenía
// NINGÚN mecanismo de reintento -- VideoDetailViewModel.
// syncPlatformsToCentralIfNeeded la hacía con runCatching puro, así que un
// corte de red al tocar/descartar una plataforma dejaba el cambio guardado
// localmente (Room) pero la central nunca se enteraba, hasta que el usuario
// volviera a tocar esa misma plataforma a mano. Mirror de
// PendingPlatformUpdate.swift (iOS), misma diferencia clave respecto al
// outbox de historial: acá `fileName` es la PRIMARY KEY (no autoincrement),
// no un id random -- si el archivo se toca varias veces mientras está
// offline, no tiene sentido encolar cada intermedio (la llamada real manda
// el estado COMPLETO, no un delta), así que un insert con REPLACE pisa el
// pendiente existente con el estado más reciente en vez de apilar entradas
// obsoletas.
@Entity(tableName = "pending_platform_updates")
data class PendingPlatformUpdateEntity(
    @PrimaryKey val fileName: String,
    val remoteLibraryVideoId: String?,
    val platforms: String,            // CSV de Platform.apiValue, mismo criterio que FileEntity
    val platformsDiscarded: String,   // CSV de Platform.apiValue
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val attempts: Int = 0,
)
