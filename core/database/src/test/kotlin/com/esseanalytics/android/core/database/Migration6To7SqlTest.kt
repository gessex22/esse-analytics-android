package com.esseanalytics.android.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

// Comparación ESTÁTICA entre el SQL de MIGRATION_6_7 y el schema exportado por
// Room/KSP. No ejecuta SQLite: la prueba real de migración (createDatabase v6 +
// runMigrationsAndValidate sobre SQLite) vive en Migration6To7InstrumentedTest.
// Lo que esto atrapa es la clase de error que más fácil pasa a producción sin
// compilar mal: migración y entidad que declaran cosas distintas (columnas de
// más o de menos, default divergente, índice con typo).
//
// Regla del 7.json: tiene que ser el export REAL de Room/KSP. Prohibido crearlo
// o editarlo a mano (un identityHash inventado se presentaría como export
// genuino y rompería MigrationTestHelper). Mientras no exista, los tests que lo
// necesitan se saltan con Assume y un mensaje explícito -- nunca pasan de
// rebote contra un schema falso. El 6.json sí está versionado y existe siempre.
class Migration6To7SqlTest {

    private val v6: String = schemaJson(6)

    @Test
    fun `la migracion agrega a files exactamente las columnas que le faltan a la v6`() {
        val v7 = assumeV7()
        val before = columnsOf(v6, "files")
        val after = columnsOf(v7, "files")
        val added = after - before

        assertEquals(setOf("clientFileId"), added)
        assertTrue(
            "falta el ALTER de files.clientFileId",
            Migration6To7Sql.ADD_CLIENT_FILE_ID_TO_FILES.contains("`files` ADD COLUMN `clientFileId`"),
        )
        // Ninguna columna de la v6 desaparece: la migración es aditiva, no
        // recrea la tabla ni pierde datos.
        assertTrue("la v7 no puede perder columnas de la v6", after.containsAll(before))
    }

    @Test
    fun `la migracion agrega al outbox exactamente las columnas que le faltan a la v6`() {
        val v7 = assumeV7()
        val before = columnsOf(v6, "platform_transition_outbox")
        val after = columnsOf(v7, "platform_transition_outbox")

        assertEquals(setOf("clientFileId"), after - before)
        assertTrue(after.containsAll(before))
        assertTrue(
            Migration6To7Sql.ADD_CLIENT_FILE_ID_TO_OUTBOX
                .contains("`platform_transition_outbox` ADD COLUMN `clientFileId`"),
        )
    }

    @Test
    fun `la migracion crea el journal publish_operations con todas sus columnas`() {
        // No necesita el 7.json: se compara el SQL de la migración contra la
        // lista de columnas del contrato (la misma que declara
        // PublishOperationEntity). Cuando exista el schema, el test de abajo
        // verifica además que Room genere exactamente esto.
        val columnas = columnsOfSql(Migration6To7Sql.CREATE_PUBLISH_OPERATIONS_TABLE)
        val esperadas = setOf(
            "id", "userKey", "sourceId", "platform", "operationId", "phase",
            "ytSessionURL", "ytBytesConfirmed", "ytTotalBytes", "ytFinalChunkSent",
            "igStage", "igContainerId", "igUploadURI", "igPublishRequested",
            "resultPlatformId", "resultURL", "lastError",
            "createdAtEpochMs", "updatedAtEpochMs",
        )

        assertEquals(esperadas, columnas)
        assertTrue(
            Migration6To7Sql.CREATE_PUBLISH_OPERATIONS_UNIQUE_INDEX.contains(
                "ON `publish_operations` (`userKey`, `sourceId`, `platform`)",
            ),
        )
    }

    @Test
    fun `el journal es tabla nueva en la v7 y su schema coincide con la migracion`() {
        val v7 = assumeV7()
        val before = tablesOf(v6)
        val after = tablesOf(v7)

        assertTrue("publish_operations tiene que ser tabla nueva en la v7", "publish_operations" !in before)
        assertTrue("publish_operations tiene que existir en la v7", "publish_operations" in after)

        // El createSql del schema usa el placeholder ${TABLE_NAME} (Room lo
        // reemplaza al crear la tabla), así que hay que normalizarlo antes de
        // comparar — un regex que busque el nombre literal nunca matchearía.
        val declaredTable = createSqlOf(v7, "publish_operations")
            .replace("\${TABLE_NAME}", "publish_operations")
        assertEquals(Migration6To7Sql.CREATE_PUBLISH_OPERATIONS_TABLE, declaredTable)

        // El createSql del índice sí lleva el nombre del índice en literal
        // (solo la tabla es placeholder).
        val declaredIndex = Regex(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_publish_operations_userKey_sourceId_platform`[^\"]*",
        )
            .find(v7)
            ?.value
        assertTrue("7.json no declara el índice único del journal", declaredIndex != null)
        assertEquals(
            declaredIndex!!.replace("\${TABLE_NAME}", "publish_operations"),
            Migration6To7Sql.CREATE_PUBLISH_OPERATIONS_UNIQUE_INDEX,
        )
    }

    @Test
    fun `el default de files clientFileId coincide entre la migracion y el schema`() {
        val v7 = assumeV7()

        // Room valida defaults al abrir la base: si el ALTER migra con DEFAULT ''
        // pero el schema de la entidad no lo declara (o al revés), una base
        // migrada y una nueva difieren y Room rechaza la migración. Ambos lados
        // declaran el mismo default a propósito (ver FileEntity y
        // Migration6To7Sql.ADD_CLIENT_FILE_ID_TO_FILES).
        assertTrue(
            "el ALTER debe declarar DEFAULT ''",
            Migration6To7Sql.ADD_CLIENT_FILE_ID_TO_FILES.contains("`clientFileId` TEXT NOT NULL DEFAULT ''"),
        )
        assertTrue(
            "el schema v7 no declara DEFAULT '' en files.clientFileId " +
                "(¿se regeneró con KSP tras el @ColumnInfo?)",
            createSqlOf(v7, "files").contains("`clientFileId` TEXT NOT NULL DEFAULT ''"),
        )
    }

    @Test
    fun `el indice unico de clientFileId coincide con el declarado en la v7`() {
        val v7 = assumeV7()
        val declared = Regex("CREATE UNIQUE INDEX IF NOT EXISTS `index_files_clientFileId`[^\"]*")
            .find(v7)
            ?.value
        assertTrue("7.json no declara el índice único de files.clientFileId", declared != null)

        // El schema usa el placeholder ${TABLE_NAME}; la migración escribe la
        // tabla real. Comparar normalizando es lo que detecta un typo.
        assertEquals(
            declared!!.replace("\${TABLE_NAME}", "files"),
            Migration6To7Sql.CREATE_FILES_CLIENT_FILE_ID_INDEX,
        )
    }

    @Test
    fun `el indice unico se crea despues de asignar los UUID`() {
        val statements = Migration6To7Sql.orderedStatements
        val alter = statements.indexOf(Migration6To7Sql.ADD_CLIENT_FILE_ID_TO_FILES)
        val index = statements.indexOf(Migration6To7Sql.CREATE_FILES_CLIENT_FILE_ID_INDEX)

        assertTrue(alter in 0 until index)
        // Crear el índice único con todas las filas todavía en '' fallaría por
        // duplicados apenas haya 2+ videos.
        assertTrue(
            "el UPDATE por fila tiene que correr entre el ALTER y el índice",
            Migration6To7Sql.ASSIGN_CLIENT_FILE_ID.contains("SET `clientFileId` = ?"),
        )
    }

    @Test
    fun `la asociacion de filas legacy exige evidencia inequivoca y nunca usa el nombre`() {
        val sql = Migration6To7Sql.LINK_LEGACY_OUTBOX_BY_REMOTE_ID

        assertFalse("la migración no puede emparejar por fileName", sql.contains("fileName"))
        assertTrue("solo se asocian filas sin clientFileId", sql.contains("`clientFileId` IS NULL"))
        assertTrue(
            "solo se asocian filas con remoteLibraryVideoId",
            sql.contains("`remoteLibraryVideoId` IS NOT NULL"),
        )
        // La guarda que hace que sea "inequívoca": si hay 0 o 2+ archivos con
        // ese remoteLibraryVideoId, la fila queda sin asociar (y bloqueada).
        assertTrue("falta la guarda de coincidencia única", sql.contains("COUNT(*)") && sql.contains(") = 1"))
    }

    @Test
    fun `ninguna sentencia de la migracion es destructiva`() {
        val forbidden = listOf("DROP TABLE", "DELETE FROM", "DROP COLUMN", "TRUNCATE")
        for (statement in Migration6To7Sql.orderedStatements) {
            for (word in forbidden) {
                assertFalse("$word en la migración v6->v7: $statement", statement.uppercase().contains(word))
            }
        }
    }

    // --- helpers ---------------------------------------------------------

    // Los tests que comparan contra la v7 se saltan (no pasan, no fallan) con
    // un mensaje que dice exactamente qué falta generar y cómo.
    private fun assumeV7(): String {
        val schema = schemaJsonOrNull(7)
        Assume.assumeTrue(
            "schemas/.../7.json aún no generado por Room/KSP -- correr " +
                "./gradlew :core:database:assembleDebug en un entorno con Gradle sano. " +
                "PROHIBIDO crearlo a mano (ver comentario de MIGRATION_6_7).",
            schema != null,
        )
        return schema!!
    }

    // Las columnas se sacan del createSql del schema en vez de parsear el JSON
    // (no hay dependencia de parser acá): alcanza para lo que se compara.
    private fun createSqlOf(schema: String, table: String): String {
        val entity = schema.substringAfter("\"tableName\": \"$table\"", "")
        require(entity.isNotEmpty()) { "no aparece la tabla $table en el schema" }
        return entity.substringAfter("\"createSql\": \"").substringBefore("\"")
    }

    private fun columnsOf(schema: String, table: String): Set<String> =
        columnsOfSql(createSqlOf(schema, table))

    private fun columnsOfSql(createSql: String): Set<String> =
        Regex("`([A-Za-z_][A-Za-z0-9_]*)` (TEXT|INTEGER|REAL|BLOB)")
            .findAll(createSql)
            .map { it.groupValues[1] }
            .toSet()

    private fun tablesOf(schema: String): Set<String> =
        Regex("\"tableName\": \"([A-Za-z_][A-Za-z0-9_]*)\"")
            .findAll(schema)
            .map { it.groupValues[1] }
            .toSet()

    // El working dir de los tests JVM depende de cómo los invoque Gradle/el
    // IDE, así que se busca hacia arriba en vez de asumir uno.
    private fun schemaJson(version: Int): String =
        schemaJsonOrNull(version) ?: throw AssertionError(
            "no se encontró el schema $version desde ${File("").absolutePath}",
        )

    private fun schemaJsonOrNull(version: Int): String? {
        val relative = "schemas/com.esseanalytics.android.core.database.EsseAnalyticsDatabase/$version.json"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, relative), File(dir, "core/database/$relative"))) {
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir.parentFile
        }
        return null
    }
}
