package com.example.healthapp.core.data

import android.content.Context

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDateTime
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.response.InsertRecordsResponse
import java.time.ZoneOffset

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    //Kiểm tra xem máy có hỗ trợ Health Connect không
    fun checkAvailability() = HealthConnectClient.getSdkStatus(context)

    // Định nghĩa các quyền cần xin
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class)
    )

    // Hàm xin quyền (gọi từ Activity)
    suspend fun hasAllPermissions(): Boolean {
        return healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)
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
        if(steps <= 0) return true
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
                val avg = response.records.flatMap { it.samples }.map { it.beatsPerMinute }.average()
                avg.toInt()
            } else 0
        } catch (e: Exception) {
            0
        }
    }
}