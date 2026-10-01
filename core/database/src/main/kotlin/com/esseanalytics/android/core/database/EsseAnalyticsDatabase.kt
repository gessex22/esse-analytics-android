package com.esseanalytics.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.dao.PendingHistoryEventDao
import com.esseanalytics.android.core.database.dao.PendingPlatformUpdateDao
import com.esseanalytics.android.core.database.dao.PlatformVideoDao
import com.esseanalytics.android.core.database.dao.PublishOperationDao
import com.esseanalytics.android.core.database.entity.ContentIdentityEntity
import com.esseanalytics.android.core.database.entity.FileEntity
import com.esseanalytics.android.core.database.entity.PendingHistoryEventEntity
import com.esseanalytics.android.core.database.entity.PendingPlatformUpdateEntity
import com.esseanalytics.android.core.database.entity.PlatformRevisionEntity
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity
import com.esseanalytics.android.core.database.entity.PlatformVideoEntity
import com.esseanalytics.android.core.database.entity.PublishOperationEntity
import java.util.UUID

// La base arrancó en v1 con la app nueva (sin usuarios en producción) y desde
// entonces cada versión tiene su Migration real (v2->...->v7, ver abajo y
// DatabaseModule) — a diferencia de un reinstall de escritorio, acá los
// usuarios sí tienen datos locales entre actualizaciones de la app. El único
// fallback destructivo permitido es el salto desde v1.
@Database(
    entities = [
        FileEntity::class,
        PlatformVideoEntity::class,
        PendingHistoryEventEntity::class,
        PendingPlatformUpdateEntity::class,
        ContentIdentityEntity::class,
        PlatformRevisionEntity::class,
        PlatformTransitionOutboxEntity::class,
        PublishOperationEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class EsseAnalyticsDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun platformVideoDao(): PlatformVideoDao
    abstract fun pendingHistoryEventDao(): PendingHistoryEventDao
    abstract fun pendingPlatformUpdateDao(): PendingPlatformUpdateDao
    abstract fun causalPlatformDao(): CausalPlatformDao
    abstract fun publishOperationDao(): PublishOperationDao
}

// v2 -> v3: agrega remoteLibraryVideoId (ver FileEntity, ImportUseCase.importFromRemoteLibrary).
// Real, no destructiva -- a diferencia de fallbackToDestructiveMigration()
// (ver DatabaseModule), esta SÍ preserva el catálogo local ya importado.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE files ADD COLUMN remoteLibraryVideoId TEXT")
    }
}

// v3 -> v4: tabla nueva pending_history_events (ver PendingHistoryEventEntity,
// hallazgo SYNC-02#4). No toca ninguna tabla existente -- solo CREATE TABLE.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_history_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `platform` TEXT NOT NULL,
                `platformId` TEXT NOT NULL,
                `platformUrl` TEXT,
                `fileName` TEXT,
                `remoteLibraryVideoId` TEXT,
                `title` TEXT,
                `publishedAt` TEXT,
                `operationId` TEXT,
                `deviceId` TEXT,
                `deviceName` TEXT,
                `createdAtEpochMs` INTEGER NOT NULL,
                `attempts` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

// v4 -> v5: tabla nueva pending_platform_updates (ver
// PendingPlatformUpdateEntity, SYNC-01 #4 parte b). No toca ninguna tabla
// existente -- solo CREATE TABLE, misma forma que MIGRATION_3_4.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_platform_updates` (
                `fileName` TEXT NOT NULL PRIMARY KEY,
                `remoteLibraryVideoId` TEXT,
                `platforms` TEXT NOT NULL,
                `platformsDiscarded` TEXT NOT NULL,
                `createdAtEpochMs` INTEGER NOT NULL,
                `attempts` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}

// v5 -> v6: infraestructura causal (content_identity, platform_revision,
// platform_transition_outbox). Estrictamente aditiva -- 3 CREATE TABLE + 1
// CREATE INDEX, NO toca ni migra ninguna tabla existente (files,
// platform_videos, pending_*), misma forma no destructiva que MIGRATION_3_4/
// _4_5. El SQL replica exactamente el schema que Room genera para las 3
// entidades nuevas (ver ContentIdentityEntity / PlatformRevisionEntity /
// PlatformTransitionOutboxEntity); si se les cambia una columna, cambiar acá
// también o la validación de identidad de Room falla al abrir la base.
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `content_identity` (
                `userKey` TEXT NOT NULL,
                `localKey` TEXT NOT NULL,
                `contentId` TEXT NOT NULL,
                PRIMARY KEY(`userKey`, `localKey`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `platform_revision` (
                `userKey` TEXT NOT NULL,
                `contentId` TEXT NOT NULL,
                `platform` TEXT NOT NULL,
                `revision` INTEGER NOT NULL,
                PRIMARY KEY(`userKey`, `contentId`, `platform`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `platform_transition_outbox` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `userKey` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `contentId` TEXT,
                `remoteLibraryVideoId` TEXT,
                `fileName` TEXT,
                `platform` TEXT NOT NULL,
                `action` TEXT,
                `operationId` TEXT NOT NULL,
                `baseVersion` INTEGER,
                `platformId` TEXT,
                `platformUrl` TEXT,
                `title` TEXT,
                `publishedAt` TEXT,
                `createdAtEpochMs` INTEGER NOT NULL,
                `attempts` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS " +
                "`index_platform_transition_outbox_userKey_contentId_platform` " +
                "ON `platform_transition_outbox` (`userKey`, `contentId`, `platform`)",
        )
    }
}

// SQL de MIGRATION_6_7, aparte del objeto Migration para poder verificarlo en
// tests: comparación estática contra el schema exportado (Migration6To7SqlTest,
// JVM) y ejecución real sobre SQLite (Migration6To7InstrumentedTest,
// MigrationTestHelper).
object Migration6To7Sql {
    // NOT NULL DEFAULT '' porque SQLite exige un default al agregar una columna
    // NOT NULL a una tabla con filas. El '' es SOLO un estado transitorio dentro
    // de esta migración: ASSIGN_CLIENT_FILE_ID lo reemplaza fila por fila antes
    // de crear el índice único. La entidad declara EXACTAMENTE este mismo
    // default (@ColumnInfo(defaultValue = "''") en FileEntity) a propósito: así
    // el schema físico de una base migrada es idéntico al de una base nueva, y
    // la validación de Room (que sí compara defaults) no rechaza la migración.
    const val ADD_CLIENT_FILE_ID_TO_FILES =
        "ALTER TABLE `files` ADD COLUMN `clientFileId` TEXT NOT NULL DEFAULT ''"

    const val SELECT_FILES_WITHOUT_IDENTITY = "SELECT `id` FROM `files` WHERE `clientFileId` = ''"

    // Un UUID por fila, generado en Kotlin (no con randomblob() en SQL): así la
    // fuente de los ids es la MISMA que la de un archivo nuevo
    // (newClientFileId), y no hay forma de que dos filas compartan valor.
    const val ASSIGN_CLIENT_FILE_ID = "UPDATE `files` SET `clientFileId` = ? WHERE `id` = ?"

    const val CREATE_FILES_CLIENT_FILE_ID_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_files_clientFileId` ON `files` (`clientFileId`)"

    const val ADD_CLIENT_FILE_ID_TO_OUTBOX =
        "ALTER TABLE `platform_transition_outbox` ADD COLUMN `clientFileId` TEXT"

    // Única asociación automática permitida para las filas legacy: cuando la
    // fila trae remoteLibraryVideoId (id que asignó la CENTRAL, guardado al
    // importar desde Nube) y existe EXACTAMENTE UN archivo local con ese id.
    // Con 0 o 2+ coincidencias no se toca nada: la fila se conserva con
    // clientFileId NULL y queda excluida del envío (getUnresolved), en vez de
    // adivinar. Nunca se empareja por fileName.
    const val LINK_LEGACY_OUTBOX_BY_REMOTE_ID = """
        UPDATE `platform_transition_outbox`
        SET `clientFileId` = (
            SELECT `f`.`clientFileId` FROM `files` AS `f`
            WHERE `f`.`remoteLibraryVideoId` = `platform_transition_outbox`.`remoteLibraryVideoId`
        )
        WHERE `clientFileId` IS NULL
          AND `remoteLibraryVideoId` IS NOT NULL
          AND (
            SELECT COUNT(*) FROM `files` AS `f`
            WHERE `f`.`remoteLibraryVideoId` = `platform_transition_outbox`.`remoteLibraryVideoId`
          ) = 1
    """

    // --- Journal anti-duplicados de publicación (publish_operations) --------
    // Tabla NUEVA en la v7 (misma versión que la identidad causal — la v7 aún
    // no se publicó, así que el journal entra en la misma migración en vez de
    // abrir una v8). El SQL replica exactamente el schema que Room genera para
    // PublishOperationEntity; si se le cambia una columna, cambiar acá también
    // o la validación de identidad de Room falla al abrir la base. El índice
    // ÚNICO es el contrato "un registro durable por (userKey, sourceId,
    // platform)": una colisión revienta en el insert en vez de pisar. Las dos
    // columnas de evidencia de confirmación humana (lastConfirmation*) son
    // NULLables a propósito: null = la fila nunca pasó por la confirmación
    // «Revisé la plataforma y el video no se publicó» (ver
    // PublishOperationStore.confirmNotPublished y el diseño
    // docs/ambiguous-publish-recovery-design-2026-09-22.md).
    const val CREATE_PUBLISH_OPERATIONS_TABLE =
        "CREATE TABLE IF NOT EXISTS `publish_operations` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`userKey` TEXT NOT NULL, " +
            "`sourceId` TEXT NOT NULL, " +
            "`platform` TEXT NOT NULL, " +
            "`operationId` TEXT NOT NULL, " +
            "`phase` TEXT NOT NULL, " +
            "`ytSessionURL` TEXT, " +
            "`ytBytesConfirmed` INTEGER NOT NULL, " +
            "`ytTotalBytes` INTEGER, " +
            "`ytFinalChunkSent` INTEGER NOT NULL, " +
            "`igStage` TEXT, " +
            "`igContainerId` TEXT, " +
            "`igUploadURI` TEXT, " +
            "`igPublishRequested` INTEGER NOT NULL, " +
            "`resultPlatformId` TEXT, " +
            "`resultURL` TEXT, " +
            "`lastError` TEXT, " +
            "`lastConfirmationAction` TEXT, " +
            "`lastConfirmationAtEpochMs` INTEGER, " +
            "`createdAtEpochMs` INTEGER NOT NULL, " +
            "`updatedAtEpochMs` INTEGER NOT NULL" +
            ")"

    const val CREATE_PUBLISH_OPERATIONS_UNIQUE_INDEX =
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_publish_operations_userKey_sourceId_platform` " +
            "ON `publish_operations` (`userKey`, `sourceId`, `platform`)"

    // Sentencias en el orden exacto en que corren (las de UUID por fila quedan
    // fuera: son parametrizadas y su cantidad depende de los datos).
    val orderedStatements: List<String> = listOf(
        CREATE_PUBLISH_OPERATIONS_TABLE,
        CREATE_PUBLISH_OPERATIONS_UNIQUE_INDEX,
        ADD_CLIENT_FILE_ID_TO_FILES,
        CREATE_FILES_CLIENT_FILE_ID_INDEX,
        ADD_CLIENT_FILE_ID_TO_OUTBOX,
        LINK_LEGACY_OUTBOX_BY_REMOTE_ID,
    )
}

// v6 -> v7: (a) identidad local estable por archivo (files.clientFileId) + su
// propagación al outbox causal — corrige el contrato de
// POST /api/sync/resolve-identity, que identifica por (deviceId, clientFileId)
// y NUNCA por fileName -- ver CausalKeys, PlatformIdentityStore y
// ResolveIdentityRequest. (b) Journal durable anti-duplicados de publicación
// (publish_operations, con sus columnas de evidencia de confirmación humana
// lastConfirmation*) — ver PublishOperationEntity/PublishOperationStore y el
// diseño docs/ambiguous-publish-recovery-design-2026-09-22.md.
//
// No destructiva: ninguna tabla se borra ni se recrea. Cada archivo existente
// recibe un UUID propio y persistente, así que dos filas que se llamen igual
// quedan como dos identidades distintas (el índice único lo garantiza de ahí en
// más).
//
// PENDIENTE DE GENERACIÓN REAL: el schema exportado schemas/.../7.json tiene
// que salir de Room/KSP (./gradlew :core:database:assembleDebug), jamás de una
// edición a mano -- un identityHash inventado se presentaría como export real y
// rompería MigrationTestHelper. Mientras no exista, Migration6To7SqlTest salta
// con skip explícito y Migration6To7InstrumentedTest no corre.
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Tabla nueva del journal — primero, es puramente aditiva.
        db.execSQL(Migration6To7Sql.CREATE_PUBLISH_OPERATIONS_TABLE)
        db.execSQL(Migration6To7Sql.CREATE_PUBLISH_OPERATIONS_UNIQUE_INDEX)

        db.execSQL(Migration6To7Sql.ADD_CLIENT_FILE_ID_TO_FILES)

        // Primero se leen TODOS los ids y recién después se actualiza: hacer el
        // UPDATE con el cursor abierto sobre la misma tabla es justamente el
        // caso en el que SQLite no garantiza qué filas ve el cursor.
        val ids = mutableListOf<Long>()
        db.query(Migration6To7Sql.SELECT_FILES_WITHOUT_IDENTITY).use { cursor ->
            while (cursor.moveToNext()) ids += cursor.getLong(0)
        }
        for (id in ids) {
            db.execSQL(Migration6To7Sql.ASSIGN_CLIENT_FILE_ID, arrayOf<Any>(UUID.randomUUID().toString(), id))
        }

        db.execSQL(Migration6To7Sql.CREATE_FILES_CLIENT_FILE_ID_INDEX)
        db.execSQL(Migration6To7Sql.ADD_CLIENT_FILE_ID_TO_OUTBOX)
        db.execSQL(Migration6To7Sql.LINK_LEGACY_OUTBOX_BY_REMOTE_ID)
    }
}
