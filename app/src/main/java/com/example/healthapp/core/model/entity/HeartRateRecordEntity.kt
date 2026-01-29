package com.example.healthapp.core.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "heart_rate_records")
data class HeartRateRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val time: Long,
    val bpm: Int,
) {
    constructor() : this("", "", 0, 0)
}