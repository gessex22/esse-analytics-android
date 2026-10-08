package com.esseanalytics.android.core.network

import com.esseanalytics.android.core.network.dto.BackupFilesResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CanonicalCatalogContractTest {
    @Test fun canonicalCatalogRestoresUsingExistingDto() {
        // Capturado del controlador central real con Mongo aislado.
        val body = """
{
  "files": [
    {
      "_id": "6ac6b8fc4b5caeaee03c2def",
      "createdAt": "2026-10-07T21:26:20.937Z",
      "file_name": "catalog-fixture.mp4",
      "content_id": "catalog-stable-fixture",
      "platforms": [
        "instagram",
        "facebook"
      ],
      "platforms_discarded": [
        "youtube"
      ],
      "platform_states": [],
      "platform_rev": {
        "youtube": 3,
        "instagram": 1
      },
      "content_status": "borrador",
      "tipo_contenido": "GUION_ESTRUCTURADO",
      "scheduled_date": null,
      "duracion_segundos": 28.329,
      "resolucion": "1080x1920",
      "formato": "mp4",
      "fecha_creacion": null,
      "local_updated_at": "2026-10-07T21:26:20.937Z",
      "platforms_updated_at": null
    },
    {
      "_id": "6ac6b8fc4b5caeaee03c2df0",
      "createdAt": "2026-10-07T21:26:20.937Z",
      "file_name": "legacy-fixture.mp4",
      "content_id": null,
      "platforms": [],
      "platforms_discarded": [],
      "platform_states": [],
      "content_status": "borrador",
      "tipo_contenido": null,
      "scheduled_date": null,
      "duracion_segundos": null,
      "resolucion": null,
      "formato": null,
      "fecha_creacion": null,
      "local_updated_at": "2026-10-07T21:26:20.937Z",
      "platforms_updated_at": null
    }
  ],
  "total": 2,
  "video_folder": null
}
        """.trimIndent()
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<BackupFilesResponse>(body)
        assertEquals(2, response.total)
        val file = response.files.first { it.file_name == "catalog-fixture.mp4" }
        assertEquals(listOf("instagram", "facebook"), file.platforms)
        assertEquals(listOf("youtube"), file.platforms_discarded)
        assertEquals(28.329, file.duracion_segundos!!, 0.000001)
        assertEquals("GUION_ESTRUCTURADO", file.tipo_contenido)
        assertEquals("1080x1920", file.resolucion)
        assertNull(response.files.first { it.file_name == "legacy-fixture.mp4" }.tipo_contenido)
    }
}
