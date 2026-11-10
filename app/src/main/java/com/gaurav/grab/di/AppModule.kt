package com.gaurav.grab.di

import android.content.Context
import androidx.room.Room
import com.gaurav.grab.data.local.*
import com.gaurav.grab.data.repository.GrabRepositoryImpl
import com.gaurav.grab.domain.repository.GrabRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): GrabDatabase =
        Room.databaseBuilder(context, GrabDatabase::class.java, "grab.db").fallbackToDestructiveMigration().build()
    @Provides @Singleton fun provideDownloadDao(db: GrabDatabase): DownloadDao = db.downloadDao()
    @Provides @Singleton fun provideGrabRepository(@ApplicationContext context: Context, dao: DownloadDao, db: androidx.room.RoomDatabase, settings: GrabSettingsStore): GrabRepository =
        GrabRepositoryImpl(context, dao, db, settings)
}
