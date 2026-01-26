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
import com.example.healthapp.core.data.StepBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StepChart(
    data: List<StepBucket>,
    timeRange: ChartTimeRange
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF1E293B))
            .padding(16.dp),
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setDrawGridBackground(false)

                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                animateY(1000)

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.LTGRAY
                    textSize = 10f
                    granularity = 1f
                }

                axisLeft.isEnabled = false
                axisRight.isEnabled = false

                setNoDataText("Chưa có dữ liệu bước chân")
                setNoDataTextColor(Color.WHITE)
            }
        },
        update = { chart ->
            if (data.isNotEmpty()) {
                val entries = data.mapIndexed { index, bucket ->
                    BarEntry(index.toFloat(), bucket.totalSteps.toFloat())
                }

                val dataSet = BarDataSet(entries, "Steps").apply {
                    color = Color.parseColor("#F59E0B") // Màu Cam (Amber) cho năng động
                    valueTextColor = Color.WHITE
                    valueTextSize = 10f

                    // Formatter: Hiển thị số bước (VD: 1500)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return value.toInt().toString()
                        }
                    }
                }

                chart.data = BarData(dataSet).apply {
                    barWidth = 0.5f
                }

                // Trục X hiển thị ngày tháng
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index in data.indices) {
                            val time = data[index].startTime
                            return when (timeRange) {
                                ChartTimeRange.WEEK -> time.format(DateTimeFormatter.ofPattern("EEE", Locale("vi", "VN")))
                                ChartTimeRange.MONTH -> time.format(DateTimeFormatter.ofPattern("dd"))
                                ChartTimeRange.YEAR -> "T${time.monthValue}"
                                else -> ""
                            }
                        }
                        return ""
                    }
                }

                chart.xAxis.axisMaximum = data.size.toFloat() - 0.5f
                chart.xAxis.axisMinimum = -0.5f

                if (data.size > 7) {
                    chart.setVisibleXRangeMaximum(7f)
                    chart.moveViewToX(data.size.toFloat())
                } else {
                    chart.fitScreen()
                }

                chart.notifyDataSetChanged()
                chart.invalidate()
            } else {
                chart.clear()
            }
        }
    )
}