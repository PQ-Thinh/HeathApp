package com.example.healthapp.core.data

import android.util.Log
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.HeartRateRecordEntity
import com.example.healthapp.core.model.entity.InvitationEntity
import com.example.healthapp.core.model.entity.NotificationEntity
import com.example.healthapp.core.model.entity.SleepSessionEntity
import com.example.healthapp.core.model.entity.StepRecordEntity
import com.example.healthapp.core.model.entity.UserEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncManager @Inject constructor(
    private val healthDao: HealthDao,
    private val firestore: FirebaseFirestore
) {
    private var invitationListener: ListenerRegistration? = null

    //Kéo dữ liệu khi đăng nhập
    suspend fun pullUserData(uid: String) {
        try {
            // A. Profile
            val userDoc = firestore.collection("users").document(uid).get().await()
            val user = userDoc.toObject(UserEntity::class.java)
            if (user != null) {
                healthDao.saveUser(user.copy(id = uid))
            }

            // Sức khỏe
            val healthDocs = firestore.collection("users").document(uid)
                .collection("daily_health").get().await()
            val listHealth = healthDocs.toObjects(DailyHealthEntity::class.java)
            listHealth.forEach { healthDao.insertOrUpdateDailyHealth(it) }

            // Kéo Thông báo cũ về
            val notifDocs = firestore.collection("users").document(uid)
                .collection("notifications").get().await()
            val listNotifs = notifDocs.toObjects(NotificationEntity::class.java)
            listNotifs.forEach {
                // Khi kéo về, ta để Room tự sinh ID mới (id = null) để tránh trùng lặp Int
                healthDao.insertNotification(it.copy(id = null))
            }
            // Kéo Sleep Sessions (Để vẽ biểu đồ ngủ)
            val sleepDocs = firestore.collection("users").document(uid)
                .collection("sleep_sessions").get().await()
            val sleepSessions = sleepDocs.toObjects(SleepSessionEntity::class.java)
            healthDao.insertSleepSessions(sleepSessions)

            // Kéo Step Sessions (Để vẽ biểu bước chân)
            val stepDocs = firestore.collection("users").document(uid)
                .collection("step_records").get().await()
            val stepSessions = stepDocs.toObjects(StepRecordEntity::class.java)
            healthDao.insertStepRecords(stepSessions)

            // Kéo  heart_rate_records (Để vẽ biểu đồ nhịp tim)
            val heartRateDocs = firestore.collection("users").document(uid)
                .collection("heart_rate_records").get().await()
            val heartRateSessions = heartRateDocs.toObjects(HeartRateRecordEntity::class.java)
            healthDao.insertHeartRateRecords(heartRateSessions)

            // Lấy các lời mời mà mình là người nhận
            val receivedInvites = firestore.collection("invitations")
                .whereEqualTo("receiverId", uid)
                .get().await()
                .toObjects(InvitationEntity::class.java)

            // Lấy các lời mời mà mình là người gửi (nếu muốn hiển thị lịch sử gửi)
            val sentInvites = firestore.collection("invitations")
                .whereEqualTo("senderId", uid)
                .get().await()
                .toObjects(InvitationEntity::class.java)

            // Gộp lại và lưu vào Room (Dùng Set để tránh trùng nếu lỡ có)
            val allInvites = (receivedInvites + sentInvites).distinctBy { it.id }

            if (allInvites.isNotEmpty()) {
                healthDao.insertListInvitation(allInvites)
                Log.d("Sync", "Đã sync ${allInvites.size} lời mời")
            }

            Log.d("Sync", "Đã kéo dữ liệu User, Sức khỏe và Thông báo về máy.")
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi pullUserData: ${e.message}")
        }
    }

    // Đẩy dữ liệu Profile
    suspend fun pushUserProfile(user: UserEntity) {
        try {
            firestore.collection("users").document(user.id)
                .set(user, SetOptions.merge())
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi pushUserProfile: ${e.message}")
        }
    }

    // Đẩy dữ liệu Sức khỏe
    //Đẩy dữ liệu sức khỏe với cơ chế "Max Steps Wins"
    // Ngăn chặn việc máy mới (ít bước) ghi đè lên máy cũ (nhiều bước)
    suspend fun pushDailyHealth(data: DailyHealthEntity) {
        val docRef = firestore.collection("users").document(data.userId)
            .collection("daily_health").document(data.date)

        try {
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)

                if (snapshot.exists()) {
                    //Nếu trên Cloud đã có dữ liệu -> So sánh để lấy số lớn nhất
                    val cloudSteps = snapshot.getLong("steps")?.toInt() ?: 0
                    val cloudCal = snapshot.getDouble("calories_burned")?.toFloat() ?: 0f
                    val cloudSleep = snapshot.getLong("sleep_hours") ?: 0

                    // Logic Merge:
                    // - Steps/Calo: Lấy số lớn nhất (Chống ghi đè)
                    // - HeartRate/Sleep: Có thể lấy cái mới nhất (từ data gửi lên) hoặc lớn nhất tùy logic.
                    // Ở đây Sleep ta cũng lấy lớn nhất để an toàn.

                    val mergedSteps = maxOf(cloudSteps, data.steps)
                    val mergedCal = maxOf(cloudCal, data.caloriesBurned)
                    val mergedSleep = maxOf(cloudSleep, data.sleepHours)

                    // Chỉ update nếu dữ liệu Local tốt hơn hoặc bằng Cloud
                    // (Hoặc cập nhật các trường khác như heart_rate nếu cần)
                    transaction.update(docRef, mapOf(
                        "steps" to mergedSteps,
                        "calories_burned" to mergedCal,
                        "sleep_hours" to mergedSleep,
                        "heart_rate_avg" to data.heartRateAvg // Nhịp tim thì cứ lấy cái mới nhất gửi lên
                    ))
                } else {
                    //Nếu Cloud chưa có -> Tạo mới hoàn toàn
                    transaction.set(docRef, data)
                }
            }.await()


            Log.d("Sync", "Pushed DailyHealth: ${data.date} | Steps: ${data.steps}")

        } catch (e: Exception) {
            Log.e("Sync", "Lỗi Transaction pushDailyHealth: ${e.message}")
        }
    }
    // Hàm đẩy 1 bản ghi Sleep Session (hoặc Stage) lên Firebase
    suspend fun pushSleepSession(sleepSession: SleepSessionEntity) {
        try {
            // Lưu vào: users/{uid}/sleep_sessions/{id_random}
            firestore.collection("users").document(sleepSession.userId)
                .collection("sleep_sessions")
                .document(sleepSession.id) // Dùng ID của entity làm ID document
                .set(sleepSession, SetOptions.merge())

            Log.d("Sync", "Đã đẩy SleepSession: ${sleepSession.type} - ${sleepSession.startTime}")
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi pushSleepSession: ${e.message}")
        }
    }

    // Đẩy Thông báo lên Cloud (Backup)
    suspend fun pushNotification(uid: String, notification: NotificationEntity) {
        try {
            // Lưu vào sub-collection "notifications" của user đó
            // Dùng Auto-ID cho document
            firestore.collection("users").document(uid)
                .collection("notifications").add(notification)
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi pushNotification: ${e.message}")
        }
    }

    //  Gửi lời mời kết bạn
    suspend fun sendInvitation(senderUid: String, senderName: String, receiverEmail: String): Boolean {
        return try {
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("email", receiverEmail)
                .get().await()

            if (querySnapshot.isEmpty) return false

            val receiverDoc = querySnapshot.documents[0]
            val receiverUid = receiverDoc.id

            if (receiverUid == senderUid) return false

            val inviteId = firestore.collection("invitations").document().id
            val invitation = hashMapOf(
                "id" to inviteId,
                "senderId" to senderUid,
                "senderName" to senderName,
                "receiverId" to receiverUid,
                "status" to "PENDING",
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("invitations").document(inviteId).set(invitation).await()
            true
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi gửi lời mời: ${e.message}")
            false
        }
    }

    // 6. Lắng nghe lời mời ĐẾN
    fun startListeningForInvitations(currentUid: String) {
        invitationListener?.remove()

        invitationListener = firestore.collection("invitations")
            .whereEqualTo("receiverId", currentUid)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("Sync", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    for (doc in snapshots.documentChanges) {
                        val inviteId = doc.document.getString("id") ?: ""
                        val senderName = doc.document.getString("senderName") ?: "Unknown"
                        val senderId = doc.document.getString("senderId") ?: ""
                        val status = doc.document.getString("status") ?: "PENDING"
                        val timestamp = doc.document.getLong("timestamp") ?: 0L

                        val entity = InvitationEntity(inviteId, senderId, senderName, currentUid, status, timestamp)

                        //Dùng Dispatchers.IO thay vì coroutineContext (gây crash)
                        CoroutineScope(Dispatchers.IO).launch {
                            healthDao.insertInvitation(entity)

                            if (status == "PENDING") {
                                // 1. Tạo entity
                                val notif = NotificationEntity(
                                    title = "Lời mời kết bạn mới",
                                    message = "$senderName muốn kết nối với bạn.",
                                    type = "INVITE_REQUEST",
                                    relatedId = inviteId
                                )

                                //Lưu Local
                                healthDao.insertNotification(notif)

                                // Đẩy lên Cloud ngay lập tức để backup
                                pushNotification(currentUid, notif)
                            }
                        }
                    }
                }
            }
    }

    // 7. Phản hồi lời mời
    suspend fun respondToInvitation(inviteId: String, isAccepted: Boolean) {
        val newStatus = if (isAccepted) "ACCEPTED" else "REJECTED"
        firestore.collection("invitations").document(inviteId)
            .update("status", newStatus).await()
        healthDao.updateInvitationStatus(inviteId, newStatus)
    }

    fun stopListening() {
        invitationListener?.remove()
    }
}