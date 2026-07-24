package com.esseanalytics.android.core.media.di

import com.esseanalytics.android.core.media.AndroidMediaProber
import com.esseanalytics.android.core.media.AndroidTrimProcessor
import com.esseanalytics.android.core.media.Media3NormalizeProcessor
import com.esseanalytics.android.core.media.MediaProber
import com.esseanalytics.android.core.media.NormalizeProcessor
import com.esseanalytics.android.core.media.TrimProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    abstract fun bindMediaProber(impl: AndroidMediaProber): MediaProber

    @Binds
    abstract fun bindTrimProcessor(impl: AndroidTrimProcessor): TrimProcessor

    @Binds
    abstract fun bindNormalizeProcessor(impl: Media3NormalizeProcessor): NormalizeProcessor
}
