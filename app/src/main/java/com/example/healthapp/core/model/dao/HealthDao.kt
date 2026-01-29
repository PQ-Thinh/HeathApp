package com.example.healthapp.core.model.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.InvitationEntity
import com.example.healthapp.core.model.entity.NotificationEntity
import com.example.healthapp.core.model.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthDao {
    // --- USER PROFILE ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserEntity)

    // Lấy user mặc định
    @Query("SELECT * FROM users LIMIT 1")
    fun getUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    fun getUserFlowByEmail(email: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    // [SỬA]: Int -> String
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?



    @Query("UPDATE users SET target_steps = :newTarget WHERE id = :userId")
    suspend fun updateTargetSteps(userId: String, newTarget: Int)

    @Query("UPDATE users SET name = :newName WHERE id = :userId")
    suspend fun updateName(userId: String, newName: String)

    @Query("UPDATE users SET bmi = :newBMI WHERE id = :userId")
    suspend fun updateBMI(userId: String, newBMI: Float)

    // [SỬA]: Int? -> String
    @Query("UPDATE users SET height = :height WHERE id = :userId")
    suspend fun updateHeight(userId: String, height: Float)

    // [SỬA]: Int? -> String
    @Query("UPDATE users SET weight = :weight WHERE id = :userId")
    suspend fun updateWeight(userId: String, weight: Float)


    // --- DAILY HEALTH DATA ---

    @Query("SELECT * FROM daily_health WHERE date = :date AND userId = :userId")
    fun getDailyHealth(date: String, userId: String): Flow<DailyHealthEntity?>

    @Query("SELECT * FROM daily_health WHERE userId = :userId ORDER BY date DESC LIMIT 7")
    fun getLast7DaysHealth(userId: String): Flow<List<DailyHealthEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDailyHealth(data: DailyHealthEntity)

    @Query("UPDATE daily_health SET steps = :steps, calories_burned = :calories WHERE date = :date AND userId = :userId")
    suspend fun updateSteps(date: String, userId: String, steps: Int, calories: Float)

    // [SỬA]: Int -> String
    @Query("UPDATE daily_health SET steps = steps + :stepsToAdd, calories_burned = calories_burned + :calories WHERE date = :date AND userId = :userId")
    suspend fun incrementSteps(date: String, userId: String, stepsToAdd: Int, calories: Float)

    // [SỬA]: Int -> String
    @Query("UPDATE daily_health SET sleep_hours = :duration WHERE date = :date AND userId = :userId")
    suspend fun updateSleepDuration(date: String, userId: String, duration: Long)

    // [SỬA]: Int -> String
    @Query("UPDATE daily_health SET heart_rate_avg = :bpm WHERE date = :date AND userId = :userId")
    suspend fun updateHeartRate(date: String, userId: String, bpm: Int)


    // --- NOTIFICATIONS (Giữ nguyên ID là Int vì nó là Local AutoIncrement) ---

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

    // --- INVITATIONS (Mới thêm cho tính năng mời bạn bè) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvitation(invite: InvitationEntity)

    @Query("SELECT * FROM invitations WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingInvitations(): Flow<List<InvitationEntity>>

    @Query("UPDATE invitations SET status = :status WHERE id = :id")
    suspend fun updateInvitationStatus(id: String, status: String)
}