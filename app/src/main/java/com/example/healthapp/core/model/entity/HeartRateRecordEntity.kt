package com.example.healthapp.core.model.entity

import java.util.UUID

data class HeartRateRecordEntity(
    var id: String = UUID.randomUUID().toString(),
    val userId: String,
    var time: Long,
    var bpm: Int,
) {
    constructor() : this("", "", 0, 0)
}