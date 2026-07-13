package com.esseanalytics.android.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
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
    version = 2,
    exportSchema = true,
)
abstract class EsseAnalyticsDatabase : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun platformVideoDao(): PlatformVideoDao
}
