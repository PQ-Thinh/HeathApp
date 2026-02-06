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
import java.time.ZoneId
import kotlin.reflect.KClass
import androidx.health.connect.client.records.Record

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
        HealthPermission.getWritePermission(SleepSessionRecord::class),
        // HealthPermission.getReadPermission(SleepStageRecord::class)
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


    suspend fun writeSteps(start: LocalDateTime, end: LocalDateTime, count: Int) {
        try {
            if (count <= 0) return // Không ghi nếu không có bước nào

            val zoneOffset = ZoneId.systemDefault().rules.getOffset(start)

            // CHỈ GHI DỮ LIỆU MỚI (CỘNG DỒN)
            // Health Connect sẽ tự động cộng tổng các record lại với nhau
            val stepsRecord = StepsRecord(
                startTime = start.toInstant(zoneOffset),
                endTime = end.toInstant(zoneOffset),
                startZoneOffset = zoneOffset,
                endZoneOffset = zoneOffset,
                count = count.toLong(),
                metadata = Metadata.manualEntry()
            )
            healthConnectClient.insertRecords(listOf(stepsRecord))
            Log.d("HealthConnect", "Đã cộng thêm $count bước vào hệ thống")
        } catch (e: Exception) {
            Log.e("HealthConnect", "Lỗi ghi bước chân: ${e.message}")
        }
    }

    // Hàm lấy dữ liệu bước chân cho biểu đồ
    suspend fun readStepChartData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        period: Period
    ): List<StepBucket> {
        try {
            val request = AggregateGroupByPeriodRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                timeRangeSlicer = period
            )
            val response = healthConnectClient.aggregateGroupByPeriod(request)
            return response.map { bucket ->
                StepBucket(
                    startTime = bucket.startTime,
                    endTime = bucket.endTime,
                    totalSteps = bucket.result[StepsRecord.COUNT_TOTAL] ?: 0
                )
            }.filter { it.totalSteps > 0 } // Chỉ lấy ngày nào có đi bộ
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }

    //  Đọc danh sách chi tiết các bản ghi bước chân
    suspend fun readRawStepRecords(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<StepsRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnect", "Lỗi đọc raw steps: ${e.message}")
            emptyList()
        }
    }

    // --- 3. HEART RATE MANAGEMENT ---

    suspend fun writeHeartRate(bpm: Int, time: LocalDateTime): Boolean {
        return try {
            // Lấy offset hiện tại của máy (Ví dụ VN là +07:00)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(time)

            // Dùng zoneOffset để convert, KHÔNG dùng ZoneOffset.UTC ở đây
            val startInstant = time.toInstant(zoneOffset)
            val endInstant =
                time.plusSeconds(60).toInstant(zoneOffset) // Tăng độ rộng mẫu lên chút cho chắc

            val record = HeartRateRecord(
                startTime = startInstant,
                endTime = endInstant,
                startZoneOffset = zoneOffset,
                endZoneOffset = zoneOffset,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = startInstant,
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
    suspend fun readHeartRateAggregationByPeriod(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        period: Period
    ): List<HeartRateBucket> {
        return try {
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(LocalDateTime.now())

            val request = AggregateGroupByPeriodRequest(
                metrics = setOf(
                    HeartRateRecord.BPM_AVG,
                    HeartRateRecord.BPM_MAX,
                    HeartRateRecord.BPM_MIN
                ),
                timeRangeFilter = TimeRangeFilter.between(
                    startTime.toInstant(zoneOffset),
                    endTime.toInstant(zoneOffset)
                ),
                timeRangeSlicer = period
            )
            val response = healthConnectClient.aggregateGroupByPeriod(request)

            response.map { bucket ->
                HeartRateBucket(
                    startTime = bucket.startTime, // Period trả về sẵn LocalDateTime
                    min = bucket.result[HeartRateRecord.BPM_MIN] ?: 0,
                    max = bucket.result[HeartRateRecord.BPM_MAX] ?: 0,
                    avg = bucket.result[HeartRateRecord.BPM_AVG] ?: 0
                )
            }.filter { it.avg > 0 }.sortedBy { it.startTime }
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Lỗi Aggregation Period: ${e.message}")
            emptyList()
        }
    }

    // Lấy dữ liệu theo NGÀY (Duration - ví dụ chia theo mỗi giờ)
    suspend fun readHeartRateAggregationByDuration(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        duration: Duration
    ): List<HeartRateBucket> {
        return try {
            val zoneId = ZoneId.systemDefault()
            val zoneOffset = zoneId.rules.getOffset(LocalDateTime.now())

            val request = AggregateGroupByDurationRequest(
                metrics = setOf(
                    HeartRateRecord.BPM_AVG,
                    HeartRateRecord.BPM_MAX,
                    HeartRateRecord.BPM_MIN
                ),
                timeRangeFilter = TimeRangeFilter.between(
                    startTime.toInstant(zoneOffset),
                    endTime.toInstant(zoneOffset)
                ),
                timeRangeSlicer = duration
            )
            val response = healthConnectClient.aggregateGroupByDuration(request)

            response.map { bucket ->
                HeartRateBucket(
                    // Convert Instant -> LocalDateTime theo múi giờ máy
                    startTime = LocalDateTime.ofInstant(bucket.startTime, zoneId),
                    min = bucket.result[HeartRateRecord.BPM_MIN] ?: 0,
                    max = bucket.result[HeartRateRecord.BPM_MAX] ?: 0,
                    avg = bucket.result[HeartRateRecord.BPM_AVG] ?: 0
                )
            }.filter { it.avg > 0 }.sortedBy { it.startTime }
        } catch (e: Exception) {
            Log.e("HealthConnectManager", "Lỗi Aggregation Duration: ${e.message}")
            emptyList()
        }
    }

    suspend fun readRawHeartRecords(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<HeartRateRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnect", "Lỗi đọc raw steps: ${e.message}")
            emptyList()
        }
    }
    // --- 4. SLEEP MANAGEMENT ---

    suspend fun writeSleepSession(start: LocalDateTime, end: LocalDateTime) {
        try {
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(start)
            val record = SleepSessionRecord(
                startTime = start.toInstant(zoneOffset),
                startZoneOffset = zoneOffset,
                endTime = end.toInstant(zoneOffset),
                endZoneOffset = zoneOffset,
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

    suspend fun deleteRecords(recordType: KClass<out Record>, timeRangeFilter: TimeRangeFilter) {
        try {
            healthConnectClient.deleteRecords(recordType, timeRangeFilter)
            Log.d("HealthConnect", "Đã xóa record loại ${recordType.simpleName}")
        } catch (e: Exception) {
            Log.e("HealthConnect", "Lỗi xóa dữ liệu: ${e.message}")
        }
    }

    suspend fun readRawSleepRecords(
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): List<SleepSessionRecord> {
        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records
        } catch (e: Exception) {
            Log.e("HealthConnect", "Lỗi đọc raw steps: ${e.message}")
            emptyList()
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
data class StepBucket(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val totalSteps: Long
)