package com.example.healthapp.core.data.responsitory

import android.util.Log
import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.SleepBucket
import com.example.healthapp.core.data.StepBucket
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.DailyHealthEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlinx.coroutines.flow.firstOrNull
import java.time.Duration
import java.time.LocalDateTime
import java.time.Period
import javax.inject.Inject

class HealthRepository @Inject constructor(
    private val healthDao: HealthDao,
    private val healthConnectManager: HealthConnectManager
) {
    // Lấy dữ liệu để hiển thị lên UI (Luôn lấy từ Local Cache cho nhanh)
    fun getDailyHealth(date: String, userId: Int): Flow<DailyHealthEntity?> {
        return healthDao.getDailyHealth(date, userId)
    }

    // HÀM QUAN TRỌNG: Đồng bộ từ Health Connect về Room
    suspend fun syncHealthData(userId: Int?) {
        val now = LocalDateTime.now()
        val startOfDay = now.toLocalDate().atStartOfDay()
        val todayStr = now.toLocalDate().toString()

        // Lấy dữ liệu từ Health Connect
        val hcSteps = healthConnectManager.readSteps(startOfDay, now)
        val hcHeartRate = healthConnectManager.readHeartRate(startOfDay, now)

        // Lấy dữ liệu Local hiện tại (để so sánh)
        val currentLocalData = if (userId != null) {
            healthDao.getDailyHealth(todayStr, userId).firstOrNull()
        } else null

        //Chỉ ghi đè nếu Health Connect có nhiều bước hơn (VD: đi bộ từ thiết bị khác)
        // Nếu Local đang lớn hơn (do Service đang chạy), thì GIỮ NGUYÊN Local.
        val currentLocalSteps = currentLocalData?.steps ?: 0

        if (hcSteps > currentLocalSteps) {
            val entity = DailyHealthEntity(
                date = todayStr,
                userId = userId ?: 0,
                steps = hcSteps,
            )
            healthDao.insertOrUpdateDailyHealth(entity)
            Log.d("HealthRepo", "Synced from HC: Updated local to $hcSteps")
        } else {
            Log.d("HealthRepo", "Synced from HC: Ignored because Local ($currentLocalSteps) >= HC ($hcSteps)")
        }
    }
    // 2. Hàm Lưu dữ liệu (Gọi liên tục khi đi bộ để update Room)
    suspend fun updateLocalSteps(userId: Int?, steps: Int, calories: Float) {
        if (userId == null) {
            Log.w("HealthRepository", "Chưa có User ID, bỏ qua việc lưu bước chân.")
            return
        }
        val today = LocalDate.now().toString()
        // Kiểm tra xem đã có record hôm nay chưa
        val currentData = healthDao.getDailyHealth(today, userId).firstOrNull()

        if (currentData == null) {
            // Tạo mới ngày hôm nay
            val newData = DailyHealthEntity(
                date = today,
                userId = userId?:1,
                steps = steps,
                caloriesBurned = calories
            )
            healthDao.insertOrUpdateDailyHealth(newData)
        } else {
            // Cập nhật số bước (Ghi đè số mới nhất)
            healthDao.updateSteps(today, userId, steps, calories)
        }
    }

    // 3. Hàm "Chốt sổ" cuối ngày: Đẩy lên Health Connect
    suspend fun writeStepsToHealthConnect(start: LocalDateTime, end: LocalDateTime, stepsDelta: Int) {
        // Gọi hàm writeSteps mới đã sửa ở Bước 1
        healthConnectManager.writeSteps(start, end, stepsDelta)
    }
    suspend fun saveHeartRate(userId: Int, bpm: Int) {
        val now = LocalDateTime.now()

        //Ghi vào Health Connect (Source of Truth)
        val success = healthConnectManager.writeHeartRate(bpm, now)
        Log.d("HealthRepository", "Write to Health Connect: $success")

        if (success) {
            //Chỉ lưu giá trị mới nhất vào Room để hiển thị ở Dashboard (Realtime)
            healthDao.updateHeartRate(now.toLocalDate().toString(), userId, bpm)
        }
    }
    suspend fun saveSleepSession(userId: Int, start: LocalDateTime, end: LocalDateTime) {
        // 1. Đẩy lên Health Connect
        healthConnectManager.writeSleepSession(start, end)

        // 2. Tính duration (phút)
        val durationMinutes = Duration.between(start, end).toMinutes()

        // 3. Lưu vào Room (Local DB)
        val today = LocalDate.now().toString()
        // Kiểm tra xem record hôm nay có chưa, nếu chưa thì tạo, có rồi thì update
        val currentData = healthDao.getDailyHealth(today, userId).firstOrNull()
        if (currentData == null) {
            val newData = DailyHealthEntity(
                date = today,
                userId = userId,
                sleepHours = durationMinutes,


            )
            healthDao.insertOrUpdateDailyHealth(newData)
        } else {
            //val updatedData = currentData.copy( sleepHours = durationMinutes )// giữ nguyên steps, heartRateAvg, caloriesBurned... )
            healthDao.updateSleepDuration(today, userId, durationMinutes)
        }
    }
    // Thêm hàm này
    suspend fun getSleepChartData(range: ChartTimeRange): List<SleepBucket> {
        val now = LocalDateTime.now()
        val end = now.toLocalDate().plusDays(1).atStartOfDay()

        // Xác định thời gian bắt đầu (Start)
        val start = when (range) {
            ChartTimeRange.WEEK -> now.minusDays(6) // 7 ngày gần nhất
            ChartTimeRange.MONTH -> now.minusDays(30)
            ChartTimeRange.YEAR -> now.minusYears(1).withDayOfMonth(1) // Lấy từ tháng này năm ngoái
            else -> now.minusDays(6)
        }

        // Xác định lát cắt (Slicer)
        val period = if (range == ChartTimeRange.YEAR) {
            Period.ofMonths(1) // Nếu chọn Năm -> Gom nhóm theo THÁNG
        } else {
            Period.ofDays(1)   // Nếu chọn Tuần/Tháng -> Gom nhóm theo NGÀY
        }

        return healthConnectManager.readSleepChartData(start, end, period)
    }

    // 2. Lấy dữ liệu biểu đồ (Trực tiếp từ Health Connect)
    suspend fun getHeartRateChartData(range: ChartTimeRange): List<HeartRateBucket> {
        val now = LocalDateTime.now()
        val end = now.toLocalDate().plusDays(1).atStartOfDay()
        return when (range) {
            ChartTimeRange.DAY -> {
                // Xem trong 24h qua, chia mỗi 1 giờ
                val start = LocalDate.now().atStartOfDay()
                healthConnectManager.readHeartRateAggregationByDuration(start, end, Duration.ofHours(1))
            }
            ChartTimeRange.WEEK -> {
                // Xem 7 ngày qua, chia mỗi 1 ngày
                val start = now.minusDays(6)// 7 ngày bao gồm hôm nay
                healthConnectManager.readHeartRateAggregation(start, end, Period.ofDays(1))
            }
            ChartTimeRange.MONTH -> {
                // Xem 30 ngày qua, chia mỗi 1 ngày
                val start = now.minusDays(30)
                healthConnectManager.readHeartRateAggregation(start, end, Period.ofDays(1))
            }
            ChartTimeRange.YEAR -> {
                // Xem 1 năm qua, chia mỗi 1 tháng
                val start = now.minusYears(1)
                healthConnectManager.readHeartRateAggregation(start, end, Period.ofMonths(1))
            }
        }
    }

    // Hàm lấy dữ liệu chart bước chân
    suspend fun getStepChartData(range: ChartTimeRange): List<StepBucket> {
        val now = LocalDateTime.now()
        //Lấy hết ngày hôm nay
        val end = now.toLocalDate().plusDays(1).atStartOfDay()
        val start = when (range) {
            ChartTimeRange.WEEK -> LocalDate.now().minusDays(6).atStartOfDay() // 00:00 6 ngày trước
            ChartTimeRange.MONTH -> LocalDate.now().minusDays(30).atStartOfDay()
            ChartTimeRange.YEAR -> LocalDate.now().minusYears(1).withDayOfMonth(1).atStartOfDay()
            else -> LocalDate.now().minusDays(6).atStartOfDay()
        }

        val period = if (range == ChartTimeRange.YEAR) {
            Period.ofMonths(1)
        } else {
            Period.ofDays(1)
        }

        return healthConnectManager.readStepChartData(start, end, period)
    }
}
enum class ChartTimeRange { DAY, WEEK, MONTH, YEAR}