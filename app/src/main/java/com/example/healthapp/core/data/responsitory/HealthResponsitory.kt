package com.example.healthapp.core.data.responsitory

import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.DailyHealthEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
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
        val calories = steps * 0.04f // Công thức tính nhanh calo

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
}