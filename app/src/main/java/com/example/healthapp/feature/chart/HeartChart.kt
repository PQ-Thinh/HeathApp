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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import java.time.format.DateTimeFormatter

@Composable
fun HeartChart(
    data: List<HeartRateBucket>,
    timeRange: ChartTimeRange
) {
    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp) // Tăng chiều cao để chứa Legend
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF1E293B))
            .padding(8.dp),
        factory = { context ->
            LineChart(context).apply {
//                description.text = "Biểu đồ nhịp tim (Min/Max/Avg)"
//                description.textColor = Color.WHITE
//                description.textSize = 12f
                setDrawGridBackground(false)
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)  // Cho phép zoom (cả 2 trục)
                setPinchZoom(true)     // Cho phép dùng 2 ngón tay để zoom
                animateX(1000)

                // --- Legend (Chú thích) ---
                legend.apply {
                    isEnabled = true
                    textColor = Color.WHITE
                    textSize = 12f
                    form = Legend.LegendForm.LINE
                    horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    yEntrySpace = 10f
                }

                // --- Trục X ---
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.LTGRAY
                    textSize = 10f
                    granularity = 1f
                    labelRotationAngle = -45f // Xoay chữ nhẹ nếu dày quá
                }

                // --- Trục Y ---
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#33FFFFFF")
                    textColor = Color.LTGRAY
                    axisMinimum = 40f
                }
                axisRight.isEnabled = false
                setNoDataText("Chưa có dữ liệu")
                setNoDataTextColor(Color.WHITE)
            }
        },
        update = { chart ->
            if (data.isNotEmpty()) {
                val entriesMin = ArrayList<Entry>()
                val entriesMax = ArrayList<Entry>()
                val entriesAvg = ArrayList<Entry>()

                data.forEachIndexed { index, bucket ->
                    entriesMin.add(Entry(index.toFloat(), bucket.min.toFloat()))
                    entriesMax.add(Entry(index.toFloat(), bucket.max.toFloat()))
                    entriesAvg.add(Entry(index.toFloat(), bucket.avg.toFloat()))
                }

                // Helper function tạo LineDataSet
                fun createSet(entries: List<Entry>, label: String, color: Int): LineDataSet {
                    return LineDataSet(entries, label).apply {
                        this.color = color
                        setCircleColor(color)
                        lineWidth = 2f
                        circleRadius = 3f
                        setDrawCircleHole(false)
                        setDrawValues(false) // Ẩn số trên line cho đỡ rối
                        mode = LineDataSet.Mode.CUBIC_BEZIER // Đường cong
                    }
                }

                // Màu sắc: Min (Xanh dương), Max (Đỏ), Avg (Xanh lá)
                val setMin = createSet(entriesMin, "Min", Color.parseColor("#3B82F6")) // Blue
                val setMax = createSet(entriesMax, "Max", Color.parseColor("#EF4444")) // Red
                val setAvg = createSet(entriesAvg, "Avg", Color.parseColor("#22C55E")) // Green

                val dataSets = ArrayList<ILineDataSet>()
                dataSets.add(setMin)
                dataSets.add(setMax)
                dataSets.add(setAvg)

                chart.data = LineData(dataSets)

                // Formatter ngày tháng
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        val index = value.toInt()
                        if (index >= 0 && index < data.size) {
                            val time = data[index].startTime
                            return when (timeRange) {
                                ChartTimeRange.DAY -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
                                ChartTimeRange.WEEK -> time.format(DateTimeFormatter.ofPattern("dd/MM"))
                                ChartTimeRange.MONTH -> time.format(DateTimeFormatter.ofPattern("dd"))
                                ChartTimeRange.YEAR -> time.format(DateTimeFormatter.ofPattern("MM/yy"))
                            }
                        }
                        return ""
                    }
                }

                chart.xAxis.axisMaximum = (data.size - 1).toFloat() + 0.5f // Padding phải
                chart.xAxis.axisMinimum = -0.5f // Padding trái

                chart.notifyDataSetChanged()
                chart.invalidate()
            } else {
                chart.clear()
            }
        }
    )
}