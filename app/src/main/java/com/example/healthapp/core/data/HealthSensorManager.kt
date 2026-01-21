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

    val stepFlow: Flow<Int> = callbackFlow {
        // Ưu tiên 1: Tìm cảm biến đếm bước chuyên dụng (Hardware Step Counter)
        //val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Ưu tiên 2: Nếu không có, dùng cảm biến Gia tốc (Accelerometer)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        Log.d("HealthSensor", "Bắt đầu khởi tạo Flow đếm bước...")

        var steps = 0

        val listener = object : SensorEventListener {
            // Biến dùng cho thuật toán Accelerometer
            private var lastMagnitude = 0f
            private val threshold = 10f // Độ nhạy (rung lắc mạnh mới tính là bước)

            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
//                    if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
//                        //có sẵn cảm biến đếm
//                        val totalSteps = it.values[0].toInt()
//                        trySend(totalSteps)
//                        Log.d("HealthSensor", "Nhận dữ liệu từ Cảm biến gốc: $totalSteps")
                   // } else
                        if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {

                        val x = it.values[0]
                        val y = it.values[1]
                        val z = it.values[2]

                        // Tính độ lớn vector gia tốc
                        val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

                        // Thuật toán phát hiện đỉnh sóng (Peak Detection) đơn giản
                        val delta = magnitude - lastMagnitude

                        // Nếu độ rung lớn hơn ngưỡng -> coi là một bước chân
                        if (delta > 5 && magnitude > threshold) {
                            steps++
                            Log.d("HealthSensor", "Đếm bước bằng Thuật toán: $steps") // Log khi lắc máy
                            trySend(steps) // Gửi số bước tự tính
                        }

                        lastMagnitude = magnitude
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

//        if (stepSensor != null) {
//            // TRƯỜNG HỢP 1: Máy có cảm biến thật
//            Log.d("HealthSensor", "Máy CÓ hỗ trợ Step Counter! Đang đăng ký listener...")
//            val registered = sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
//            Log.d("HealthSensor", "Kết quả đăng ký Step Counter: $registered")

        //} else
            if (accelerometer != null) {
            // TRƯỜNG HỢP 2: Máy không có, dùng gia tốc kế
            Log.d("HealthSensor", "Máy KHÔNG có Step Counter -> Dùng Accelerometer thay thế.")
            val registered = sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d("HealthSensor", "Kết quả đăng ký Accelerometer: $registered")

        } else {
            Log.e("HealthSensor", "LỖI: Máy không có cả Accelerometer!")
            trySend(0)
            close()
        }

        awaitClose {
            Log.d("HealthSensor", "Hủy đăng ký cảm biến.")
            sensorManager.unregisterListener(listener)
        }
    }

    // ---Flow Nhịp tim  trả về 0 nếu không có
    val heartRateFlow: Flow<Int> = callbackFlow {
        val heartSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        if (heartSensor == null) {
            trySend(0)
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let { trySend(it.values[0].toInt()) }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, heartSensor, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose { sensorManager.unregisterListener(listener) }
    }
}