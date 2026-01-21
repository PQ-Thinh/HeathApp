package com.example.healthapp.core.model


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.NotificationEntity
import com.example.healthapp.core.model.entity.UserEntity


private const val DATABASE_NAME ="app_db"
private const val DATABASE_VERSION =2
@Database(
    entities = [UserEntity::class, DailyHealthEntity::class, NotificationEntity::class],
    version = DATABASE_VERSION
)
abstract class AppDb: RoomDatabase(){
    abstract fun healthDao(): HealthDao

    companion object{
        private var instance: AppDb? = null

        operator fun invoke(context: Context): AppDb{
            return instance ?: synchronized(this){
                instance ?: buildDatabase(context).also { instance = it }

            }
        }

        private fun buildDatabase(context: Context): AppDb = Room.databaseBuilder(
            context,
            AppDb::class.java,
            DATABASE_NAME
        ).build()
    }
}