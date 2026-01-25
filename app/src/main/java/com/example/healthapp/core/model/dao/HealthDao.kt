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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity)

    // Lấy user mặc định (Cẩn thận: Hàm này chỉ nên dùng khi chắc chắn chỉ có 1 user)
    // Tốt nhất nên dùng getUserByEmail hoặc getUserById
    @Query("SELECT * FROM users LIMIT 1")
    fun getUser(): Flow<UserEntity?>
    // Hàm này trả về Flow (Live Data), khác với hàm suspend (chỉ trả về 1 lần)
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun getUserFlowByEmail(email: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: Int): UserEntity?

    // --- CÁC HÀM UPDATE (Đã sửa: Thêm userId) ---

    @Query("UPDATE users SET password = :newPassword WHERE id = :userId")
    suspend fun updatePassword(userId: Int, newPassword: String)

    @Query("UPDATE users SET target_steps = :newTarget WHERE id = :userId")
    suspend fun updateTargetSteps(userId: Int, newTarget: Int)

    @Query("UPDATE users SET name = :newName WHERE id = :userId")
    suspend fun updateName(userId: Int?, newName: String)

    @Query("UPDATE users SET bmi = :newBMI WHERE id = :userId")
    suspend fun updateBMI(userId: Int, newBMI: Float)

    // Lưu ý: Height/Weight trong Entity thường là Float, nên để Float cho đồng bộ
    @Query("UPDATE users SET height = :height WHERE id = :userId")
    suspend fun updateHeight(userId: Int?, height: Float)

    @Query("UPDATE users SET weight = :weight WHERE id = :userId")
    suspend fun updateWeight(userId: Int?, weight: Float)


    // --- DAILY HEALTH DATA ---

    @Query("SELECT * FROM daily_health WHERE date = :date AND userId = :userId")
    fun getDailyHealth(date: String, userId: Int?): Flow<DailyHealthEntity?>

    // Sửa lỗi: Chỉ lấy 7 ngày CỦA USER ĐÓ (thêm WHERE userId)
    @Query("SELECT * FROM daily_health WHERE userId = :userId ORDER BY date DESC LIMIT 7")
    fun getLast7DaysHealth(userId: Int): Flow<List<DailyHealthEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDailyHealth(data: DailyHealthEntity)

    @Query("UPDATE daily_health SET steps = :steps, calories_burned = :calories WHERE date = :date AND userId = :userId")
    suspend fun updateSteps(date: String, userId: Int?, steps: Int, calories: Float)

    // Sửa lỗi: Thêm userId để cộng dồn đúng người
    @Query("UPDATE daily_health SET steps = steps + :stepsToAdd, calories_burned = calories_burned + :calories WHERE date = :date AND userId = :userId")
    suspend fun incrementSteps(date: String, userId: Int, stepsToAdd: Int, calories: Float)

    @Query("UPDATE daily_health SET sleep_hours = :duration WHERE date = :date AND userId = :userId")
    suspend fun updateSleepDuration(date: String, userId: Int, duration: Long)

    // Sửa lỗi: Thêm userId
    @Query("UPDATE daily_health SET heart_rate_avg = :bpm WHERE date = :date AND userId = :userId")
    suspend fun updateHeartRate(date: String, userId: Int, bpm: Int)


    // --- NOTIFICATIONS (Giữ nguyên hoặc thêm userId nếu muốn tách thông báo riêng) ---
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE is_read = 0")
    fun getUnreadCount(): Flow<Int>

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