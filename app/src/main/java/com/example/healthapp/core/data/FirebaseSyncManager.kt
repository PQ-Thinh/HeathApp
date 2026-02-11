package com.example.healthapp.core.data

import android.util.Log
import com.example.healthapp.core.model.entity.InvitationEntity
import com.example.healthapp.core.model.entity.NotificationEntity
import com.example.healthapp.core.model.entity.UserEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private var invitationListener: ListenerRegistration? = null
    private var sentInvitationListener: ListenerRegistration? = null

        // 1. Lấy danh sách tất cả user (Trừ bản thân)
        suspend fun getAllUsers(currentUid: String): List<UserEntity> {
            return try {
                val snapshot = firestore.collection("users").get().await()
                // Map sang UserEntity và lọc bỏ chính mình
                snapshot.toObjects(UserEntity::class.java).filter { it.id != currentUid }
            } catch (e: Exception) {
                Log.e("Sync", "Lỗi lấy danh sách user: ${e.message}")
                emptyList()
            }
        }

        //Gửi lời mời (Kèm targetSteps)
        suspend fun sendInvitation(
            senderUid: String,
            senderName: String,
            receiverId: String,
            targetSteps: Int
        ): Boolean {
            return try {
                // Tạo ID cho lời mời
                val newInviteRef = firestore.collection("invitations").document()

                val invitation = InvitationEntity(
                    id = newInviteRef.id,
                    senderId = senderUid,
                    senderName = senderName,
                    receiverId = receiverId,
                    status = "PENDING",
                    targetSteps = targetSteps, // Gửi kèm mục tiêu
                    timestamp = System.currentTimeMillis()
                )

                newInviteRef.set(invitation).await()
                true
            } catch (e: Exception) {
                Log.e("Sync", "Lỗi gửi lời mời: ${e.message}")
                false
            }
        }

        //Phản hồi lời mời (Accept/Reject)
        // Nếu Accept -> Cập nhật targetSteps của người nhận (currentUid) thành targetSteps của lời mời
        suspend fun respondToInvitation(
            inviteId: String,
            currentUid: String,
            isAccepted: Boolean,
            targetSteps: Int
        ) {
            val newStatus = if (isAccepted) "ACCEPTED" else "REJECTED"
            try {
                // B1: Cập nhật trạng thái lời mời
                firestore.collection("invitations").document(inviteId)
                    .update("status", newStatus)
                    .await()

                // B2: Nếu chấp nhận -> Cập nhật Target Steps cho ngày hôm nay của người nhận
                if (isAccepted && targetSteps > 0) {
                    val todayStr = LocalDate.now().toString()
                    val updateData = mapOf("targetSteps" to targetSteps)

                    firestore.collection("users").document(currentUid)
                        .collection("daily_health").document(todayStr)
                        .set(
                            updateData,
                            SetOptions.merge()
                        ) // Merge để không mất dữ liệu bước chân đã đi
                        .await()

                    Log.d("Sync", "Đã cập nhật Target Steps mới: $targetSteps")
                }
            } catch (e: Exception) {
                Log.e("Sync", "Lỗi phản hồi lời mời: ${e.message}")
            }
        }

        //LẮNG NGHE LỜI MỜI ĐẾN (Để hiển thị cho người nhận)
        fun startListeningForIncomingInvitations(
            currentUid: String,
            onIncomingInviteUpdate: (List<InvitationEntity>) -> Unit
        ) {
            invitationListener?.remove()
            invitationListener = firestore.collection("invitations")
                .whereEqualTo("receiverId", currentUid)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener
                    val list = snapshots?.toObjects(InvitationEntity::class.java) ?: emptyList()
                    onIncomingInviteUpdate(list)
                }
        }

        //LẮNG NGHE LỜI MỜI ĐÃ GỬI (Để biết họ từ chối hay chấp nhận)
        fun startListeningForSentInvitations(
            currentUid: String,
            onStatusChange: (InvitationEntity) -> Unit
        ) {
            sentInvitationListener?.remove()
            sentInvitationListener = firestore.collection("invitations")
                .whereEqualTo("senderId", currentUid)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener

                    // Kiểm tra các thay đổi (chỉ quan tâm MODIFIED)
                    snapshots?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                            val invite = change.document.toObject(InvitationEntity::class.java)
                            // Chỉ báo callback nếu trạng thái không phải PENDING (tức là đã được rep)
                            if (invite.status != "PENDING") {
                                onStatusChange(invite)
                            }
                        }
                    }
                }
        }

        fun stopListening() {
            invitationListener?.remove()
            sentInvitationListener?.remove()
        }


        suspend fun pushNotification(
            userId: String,
            title: String,
            message: String,
            type: String,
            relatedId: String?
        ) {
            try {
                val notif = NotificationEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    title = title,
                    message = message,
                    type = type,
                    relatedId = relatedId ?: ""
                )

                firestore.collection("users").document(userId)
                    .collection("notifications")
                    .add(notif) // Dùng .add() để Firebase tự sinh ID document
            } catch (e: Exception) {
                Log.e("Sync", "Lỗi pushNotification: ${e.message}")
            }
        }

        // --- LẮNG NGHE LỜI MỜI (Realtime) ---
        // Hàm này sẽ trả về Callback để ViewModel xử lý việc hiển thị thông báo
        fun startListeningForInvitations(
            currentUid: String,
            onNewInvite: (InvitationEntity) -> Unit
        ) {
            invitationListener?.remove()

            invitationListener = firestore.collection("invitations")
                .whereEqualTo("receiverId", currentUid)
                .whereEqualTo("status", "PENDING") // Chỉ nghe lời mời mới
                .addSnapshotListener { snapshots, e ->
                    if (e != null) return@addSnapshotListener

                    snapshots?.documentChanges?.forEach { docChange ->
                        // Chỉ xử lý khi có lời mời MỚI ĐƯỢC THÊM VÀO
                        if (docChange.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val invite = docChange.document.toObject(InvitationEntity::class.java)
                            // Gọi callback để bên ngoài xử lý (Hiển thị UI/Notif)
                            onNewInvite(invite)
                        }
                    }
                }
        }

    }