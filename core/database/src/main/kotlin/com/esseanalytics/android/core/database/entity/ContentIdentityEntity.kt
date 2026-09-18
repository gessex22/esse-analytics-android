package com.esseanalytics.android.core.database.entity

import androidx.room.Entity

// Identidad causal aprendida de la central: (usuario, handle local) -> contentId
// canónico. Se hidrata SOLO desde la central -- RemoteLibraryVideoDto.contentId
// al listar Nube, o POST /api/sync/resolve-identity para archivos locales (ver
// PlatformIdentityStore). NUNCA se deriva de un fileName ni se inventa: el
// fileName solo sirve como CLAVE de búsqueda del mapeo que devuelve la central,
// no como fuente del contentId.
//
// `localKey` es el handle local estable, con prefijo para no confundir espacios:
//   "rlv:<RemoteLibraryVideoDto._id>"  (archivo bajado de la cola remota)
//   "file:<fileName>"                  (archivo puramente local)
// Se guardan las dos claves apuntando al mismo contentId cuando ambas se
// conocen, para que un archivo local y su copia en Nube resuelvan igual.
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
