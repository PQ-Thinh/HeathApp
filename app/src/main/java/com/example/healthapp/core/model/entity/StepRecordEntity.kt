package com.example.healthapp.core.model.entity

import java.util.UUID

data class StepRecordEntity(
    var id: String = UUID.randomUUID().toString(),
    var userId: String = "",
    var startTime: Long = 0,
    var endTime: Long = 0,
    var count: Int = 0,
    var updatedAt: Long = System.currentTimeMillis(),
    val source: String = "Health App"
) {
    constructor() : this("", "", 0, 0, 0)
}