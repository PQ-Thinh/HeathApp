package com.example.healthapp.core.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_health")
data class DailyHealthEntity(
    @PrimaryKey val date: String, // Format: YYYY-MM-DD
    @ColumnInfo(name = "steps") val steps: Int = 0,
    @ColumnInfo(name = "heart_rate_avg") val heartRateAvg: Int = 0,
    @ColumnInfo(name = "calories_burned") val caloriesBurned: Float = 0f,
    @ColumnInfo(name = "sleep_hours") val sleepHours: Float = 0f
)