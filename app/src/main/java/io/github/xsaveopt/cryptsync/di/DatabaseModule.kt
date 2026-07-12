package io.github.xsaveopt.cryptsync.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.xsaveopt.cryptsync.data.db.ActivityDao
import io.github.xsaveopt.cryptsync.data.db.CryptSyncDatabase
import io.github.xsaveopt.cryptsync.data.db.LogDao
import io.github.xsaveopt.cryptsync.data.db.MediaCacheDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CryptSyncDatabase =
        Room.databaseBuilder(context, CryptSyncDatabase::class.java, "cryptsync.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideMediaCacheDao(database: CryptSyncDatabase): MediaCacheDao =
        database.mediaCacheDao()

    @Provides
    fun provideLogDao(database: CryptSyncDatabase): LogDao =
        database.logDao()

    @Provides
    fun provideActivityDao(database: CryptSyncDatabase): ActivityDao =
        database.activityDao()
}
