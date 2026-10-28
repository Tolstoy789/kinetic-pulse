package com.kineticpulse.app.di

import android.content.Context
import androidx.room.Room
import com.kineticpulse.app.data.db.KineticDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KineticDatabase =
        Room.databaseBuilder(context, KineticDatabase::class.java, KineticDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()
}
