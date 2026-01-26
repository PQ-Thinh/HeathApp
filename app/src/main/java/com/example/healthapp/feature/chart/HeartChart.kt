package com.example.healthapp.feature.chart

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.format.DateTimeFormatter

@Composable
fun HeartChart(
    data: List<HeartRateBucket>,
    timeRange: ChartTimeRange,
    lineColor: Int = android.graphics.Color.parseColor("#EF4444") // Màu đỏ
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp) // Tăng chiều cao xíu cho thoáng
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF1E293B)) // Nền tối
            .padding(8.dp),
        factory = { context ->
            LineChart(context).apply {
                // --- Cấu hình chung ---
                description.isEnabled = false
                legend.isEnabled = false
                setDrawGridBackground(false)
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                animateX(1000) // Animation chạy từ trái sang phải

                // --- Trục X (Thời gian) ---
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false) // Bỏ lưới dọc
                    textColor = Color.LTGRAY
                    textSize = 11f
                    granularity = 1f
                    // Thêm khoảng đệm 2 đầu để điểm đầu/cuối không bị cắt
                    axisMinimum = -0.5f
                }

                // --- Trục Y (BPM) ---
                axisLeft.apply {
                    setDrawGridLines(true) // Giữ lưới ngang để dễ so sánh mức BPM
                    gridColor = Color.parseColor("#33FFFFFF") // Lưới mờ
                    textColor = Color.LTGRAY
                    axisMinimum = 40f // Nhịp tim thường > 40, set 0 sẽ bị khoảng trống lớn bên dưới
                    setDrawAxisLine(false)
                }
                axisRight.isEnabled = false // Tắt trục phải
                extraBottomOffset = 10f
            }
        },
        update = { chart ->
            if (data.isNotEmpty()) {
                val entries = data.mapIndexed { index, bucket ->
                    // Dùng Entry cho LineChart
                    Entry(index.toFloat(), bucket.avg.toFloat())
                }

                val dataSet = LineDataSet(entries, "Nhịp tim").apply {
                    color = lineColor
                    lineWidth = 3f // Đường kẻ dày hơn chút cho rõ

                    // --- Hiệu ứng đường cong mềm mại ---
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    cubicIntensity = 0.2f

                    // --- Điểm tròn trên đường ---
                    setDrawCircles(true)
                    setCircleColor(lineColor)
                    circleRadius = 4f
                    setDrawCircleHole(true)
                    circleHoleRadius = 2f
                    circleHoleColor = Color.WHITE // Lỗ tròn màu trắng nổi bật

                    setDrawValues(true)
                    valueTextColor = Color.WHITE
                    valueTextSize = 10f

                    // Format số để bỏ phần thập phân (VD: 75.0 -> 75)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return value.toInt().toString()
                        }
                    }
                    // --- Tô màu nền bên dưới (Gradient) ---
                    setDrawFilled(true)
                    val gradientDrawable = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(lineColor, Color.TRANSPARENT) // Nhạt dần xuống dưới
                    )
                    fillDrawable = gradientDrawable
                }

                chart.data = LineData(dataSet)

                // Cập nhật trục X Max để view vừa vặn
                chart.xAxis.axisMaximum = (data.size - 0.5).toFloat()

                // Formatter ngày tháng
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index >= 0 && index < data.size) {
                            val time = data[index].startTime
                            return when (timeRange) {

                                ChartTimeRange.DAY -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
                                ChartTimeRange.WEEK -> time.format(DateTimeFormatter.ofPattern("EEE"))
                                ChartTimeRange.MONTH -> time.format(DateTimeFormatter.ofPattern("dd"))
                                ChartTimeRange.YEAR -> time.format(DateTimeFormatter.ofPattern("MM"))
                            }
                        }
                        return ""
                    }
                }

                // Zoom chart để hiển thị điểm cuối cùng rõ hơn nếu cần
                chart.setVisibleXRangeMaximum(7f) // Ví dụ chỉ hiện 7 điểm 1 lúc nếu quá nhiều
                chart.moveViewToX(data.size.toFloat()) // Tự scroll đến điểm mới nhất
            } else {
                chart.clear()
            }
        }
    )
}