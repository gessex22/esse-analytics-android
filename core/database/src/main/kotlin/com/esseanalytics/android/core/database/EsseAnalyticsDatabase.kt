package com.esseanalytics.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.dao.PlatformVideoDao
import com.esseanalytics.android.core.database.entity.FileEntity
import com.esseanalytics.android.core.database.entity.PlatformVideoEntity

// version = 1 porque es una app nueva (sin usuarios en producción todavía). A
// partir de la v2 hay que escribir Migration reales (Room.databaseBuilder sin
// fallbackToDestructiveMigration) — a diferencia de un reinstall de escritorio,
// acá los usuarios sí van a tener datos locales entre actualizaciones de la app.
@Database(
    entities = [FileEntity::class, PlatformVideoEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class EsseAnalyticsDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun platformVideoDao(): PlatformVideoDao
}

// v2 -> v3: agrega remoteLibraryVideoId (ver FileEntity, ImportUseCase.importFromRemoteLibrary).
// Real, no destructiva -- a diferencia de fallbackToDestructiveMigration()
// (ver DatabaseModule), esta SÍ preserva el catálogo local ya importado.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE files ADD COLUMN remoteLibraryVideoId TEXT")
    }
}
