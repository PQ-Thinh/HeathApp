package com.example.healthapp.core.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.data.responsitory.HealthRepository
import com.example.healthapp.core.model.entity.HeartRateRecordEntity
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class HeartViewModel @Inject constructor(
    private val repository: HealthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _heartRateData = MutableStateFlow<List<HeartRateBucket>>(emptyList())
    val heartRateData = _heartRateData.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(ChartTimeRange.DAY)
    val selectedTimeRange = _selectedTimeRange.asStateFlow()

    private val _latestHeartRate = MutableStateFlow<Int>(0)
    val latestHeartRate = _latestHeartRate.asStateFlow()

    private val _assessment = MutableStateFlow<String>("Chưa có dữ liệu")
    val assessment = _assessment.asStateFlow()

    private val _heartHistory = MutableStateFlow<List<HeartRateRecordEntity>>(emptyList())
    val heartHistory = _heartHistory.asStateFlow()

    private var realtimeJob: Job? = null

    init {
        // Tự động theo dõi trạng thái đăng nhập để reset hoặc load dữ liệu
        viewModelScope.launch {
            authStateChanges().collectLatest { user ->
                if (user == null) {
                    // 1. Khi Logout: Xóa sạch dữ liệu cũ
                    clearData()
                } else {
                    // 2. Khi Login/Register: Tải dữ liệu mới
                    loadDataForUser(user.uid)
                }
            }
        }
    }

    private fun clearData() {
        _heartRateData.value = emptyList()
        _latestHeartRate.value = 0
        _assessment.value = "Chưa có dữ liệu"
        _heartHistory.value = emptyList()
        realtimeJob?.cancel()
    }

    private fun loadDataForUser(uid: String) {
        loadChartData()
        loadHistory()

        // Lắng nghe realtime nhịp tim
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val today = LocalDate.now().toString()
            repository.getDailyHealth(today, uid).collect { data ->
                if (data != null && data.heartRateAvg > 0) {
                    _latestHeartRate.value = data.heartRateAvg
                    evaluateHeartRate(data.heartRateAvg)
                }
            }
        }
    }

    fun setTimeRange(range: ChartTimeRange) {
        _selectedTimeRange.value = range
        loadChartData()
    }

    private fun loadChartData() {
        viewModelScope.launch {
            val data = repository.getHeartRateChartData(_selectedTimeRange.value)
            _heartRateData.value = data
        }
    }

    private fun loadHistory() {
        viewModelScope.launch {
            _heartHistory.value = repository.getHeartRateHistory()
        }
    }

    private fun evaluateHeartRate(bpm: Int) {
        _assessment.value = when {
            bpm == 0 -> "Chưa có dữ liệu"
            bpm < 60 -> "Nhịp tim chậm"
            bpm in 60..100 -> "Bình thường"
            bpm in 101..120 -> "Hơi cao"
            else -> "Cao"
        }
    }

    fun saveHeartRateRecord(bpm: Int) {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.saveHeartRate(userId, bpm)
            _latestHeartRate.value = bpm
            evaluateHeartRate(bpm)
            delay(1000)
            loadChartData()
            loadHistory()
        }
    }
    fun deleteHeartRecord(record: HeartRateRecordEntity) {
        val uid = auth.currentUser?.uid ?: return

        // Xóa khỏi List hiển thị NGAY LẬP TỨC
        val currentList = _heartHistory.value.toMutableList()
        currentList.remove(record)
        _heartHistory.value = currentList

        viewModelScope.launch {
            try {
                //Sau đó mới gọi xuống Database xóa ngầm
                repository.deleteHeartRate(record)
                // Load lại chart cho chuẩn số liệu
                loadChartData()
            } catch (e: Exception) {
                // Nếu lỗi thì load lại list cũ (hoàn tác)
                loadHistory()
                Log.e("HeartViewModel", "Xóa thất bại", e)
            }
        }
    }
    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    // Tiện ích theo dõi Auth
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun authStateChanges() = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
}