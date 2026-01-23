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
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        // Ưu tiên 2: Nếu không có, dùng cảm biến Gia tốc (Accelerometer)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        Log.d("HealthSensor", "Bắt đầu khởi tạo Flow đếm bước...")

        var steps = 0


        val listener = object : SensorEventListener {
            // 1. Biến lưu thời gian để chặn rung lắc quá nhanh
            private var lastStepTimeNs: Long = 0

            // 2. Ngưỡng lọc (Threshold)
            // Trọng lực ~ 9.8. Khi đi bộ, lực dậm chân thường đẩy tổng lực lên khoảng 11-13.
            // Đặt 12f là an toàn để loại bỏ các rung động nhẹ.
            private val threshold = 12f

            // 3. Bộ lọc thông thấp (Low-pass filter) để tách trọng lực (Alpha)
            private var gravity = FloatArray(3)
            private val alpha = 0.8f

            override fun onSensorChanged(event: SensorEvent?) {
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

//                        val x = xRaw - gravity[0]
//                        val y = yRaw - gravity[1]
//                        val z = zRaw - gravity[2]


                        val magnitudeOld = sqrt((xRaw * xRaw + yRaw * yRaw + zRaw * zRaw).toDouble()).toFloat()

                        // --- BƯỚC 3: Kiểm tra điều kiện đếm bước ---

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
        if (stepSensor != null) {
            // TRƯỜNG HỢP 1: Máy có cảm biến thật
            Log.d("HealthSensor", "Máy CÓ hỗ trợ Step Counter! Đang đăng ký listener...")
            val registered = sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
            Log.d("HealthSensor", "Kết quả đăng ký Step Counter: $registered")

        } else
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


}