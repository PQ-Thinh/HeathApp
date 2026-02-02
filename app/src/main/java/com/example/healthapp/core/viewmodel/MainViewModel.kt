package com.example.healthapp.core.viewmodel

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val repository: HealthRepository,
    val healthConnectManager: HealthConnectManager,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    // --- KEYS DATASTORE
    private val CURRENT_MODE_KEY = stringPreferencesKey("current_mode")

    // --- STATE FLOWS ---
    private val _realtimeSteps = MutableStateFlow(0)
    val realtimeSteps: StateFlow<Int> = _realtimeSteps.asStateFlow()

    private val _realtimeCalories = MutableStateFlow(0f)
    val realtimeCalories: StateFlow<Float> = _realtimeCalories.asStateFlow()

    private val _realtimeHeartRate = MutableStateFlow(0)
    val realtimeHeartRate: StateFlow<Int> = _realtimeHeartRate.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private val _healthConnectState = MutableStateFlow(0)
    val healthConnectState = _healthConnectState.asStateFlow()

    private val _todayHealthData = MutableStateFlow<DailyHealthEntity?>(null)
    val todayHealthData: StateFlow<DailyHealthEntity?> = _todayHealthData.asStateFlow()

    val currentMode: StateFlow<String> = dataStore.data
        .map { preferences -> preferences[CURRENT_MODE_KEY] ?: "Chạy Bộ" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Chạy Bộ")

    private val _isLoggedIn = MutableStateFlow<Boolean?>(null) // null nghĩa là đang kiểm tra (Loading)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn

    init {
        initializeData()
    }

    private fun initializeData() {
        // Lấy UID trực tiếp từ Auth
        val uid = auth.currentUser?.uid
        if (uid != null) {
            // Lắng nghe dữ liệu sức khỏe hôm nay từ Firestore (Realtime)
            listenToDailyHealth(uid)

            // Lắng nghe Lời mời kết bạn (Thông qua Repository)
            repository.startRealtimeSync(uid) { invitation ->
                // Xử lý khi có lời mời mới (VD: Bắn Notification)
                Log.d("MainViewModel", "Có lời mời mới từ: ${invitation.senderName}")
            }
        }
    }

    // Hàm lắng nghe Realtime Dashboard
    private fun listenToDailyHealth(userId: String) {
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.getDailyHealth(today, userId).collect { data ->
                _todayHealthData.value = data
                if (data != null) {
                    _realtimeSteps.value = data.steps
                    _realtimeCalories.value = data.caloriesBurned
                    if (data.heartRateAvg > 0) {
                        _realtimeHeartRate.value = data.heartRateAvg
                    }
                }
            }
        }
    }

    // ĐĂNG KÝ & ĐĂNG NHẬP

    fun setIsLoggedIn(status: Boolean) {
        viewModelScope.launch {
            _isLoggedIn.value = status
        }
    }
    fun registerUser(
        email: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                if (firebaseUser != null) {
                    val uid = firebaseUser.uid

                    // Tạo User Entity mới
                    val newUser = UserEntity(
                        id = uid, // Gán ID thủ công
                        name = "Người dùng mới",
                        email = email,
                        targetSteps = 10000,
                        gender = "Male",
                        updatedAt = System.currentTimeMillis()
                    )

                    viewModelScope.launch {
                        // Lưu Profile lên Cloud
                        setIsLoggedIn(true)
                        firestore.collection("users").document(uid).set(newUser)
                        // Reset các chỉ số hiển thị
                        _realtimeSteps.value = 0
                        _realtimeCalories.value = 0f

                        // Bắt đầu lắng nghe
                        initializeData()
                        onSuccess()
                    }
                } else {
                    onError("Lỗi tạo user.")
                }
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Đăng ký thất bại")
            }
    }

    fun loginUser(email: String, pass: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid != null) {
                    viewModelScope.launch {
                        setIsLoggedIn(true)
                        initializeData()
                        onSuccess()
                    }
                }
            }
            .addOnFailureListener {
                onError("Sai email hoặc mật khẩu")
            }
    }

    fun logout() {
        auth.signOut()
        viewModelScope.launch {
            setIsLoggedIn(false)
            //Reset toàn bộ StateFlow về 0/Null ngay lập tức
            _realtimeSteps.value = 0
            _realtimeCalories.value = 0f
            _realtimeHeartRate.value = 0
            _todayHealthData.value = null
            //Dừng lắng nghe
            repository.stopRealtimeSync()
        }
    }

    // --- HEALTH CONNECT ---

    fun checkHealthConnectStatus() {
        viewModelScope.launch {
            val status = healthConnectManager.checkAvailability()
            when (status) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (healthConnectManager.hasAllPermissions()) {
                        syncData() // Sync 1 lần khi mở app
                        _healthConnectState.value = 1
                    } else {
                        _healthConnectState.value = 4
                    }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                    _healthConnectState.value = 2
                }
                else -> {
                    _healthConnectState.value = 3
                }
            }
        }
    }

    fun syncData() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                repository.syncHealthData(userId)
            } catch (e: Exception) {
                Log.e("MainViewModel", "Sync error: ${e.message}")
            }
        }
    }

    // --- SERVICE STATUS ---
    fun setServiceRunningStatus(isRunning: Boolean) {
        _isServiceRunning.value = isRunning
    }
}