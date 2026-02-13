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
import com.example.healthapp.core.helperEnumAndData.ChartTimeRange
import com.github.mikephil.charting.charts.LineChart
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
            .height(320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color(0xFF1E293B))
            .padding(8.dp),
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                setDrawGridBackground(false)
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                animateX(1000)

                // --- Legend (Chú thích) ---
                legend.isEnabled = false // Ẩn chú thích vì chỉ có 1 đường

                // --- Trục X ---
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = Color.LTGRAY
                    textSize = 10f
                    granularity = 1f
                    labelRotationAngle = -45f
                }

                // --- Trục Y ---
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#33FFFFFF")
                    textColor = Color.LTGRAY
                    axisMinimum = 40f // Giới hạn dưới hợp lý cho nhịp tim
                }
                axisRight.isEnabled = false

                setNoDataText("Chưa có dữ liệu")
                setNoDataTextColor(Color.WHITE)
            }
        },
        update = { chart ->
            if (data.isNotEmpty()) {
                // CHỈ LẤY DỮ LIỆU AVG (TRUNG BÌNH) ĐỂ VẼ 1 ĐƯỜNG
                val entriesAvg = ArrayList<Entry>()

                data.forEachIndexed { index, bucket ->
                    // Sử dụng giá trị trung bình (avg) làm điểm dữ liệu chính
                    if (bucket.avg > 0) {
                        entriesAvg.add(Entry(index.toFloat(), bucket.avg.toFloat()))
                    }
                }

                if (entriesAvg.isNotEmpty()) {
                    val lineDataSet = LineDataSet(entriesAvg, "Nhịp tim").apply {
                        color = Color.parseColor("#EF4444") // Màu Đỏ cho tim
                        setCircleColor(Color.parseColor("#EF4444"))
                        lineWidth = 3f
                        circleRadius = 4f
                        setDrawCircleHole(false)
                        setDrawValues(false)
                        mode = LineDataSet.Mode.CUBIC_BEZIER // Đường cong mềm mại

                        // Thêm hiệu ứng Fill màu bên dưới đường (Gradient)
                        setDrawFilled(true)
                        fillDrawable = GradientDrawable(
                            GradientDrawable.Orientation.TOP_BOTTOM,
                            intArrayOf(
                                Color.parseColor("#80EF4444"), // Đỏ nhạt (trong suốt 50%)
                                Color.parseColor("#00EF4444")  // Trong suốt hoàn toàn
                            )
                        )
                    }

                    val dataSets = ArrayList<ILineDataSet>()
                    dataSets.add(lineDataSet)

                    chart.data = LineData(dataSets)

                    // Formatter ngày tháng cho trục X
                    chart.xAxis.valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val index = value.toInt()
                            if (index >= 0 && index < data.size) {
                                val time = data[index].startTime
                                return when (timeRange) {
                                    ChartTimeRange.DAY -> time.format(DateTimeFormatter.ofPattern("HH:mm"))
                                    ChartTimeRange.WEEK -> time.format(DateTimeFormatter.ofPattern("EEE"))
                                    ChartTimeRange.MONTH -> time.format(DateTimeFormatter.ofPattern("dd"))
                                    ChartTimeRange.YEAR -> time.format(DateTimeFormatter.ofPattern("MM/yy"))
                                }
                            }
                            return ""
                        }
                    }

                    chart.xAxis.axisMaximum = (data.size - 1).toFloat() + 0.5f
                    chart.xAxis.axisMinimum = -0.5f

                    chart.notifyDataSetChanged()
                    chart.invalidate()
                } else {
                    chart.clear()
                }
            } else {
                chart.clear()
            }
        }
    )
}