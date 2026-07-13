package com.esseanalytics.android.core.media.di

import com.esseanalytics.android.core.media.AndroidMediaProber
import com.esseanalytics.android.core.media.MediaProber
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    abstract fun bindMediaProber(impl: AndroidMediaProber): MediaProber
}
