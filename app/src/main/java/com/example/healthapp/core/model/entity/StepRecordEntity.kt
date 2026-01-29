package com.example.healthapp.core.model.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "step_records")
data class StepRecordEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val startTime: Long,
    val endTime: Long,
    val count: Int,
) {
    constructor() : this("", "", 0, 0, 0)
}