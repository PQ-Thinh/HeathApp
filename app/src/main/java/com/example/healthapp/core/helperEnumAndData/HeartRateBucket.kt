package com.example.healthapp.core.helperEnumAndData

import java.time.LocalDateTime

data class HeartRateBucket(
    val startTime: LocalDateTime,
    val min: Long,
    val max: Long,
    val avg: Long
)