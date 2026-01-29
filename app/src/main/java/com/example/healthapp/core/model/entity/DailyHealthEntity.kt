package com.example.healthapp.core.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_health",
    primaryKeys = ["date", "userId"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DailyHealthEntity(
    val date: String,
    val userId: String,
    @ColumnInfo(name = "steps") val steps: Int = 0,
    @ColumnInfo(name = "heart_rate_avg") val heartRateAvg: Int = 0,
    @ColumnInfo(name = "calories_burned") val caloriesBurned: Float = 0f,
    @ColumnInfo(name = "sleep_hours") val sleepHours: Long = 0
) {
    constructor() : this("", "", 0, 0, 0f, 0)
}