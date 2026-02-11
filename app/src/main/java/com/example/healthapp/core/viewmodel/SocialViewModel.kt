package com.example.healthapp.core.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.entity.InvitationEntity
import com.example.healthapp.core.model.entity.UserEntity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    // Danh sách tất cả user (để tìm và gửi lời mời)
    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users = _users.asStateFlow()

    // Danh sách lời mời ĐẾN (mình nhận được)
    private val _incomingInvitations = MutableStateFlow<List<InvitationEntity>>(emptyList())
    val incomingInvitations = _incomingInvitations.asStateFlow()

    private val currentUserId = auth.currentUser?.uid

    init {
        loadUsers()
        startListening()
    }

    // Lấy danh sách user để hiển thị trong dialog "Mời bạn bè"
    fun loadUsers() {
        if (currentUserId == null) return
        viewModelScope.launch {
            _users.value = repository.getAllUsers()
        }
    }

    // Lắng nghe Realtime các lời mời
    private fun startListening() {
        if (currentUserId == null) return

        repository.startSocialListening(
            onIncomingInvites = { list ->
                // Chỉ hiển thị những lời mời đang chờ (PENDING)
                _incomingInvitations.value = list.filter { it.status == "PENDING" }
            },
            onSentInviteStatusChange = { invite ->
                // Xử lý khi lời mời mình gửi đi có thay đổi (VD: Họ từ chối)
                // Có thể bắn Notification cục bộ ở đây nếu muốn
            }
        )
    }

    // Gửi lời mời thách đấu
    fun sendInvite(receiver: UserEntity, myTargetSteps: Int, myName: String) {
        if (currentUserId == null) return
        viewModelScope.launch {
            repository.sendInvitation(receiver.id, myName, myTargetSteps)
        }
    }

    // Chấp nhận lời mời
    fun acceptInvitation(invite: InvitationEntity) {
        viewModelScope.launch {
            // 1. Cập nhật trạng thái trên Firebase & Set Target Steps cho mình
            repository.respondToInvitation(invite, true)

            // 2. Danh sách local sẽ tự cập nhật nhờ Listener Realtime
        }
    }

    // Từ chối lời mời
    fun rejectInvitation(invite: InvitationEntity) {
        viewModelScope.launch {
            repository.respondToInvitation(invite, false)
        }
    }
}