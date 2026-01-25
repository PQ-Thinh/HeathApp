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
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HeartChart(
    data: List<HeartRateBucket>,
    timeRange: ChartTimeRange, // Để định dạng ngày tháng phù hợp
    barColor: Int = android.graphics.Color.parseColor("#EF4444") // Màu đỏ
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
                description.isEnabled = false
                legend.isEnabled = false
                setDrawGridBackground(false)
                animateY(1000)

                // Cấu hình trục X
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.WHITE
                    textSize = 10f
                    granularity = 1f
                }

                // Cấu hình trục Y
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.DKGRAY
                    textColor = Color.WHITE
                    axisMinimum = 0f
                }
                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            if (data.isNotEmpty()) {
                val entries = data.mapIndexed { index, bucket ->
                    // Vẽ cột dựa trên nhịp tim TRUNG BÌNH (AVG)
                    BarEntry(index.toFloat(), bucket.avg.toFloat())
                }

                val dataSet = BarDataSet(entries, "Nhịp tim Avg").apply {
                    color = barColor
                    valueTextColor = Color.WHITE
                    valueTextSize = 10f
                    setDrawValues(true)
                }

                chart.data = BarData(dataSet)
                chart.data.barWidth = 0.6f // Độ rộng cột

                // Cập nhật Formatter cho trục X dựa trên TimeRange
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index >= 0 && index < data.size) {
                            val timeInstant = data[index].startTime
                            val time = timeInstant.atZone(ZoneId.systemDefault()) // chuyển sang ZonedDateTime

                            return when (timeRange) {
                                ChartTimeRange.DAY -> time.format(DateTimeFormatter.ofPattern("HH:mm")) // 14:00
                                ChartTimeRange.WEEK -> time.format(DateTimeFormatter.ofPattern("EEE"))  // Mon, Tue
                                ChartTimeRange.MONTH -> time.format(DateTimeFormatter.ofPattern("dd"))  // 01, 02
                                ChartTimeRange.YEAR -> time.format(DateTimeFormatter.ofPattern("MM"))   // 01, 02
                            }
                        }
                        return ""
                    }
                }


                chart.notifyDataSetChanged()
                chart.invalidate()
            } else {
                chart.clear()
            }
        }
    )
}