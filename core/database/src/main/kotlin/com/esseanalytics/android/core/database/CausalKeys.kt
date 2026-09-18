package com.esseanalytics.android.core.database

// Construcción del `localKey` de content_identity -- handle local estable con
// prefijo por espacio para no confundir un _id de la cola remota con un
// fileName. Compartido entre el repositorio de enqueue (core:database) y la
// hidratación/resolución de identidad (PlatformIdentityStore, core:network),
// para que las dos escriban/lean exactamente la misma clave.
object CausalKeys {
    fun forRemote(remoteLibraryVideoId: String): String = "rlv:$remoteLibraryVideoId"
    fun forFile(fileName: String): String = "file:$fileName"
}
