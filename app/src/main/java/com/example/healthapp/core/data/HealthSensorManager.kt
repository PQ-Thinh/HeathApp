package com.example.healthapp.core.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import kotlin.math.sqrt

class HealthSensorManager @Inject constructor(
    private val context: Context
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var _isPaused = false
    val isPaused get() = _isPaused

    fun pauseTracking() {
        _isPaused = true
    }

    fun resumeTracking() {
        _isPaused = false
    }
    val stepFlow: Flow<Int> = callbackFlow {
        // Ưu tiên 1: Tìm cảm biến đếm bước chuyên dụng (Hardware Step Counter)
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Ưu tiên 2: Nếu không có, dùng cảm biến Gia tốc (Accelerometer)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        Log.d("HealthSensor", "Bắt đầu khởi tạo Flow đếm bước...")

        var steps = 0


        val listener = object : SensorEventListener {
            // Biến lưu thời gian để chặn rung lắc quá nhanh
            private var lastStepTimeNs: Long = 0

            // Ngưỡng lọc (Threshold)
            // Trọng lực ~ 9.8. Khi đi bộ, lực dậm chân thường đẩy tổng lực lên khoảng 11-13.
            // Đặt 12f là an toàn để loại bỏ các rung động nhẹ.
            private val threshold = 12f

            // 3. Bộ lọc thông thấp (Low-pass filter) để tách trọng lực (Alpha)
            private var gravity = FloatArray(3)
            private val alpha = 0.8f

            override fun onSensorChanged(event: SensorEvent?) {
                if (!_isPaused && event != null) {
                    trySend(event.values[0].toInt())
                }
                event?.let {
                    if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                        //có sẵn cảm biến đếm
                        val totalSteps = it.values[0].toInt()
                        trySend(totalSteps)
                        Log.d("HealthSensor", "Nhận dữ liệu từ Cảm biến gốc: $totalSteps")
                    } else
                    if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        val xRaw = it.values[0]
                        val yRaw = it.values[1]
                        val zRaw = it.values[2]

                        // --- BƯỚC 1: Lọc bỏ trọng lực (Gravity Filter) ---
                        // Mục đích: Chỉ lấy gia tốc thực sự do di chuyển tạo ra (Linear Acceleration)
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * xRaw
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * yRaw
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * zRaw




                        val magnitudeOld = sqrt((xRaw * xRaw + yRaw * yRaw + zRaw * zRaw).toDouble()).toFloat()

                        //  Kiểm tra điều kiện đếm bước ---
                        // Lấy thời gian hiện tại
                        val currentTimeNs = System.nanoTime()

                        // Quy tắc A: Độ lớn phải vượt ngưỡng (lực dậm chân)
                        // Quy tắc B: Phải cách bước trước ít nhất 350ms ( ~3 bước/giây là max của con người)
                        // 350ms = 350_000_000 nanoseconds
                        val minTimeBetweenSteps = 350_000_000

                        if (magnitudeOld > threshold &&
                            (currentTimeNs - lastStepTimeNs) > minTimeBetweenSteps) {

                            steps++
                            lastStepTimeNs = currentTimeNs // Cập nhật thời gian bước vừa đi

                            Log.d("HealthSensor", "Phát hiện bước chân: $steps (Lực: $magnitudeOld)")
                            trySend(steps)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        try {
            if (stepSensor != null) {
                Log.d("HealthSensor", "Máy CÓ hỗ trợ Step Counter! Đang đăng ký...")
                sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
            } else if (accelerometer != null) {
                Log.d("HealthSensor", "Dùng Accelerometer thay thế.")
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            } else {
                Log.e("HealthSensor", "LỖI: Không có cảm biến nào!")
            }
        } catch (e: Exception) {
            Log.e("HealthSensor", "Không thể đăng ký cảm biến: ${e.message}")
            trySend(0) // Gửi về 0 để flow không bị chết
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }


}