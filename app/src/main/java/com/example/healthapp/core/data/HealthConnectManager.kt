package com.example.healthapp.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDateTime
import androidx.health.connect.client.records.metadata.Metadata
import java.time.ZoneOffset
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import java.time.Period
import androidx.health.connect.client.records.SleepSessionRecord

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    //Kiểm tra xem máy có hỗ trợ Health Connect không
    fun checkAvailability() = HealthConnectClient.getSdkStatus(context)

    // Định nghĩa các quyền cần xin
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class)
    )

    // Hàm xin quyền (gọi từ Activity)
    suspend fun hasAllPermissions(): Boolean {
        return healthConnectClient.permissionController.getGrantedPermissions()
            .containsAll(permissions)
    }

    //Đọc tổng số bước chân trong khoảng thời gian
    suspend fun readSteps(startTime: LocalDateTime, endTime: LocalDateTime): Int {
        return try {
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            // Cộng dồn tất cả các record bước chân tìm thấy
            response.records.sumOf { it.count.toInt() }
        } catch (e: Exception) {
            0 // Trả về 0 nếu lỗi hoặc chưa cấp quyền
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

    suspend fun writeHeartRate(bpm: Int, time: LocalDateTime): Boolean {
        return try {
            val record = HeartRateRecord(
                startTime = time.toInstant(ZoneOffset.UTC),
                endTime = time.plusSeconds(1).toInstant(ZoneOffset.UTC), // Record tức thời
                startZoneOffset = ZoneOffset.UTC,
                endZoneOffset = ZoneOffset.UTC,
                samples = listOf(
                    HeartRateRecord.Sample(
                        time = time.toInstant(ZoneOffset.UTC),
                        beatsPerMinute = bpm.toLong()
                    )
                ),
                metadata = Metadata.manualEntry() // Đánh dấu là app tự đo
            )
            healthConnectClient.insertRecords(listOf(record))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun readHeartRateAggregation(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        period: Period // Hoặc Duration nếu muốn chia nhỏ hơn 1 ngày
    ): List<HeartRateBucket> {
        try {
            val request = AggregateGroupByPeriodRequest(
                metrics = setOf(
                    HeartRateRecord.BPM_AVG,
                    HeartRateRecord.BPM_MAX,
                    HeartRateRecord.BPM_MIN
                ),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                timeRangeSlicer = period // Tự động cắt lát dữ liệu theo ngày/tuần
            )

            val response = healthConnectClient.aggregateGroupByPeriod(request)

            return response.map { bucket ->
                HeartRateBucket(
                    startTime = bucket.startTime,
                    // Nếu không có dữ liệu thì trả về 0
                    min = bucket.result[HeartRateRecord.BPM_MIN] ?: 0,
                    max = bucket.result[HeartRateRecord.BPM_MAX] ?: 0,
                    avg = bucket.result[HeartRateRecord.BPM_AVG] ?: 0

                )
            }.filter { it.avg > 0 } // Lọc bỏ những khoảng thời gian không có dữ liệu
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
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

    // Thêm vào HealthConnectManager
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
            Log.d("HealthConnect", "Đã lưu giấc ngủ: $start -> $end")
        } catch (e: Exception) {
            Log.e("HealthConnect", "Lỗi lưu giấc ngủ: ${e.message}")
        }
    }

    suspend fun readSleepSessions(start: LocalDateTime, end: LocalDateTime): Long {
        return try {
            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    start.toInstant(ZoneOffset.UTC),
                    end.toInstant(ZoneOffset.UTC)
                )
            )
            val response = healthConnectClient.readRecords(request)
            // Tính tổng thời gian ngủ (phút)
            response.records.sumOf { (it.endTime.toEpochMilli() - it.startTime.toEpochMilli()) / 60000 }
        } catch (e: Exception) {
            Log.e("HealthConnect", "Lỗi đọc giấc ngủ: ${e.message}")
            return 0
        }
    }
    // Hàm lấy dữ liệu biểu đồ giấc ngủ
    suspend fun readSleepChartData(
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        period: Period // Period.ofDays(1) cho biểu đồ tuần/tháng
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
            }.filter { it.totalMinutes > 0 } // Chỉ lấy ngày có dữ liệu
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }
    }
    // Hàm mở CH Play để cài/update Health Connect
    fun openHealthConnectInPlayStore(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending") // Mở bằng CH Play
            data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
            putExtra("overlay", true) // Hiển thị đè lên app (nếu hỗ trợ)
            putExtra("callerId", context.packageName)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback nếu không có CH Play: mở bằng trình duyệt
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"))
            context.startActivity(webIntent)
        }
    }
}
data class HeartRateBucket(
    val startTime: LocalDateTime,
    val min: Long,
    val max: Long,
    val avg: Long
)
data class SleepBucket(
    val startTime: LocalDateTime,
    val totalMinutes: Long
)