package com.esseanalytics.android.core.database

// Construcción del `localKey` de content_identity -- handle local estable con
// prefijo por espacio para no confundir un _id de la cola remota con una
// identidad local. Compartido entre el repositorio de enqueue (core:database) y
// la hidratación/resolución de identidad (PlatformIdentityStore, core:network),
// para que las dos escriban/lean exactamente la misma clave.
//
// NO existe (ni puede volver a existir) una clave por fileName: el nombre no
// identifica nada -- dos videos distintos pueden llamarse igual y un renombre
// no cambia el contenido. La identidad local es clientFileId (UUID estable por
// archivo, ver newClientFileId en core:model); el remoteLibraryVideoId es el
// otro handle válido, porque lo asigna la central.
object CausalKeys {
    fun forRemote(remoteLibraryVideoId: String): String = "rlv:$remoteLibraryVideoId"
    fun forClientFile(clientFileId: String): String = "cfid:$clientFileId"
}
