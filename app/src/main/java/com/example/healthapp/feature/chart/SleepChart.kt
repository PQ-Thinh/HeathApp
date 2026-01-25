package com.example.healthapp.feature.chart

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.healthapp.core.data.SleepBucket
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.format.DateTimeFormatter
import com.example.healthapp.core.data.responsitory.ChartTimeRange

@Composable
fun SleepChart(
    data: List<SleepBucket>,
    barColor: Int = android.graphics.Color.parseColor("#6366F1") // Màu tím nhạt
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF1E293B)) // Nền tối
            .padding(8.dp),
        factory = { context ->
            BarChart(context).apply {
                // --- Cấu hình cơ bản ---
                description.isEnabled = false // Tắt chữ Description góc phải
                legend.isEnabled = false      // Tắt chú thích màu
                setDrawGridBackground(false)  // Tắt lưới nền
                setTouchEnabled(false)        // Tắt cảm ứng (nếu muốn tĩnh)
                animateY(1000)                // Hiệu ứng mượt mà

                // --- Cấu hình trục X (Ngày tháng) ---
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.WHITE
                    textSize = 10f
                    granularity = 1f // Mỗi cột là 1 đơn vị
                    // Formatter để đổi số 0,1,2... thành "Mon", "Tue"
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            if (index >= 0 && index < data.size) {
                                // Lấy ngày từ data và format ngắn gọn
                                return data[index].startTime.format(DateTimeFormatter.ofPattern("dd/MM"))
                            }
                            return ""
                        }
                    }
                }

                // --- Cấu hình trục Y (Giờ ngủ) ---
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.GRAY
                    textColor = Color.WHITE
                    axisMinimum = 0f // Bắt đầu từ 0
                }
                axisRight.isEnabled = false // Tắt trục phải
            }
        },
        update = { chart ->
            // --- Cập nhật dữ liệu khi State thay đổi ---
            if (data.isNotEmpty()) {
                val entries = data.mapIndexed { index, bucket ->
                    // Chuyển phút thành giờ (VD: 450 phút -> 7.5 giờ)
                    BarEntry(index.toFloat(), bucket.totalMinutes / 60f)
                }

                val dataSet = BarDataSet(entries, "Giấc ngủ").apply {
                    color = barColor
                    valueTextColor = Color.WHITE
                    valueTextSize = 10f
                    setDrawValues(true) // Hiện số giờ trên đầu cột
                }

                chart.data = BarData(dataSet)
                chart.data.barWidth = 0.5f // Độ rộng cột
                chart.invalidate() // Vẽ lại
            } else {
                chart.clear()
            }
        }
    )
}