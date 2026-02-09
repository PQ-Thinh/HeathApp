package com.example.healthapp.core.viewmodel

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.HealthConnectManager
import com.example.healthapp.core.data.HealthSensorManager
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.entity.DailyHealthEntity
import com.example.healthapp.core.model.entity.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val repository: HealthRepository,
    val healthConnectManager: HealthConnectManager,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sensorManager: HealthSensorManager
) : ViewModel() {

    // --- KEYS DATASTORE
    companion object {
        private val IS_INTRO_SHOWN_KEY = booleanPreferencesKey("is_intro_shown")
    }
    // Biến cờ: Đã điều hướng đến màn hình chạy chưa?
    private var _hasNavigatedToRun = false

    // --- STATE FLOWS ---
    private var dailyHealthJob: Job? = null
    private val _realtimeSteps = MutableStateFlow(0)
    val realtimeSteps: StateFlow<Int> = _realtimeSteps.asStateFlow()

    val rawSensorSteps: Flow<Int> = sensorManager.stepFlow

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



    // Trạng thái Login: null (đang check), true, false
    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn

    // Điểm đến bắt đầu: null (loading), "intro", "login", "dashboard"
    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination = _startDestination.asStateFlow()

    init {
        determineStartScreen()
    }

    // --- LOGIC QUYẾT ĐỊNH MÀN HÌNH ---
    private fun determineStartScreen() {
        viewModelScope.launch {
            // 1. Kiểm tra Firebase Auth (Cache trên máy)
            val currentUser = auth.currentUser

            if (currentUser != null) {
                // User còn hạn -> Vào thẳng Dashboard
                _isLoggedIn.value = true
                initializeData() // Bắt đầu lắng nghe dữ liệu
                _startDestination.value = "dashboard"
            } else {
                // User chưa đăng nhập -> Check DataStore xem đã hiện Intro chưa
                _isLoggedIn.value = false

                // Lấy giá trị DataStore (dùng .first() để lấy 1 lần duy nhất)
                val preferences = dataStore.data.first()
                val isIntroShown = preferences[IS_INTRO_SHOWN_KEY] ?: false

                if (isIntroShown) {
                    _startDestination.value = "login"
                } else {
                    _startDestination.value = "intro"
                }
            }
        }
    }

    // Hàm gọi khi bấm nút "Bắt đầu" ở màn hình Intro
    fun completeIntro() {
        viewModelScope.launch {
            dataStore.edit { it[IS_INTRO_SHOWN_KEY] = true }
            // Không cần chuyển màn hình ở đây, UI (MainActivity) sẽ xử lý dựa trên callback
        }
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
        dailyHealthJob?.cancel()
        dailyHealthJob = viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.getDailyHealth(today, userId)
                .catch { e ->
                    Log.e("MainViewModel", "Lỗi lắng nghe sức khỏe: ${e.message}")
                }
                .collect { data ->
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
    fun updateTargetSteps(newTarget: Int) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            // Gọi hàm repository mà chúng ta đã viết ở bước trước
            repository.updateTargetSteps(uid, newTarget)

            // Cập nhật lại dữ liệu hiển thị ngay lập tức (Optional, vì Firestore listener sẽ tự lo)
            val currentData = _todayHealthData.value
            if (currentData != null) {
                currentData.targetSteps = newTarget
                _todayHealthData.value = currentData
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

                    val newUser = UserEntity(
                        id = uid,
                        name = "",
                        email = email,
                        gender = "Male",
                        updatedAt = System.currentTimeMillis()
                    )

                    viewModelScope.launch {
                        setIsLoggedIn(true)

                        try {
                            firestore.collection("users").document(uid).set(newUser).await()

                            _realtimeSteps.value = 0
                            _realtimeCalories.value = 0f

                            initializeData()
                            onSuccess()

                        } catch (e: Exception) {
                            onError("Lỗi khởi tạo dữ liệu: ${e.message}")
                        }
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
            _startDestination.value = "login" // Reset về màn login sau khi thoát

            //Reset toàn bộ StateFlow về 0/Null ngay lập tức
            _realtimeSteps.value = 0
            _realtimeCalories.value = 0f
            _realtimeHeartRate.value = 0
            _todayHealthData.value = null
            //Dừng lắng nghe
            dailyHealthJob?.cancel() // Hủy job realtime
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
    // --- Run Tracking---
    // Hàm check xem có nên tự động mở RunScreen không
    fun shouldAutoOpenRunScreen(isRunning: Boolean): Boolean {
        if (isRunning && !_hasNavigatedToRun) {
            _hasNavigatedToRun = true
            return true
        }
        return false
    }

    // Khi user chủ động bấm vào nút chạy (hoặc từ thông báo), reset cờ này nếu cần
    fun resetNavigationFlag() {
        _hasNavigatedToRun = false
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