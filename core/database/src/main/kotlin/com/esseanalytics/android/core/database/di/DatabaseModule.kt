package com.esseanalytics.android.core.database.di

import android.content.Context
import androidx.room.Room
import com.esseanalytics.android.core.database.EsseAnalyticsDatabase
import com.esseanalytics.android.core.database.MIGRATION_2_3
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.dao.PlatformVideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EsseAnalyticsDatabase =
        Room.databaseBuilder(context, EsseAnalyticsDatabase::class.java, "essenalytics.db")
            .addMigrations(MIGRATION_2_3)
            // Sigue como red de contención para saltos de versión SIN
            // Migration explícita (ej. instalaciones que quedaron en v1) --
            // MIGRATION_2_3 cubre el único salto real de acá en más.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideFileDao(db: EsseAnalyticsDatabase): FileDao = db.fileDao()

    @Provides
    fun providePlatformVideoDao(db: EsseAnalyticsDatabase): PlatformVideoDao = db.platformVideoDao()
}
