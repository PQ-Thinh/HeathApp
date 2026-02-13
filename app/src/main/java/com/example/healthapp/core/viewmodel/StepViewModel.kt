package com.example.healthapp.core.viewmodel

import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.StepBucket
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.helperEnumAndData.ChartTimeRange
import com.example.healthapp.core.helperEnumAndData.RunState
import com.example.healthapp.core.model.entity.StepRecordEntity
import com.example.healthapp.core.service.StepForegroundService
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class StepViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val auth: FirebaseAuth,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    // --- CHART & HISTORY ---
    private val _chartData = MutableStateFlow<List<StepBucket>>(emptyList())
    val chartData = _chartData.asStateFlow()
    private val _stepHistory = MutableStateFlow<List<StepRecordEntity>>(emptyList())
    val stepHistory = _stepHistory.asStateFlow()
    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.WEEK)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    // --- RUN SESSION STATE ---
    // Sử dụng Enum thay vì Boolean để quản lý trạng thái tốt hơn
    private val _runState = MutableStateFlow(RunState.IDLE)
    val runState = _runState.asStateFlow()

    private val _isRunPrepared = MutableStateFlow(false)
    val isRunPrepared = _isRunPrepared.asStateFlow()

    val sessionSteps = MutableStateFlow(0)
    val sessionDuration = MutableStateFlow(0L)
    val sessionDistance = MutableStateFlow(0.0)
    val sessionSpeed = MutableStateFlow(0.0)

    private val _isCountdownActive = MutableStateFlow(false)
    val isCountdownActive = _isCountdownActive.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()


    // Biến nội bộ
    private var _startSessionSteps = 0
    private var _sessionStartTimeMillis = 0L
    private var userWeight = 70f
    private var strideLength = 0.7
    private var timerJob: Job? = null


    // Key lưu trữ DataStore
    companion object {
        //val PREF_IS_RUNNING = booleanPreferencesKey("session_is_running")
        val PREF_START_STEPS = intPreferencesKey("session_start_steps")
        val PREF_START_TIME = longPreferencesKey("session_start_time")
        val PREF_RUN_STATE = stringPreferencesKey("session_run_state")
    }

    // --- State: Thông tin User ---
//    @OptIn(ExperimentalCoroutinesApi::class)
//    val currentUserInfo: Flow<UserEntity?> = authStateChanges()
//        .flatMapLatest { firebaseUser ->
//            if (firebaseUser == null) {
//                flowOf<UserEntity?>(null)
//            } else {
//                callbackFlow {
//                    val listener = firestore.collection("users").document(firebaseUser.uid)
//                        .addSnapshotListener { snapshot, _ ->
//                            if (snapshot != null && snapshot.exists()) {
//                                val user = snapshot.toObject(UserEntity::class.java)
//                                if (user != null) {
//                                    userWeight = user.weight ?: 70f
//                                    trySend(user)
//                                }
//                            } else {
//                                trySend(null)
//                            }
//                        }
//                    awaitClose { listener.remove() }
//                }
//            }
//        }
//        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        restoreSession()
        // Tự động load dữ liệu khi login thành công
        viewModelScope.launch {
            authStateChanges().collectLatest { user ->
                if (user == null) clearData() else loadData()
            }
        }
    }

    private fun clearData() {
        _chartData.value = emptyList()
        _stepHistory.value = emptyList()
        sessionSteps.value = 0
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
        if (auth.currentUser == null) return
        viewModelScope.launch {
            _chartData.value = repository.getStepChartData(_selectedTimeRange.value)
        }
    }

    private fun loadHistory() {
        if (auth.currentUser == null) return
        viewModelScope.launch {
            _stepHistory.value = repository.getStepRecordHistory()
        }
    }

    fun deleteStepRecord(record: StepRecordEntity) {
        val currentList = _stepHistory.value.toMutableList()
        currentList.remove(record)
        _stepHistory.value = currentList
        viewModelScope.launch {
            try {
                repository.deleteStepRecord(record)
                loadChartData()
            } catch (e: Exception) {
                loadHistory()
                Log.e("StepViewModel", "Xóa thất bại", e)
            }
        }
    }

    // --- LOGIC RUNNING SESSION  ---

    private fun restoreSession() {
        viewModelScope.launch {
            dataStore.data.collectLatest { prefs ->
                // Đọc chuỗi trạng thái từ DataStore
                val stateName = prefs[PREF_RUN_STATE] ?: RunState.IDLE.name
                val newState = try {
                    RunState.valueOf(stateName)
                } catch (e: Exception) {
                    RunState.IDLE
                }

                val startSteps = prefs[PREF_START_STEPS] ?: 0
                val startTime = prefs[PREF_START_TIME] ?: 0L

                // Cập nhật UI State nếu có thay đổi
                if (_runState.value != newState) {
                    _runState.value = newState

                    // Đồng bộ lại các biến mốc
                    if (newState != RunState.IDLE) {
                        _startSessionSteps = startSteps
                        _sessionStartTimeMillis = startTime
                    }

                    // Điều khiển Timer theo trạng thái
                    when (newState) {
                        RunState.RUNNING -> startTimer()
                        RunState.PAUSED -> pauseTimer() // Dừng timer ngay
                        RunState.IDLE -> stopTimer()
                        else -> {}
                    }
                }
            }
        }
    }

    fun prepareRun() {
        if (_runState.value == RunState.IDLE) {
            _isRunPrepared.value = true
        }
    }

    fun cancelRunPreparation() {
        _isRunPrepared.value = false
    }

    fun startRunSession(currentTotalSteps: Int) {
        _isRunPrepared.value = false
        viewModelScope.launch {
            _isCountdownActive.value = false
            _runState.value = RunState.RUNNING // Cập nhật trạng thái

            val now = System.currentTimeMillis()
            _startSessionSteps = currentTotalSteps
            _sessionStartTimeMillis = now

            sessionSteps.value = 0
            sessionDuration.value = 0
            sessionDistance.value = 0.0

            startTimer()

            // Lưu DataStore
            dataStore.edit {
                it[PREF_RUN_STATE] = RunState.RUNNING.name
                it[PREF_START_STEPS] = currentTotalSteps
                it[PREF_START_TIME] = now
            }
            val intent = Intent(context, StepForegroundService::class.java).apply {
                action = StepForegroundService.ACTION_START
            }
            context.startService(intent)
        }
    }

    fun updateSessionSteps(currentTotalSteps: Int) {
        // Chỉ update khi đang ở trạng thái RUNNING
        if (_runState.value != RunState.RUNNING) return

        // lệch bước chân (Drift)
        if (_startSessionSteps > 0 && (currentTotalSteps - _startSessionSteps) > 1000) {
            Log.e(TAG, "AUTO-CORRECT: Fix Start $_startSessionSteps -> $currentTotalSteps")
            _startSessionSteps = currentTotalSteps
            viewModelScope.launch {
                dataStore.edit { it[PREF_START_STEPS] = currentTotalSteps }
            }
        }

        if (_startSessionSteps == 0) return

        val stepsTaken = (currentTotalSteps - _startSessionSteps).coerceAtLeast(0)
        sessionSteps.value = stepsTaken
        sessionDistance.value = (stepsTaken * strideLength) / 1000.0
    }

    fun pauseRunSession() {

        viewModelScope.launch {
            dataStore.edit { it[PREF_RUN_STATE] = RunState.PAUSED.name }
        }

    }

    fun resumeRunSession() {

        viewModelScope.launch {
            dataStore.edit { it[PREF_RUN_STATE] = RunState.RUNNING.name }
        }
    }

    fun finishRunSession(currentTotalSteps: Int) {
        _runState.value = RunState.IDLE
        stopTimer()
        val steps = sessionSteps.value
        val startTime = _sessionStartTimeMillis

        viewModelScope.launch {
            // Lưu dữ liệu vào HealthConnect & Firestore
            if (steps > 0 && startTime > 0) {
                val start = java.time.Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
                val end = LocalDateTime.now()
                repository.writeStepsToHealthConnect(start, end, steps)
            }

            // Xóa DataStore
            dataStore.edit {
                it.remove(PREF_RUN_STATE)
                it.remove(PREF_START_STEPS)
                it.remove(PREF_START_TIME)
            }
            val intent = Intent(context, StepForegroundService::class.java).apply {
                action = StepForegroundService.ACTION_STOP
            }
            context.startService(intent)
            // Reset UI
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
            while (true) {
                // Chỉ chạy timer khi RUNNING
                if (_runState.value == RunState.RUNNING && _sessionStartTimeMillis > 0) {
                    val duration = (System.currentTimeMillis() - _sessionStartTimeMillis) / 1000
                    sessionDuration.value = duration

                    if (duration > 0 && sessionDistance.value > 0) {
                        val hours = duration / 3600.0
                        sessionSpeed.value = sessionDistance.value / hours
                    }
                }
                delay(1000)
            }
        }
    }

    private fun pauseTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // --- MANUAL INPUT ---
    fun saveManualStepRecord(startTime: Long, durationMinutes: Int, steps: Int) {
        viewModelScope.launch {
            val endTime = startTime + (durationMinutes * 60 * 1000)
            val startDateTime = java.time.Instant.ofEpochMilli(startTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val endDateTime = java.time.Instant.ofEpochMilli(endTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
            repository.writeStepsToHealthConnect(startDateTime, endDateTime, steps)
            loadData()
        }
    }

    fun editStepRecord(oldRecord: StepRecordEntity, newStartTime: Long, newDurationMinutes: Int, newSteps: Int) {
        viewModelScope.launch {
            repository.deleteStepRecord(oldRecord)
            val startTime = java.time.Instant.ofEpochMilli(newStartTime).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val endTime = startTime.plusMinutes(newDurationMinutes.toLong())
            repository.writeStepsToHealthConnect(startTime, endTime, newSteps)
            loadData()
        }
    }

    fun calculateCalories(steps: Long): Int {
        return (0.04 * steps * userWeight / 70).toInt()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val uid = auth.currentUser?.uid
            if (uid != null) {
                loadChartData()
                loadHistory()
                delay(500) // UI Effect
            }
            _isRefreshing.value = false
        }
    }

    fun formatDuration(seconds: Long): String {
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val s = seconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun authStateChanges() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
}