package com.gaurav.grab.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.gaurav.grab.data.local.DownloadDao
import com.gaurav.grab.data.local.GrabDatabase
import com.gaurav.grab.data.local.GrabSettingsStore
import com.gaurav.grab.data.repository.GrabRepositoryImpl
import com.gaurav.grab.domain.repository.GrabRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GrabDatabase =
        Room.databaseBuilder(context, GrabDatabase::class.java, "grab.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideRoomDatabase(db: GrabDatabase): RoomDatabase = db

    @Provides
    @Singleton
    fun provideDownloadDao(db: GrabDatabase): DownloadDao = db.downloadDao()

    @Provides
    @Singleton
    fun provideGrabRepository(
        @ApplicationContext context: Context,
        dao: DownloadDao,
        db: RoomDatabase,
        settings: GrabSettingsStore
    ): GrabRepository = GrabRepositoryImpl(context, dao, db, settings)
}
