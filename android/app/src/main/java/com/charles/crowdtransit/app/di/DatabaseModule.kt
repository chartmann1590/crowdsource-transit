package com.charles.crowdtransit.app.di

import android.content.Context
import androidx.room.Room
import com.charles.crowdtransit.app.data.local.AppDatabase
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
        Room.databaseBuilder(context, AppDatabase::class.java, "crowdtransit.db").build()

    @Provides
    @Singleton
    fun provideOfflineDao(db: AppDatabase): OfflineDao = db.offlineDao()
}
