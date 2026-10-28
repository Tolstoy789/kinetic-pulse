package com.kineticpulse.app.di

import android.content.Context
import com.kineticpulse.app.ai.AndroidDeviceResources
import com.kineticpulse.app.ai.GemmaLocalManager
import com.kineticpulse.app.ai.LiteRtLmEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideGemmaLocalManager(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): GemmaLocalManager = GemmaLocalManager(
        resources = AndroidDeviceResources(context),
        engineFactory = LiteRtLmEngine.Factory,
        scope = scope,
        ioDispatcher = Dispatchers.IO,
    )
}
