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
                // Kiểm tra biến Paused ngay đầu. Nếu đang Pause thì KHÔNG LÀM GÌ CẢ.
                if (_isPaused || event == null) return
                Log.d("HealthSensor", "Sensor Changed $_isPaused")

                event.let {
                    // TRƯỜNG HỢP 1: Máy có sẵn cảm biến đếm bước (Hardware Step Counter)
                    if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                        val totalSteps = it.values[0].toInt()
                        // Chỉ gửi khi là dữ liệu bước chân thực sự
                        trySend(totalSteps)
                        Log.d("HealthSensor", "Hardware Step: $totalSteps")
                    }

                    // TRƯỜNG HỢP 2: Dùng gia tốc kế (Accelerometer) để tự tính
                    else if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        val xRaw = it.values[0]
                        val yRaw = it.values[1]
                        val zRaw = it.values[2]

                        // Lọc trọng lực
                        gravity[0] = alpha * gravity[0] + (1 - alpha) * xRaw
                        gravity[1] = alpha * gravity[1] + (1 - alpha) * yRaw
                        gravity[2] = alpha * gravity[2] + (1 - alpha) * zRaw

                        val x = xRaw - gravity[0]
                        val y = yRaw - gravity[1]
                        val z = zRaw - gravity[2]

                        val magnitudeOld = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                        val currentTimeNs = System.nanoTime()

                        val minTimeBetweenSteps = 350_000_000 // 350ms

                        // Kiểm tra ngưỡng rung lắc
                        if (magnitudeOld > threshold && (currentTimeNs - lastStepTimeNs) > minTimeBetweenSteps) {
                            steps++
                            lastStepTimeNs = currentTimeNs
                            Log.d("HealthSensor", "Software Step: $steps (Lực: $magnitudeOld)")
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