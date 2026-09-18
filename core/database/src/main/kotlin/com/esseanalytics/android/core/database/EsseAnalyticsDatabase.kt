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
import com.esseanalytics.android.core.database.entity.ContentIdentityEntity
import com.esseanalytics.android.core.database.entity.FileEntity
import com.esseanalytics.android.core.database.entity.PendingHistoryEventEntity
import com.esseanalytics.android.core.database.entity.PendingPlatformUpdateEntity
import com.esseanalytics.android.core.database.entity.PlatformRevisionEntity
import com.esseanalytics.android.core.database.entity.PlatformTransitionOutboxEntity
import com.esseanalytics.android.core.database.entity.PlatformVideoEntity

// version = 1 porque es una app nueva (sin usuarios en producción todavía). A
// partir de la v2 hay que escribir Migration reales (Room.databaseBuilder sin
// fallbackToDestructiveMigration) — a diferencia de un reinstall de escritorio,
// acá los usuarios sí van a tener datos locales entre actualizaciones de la app.
@Database(
    entities = [
        FileEntity::class,
        PlatformVideoEntity::class,
        PendingHistoryEventEntity::class,
        PendingPlatformUpdateEntity::class,
        ContentIdentityEntity::class,
        PlatformRevisionEntity::class,
        PlatformTransitionOutboxEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class EsseAnalyticsDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun platformVideoDao(): PlatformVideoDao
    abstract fun pendingHistoryEventDao(): PendingHistoryEventDao
    abstract fun pendingPlatformUpdateDao(): PendingPlatformUpdateDao
    abstract fun causalPlatformDao(): CausalPlatformDao
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
