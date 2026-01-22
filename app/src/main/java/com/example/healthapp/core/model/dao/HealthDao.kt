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

    // 2. Hàm lấy user mặc định (cho Dashboard/Profile)
    @Query("SELECT * FROM users LIMIT 1")
    fun getUser(): Flow<UserEntity?>

    // 3. (MỚI) Hàm lấy user theo Email -> Dùng cho LOGIN
    // Giúp kiểm tra xem email và pass có đúng không
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    // 4. (MỚI) Hàm đổi mật khẩu -> Dùng cho Settings
    @Query("UPDATE users SET password = :newPassword WHERE id = 1")
    suspend fun updatePassword(newPassword: String)

    // 5. Cập nhật mục tiêu bước chân
    @Query("UPDATE users SET target_steps = :newTarget WHERE id = 1")
    suspend fun updateTargetSteps(newTarget: Int)

    @Query("UPDATE users SET name = :newName WHERE id = 1")
    suspend fun updateName(newName: String)

    // 6. Cập nhật chỉ số cơ thể
    @Query("UPDATE users SET height = :height WHERE id = 1")
    suspend fun updateHeight(height: Int)

    @Query("UPDATE users SET  weight = :weight WHERE id = 1")
    suspend fun updateWeight( weight: Float)

    // --- DAILY HEALTH DATA (Giữ nguyên) ---
    @Query("SELECT * FROM daily_health WHERE date = :date AND userId = :userId")
    fun getDailyHealth(date: String, userId: Int): Flow<DailyHealthEntity?>

    @Query("SELECT * FROM daily_health ORDER BY date DESC LIMIT 7")
    fun getLast7DaysHealth(): Flow<List<DailyHealthEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDailyHealth(data: DailyHealthEntity)

    @Query("UPDATE daily_health SET steps = steps + :stepsToAdd, calories_burned = calories_burned + :calories WHERE date = :date")
    suspend fun incrementSteps(date: String, stepsToAdd: Int, calories: Float)

    @Query("UPDATE daily_health SET heart_rate_avg = :bpm WHERE date = :date")
    suspend fun updateHeartRate(date: String, bpm: Int)

    // --- NOTIFICATIONS (Giữ nguyên) ---
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