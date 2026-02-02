package com.example.healthapp.core.model.entity

import java.util.UUID


data class SleepSessionEntity(
    var id: String = UUID.randomUUID().toString(),
    var userId: String="",
    var startTime: Long = 0,
    var endTime: Long = 0,
    var type: String="Light, Deep, REM, Awake",
    var updatedAt: Long = System.currentTimeMillis()
) {
    constructor() : this("", "", 0, 0, "", 0)
}