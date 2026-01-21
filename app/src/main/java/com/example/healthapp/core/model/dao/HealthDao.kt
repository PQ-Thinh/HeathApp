package com.example.healthapp.core.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.NotificationEntity
import com.example.healthapp.core.model.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    // --- USER PROFILE ---
    @Query("SELECT * FROM users")
    fun getUser(): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity)

    // Cập nhật mục tiêu bước chân riêng biệt
    @Query("UPDATE users SET target_steps = :newTarget WHERE id = 1")
    suspend fun updateTargetSteps(newTarget: Int)

    // Cập nhật chiều cao/cân nặng (khi user chỉnh sửa profile)
    @Query("UPDATE users SET height = :height, weight = :weight WHERE id = 1")
    suspend fun updateBodyMetrics(height: Float, weight: Float)

    // --- DAILY HEALTH DATA ---
    @Query("SELECT * FROM daily_health WHERE date = :date")
    fun getDailyHealth(date: String): Flow<DailyHealthEntity?>

    @Query("SELECT * FROM daily_health ORDER BY date DESC LIMIT 7")
    fun getLast7DaysHealth(): Flow<List<DailyHealthEntity>> // Dùng cho biểu đồ tuần

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDailyHealth(data: DailyHealthEntity)

    // Nếu chưa có record ngày hôm nay, bạn cần insert trước rồi mới gọi hàm này
    @Query("UPDATE daily_health SET steps = steps + :stepsToAdd, calories_burned = calories_burned + :calories WHERE date = :date")
    suspend fun incrementSteps(date: String, stepsToAdd: Int, calories: Float)

    @Query("UPDATE daily_health SET heart_rate_avg = :bpm WHERE date = :date")
    suspend fun updateHeartRate(date: String, bpm: Int)

    // --- NOTIFICATIONS ---
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE is_read = 0")
    fun getUnreadCount(): Flow<Int> // Hiển thị chấm đỏ trên chuông

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("UPDATE notifications SET is_read = 1 WHERE id = :id")
    suspend fun markNotificationAsRead(id: Int)

    @Query("UPDATE notifications SET is_read = 1")
    suspend fun markAllAsRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotification(id: Int)

    @Query("DELETE FROM notifications")
    suspend fun clearAllNotifications()
}