package com.example.healthapp.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.healthapp.core.data.FirebaseSyncManager
import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.data.HealthSensorManager
import com.example.healthapp.core.model.AppDb
import com.example.healthapp.core.model.dao.HealthDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDb {
        return Room.databaseBuilder(
            context,
            AppDb::class.java,
            "app_db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideHealthDao(appDb: AppDb): HealthDao {
        return appDb.healthDao()
    }

    @Provides
    @Singleton
    fun provideHealthSensorManager(@ApplicationContext context: Context): HealthSensorManager {
        return HealthSensorManager(context)
    }

    @Provides
    @Singleton
    fun provideHealthConnectManager(@ApplicationContext context: Context): HealthConnectManager {
        return HealthConnectManager(context)
    }
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseSyncManager(
        healthDao: HealthDao,
        firestore: FirebaseFirestore
    ): FirebaseSyncManager {
        return FirebaseSyncManager(healthDao, firestore)
    }
}