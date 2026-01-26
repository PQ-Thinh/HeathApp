package com.example.healthapp.core.data.responsitory

import android.util.Log
import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.SleepBucket
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
        // Xác định thời gian (Từ đầu ngày đến hiện tại)
        val now = LocalDateTime.now()
        val startOfDay = now.toLocalDate().atStartOfDay()
        val todayStr = now.toLocalDate().toString()

        // Lấy dữ liệu từ Health Connect
        val steps = healthConnectManager.readSteps(startOfDay, now)
        val heartRate = healthConnectManager.readHeartRate(startOfDay, now)
        val calories = steps * 0.04f

        //Cập nhật vào Room (Cache) gắn với User ID
        // Nếu user đổi máy hoặc đăng nhập lại, bước 2 sẽ lấy lại đc dữ liệu cũ,
        // và bước 3 sẽ điền lại vào Room cho user này.
        if (steps > 0) {
            val entity = DailyHealthEntity(
                date = todayStr,
                userId = userId?:0,
                steps = steps,
                caloriesBurned = calories,
                heartRateAvg = heartRate
            )
            healthDao.insertOrUpdateDailyHealth(entity)
        }
    }
    // 1. Hàm lấy số bước đã lưu trong Room của ngày hôm nay
    suspend fun getSavedStepsToday(userId: Int): Int {
        val today = LocalDate.now().toString()
        val data = healthDao.getDailyHealth(today, userId).firstOrNull()
        return data?.steps ?: 0
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
    suspend fun syncToHealthConnect(steps: Int) {
        val now = LocalDateTime.now()
        val startOfDay = now.toLocalDate().atStartOfDay()

        // Ghi vào Health Connect (Google server)
        healthConnectManager.writeSteps(startOfDay, now, steps)
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
        val end = LocalDateTime.now()
        val start = when (range) {
            ChartTimeRange.WEEK -> end.minusDays(7)
            ChartTimeRange.MONTH -> end.minusDays(30)
            ChartTimeRange.YEAR -> end.minusDays(365)
            else -> end.minusDays(7)
        }
        val period = if (range == ChartTimeRange.YEAR) Period.ofMonths(1) else Period.ofDays(1)

        return healthConnectManager.readSleepChartData(start, end, period)
    }

    // 2. Lấy dữ liệu biểu đồ (Trực tiếp từ Health Connect)
    suspend fun getHeartRateChartData(range: ChartTimeRange): List<HeartRateBucket> {
        val now = LocalDateTime.now()
        val end = now.plusMinutes(1) // Cộng thêm chút thời gian đệm
        return when (range) {
            ChartTimeRange.DAY -> {
                // Xem trong 24h qua, chia mỗi 1 giờ
                val start = LocalDate.now().atStartOfDay()
                healthConnectManager.readHeartRateAggregationByDuration(start, end, Duration.ofHours(1))
            }
            ChartTimeRange.WEEK -> {
                // Xem 7 ngày qua, chia mỗi 1 ngày
                val start = now.minusDays(6) // 7 ngày bao gồm hôm nay
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
}
enum class ChartTimeRange { DAY, WEEK, MONTH, YEAR}