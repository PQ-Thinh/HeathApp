package com.example.healthapp.core.model.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "sleep_sessions")
data class SleepSessionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val startTime: Long,
    val endTime: Long,
    val type: String // Ví dụ: "Light", "Deep", "REM", "Awake"
) {
    constructor() : this("", "", 0, 0, "")
}