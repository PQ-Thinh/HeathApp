package com.example.healthapp.core.data

import android.util.Log
import com.example.healthapp.core.model.dao.HealthDao
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.InvitationEntity
import com.example.healthapp.core.model.entity.NotificationEntity
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

    // 1. Kéo dữ liệu khi đăng nhập
    suspend fun pullUserData(uid: String) {
        try {
            // A. Profile
            val userDoc = firestore.collection("users").document(uid).get().await()
            val user = userDoc.toObject(UserEntity::class.java)
            if (user != null) {
                healthDao.saveUser(user.copy(id = uid))
            }

            // B. Sức khỏe
            val healthDocs = firestore.collection("users").document(uid)
                .collection("daily_health").get().await()
            val listHealth = healthDocs.toObjects(DailyHealthEntity::class.java)
            listHealth.forEach { healthDao.insertOrUpdateDailyHealth(it) }

            // [MỚI] C. Kéo Thông báo cũ về
            val notifDocs = firestore.collection("users").document(uid)
                .collection("notifications").get().await()
            val listNotifs = notifDocs.toObjects(NotificationEntity::class.java)
            listNotifs.forEach {
                // Khi kéo về, ta để Room tự sinh ID mới (id = null) để tránh trùng lặp Int
                healthDao.insertNotification(it.copy(id = null))
            }

            Log.d("Sync", "Đã kéo dữ liệu User, Sức khỏe và Thông báo về máy.")
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi pullUserData: ${e.message}")
        }
    }

    // 2. Đẩy dữ liệu Profile
    suspend fun pushUserProfile(user: UserEntity) {
        try {
            firestore.collection("users").document(user.id)
                .set(user, SetOptions.merge())
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi pushUserProfile: ${e.message}")
        }
    }

    // 3. Đẩy dữ liệu Sức khỏe
    suspend fun pushDailyHealth(data: DailyHealthEntity) {
        try {
            firestore.collection("users").document(data.userId)
                .collection("daily_health").document(data.date)
                .set(data, SetOptions.merge())
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi pushDailyHealth: ${e.message}")
        }
    }

    // [MỚI] 4. Đẩy Thông báo lên Cloud (Backup)
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

    // 5. Gửi lời mời kết bạn
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

                        // [SỬA LỖI]: Dùng Dispatchers.IO thay vì coroutineContext (gây crash)
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

                                // 2. Lưu Local
                                healthDao.insertNotification(notif)

                                // [MỚI] 3. Đẩy lên Cloud ngay lập tức để backup
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