package com.example.healthapp.feature.chart

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.healthapp.core.data.HeartRateBucket
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.viewmodel.MainViewModel


@Composable
fun AdvancedHeartChart(
    data: List<HeartRateBucket>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Chưa có dữ liệu", color = Color.Gray)
        }
        return
    }

    val maxBpm = remember(data) { (data.maxOfOrNull { it.max } ?: 150).toFloat() + 10 }
    val minBpm = remember(data) { (data.minOfOrNull { it.min } ?: 40).toFloat() - 10 }

    // Màu sắc
    val barColor = Color(0xFFEF4444).copy(alpha = 0.5f) // Màu thanh Min-Max (nhạt)
    val avgLineColor = Color(0xFFEF4444) // Màu điểm Avg (đậm)

    Canvas(modifier = modifier.padding(16.dp)) {
        val widthPerBar = size.width / data.size
        val barWidth = widthPerBar * 0.4f // Độ rộng thanh = 40% khoảng cách

        data.forEachIndexed { index, bucket ->
            val x = index * widthPerBar + (widthPerBar / 2)

            // Tính tọa độ Y (Lưu ý: Canvas Y=0 nằm ở trên cùng)
            // Công thức: y = height - ((value - minScale) / (maxScale - minScale) * height)

            val yMax = size.height - ((bucket.max - minBpm) / (maxBpm - minBpm) * size.height)
            val yMin = size.height - ((bucket.min - minBpm) / (maxBpm - minBpm) * size.height)
            val yAvg = size.height - ((bucket.avg - minBpm) / (maxBpm - minBpm) * size.height)

            // 1. Vẽ thanh Range (từ Min đến Max)
            // Dùng Brush để tạo hiệu ứng Gradient cho đẹp
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x - barWidth / 2, yMax),
                size = Size(barWidth, yMin - yMax),
                cornerRadius = CornerRadius(10f)
            )

            // 2. Vẽ điểm Trung bình (Average)
            drawCircle(
                color = avgLineColor,
                radius = barWidth / 1.5f,
                center = Offset(x, yAvg)
            )
//            // 2. Vẽ Ngày tháng bên dưới
//            drawContext.canvas.nativeCanvas.apply {
//                drawText(
//                    day,
//                    x + barWidth / 2,
//                    size.height + 40f,
//                    android.graphics.Paint().apply {
//                        color = android.graphics.Color.LTGRAY
//                        textSize = 24f
//                        textAlign = android.graphics.Paint.Align.CENTER
//                    }
//                )
//            }
        }
    }
}
@Composable
fun HeartRateOptionChart(viewModel: MainViewModel) {
    val chartData by viewModel.chartData.collectAsState()
    val timeRange by viewModel.selectedTimeRange.collectAsState()

    Column {
        // Selector Ngày/Tuần/Tháng
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ChartTimeRange.values().forEach { range ->
                FilterChip(
                    selected = range == timeRange,
                    onClick = { viewModel.setTimeRange(range) },
                    label = { Text(range.name) }
                )
            }
        }

        // Biểu đồ
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.Black.copy(0.05f), RoundedCornerShape(16.dp))
        ) {
            AdvancedHeartChart(
                data = chartData,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}