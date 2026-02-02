package com.example.healthapp.core.data

import android.util.Log
import com.example.healthapp.core.model.entity.InvitationEntity
import com.example.healthapp.core.model.entity.NotificationEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseSyncManager @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private var invitationListener: ListenerRegistration? = null

    //Gửi lời mời kết bạn
    suspend fun sendInvitation(senderUid: String, senderName: String, receiverEmail: String): Boolean {
        return try {
            // Tìm user nhận
            val querySnapshot = firestore.collection("users")
                .whereEqualTo("email", receiverEmail)
                .get().await()

            if (querySnapshot.isEmpty) return false

            val receiverDoc = querySnapshot.documents[0]
            val receiverUid = receiverDoc.id

            if (receiverUid == senderUid) return false // Không tự mời mình

            // Tạo ID trên Cloud
            val newInviteRef = firestore.collection("invitations").document()
            val inviteId = newInviteRef.id // Lấy Auto-ID

            val invitation = InvitationEntity(
                id = inviteId,
                senderId = senderUid,
                senderName = senderName,
                receiverId = receiverUid,
                status = "PENDING",
                timestamp = System.currentTimeMillis()
            )

            //Lưu
            newInviteRef.set(invitation).await()
            true
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi gửi lời mời: ${e.message}")
            false
        }
    }

    // Phản hồi lời mời (Accept/Reject)
    suspend fun respondToInvitation(inviteId: String, isAccepted: Boolean) {
        val newStatus = if (isAccepted) "ACCEPTED" else "REJECTED"
        try {
            firestore.collection("invitations").document(inviteId)
                .update("status", newStatus)
                .await()
        } catch (e: Exception) {
            Log.e("Sync", "Lỗi phản hồi lời mời: ${e.message}")
        }
    }

    // Helper: Tạo thông báo mới lên Cloud
    suspend fun pushNotification(userId: String, title: String, message: String, type: String, relatedId: String?) {
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

    fun stopListening() {
        invitationListener?.remove()
    }
}