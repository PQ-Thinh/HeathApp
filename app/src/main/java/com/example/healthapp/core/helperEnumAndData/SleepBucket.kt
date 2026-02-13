package com.example.healthapp.core.helperEnumAndData

import java.time.LocalDateTime

data class SleepBucket(
    val startTime: LocalDateTime,
    val totalMinutes: Long
)