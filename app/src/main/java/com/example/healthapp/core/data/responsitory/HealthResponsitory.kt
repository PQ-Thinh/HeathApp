package com.example.healthapp.core.data.responsitory

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.healthapp.core.data.FirebaseSyncManager
import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.SleepBucket
import com.example.healthapp.core.data.StepBucket
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.LocalDateTime
import java.time.Period
import javax.inject.Inject

class HealthRepository @Inject constructor(
    private val healthDao: HealthDao,
    private val healthConnectManager: HealthConnectManager,
    private val dataStore: DataStore<Preferences>,
    private val syncManager: FirebaseSyncManager // <-- [MỚI] Thêm SyncManager
) {
    private val CURRENT_USER_EMAIL_KEY = stringPreferencesKey("current_user_email")

    // Lấy dữ liệu để hiển thị lên UI (Luôn lấy từ Local Cache cho nhanh)
    fun getDailyHealth(date: String, userId: String): Flow<DailyHealthEntity?> {
        return healthDao.getDailyHealth(date, userId)
    }

    //Đồng bộ từ Health Connect về Room
    suspend fun syncHealthData(userId: String?) {
        if (userId.isNullOrEmpty()) return

        val now = LocalDateTime.now()
        val startOfDay = now.toLocalDate().atStartOfDay()
        val todayStr = now.toLocalDate().toString()

        // Lấy dữ liệu từ Health Connect
        val hcSteps = healthConnectManager.readSteps(startOfDay, now)
        // val hcHeartRate = healthConnectManager.readHeartRate(startOfDay, now)

        // Lấy dữ liệu Local hiện tại (để so sánh)
        val currentLocalData = healthDao.getDailyHealth(todayStr, userId).firstOrNull()

        // Chỉ ghi đè nếu Health Connect có nhiều bước hơn (VD: đi bộ từ thiết bị khác)
        // Nếu Local đang lớn hơn (do Service đang chạy), thì GIỮ NGUYÊN Local.
        val currentLocalSteps = currentLocalData?.steps ?: 0

        if (hcSteps > currentLocalSteps) {
            val entity = DailyHealthEntity(
                date = todayStr,
                userId = userId, // String ID
                steps = hcSteps,
            )
            healthDao.insertOrUpdateDailyHealth(entity)
            Log.d("HealthRepo", "Synced from HC: Updated local to $hcSteps")

            // [MỚI]: Đẩy cập nhật mới lên Cloud
            syncManager.pushDailyHealth(entity)
        } else {
            Log.d("HealthRepo", "Synced from HC: Ignored because Local ($currentLocalSteps) >= HC ($hcSteps)")
        }
    }
    suspend fun syncUserToCloud(user: UserEntity) {
        // Gọi sang FirebaseSyncManager để đẩy lên
        // Lưu ý: UserEntity đã có id = UID (do ta gán lúc tạo)
        syncManager.pushUserProfile(user)
    }
    //  Hàm Lưu dữ liệu (Gọi liên tục khi đi bộ để update Room)
    suspend fun updateLocalSteps(userId: String?, steps: Int, calories: Float) {
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
                userId = userId,
                steps = steps,
                caloriesBurned = calories
            )
            healthDao.insertOrUpdateDailyHealth(newData)
            // [MỚI]: Đẩy lên Cloud
            syncManager.pushDailyHealth(newData)
        } else {
            // Cập nhật số bước (Ghi đè số mới nhất)
            healthDao.updateSteps(today, userId, steps, calories)

            // [MỚI]: Đẩy lên Cloud (Tạo bản copy mới nhất để đẩy)
            val updatedData = currentData.copy(steps = steps, caloriesBurned = calories)
            syncManager.pushDailyHealth(updatedData)
        }
    }

    // Hàm "Chốt sổ" cuối ngày: Đẩy lên Health Connect
    suspend fun writeStepsToHealthConnect(start: LocalDateTime, end: LocalDateTime, stepsDelta: Int) {
        // Gọi hàm writeSteps mới đã sửa ở Bước
        healthConnectManager.writeSteps(start, end, stepsDelta)
    }

    suspend fun saveHeartRate(userId: String, bpm: Int) {
        val now = LocalDateTime.now()

        // Ghi vào Health Connect (Source of Truth)
        val success = healthConnectManager.writeHeartRate(bpm, now)
        Log.d("HealthRepository", "Write to Health Connect: $success")

        if (success) {
            // Chỉ lưu giá trị mới nhất vào Room để hiển thị ở Dashboard (Realtime)
            healthDao.updateHeartRate(now.toLocalDate().toString(), userId, bpm)

            // Có thể đẩy lên Cloud nếu muốn (tùy chọn)
            // Nhưng hiện tại hàm pushDailyHealth chưa hỗ trợ field heartRate riêng lẻ update
            // Nếu muốn chuẩn thì lấy full object -> update -> push.
            // Để đơn giản, ta tạm bỏ qua push heart rate realtime liên tục (tốn quota)
        }
    }

    suspend fun saveSleepSession(userId: String, start: LocalDateTime, end: LocalDateTime) {
        // 1. Đẩy lên Health Connect
        healthConnectManager.writeSleepSession(start, end)

        // 2. Tính duration (phút)
        val durationMinutes = Duration.between(start, end).toMinutes()

        // 3. Lưu vào Room (Local DB)
        val today = LocalDate.now().toString()
        // Kiểm tra xem record hôm nay có chưa, nếu chưa thì tạo, có rồi thì update
        val currentData = healthDao.getDailyHealth(today, userId).firstOrNull()

        val finalData = if (currentData == null) {
            DailyHealthEntity(
                date = today,
                userId = userId,
                sleepHours = durationMinutes,
            )
        } else {
            // healthDao.updateSleepDuration(today, userId, durationMinutes) -> Hàm này chỉ update DB
            // Chúng ta cần object để push lên Cloud
            currentData.copy(sleepHours = durationMinutes)
        }

        healthDao.insertOrUpdateDailyHealth(finalData)

        // [MỚI]: Backup giấc ngủ lên Cloud
        syncManager.pushDailyHealth(finalData)
    }

    // Lấy dữ liệu biểu đồ giấc ngủ (Từ Health Connect - Không đổi)
    suspend fun getSleepChartData(range: ChartTimeRange): List<SleepBucket> {
        val today = LocalDate.now()
        val end = today.plusDays(1).atStartOfDay()

        // Xác định thời gian bắt đầu (Start)
        val start = when (range) {
            ChartTimeRange.WEEK -> today.minusDays(6).atStartOfDay() // 7 ngày gần nhất
            ChartTimeRange.MONTH -> today.minusDays(30).atStartOfDay()
            ChartTimeRange.YEAR -> today.minusYears(1).withDayOfMonth(1).atStartOfDay() // Lấy từ tháng này năm ngoái
            else -> today.minusDays(6).atStartOfDay()
        }

        // Xác định lát cắt (Slicer)
        val period = if (range == ChartTimeRange.YEAR) {
            Period.ofMonths(1) // Nếu chọn Năm -> Gom nhóm theo THÁNG
        } else {
            Period.ofDays(1)   // Nếu chọn Tuần/Tháng -> Gom nhóm theo NGÀY
        }

        return healthConnectManager.readSleepChartData(start, end, period)
    }

    // Lấy dữ liệu biểu đồ nhịp tim (Từ Health Connect - Không đổi)
    suspend fun getHeartRateChartData(range: ChartTimeRange): List<HeartRateBucket> {
        val now = LocalDateTime.now()
        val end = now.toLocalDate().plusDays(1).atStartOfDay()
        return when (range) {
            ChartTimeRange.DAY -> {
                val start = LocalDate.now().atStartOfDay()
                healthConnectManager.readHeartRateAggregationByDuration(start, end, Duration.ofHours(1))
            }
            ChartTimeRange.WEEK -> {
                val start = now.minusDays(6)
                healthConnectManager.readHeartRateAggregation(start, end, Period.ofDays(1))
            }
            ChartTimeRange.MONTH -> {
                val start = now.minusDays(30)
                healthConnectManager.readHeartRateAggregation(start, end, Period.ofDays(1))
            }
            ChartTimeRange.YEAR -> {
                val start = now.minusYears(1)
                healthConnectManager.readHeartRateAggregation(start, end, Period.ofMonths(1))
            }
        }
    }

    // Hàm lấy dữ liệu chart bước chân
    suspend fun getStepChartData(range: ChartTimeRange): List<StepBucket> {
        // Lấy dữ liệu gốc từ Health Connect (Thường bị trễ 15p so với thực tế)
        val now = LocalDateTime.now()
        val end = now.toLocalDate().plusDays(1).atStartOfDay()
        val start = when (range) {
            ChartTimeRange.WEEK -> LocalDate.now().minusDays(6).atStartOfDay()
            ChartTimeRange.MONTH -> LocalDate.now().minusDays(30).atStartOfDay()
            ChartTimeRange.YEAR -> LocalDate.now().minusYears(1).withDayOfMonth(1).atStartOfDay()
            else -> LocalDate.now().minusDays(6).atStartOfDay() // Default WEEK
        }

        val period = if (range == ChartTimeRange.YEAR) Period.ofMonths(1) else Period.ofDays(1)

        val hcDataList = healthConnectManager.readStepChartData(start, end, period).toMutableList()

        //"Vá" dữ liệu ngày hôm nay từ Room (Real-time)
        if (range != ChartTimeRange.YEAR) {
            val todayStr = LocalDate.now().toString()

            val currentUserInfo = dataStore.data
                .map { prefs -> prefs[CURRENT_USER_EMAIL_KEY] }
                .flatMapLatest { email ->
                    if (email != null) healthDao.getUserFlowByEmail(email) else flowOf(null)
                }
            val currentUser = currentUserInfo.filterNotNull().first()

            // [SỬA]: userId: String (Default rỗng nếu null, nhưng logic trên đã filterNotNull)
            val userId = currentUser.id

            // Lấy số bước real-time từ Room
            val localTodayData = healthDao.getDailyHealth(todayStr, userId).firstOrNull()
            val realTimeSteps = localTodayData?.steps ?: 0

            // Tìm xem trong list Health Connect đã có bucket của ngày hôm nay chưa
            val todayIndex = hcDataList.indexOfFirst {
                it.startTime.toLocalDate().isEqual(LocalDate.now())
            }

            if (todayIndex != -1) {
                // Trường hợp 1: Đã có bucket hôm nay -> Ghi đè bằng số bước từ Room (lớn hơn hoặc bằng)
                val currentBucket = hcDataList[todayIndex]
                if (realTimeSteps > currentBucket.totalSteps) {
                    hcDataList[todayIndex] = StepBucket(
                        startTime = currentBucket.startTime,
                        endTime = currentBucket.endTime,
                        totalSteps = realTimeSteps.toLong()
                    )
                }
            } else {
                // Trường hợp 2: Health Connect chưa có dữ liệu hôm nay
                if (realTimeSteps > 0) {
                    hcDataList.add(StepBucket(
                        startTime = LocalDate.now().atStartOfDay(),
                        endTime = now,
                        totalSteps = realTimeSteps.toLong()
                    ))
                }
            }
        }

        return hcDataList.sortedBy { it.startTime }
    }

    // Hàm này gọi khi User vừa đăng nhập thành công
    // Nó kéo dữ liệu cũ từ Cloud về Room
    suspend fun initialSync(uid: String) {
        syncManager.pullUserData(uid)
    }
}

enum class ChartTimeRange { DAY, WEEK, MONTH, YEAR}