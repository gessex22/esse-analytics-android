package com.esseanalytics.android.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Prueba REAL de MIGRATION_6_7 sobre SQLite: crea una base en la v6 con el
// schema exportado (6.json), carga datos, corre la migración y valida el
// schema físico resultante contra el export de la v7 -- lo que
// Migration6To7SqlTest (JVM, comparación estática de strings) no puede hacer.
//
// PENDIENTE DE VALIDACIÓN: este entorno no puede correr Gradle (loopback
// IOException al spawnear la JVM daemon), así que ni siquiera compila acá. Se
// escribió contra las APIs de Room 2.8.4 / androidx.test 1.6.x y queda para:
//   ./gradlew :core:database:connectedDebugAndroidTest
// con un emulador/dispositivo. Requiere además el 7.json generado por
// Room/KSP (./gradlew :core:database:assembleDebug): runMigrationsAndValidate
// compara el schema migrado contra ese export y no debe existir ningún otro.
class Migration6To7InstrumentedTest {

    // Sin AutoMigrationSpec: el proyecto no usa autoMigrations (@Database no
    // los declara), así que alcanza la API simple de dos argumentos.
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        EsseAnalyticsDatabase::class.java,
    )

    @Test
    fun migracionAsignaIdentidadesUnicasYLasFilasLegacySoloSeAsocianConEvidenciaInequivoca() {
        // Base v6 con datos: dos archivos homónimos, un par ambiguo que
        // comparte remoteLibraryVideoId, y tres filas de outbox legacy.
        helper.createDatabase(TEST_DB, 6).apply {
            // Bajado de Nube: link explícito a rlv-1.
            execSQL(insertFile(fileName = "reel.mp4", remoteId = "rlv-1"))
            // Homónimo puramente local (mismo nombre, sin link).
            execSQL(insertFile(fileName = "reel.mp4", remoteId = null))
            // Par ambiguo: DOS archivos con el MISMO remoteLibraryVideoId.
            execSQL(insertFile(fileName = "amb.mp4", remoteId = "rlv-amb"))
            execSQL(insertFile(fileName = "amb.mp4", remoteId = "rlv-amb"))
            // Evidencia inequívoca: exactamente un archivo local con rlv-1.
            execSQL(insertOutbox(op = "op-1", remoteId = "rlv-1", fileName = "reel.mp4"))
            // Ambigua: dos archivos coinciden con rlv-amb.
            execSQL(insertOutbox(op = "op-2", remoteId = "rlv-amb", fileName = "amb.mp4"))
            // Sin evidencia: sin remoteLibraryVideoId; el nombre no asocia.
            execSQL(insertOutbox(op = "op-3", remoteId = null, fileName = "reel.mp4"))
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7)
        helper.closeWhenFinished(db)

        // --- files: cada fila recibió un UUID propio, distinto por fila ------
        data class FileRow(val id: Long, val clientFileId: String, val fileName: String, val remoteId: String?)

        val files = mutableListOf<FileRow>()
        db.query("SELECT `id`, `clientFileId`, `fileName`, `remoteLibraryVideoId` FROM `files` ORDER BY `id`")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    files += FileRow(
                        id = cursor.getLong(0),
                        clientFileId = cursor.getString(1),
                        fileName = cursor.getString(2),
                        remoteId = cursor.getString(3),
                    )
                }
            }

        assertEquals(4, files.size)
        assertTrue(
            "ninguna fila puede quedar con clientFileId vacío (default transitorio del ALTER)",
            files.all { it.clientFileId.isNotBlank() },
        )
        assertEquals(
            "dos archivos homónimos deben conservar identidades distintas",
            files.size,
            files.map { it.clientFileId }.toSet().size,
        )

        val bajado = files.single { it.remoteId == "rlv-1" }
        val homonimo = files.single { it.remoteId == null && it.fileName == "reel.mp4" }
        assertNotEquals(bajado.clientFileId, homonimo.clientFileId)

        // --- outbox: solo la fila con evidencia inequívoca se asoció ---------
        data class OutboxRow(val op: String, val clientFileId: String?)

        val outbox = mutableListOf<OutboxRow>()
        db.query("SELECT `operationId`, `clientFileId` FROM `platform_transition_outbox` ORDER BY `id`")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    outbox += OutboxRow(op = cursor.getString(0), clientFileId = cursor.getString(1))
                }
            }

        assertEquals(3, outbox.size)
        assertEquals(bajado.clientFileId, outbox.single { it.op == "op-1" }.clientFileId)
        // Ambiguo (2 coincidencias) y sin evidencia (sin remote id): se
        // conservan sin asociar, nunca se adivina por fileName.
        assertNull(outbox.single { it.op == "op-2" }.clientFileId)
        assertNull(outbox.single { it.op == "op-3" }.clientFileId)
    }

    // INSERT sobre el schema v6 de files (sin clientFileId, que solo existe
    // desde la v7). Solo las columnas NOT NULL necesarias + el link remoto.
    private fun insertFile(fileName: String, remoteId: String?): String =
        "INSERT INTO `files` (`fileName`, `filePath`, `status`, `contentStatus`, `platforms`, " +
            "`platformsDiscarded`, `createdAtEpochMs`, `updatedAtEpochMs`, `remoteLibraryVideoId`) " +
            "VALUES ('$fileName', '/videos/$fileName', 'PENDIENTE', 'BORRADOR', '', '', 0, 0, " +
            (remoteId?.let { "'$it'" } ?: "NULL") + ")"

    // INSERT sobre el schema v6 de platform_transition_outbox (sin
    // clientFileId). contentId queda NULL: son las filas que el flusher
    // intentaría resolver por resolve-identity.
    private fun insertOutbox(op: String, remoteId: String?, fileName: String): String =
        "INSERT INTO `platform_transition_outbox` (`userKey`, `kind`, `contentId`, `remoteLibraryVideoId`, " +
            "`fileName`, `platform`, `action`, `operationId`, `baseVersion`, `createdAtEpochMs`, `attempts`) " +
            "VALUES ('u1', 'transition', NULL, " + (remoteId?.let { "'$it'" } ?: "NULL") +
            ", '$fileName', 'youtube', 'discard', '$op', NULL, 0, 0)"

    private companion object {
        const val TEST_DB = "migration-6-7-test.db"
    }
}
