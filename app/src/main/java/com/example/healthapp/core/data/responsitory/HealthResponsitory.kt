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
    private val syncManager: FirebaseSyncManager
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

        // đồng bộ cả Nhịp tim từ health
        // val hcHeartRate = healthConnectManager.readHeartRate(startOfDay, now)

        // Lấy dữ liệu Local hiện tại
        val currentLocalData = healthDao.getDailyHealth(todayStr, userId).firstOrNull()
        val currentLocalSteps = currentLocalData?.steps ?: 0


        // Chỉ cập nhật nếu Health Connect có nhiều bước hơn (hoặc cập nhật trường khác nếu cần)
        if (hcSteps > currentLocalSteps) {

            // dùng copy() từ dữ liệu cũ để giữ lại heartRate/sleep
            val entityToSave = if (currentLocalData == null) {
                // Nếu chưa có dòng nào -> Tạo mới
                DailyHealthEntity(
                    date = todayStr,
                    userId = userId,
                    steps = hcSteps
                )
            } else {
                // Nếu đã có -> Copy và chỉ sửa steps
                currentLocalData.copy(steps = hcSteps)
            }

            healthDao.insertOrUpdateDailyHealth(entityToSave)
            Log.d("HealthRepo", "Synced HC Steps: $hcSteps (Preserved Heart/Sleep)")

            // Đẩy lên Cloud
            syncManager.pushDailyHealth(entityToSave)
        }
    }

    suspend fun syncUserToCloud(user: UserEntity) {
        syncManager.pushUserProfile(user)
    }

    suspend fun updateLocalSteps(userId: String?, steps: Int, calories: Float) {
        if (userId.isNullOrEmpty()) return

        val today = LocalDate.now().toString()
        val currentData = healthDao.getDailyHealth(today, userId).firstOrNull()

        if (currentData == null) {
            val newData = DailyHealthEntity(
                date = today,
                userId = userId,
                steps = steps,
                caloriesBurned = calories
            )
            healthDao.insertOrUpdateDailyHealth(newData)
            syncManager.pushDailyHealth(newData)
        } else {
            // Cách 1: Dùng Update Query (nhanh gọn)
            healthDao.updateSteps(today, userId, steps, calories)

            // Cách 2: Copy object để push lên Cloud đầy đủ
            val updatedData = currentData.copy(steps = steps, caloriesBurned = calories)
            syncManager.pushDailyHealth(updatedData)
        }
    }

    suspend fun writeStepsToHealthConnect(start: LocalDateTime, end: LocalDateTime, stepsDelta: Int) {
        healthConnectManager.writeSteps(start, end, stepsDelta)
    }

    suspend fun saveHeartRate(userId: String, bpm: Int) {
        val now = LocalDateTime.now()
        val todayStr = now.toLocalDate().toString()

        // Ghi vào Health Connect
        val success = healthConnectManager.writeHeartRate(bpm, now)

        if (success) {
            // Lưu vào Room
            // Phải kiểm tra xem bản ghi đã tồn tại chưa.
            // Nếu chưa có (VD: mới cài app, chưa đi bước nào) thì phải Insert mới.
            val currentData = healthDao.getDailyHealth(todayStr, userId).firstOrNull()

            if (currentData == null) {
                val newData = DailyHealthEntity(
                    date = todayStr,
                    userId = userId,
                    heartRateAvg = bpm
                )
                healthDao.insertOrUpdateDailyHealth(newData)
            } else {
                // Nếu đã có, update trường heart_rate
                healthDao.updateHeartRate(todayStr, userId, bpm)
            }
        }
    }

    suspend fun saveSleepSession(userId: String, start: LocalDateTime, end: LocalDateTime) {
        healthConnectManager.writeSleepSession(start, end)

        val durationMinutes = Duration.between(start, end).toMinutes()
        val today = LocalDate.now().toString()

        val currentData = healthDao.getDailyHealth(today, userId).firstOrNull()

        // Logic này của bạn đã đúng (dùng copy), nhưng kiểm tra lại cho chắc
        val finalData = if (currentData == null) {
            DailyHealthEntity(
                date = today,
                userId = userId,
                sleepHours = durationMinutes,
            )
        } else {
            currentData.copy(sleepHours = durationMinutes)
        }

        healthDao.insertOrUpdateDailyHealth(finalData)
        syncManager.pushDailyHealth(finalData)
    }

    suspend fun getSleepChartData(range: ChartTimeRange): List<SleepBucket> {
        val today = LocalDate.now()
        val end = today.plusDays(1).atStartOfDay()
        val start = when (range) {
            ChartTimeRange.WEEK -> today.minusDays(6).atStartOfDay()
            ChartTimeRange.MONTH -> today.minusDays(30).atStartOfDay()
            ChartTimeRange.YEAR -> today.minusYears(1).withDayOfMonth(1).atStartOfDay()
            else -> today.minusDays(6).atStartOfDay()
        }
        val period = if (range == ChartTimeRange.YEAR) Period.ofMonths(1) else Period.ofDays(1)
        return healthConnectManager.readSleepChartData(start, end, period)
    }

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

    suspend fun getStepChartData(range: ChartTimeRange): List<StepBucket> {
        val now = LocalDateTime.now()
        val end = now.toLocalDate().plusDays(1).atStartOfDay()
        val start = when (range) {
            ChartTimeRange.WEEK -> LocalDate.now().minusDays(6).atStartOfDay()
            ChartTimeRange.MONTH -> LocalDate.now().minusDays(30).atStartOfDay()
            ChartTimeRange.YEAR -> LocalDate.now().minusYears(1).withDayOfMonth(1).atStartOfDay()
            else -> LocalDate.now().minusDays(6).atStartOfDay()
        }
        val period = if (range == ChartTimeRange.YEAR) Period.ofMonths(1) else Period.ofDays(1)
        val hcDataList = healthConnectManager.readStepChartData(start, end, period).toMutableList()

        if (range != ChartTimeRange.YEAR) {
            val todayStr = LocalDate.now().toString()
            val currentUserInfo = dataStore.data
                .map { prefs -> prefs[CURRENT_USER_EMAIL_KEY] }
                .flatMapLatest { email ->
                    if (email != null) healthDao.getUserFlowByEmail(email) else flowOf(null)
                }
            val currentUser = currentUserInfo.filterNotNull().first()
            val userId = currentUser.id

            val localTodayData = healthDao.getDailyHealth(todayStr, userId).firstOrNull()
            val realTimeSteps = localTodayData?.steps ?: 0

            val todayIndex = hcDataList.indexOfFirst {
                it.startTime.toLocalDate().isEqual(LocalDate.now())
            }

            if (todayIndex != -1) {
                val currentBucket = hcDataList[todayIndex]
                if (realTimeSteps > currentBucket.totalSteps) {
                    hcDataList[todayIndex] = StepBucket(
                        startTime = currentBucket.startTime,
                        endTime = currentBucket.endTime,
                        totalSteps = realTimeSteps.toLong()
                    )
                }
            } else {
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

    suspend fun initialSync(uid: String) {
        syncManager.pullUserData(uid)
    }
}

enum class ChartTimeRange { DAY, WEEK, MONTH, YEAR}