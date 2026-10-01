package com.esseanalytics.android.core.database.di

import android.content.Context
import androidx.room.Room
import com.esseanalytics.android.core.database.EsseAnalyticsDatabase
import com.esseanalytics.android.core.database.MIGRATION_2_3
import com.esseanalytics.android.core.database.MIGRATION_3_4
import com.esseanalytics.android.core.database.MIGRATION_4_5
import com.esseanalytics.android.core.database.MIGRATION_5_6
import com.esseanalytics.android.core.database.MIGRATION_6_7
import com.esseanalytics.android.core.database.dao.CausalPlatformDao
import com.esseanalytics.android.core.database.dao.FileDao
import com.esseanalytics.android.core.database.dao.PendingHistoryEventDao
import com.esseanalytics.android.core.database.dao.PendingPlatformUpdateDao
import com.esseanalytics.android.core.database.dao.PlatformVideoDao
import com.esseanalytics.android.core.database.dao.PublishOperationDao
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
            .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            // Red de contención acotada a la ÚNICA versión sin Migration
            // escrita (la v1 del scaffold inicial). Antes era
            // fallbackToDestructiveMigration() a secas, que habilitaba borrar
            // la base en cualquier salto futuro al que le faltara la Migration
            // -- con datos reales en el teléfono eso es pérdida silenciosa. De
            // v2 en adelante hay cadena completa (2->3->4->5->6->7) y si algún
            // día falta un eslabón, la app tiene que fallar ruidosamente en vez
            // de vaciar la biblioteca del usuario.
            .fallbackToDestructiveMigrationFrom(1)
            .build()

    @Provides
    fun provideFileDao(db: EsseAnalyticsDatabase): FileDao = db.fileDao()

    @Provides
    fun providePlatformVideoDao(db: EsseAnalyticsDatabase): PlatformVideoDao = db.platformVideoDao()

    @Provides
    fun providePendingHistoryEventDao(db: EsseAnalyticsDatabase): PendingHistoryEventDao = db.pendingHistoryEventDao()

    @Provides
    fun providePendingPlatformUpdateDao(db: EsseAnalyticsDatabase): PendingPlatformUpdateDao = db.pendingPlatformUpdateDao()

    @Provides
    fun provideCausalPlatformDao(db: EsseAnalyticsDatabase): CausalPlatformDao = db.causalPlatformDao()

    @Provides
    fun providePublishOperationDao(db: EsseAnalyticsDatabase): PublishOperationDao = db.publishOperationDao()
}
