package com.example.healthapp.core.viewmodel

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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class StepViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
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
    private var _startSessionSteps = 0
    private val _sessionSteps = MutableStateFlow(0)
    val sessionSteps = _sessionSteps.asStateFlow()
    private var _sessionStartTime = MutableStateFlow<LocalDateTime?>(null)
    val sessionStartTime = _sessionStartTime.asStateFlow()


    // Cân nặng (Mặc định 70kg)
    private var userWeight: Float = 70f

    // --- State: Thông tin User (Realtime từ Firestore) ---
    val currentUserInfo: Flow<UserEntity?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(UserEntity::class.java)
                    if (user != null) {
                        userWeight = user.weight ?: 70f // Cập nhật cân nặng
                        trySend(user)
                    }
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)


    init {
        loadChartData()
        loadHistory()
    }

    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData()
    }

    private fun loadChartData() {
        viewModelScope.launch {
            val data = repository.getStepChartData(_selectedTimeRange.value)
            _chartData.value = data
        }
    }

    // Load lịch sử (Tổng hợp theo ngày)
    private fun loadHistory() {
        viewModelScope.launch {
            _stepHistory.value = repository.getStepRecordHistory()
        }
    }

    // --- LOGIC RUNNING SESSION ---

    fun startRunSession(currentTotalSteps: Int) {
        _startSessionSteps = currentTotalSteps
        _sessionSteps.value = 0
        _sessionStartTime.value = LocalDateTime.now()
    }

    fun updateSessionSteps(currentTotalSteps: Int) {
        if (currentTotalSteps < _startSessionSteps) {
            _startSessionSteps = 0 // Xử lý trường hợp reboot máy
        }
        _sessionSteps.value = (currentTotalSteps - _startSessionSteps).coerceAtLeast(0)
    }

    fun calculateCalories(steps: Long): Int {
        return (0.04 * steps * userWeight / 70).toInt()
    }

    fun finishRunSession(currentTotalSteps: Int) {
        val startTime = _sessionStartTime.value ?: return
        val endTime = LocalDateTime.now()
        val stepsTaken = (currentTotalSteps - _startSessionSteps).coerceAtLeast(0)

        if (stepsTaken > 0) {
            viewModelScope.launch {
                // Lưu vào Health Connect & Firestore (Chi tiết Session)
                repository.writeStepsToHealthConnect(startTime, endTime, stepsTaken)

                // Reload lại chart và history sau khi lưu
                loadChartData()
                loadHistory()
            }
        }

        // Reset
        _sessionStartTime.value = null
        _startSessionSteps = 0
        _sessionSteps.value = 0
    }
}