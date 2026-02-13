package com.example.healthapp.core.helperEnumAndData

import java.time.LocalDateTime

data class StepBucket(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val totalSteps: Long
)