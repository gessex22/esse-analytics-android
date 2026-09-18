package com.esseanalytics.android.core.database.entity

import androidx.room.Entity

// Revisión causal por (usuario, contentId, plataforma) -- la baseVersion contra
// la que la central valida una transición (POST /api/sync/platform-transition).
// Se hidrata desde la central (RemoteLibraryVideoDto.platformRev al listar Nube,
// o la `version` que devuelve una transición 200 / un 409 stale) y NUNCA se
// inventa del lado del cliente: una transición sin una revisión real conocida
// no se envía (queda excluida en CausalPlatformOutbox). Al enviarse una
// transición se toma esta revisión como snapshot durable de baseVersion; el
// 200 la avanza a `version`, el 409 la corrige a la revisión autoritativa que
// trae la central.
@Entity(tableName = "platform_revision", primaryKeys = ["userKey", "contentId", "platform"])
data class PlatformRevisionEntity(
    val userKey: String,
    val contentId: String,
    val platform: String,
    val revision: Long,
)
