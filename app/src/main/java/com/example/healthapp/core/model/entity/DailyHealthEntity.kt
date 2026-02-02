package com.example.healthapp.core.model.entity



data class DailyHealthEntity(
    var date: String="",
    var userId: String="",
    var steps: Int = 0,
    var heartRateAvg: Int = 0,
    var caloriesBurned: Float = 0f,
    var sleepHours: Long = 0
) {
    constructor() : this("", "", 0, 0, 0f, 0)
}