package com.esseanalytics.android.core.database.entity

import androidx.room.Entity

// Identidad causal aprendida de la central: (usuario, handle local) -> contentId
// canónico. Se hidrata SOLO desde la central -- RemoteLibraryVideoDto.contentId
// al listar Nube, o POST /api/sync/resolve-identity para archivos locales (ver
// PlatformIdentityStore). NUNCA se deriva de un fileName ni se inventa: el
// fileName viaja a la central solo como metadato, no como fuente ni clave del
// contentId.
//
// `localKey` es el handle local estable, con prefijo para no confundir espacios
// (ver CausalKeys, única fuente de estos formatos):
//   "rlv:<RemoteLibraryVideoDto._id>"  (id que asignó la central en la cola remota)
//   "cfid:<clientFileId>"              (identidad local estable del archivo, ver
//                                       newClientFileId en core:model)
// Se guardan las dos claves apuntando al mismo contentId cuando ambas se
// conocen, para que un archivo local y su copia en Nube resuelvan igual. Las
// filas con clave vieja "file:<fileName>" que puedan existir en bases
// instaladas antes de la v7 quedan huérfanas: nadie las lee, y es a propósito
// (el nombre no identifica a nada -- dos archivos distintos pueden llamarse
// igual y un renombre no cambia la identidad).
//
// `userKey` (id del usuario logueado) aísla la identidad por sesión: si se
// desloguea y entra otro usuario, sus mapeos no se mezclan -- mismo criterio
// con el que CausalPlatformOutbox filtra al enviar.
@Entity(tableName = "content_identity", primaryKeys = ["userKey", "localKey"])
data class ContentIdentityEntity(
    val userKey: String,
    val localKey: String,
    val contentId: String,
)
