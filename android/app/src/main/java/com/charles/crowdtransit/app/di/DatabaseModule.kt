package com.charles.crowdtransit.app.di

import android.content.Context
import androidx.room.Room
import com.charles.crowdtransit.app.data.local.AppDatabase
import com.charles.crowdtransit.app.data.local.dao.GtfsDao
import com.charles.crowdtransit.app.data.local.dao.OfflineDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "crowdtransit.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()

    @Provides
    @Singleton
    fun provideOfflineDao(db: AppDatabase): OfflineDao = db.offlineDao()

    @Provides
    @Singleton
    fun provideGtfsDao(db: AppDatabase): GtfsDao = db.gtfsDao()
}
