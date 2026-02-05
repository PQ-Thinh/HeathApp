package com.example.healthapp.core.viewmodel

import android.content.ContentValues.TAG
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
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class StepViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    // --- CHART & HISTORY (Giữ nguyên) ---
    private val _chartData = MutableStateFlow<List<StepBucket>>(emptyList())
    val chartData = _chartData.asStateFlow()
    private val _stepHistory = MutableStateFlow<List<StepRecordEntity>>(emptyList())
    val stepHistory = _stepHistory.asStateFlow()
    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()


    // --- RUN SESSION STATE ---
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    val sessionSteps = MutableStateFlow(0)
    val sessionDuration = MutableStateFlow(0L)
    val sessionDistance = MutableStateFlow(0.0)
    val sessionSpeed = MutableStateFlow(0.0)

    private val _isCountdownActive = MutableStateFlow(false)
    val isCountdownActive = _isCountdownActive.asStateFlow()

    // Biến nội bộ
    private var _startSessionSteps = 0
    private var _sessionStartTimeMillis = 0L
    private var userWeight = 70f
    private var strideLength = 0.7
    private var timerJob: Job? = null
    // --- State cho Chart ---

    private var _sessionStartTime = MutableStateFlow<LocalDateTime?>(null)
    val sessionStartTime = _sessionStartTime.asStateFlow()


    // Key lưu trữ
    companion object {
        val PREF_IS_RUNNING = booleanPreferencesKey("session_is_running")
        val PREF_START_STEPS = intPreferencesKey("session_start_steps")
        val PREF_START_TIME = longPreferencesKey("session_start_time")
    }

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
        restoreSession()
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
        sessionSteps.value = 0
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

    private fun restoreSession() {
        viewModelScope.launch {
            dataStore.data.collectLatest { prefs ->
                val running = prefs[PREF_IS_RUNNING] ?: false
                val startSteps = prefs[PREF_START_STEPS] ?: 0
                val startTime = prefs[PREF_START_TIME] ?: 0L
                Log.d("StepDebug", "VM Restore: Running=$running, StartSteps=$startSteps")
                if (_isRunning.value != running || _sessionStartTimeMillis != startTime) {
                    _isRunning.value = running
                    _startSessionSteps = startSteps
                    _sessionStartTimeMillis = startTime

                    if (running) {
                        startTimer()
                    } else {
                        stopTimer()
                    }
                }
            }
        }
    }
    // --- USER ACTIONS ---
    fun prepareRun() { _isCountdownActive.value = true }
    fun cancelRunPreparation() { _isCountdownActive.value = false }

    fun startRunSession(currentTotalSteps: Int) {
        Log.d("StepDebug", "VM Start Clicked: InputSteps=$currentTotalSteps")
        viewModelScope.launch {
            _isCountdownActive.value = false

            val now = System.currentTimeMillis()
            _isRunning.value = true
            _startSessionSteps = currentTotalSteps
            _sessionStartTimeMillis = now

            // Reset hiển thị ngay lập tức
            sessionSteps.value = 0
            sessionDuration.value = 0
            sessionDistance.value = 0.0

            startTimer() // Chạy đồng hồ ngay
            Log.d("StepDebug", "VM Started: Set StartSteps=$_startSessionSteps")
            // Lưu vào DataStore (Service sẽ lắng nghe cái này)
            dataStore.edit {
                it[PREF_IS_RUNNING] = true
                it[PREF_START_STEPS] = currentTotalSteps
                it[PREF_START_TIME] = now
            }
        }
    }

    fun updateSessionSteps(currentTotalSteps: Int) {
        if (!_isRunning.value) return
        // Nếu vừa Start mà chênh lệch quá lớn (>1000 bước), chứng tỏ mốc Start (29) bị sai.
        // Cập nhật lại mốc Start = currentTotalSteps (1783)
        if (_startSessionSteps > 0 && (currentTotalSteps - _startSessionSteps) > 1000) {
            Log.e(TAG, "AUTO-CORRECT MAJOR DRIFT: Fix Start $_startSessionSteps -> $currentTotalSteps")
            _startSessionSteps = currentTotalSteps
            // Lưu lại vào DataStore để lần sau Service cũng đọc được mốc đúng này
            viewModelScope.launch {
                dataStore.edit { it[PREF_START_STEPS] = currentTotalSteps }
            }
        }

        // Nếu mốc start vẫn 0 (chưa có sensor), thì bước chân phiên này tạm tính là 0
        if (_startSessionSteps == 0) return

        val stepsTaken = (currentTotalSteps - _startSessionSteps).coerceAtLeast(0)
        Log.d("StepDebug", "VM Calc: Current($currentTotalSteps) - Start($_startSessionSteps) = Result($stepsTaken)")
        sessionSteps.value = stepsTaken

        sessionDistance.value = (stepsTaken * strideLength) / 1000.0
    }

    fun pauseRunSession() {
        stopTimer()
        _isRunning.value = false
        viewModelScope.launch { dataStore.edit { it[PREF_IS_RUNNING] = false } }
    }

    fun resumeRunSession() {
        startTimer()
        _isRunning.value = true
        viewModelScope.launch { dataStore.edit { it[PREF_IS_RUNNING] = true } }
    }

    fun finishRunSession(currentTotalSteps: Int) {
        stopTimer()
        val steps = sessionSteps.value
        val startTime = _sessionStartTimeMillis

        viewModelScope.launch {
            // Lưu dữ liệu vào DB/HealthConnect
            if (steps > 0 && startTime > 0) {
                val start = java.time.Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
                val end = LocalDateTime.now()
                repository.writeStepsToHealthConnect(start, end, steps)
            }

            // Xóa DataStore
            dataStore.edit {
                it.remove(PREF_IS_RUNNING)
                it.remove(PREF_START_STEPS)
                it.remove(PREF_START_TIME)
            }

            // Reset UI
            _isRunning.value = false
            sessionSteps.value = 0
            sessionDuration.value = 0
            sessionDistance.value = 0.0
            _startSessionSteps = 0
            _sessionStartTimeMillis = 0L
        }
        loadData()
    }

    // --- TIMER ---
    private fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = viewModelScope.launch {
            while (true) { // Loop vô tận, kiểm tra điều kiện bên trong
                if (_isRunning.value && _sessionStartTimeMillis > 0) {
                    val duration = (System.currentTimeMillis() - _sessionStartTimeMillis) / 1000
                    sessionDuration.value = duration

                    // Tính tốc độ (Km/h)
                    if (duration > 0 && sessionDistance.value > 0) {
                        val hours = duration / 3600.0
                        sessionSpeed.value = sessionDistance.value / hours
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }
    // --- MANUAL INPUT (Chức năng nhập tay) ---
    fun saveManualStepRecord(startTime: Long, durationMinutes: Int, steps: Int) {
        viewModelScope.launch {
            val endTime = startTime + (durationMinutes * 60 * 1000)
            val startDateTime = java.time.Instant.ofEpochMilli(startTime)
                .atZone(ZoneId.systemDefault()).toLocalDateTime()
            val endDateTime = java.time.Instant.ofEpochMilli(endTime)
                .atZone(ZoneId.systemDefault()).toLocalDateTime()
            repository.writeStepsToHealthConnect(startDateTime, endDateTime, steps)
            loadData()
        }
    }
    fun editStepRecord(oldRecord: StepRecordEntity, newStartTime: Long, newDurationMinutes: Int, newSteps: Int) {
        viewModelScope.launch {
            // Xóa bản ghi cũ (cả HC và Firestore)
            repository.deleteStepRecord(oldRecord)

            //  Tính toán thời gian mới
            val startTime = java.time.Instant.ofEpochMilli(newStartTime)
                .atZone(ZoneId.systemDefault()).toLocalDateTime()
            val endTime = startTime.plusMinutes(newDurationMinutes.toLong())

            //  Ghi bản ghi mới (Coi như nhập mới)
            repository.writeStepsToHealthConnect(startTime, endTime, newSteps)

            //Refresh dữ liệu
            loadData()
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