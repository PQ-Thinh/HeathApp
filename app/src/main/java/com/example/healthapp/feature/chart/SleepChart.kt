// File: SleepChart.kt
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
import com.example.healthapp.core.helperEnumAndData.ChartTimeRange
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.format.DateTimeFormatter

@Composable
fun SleepChart(
    data: List<SleepBucket>,
    timeRange: ChartTimeRange
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF1E293B)) // Nền tối
            .padding(16.dp),
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setDrawGridBackground(false)

                // Cấu hình Zoom/Touch
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)

                // Hiệu ứng
                animateY(1000)

                // --- Trục X (Thời gian) ---
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.LTGRAY
                    textSize = 10f
                    granularity = 1f // Đảm bảo không bị lặp label
                    labelRotationAngle = 0f
                }

                // --- Trục Y (Ẩn đi cho đỡ rối, vì đã hiện số trên cột) ---
                axisLeft.isEnabled = false
                axisRight.isEnabled = false

                // Thông báo khi không có dữ liệu
                setNoDataText("Chưa có dữ liệu giấc ngủ")
                setNoDataTextColor(Color.WHITE)
            }
        },
        update = { chart ->
            if (data.isNotEmpty()) {
                val entries = data.mapIndexed { index, bucket ->
                    // BarEntry nhận vào (index, giá_trị_float)
                    BarEntry(index.toFloat(), bucket.totalMinutes.toFloat())
                }

                val dataSet = BarDataSet(entries, "Giấc ngủ").apply {
                    color = Color.parseColor("#8B5CF6") // Màu tím (Purple)
                    valueTextColor = Color.WHITE
                    valueTextSize = 10f

                    // --- FORMATTER 1: Hiển thị "7h 30m" trên đỉnh cột ---
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val totalMinutes = value.toInt()
                            if (totalMinutes == 0) return ""
                            val h = totalMinutes / 60
                            val m = totalMinutes % 60
                            return if (h > 0) "${h}h ${m}m" else "${m}m"
                        }
                    }
                }

                chart.data = BarData(dataSet).apply {
                    barWidth = 0.5f // Độ rộng cột (0.1 -> 1.0)
                }

                // --- FORMATTER 2: Trục X hiển thị thời gian ---
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index >= 0 && index < data.size) {
                            val time = data[index].startTime
                            return when (timeRange) {
                                ChartTimeRange.WEEK -> time.format(DateTimeFormatter.ofPattern("EEE")) // T2, T3...
                                ChartTimeRange.MONTH -> time.format(DateTimeFormatter.ofPattern("dd")) // Ngày 01, 02...
                                ChartTimeRange.YEAR -> time.format(DateTimeFormatter.ofPattern("MM"))  // Tháng 01, 02...
                                else -> ""
                            }
                        }
                        return ""
                    }
                }

                // Cập nhật view range
                chart.xAxis.axisMaximum = data.size.toFloat() - 0.5f
                chart.xAxis.axisMinimum = -0.5f

                // Zoom mặc định nếu quá nhiều cột (ví dụ tháng/năm)
                if (data.size > 7) {
                    chart.setVisibleXRangeMaximum(7f) // Chỉ hiện 7 cột 1 lúc
                    chart.moveViewToX(data.size.toFloat()) // Scroll tới cuối
                }

                chart.notifyDataSetChanged()
                chart.invalidate()
            } else {
                chart.clear()
            }
        }
    )
}