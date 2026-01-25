package com.example.healthapp.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneOffset

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    // --- 1. SETUP & PERMISSIONS ---

    fun checkAvailability() = HealthConnectClient.getSdkStatus(context)

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class)
    )

    suspend fun hasAllPermissions(): Boolean {
        return healthConnectClient.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    fun openHealthConnectInPlayStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- 2. STEPS MANAGEMENT ---

    suspend fun readSteps(startTime: LocalDateTime, endTime: LocalDateTime): Int {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            response.records.sumOf { it.count.toInt() }
        } catch (e: Exception) {
            0
        }
    }

    suspend fun writeSteps(startTime: LocalDateTime, endTime: LocalDateTime, steps: Int): Boolean {
        if (steps <= 0) return true
        return try {
            val stepsRecord = StepsRecord(
                startTime = startTime.toInstant(ZoneOffset.UTC),
                endTime = endTime.toInstant(ZoneOffset.UTC),
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC,
                count = steps.toLong(),
                metadata = Metadata.manualEntry()
            )
            healthConnectClient.insertRecords(listOf(stepsRecord))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- 3. HEART RATE MANAGEMENT ---

    suspend fun writeHeartRate(bpm: Int, time: LocalDateTime): Boolean {
        return try {
            val record = HeartRateRecord(
                startTime = time.toInstant(ZoneOffset.UTC),
                endTime = time.plusSeconds(1).toInstant(ZoneOffset.UTC),
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = time.toInstant(ZoneOffset.UTC),
                        beatsPerMinute = bpm.toLong()
                    )
                ),
                metadata = Metadata.manualEntry()
            )
            healthConnectClient.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    //Đọc nhịp tim trung bình
    suspend fun readHeartRate(startTime: LocalDateTime, endTime: LocalDateTime): Int {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            if (response.records.isNotEmpty()) {
                // Tính trung bình cộng nhịp tim
                val avg =
                    response.records.flatMap { it.samples }.map { it.beatsPerMinute }.average()
                avg.toInt()
            } else 0
        } catch (e: Exception) {
            0
        }
    }


    // Lấy dữ liệu theo TUẦN / THÁNG / NĂM (Period)
    suspend fun readHeartRateAggregation(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        period: Period
    ): List<HeartRateBucket> {
        try {
            val request = AggregateGroupByPeriodRequest(
                metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX, HeartRateRecord.BPM_MIN),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                timeRangeSlicer = period
            )
            val response = healthConnectClient.aggregateGroupByPeriod(request)
            return response.map { bucket ->
                HeartRateBucket(
                    // FIX LỖI: bucket.startTime ở đây là LocalDateTime -> Đúng kiểu
                    startTime = bucket.startTime,
                    min = bucket.result[HeartRateRecord.BPM_MIN] ?: 0,
                    max = bucket.result[HeartRateRecord.BPM_MAX] ?: 0,
                    avg = bucket.result[HeartRateRecord.BPM_AVG] ?: 0
                )
            }.filter { it.avg > 0 }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    // Lấy dữ liệu theo NGÀY (Duration - ví dụ chia theo mỗi giờ)
    suspend fun readHeartRateAggregationByDuration(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        duration: Duration
    ): List<HeartRateBucket> {
        try {
            val request = AggregateGroupByDurationRequest(
                metrics = setOf(HeartRateRecord.BPM_AVG, HeartRateRecord.BPM_MAX, HeartRateRecord.BPM_MIN),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                timeRangeSlicer = duration
            )
            val response = healthConnectClient.aggregateGroupByDuration(request)
            return response.map { bucket ->
                HeartRateBucket(
                    // FIX LỖI: bucket.startTime ở đây là Instant -> Cần convert sang LocalDateTime
                    startTime = LocalDateTime.ofInstant(bucket.startTime, ZoneOffset.UTC),
                    min = bucket.result[HeartRateRecord.BPM_MIN] ?: 0,
                    max = bucket.result[HeartRateRecord.BPM_MAX] ?: 0,
                    avg = bucket.result[HeartRateRecord.BPM_AVG] ?: 0
                )
            }.filter { it.avg > 0 }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    // --- 4. SLEEP MANAGEMENT ---

    suspend fun writeSleepSession(start: LocalDateTime, end: LocalDateTime) {
        try {
            val record = SleepSessionRecord(
                startTime = start.toInstant(ZoneOffset.UTC),
                startZoneOffset = ZoneOffset.UTC,
                endTime = end.toInstant(ZoneOffset.UTC),
                endZoneOffset = ZoneOffset.UTC,
                metadata = Metadata.manualEntry()
            )
            healthConnectClient.insertRecords(listOf(record))
        } catch (e: Exception) {
            Log.e("HealthConnect", "Lỗi lưu giấc ngủ: ${e.message}")
        }
    }

    // Dùng cho biểu đồ Giấc ngủ (Tuần/Tháng)
    suspend fun readSleepChartData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        period: Period
    ): List<SleepBucket> {
        try {
            val request = AggregateGroupByPeriodRequest(
                metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                timeRangeSlicer = period
            )
            val response = healthConnectClient.aggregateGroupByPeriod(request)
            return response.map { bucket ->
                val duration = bucket.result[SleepSessionRecord.SLEEP_DURATION_TOTAL]
                val minutes = duration?.toMinutes() ?: 0
                SleepBucket(
                    startTime = bucket.startTime,
                    totalMinutes = minutes
                )
            }.filter { it.totalMinutes > 0 }
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
}

// --- DATA CLASSES (Đã chuẩn hóa dùng LocalDateTime) ---

data class HeartRateBucket(
    val startTime: LocalDateTime, // Đã đổi từ Instant -> LocalDateTime để vẽ chart dễ hơn
    val min: Long,
    val max: Long,
    val avg: Long
)

data class SleepBucket(
    val startTime: LocalDateTime,
    val totalMinutes: Long
)