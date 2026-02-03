package com.example.healthapp.core.viewmodel

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.StepBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.entity.StepRecordEntity
import com.example.healthapp.core.model.entity.UserEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import androidx.datastore.preferences.core.Preferences
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class StepViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    // --- State cho Chart ---
    private val _chartData = MutableStateFlow<List<StepBucket>>(emptyList())
    val chartData = _chartData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    // --- State: Lịch sử (Daily List) ---
    private val _stepHistory = MutableStateFlow<List<StepRecordEntity>>(emptyList())
    val stepHistory = _stepHistory.asStateFlow()

    // --- State cho phiên chạy bộ (Run Session) ---
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()
    private var _startSessionSteps = 0
    private val _sessionSteps = MutableStateFlow(0)
    val sessionSteps = _sessionSteps.asStateFlow()
    private var _sessionStartTime = MutableStateFlow<LocalDateTime?>(null)
    val sessionStartTime = _sessionStartTime.asStateFlow()
    private val _sessionDuration = MutableStateFlow(0L) // Giây
    val sessionDuration = _sessionDuration.asStateFlow()

    private val _sessionDistance = MutableStateFlow(0.0) // Km
    val sessionDistance = _sessionDistance.asStateFlow()

    private val _sessionSpeed = MutableStateFlow(0.0) // Km/h
    val sessionSpeed = _sessionSpeed.asStateFlow()

    // Biến nội bộ lưu mốc bắt đầu
    private var _sessionStartTimeMillis = 0L

    // Cấu hình (lấy từ User hoặc mặc định)
    private var strideLength = 0.7 // Độ dài sải chân (mét) - Có thể lấy từ chiều cao user

    // Key lưu trữ
    companion object {
        val PREF_IS_RUNNING = booleanPreferencesKey("session_is_running")
        val PREF_START_STEPS = intPreferencesKey("session_start_steps")
        val PREF_START_TIME = longPreferencesKey("session_start_time")
    }




    // Cân nặng (Mặc định 70kg)
    private var userWeight: Float = 70f

    // --- State: Thông tin User (Realtime từ Firestore) ---
    //Dùng flatMapLatest để tự động lắng nghe lại khi đổi user
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserInfo: Flow<UserEntity?> = authStateChanges()
        .flatMapLatest { firebaseUser ->
            if (firebaseUser == null) {
                flowOf<UserEntity?>(null)
            } else {
                callbackFlow {
                    val listener = firestore.collection("users").document(firebaseUser.uid)
                        .addSnapshotListener { snapshot, _ ->
                            if (snapshot != null && snapshot.exists()) {
                                val user = snapshot.toObject(UserEntity::class.java)
                                if (user != null) {
                                    userWeight = user.weight ?: 70f // Cập nhật cân nặng để tính Calo
                                    trySend(user)
                                }
                            } else {
                                trySend(null)
                            }
                        }
                    awaitClose { listener.remove() }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    init {
        restoreSessionState()
        // Tự động theo dõi trạng thái đăng nhập
        viewModelScope.launch {
            authStateChanges().collectLatest { user ->
                if (user == null) {
                    // Logout -> Xóa sạch dữ liệu
                    clearData()
                } else {
                    // Login -> Tải dữ liệu mới
                    loadData()
                }
            }
        }
    }


    private fun clearData() {
        _chartData.value = emptyList()
        _stepHistory.value = emptyList()
        _sessionSteps.value = 0
        _sessionStartTime.value = null
        _startSessionSteps = 0
        userWeight = 70f
    }

    private fun loadData() {
        loadChartData()
        loadHistory()
    }

    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData()
    }

    private fun loadChartData() {
        // Kiểm tra user trước khi gọi repository để tránh lỗi
        if (auth.currentUser == null) return

        viewModelScope.launch {
            val data = repository.getStepChartData(_selectedTimeRange.value)
            _chartData.value = data
        }
    }

    private fun loadHistory() {
        if (auth.currentUser == null) return

        viewModelScope.launch {
            _stepHistory.value = repository.getStepRecordHistory()
        }
    }

    fun deleteStepRecord(record: StepRecordEntity) {
        val uid = auth.currentUser?.uid ?: return

        // Xóa khỏi List hiển thị NGAY LẬP TỨC
        val currentList = _stepHistory.value.toMutableList()
        currentList.remove(record)
        _stepHistory.value = currentList

        viewModelScope.launch {
            try {
                //Sau đó mới gọi xuống Database xóa ngầm
                repository.deleteStepRecord(record)
                // Load lại chart cho chuẩn số liệu
                loadChartData()
            } catch (e: Exception) {
                // Nếu lỗi thì load lại list cũ (hoàn tác)
                loadHistory()
                Log.e("HeartViewModel", "Xóa thất bại", e)
            }
        }
    }
    // --- LOGIC RUNNING SESSION ---

    private fun restoreSessionState() {
        viewModelScope.launch {
            dataStore.data.collectLatest { prefs ->
                val isRunning = prefs[PREF_IS_RUNNING] ?: false
                if (isRunning) {
                    _isRunning.value = true
                    _startSessionSteps = prefs[PREF_START_STEPS] ?: 0
                    _sessionStartTimeMillis = prefs[PREF_START_TIME] ?: System.currentTimeMillis()
                } else {
                    _isRunning.value = false
                }
            }
        }
    }
    fun resumeRunSession() {
        _isRunning.value = true
        viewModelScope.launch { dataStore.edit { it[PREF_IS_RUNNING] = true } }
    }
    fun pauseRunSession() {
        // Chỉ cần chỉnh biến UI, không cần xóa DataStore (để lỡ kill app vẫn biết là đang có session)
        _isRunning.value = false
        // Lưu trạng thái Tracking là false nhưng KHÔNG XÓA start_time
        viewModelScope.launch { dataStore.edit { it[PREF_IS_RUNNING] = false } }
    }
    // --- BẮT ĐẦU CHẠY ---
    fun startRunSession(currentTotalSteps: Int) {
        viewModelScope.launch {
            _isRunning.value = true
            _startSessionSteps = currentTotalSteps
            _sessionStartTimeMillis = System.currentTimeMillis()

            // Lưu ngay xuống ổ cứng
            dataStore.edit {
                it[PREF_IS_RUNNING] = true
                it[PREF_START_STEPS] = currentTotalSteps
                it[PREF_START_TIME] = _sessionStartTimeMillis
            }
        }
    }

    // --- CẬP NHẬT LIÊN TỤC (Gọi từ UI) ---
    fun updateSessionSteps(currentTotalSteps: Int) {
        if (!_isRunning.value) return

        // Xử lý logic reboot máy (bước bị reset về 0)
        val actualStart = if (currentTotalSteps < _startSessionSteps) 0 else _startSessionSteps
        val stepsTaken = (currentTotalSteps - actualStart).coerceAtLeast(0)

        _sessionSteps.value = stepsTaken

        // Tính thời gian
        val now = System.currentTimeMillis()
        val durationSec = (now - _sessionStartTimeMillis) / 1000
        _sessionDuration.value = durationSec

        // Tính quãng đường (S = Steps * Stride) -> Km
        val distKm = (stepsTaken * strideLength) / 1000.0
        _sessionDistance.value = distKm

        // Tính tốc độ (V = S / T) -> Km/h
        val hours = durationSec / 3600.0
        if (hours > 0) {
            _sessionSpeed.value = distKm / hours
        } else {
            _sessionSpeed.value = 0.0
        }
    }

    // --- KẾT THÚC ---
    fun finishRunSession(currentTotalSteps: Int) {
        if (!_isRunning.value) return

        val endTime = LocalDateTime.now()
        val startTime = java.time.Instant.ofEpochMilli(_sessionStartTimeMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
        val stepsTaken = _sessionSteps.value

        viewModelScope.launch {
            if (stepsTaken > 0) {
                repository.writeStepsToHealthConnect(startTime, endTime, stepsTaken)
            }
            // Xóa dữ liệu phiên chạy
            dataStore.edit {
                it.remove(PREF_IS_RUNNING)
                it.remove(PREF_START_STEPS)
                it.remove(PREF_START_TIME)
            }
            // Reset biến RAM
            _isRunning.value = false
            _sessionSteps.value = 0
            _sessionDuration.value = 0
            _sessionDistance.value = 0.0
            _sessionSpeed.value = 0.0

            loadData() // Reload chart
        }
    }
    fun calculateCalories(steps: Long): Int {
        return (0.04 * steps * userWeight / 70).toInt()
    }
    fun formatDuration(seconds: Long): String {
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val s = seconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
    // Theo dõi Auth state
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun authStateChanges() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
}