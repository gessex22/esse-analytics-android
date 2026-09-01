package com.esseanalytics.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.dao.PendingHistoryEventDao
import com.esseanalytics.android.core.database.dao.PendingPlatformUpdateDao
import com.esseanalytics.android.core.database.dao.PlatformVideoDao
import com.esseanalytics.android.core.database.entity.FileEntity
import com.esseanalytics.android.core.database.entity.PendingHistoryEventEntity
import com.esseanalytics.android.core.database.entity.PendingPlatformUpdateEntity
import com.esseanalytics.android.core.database.entity.PlatformVideoEntity

// version = 1 porque es una app nueva (sin usuarios en producción todavía). A
// partir de la v2 hay que escribir Migration reales (Room.databaseBuilder sin
// fallbackToDestructiveMigration) — a diferencia de un reinstall de escritorio,
// acá los usuarios sí van a tener datos locales entre actualizaciones de la app.
@Database(
    entities = [FileEntity::class, PlatformVideoEntity::class, PendingHistoryEventEntity::class, PendingPlatformUpdateEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class EsseAnalyticsDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun platformVideoDao(): PlatformVideoDao
    abstract fun pendingHistoryEventDao(): PendingHistoryEventDao
    abstract fun pendingPlatformUpdateDao(): PendingPlatformUpdateDao
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
