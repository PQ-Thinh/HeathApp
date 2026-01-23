package com.example.healthapp.feature.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.core.model.entity.DailyHealthEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HeartRateChart(
    data: List<DailyHealthEntity>,
    barColor: Color = Color(0xFFEF4444) // Màu đỏ cho tim
) {
    // 1. Chuẩn bị dữ liệu: Đảm bảo đủ 7 ngày (nếu thiếu thì điền 0)
    val chartData = remember(data) {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val displayFormatter = DateTimeFormatter.ofPattern("dd/MM")
        val today = LocalDate.now()

        // Tạo danh sách 7 ngày gần nhất từ quá khứ -> hiện tại
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val dateString = date.toString()

            // Tìm trong DB xem ngày đó có dữ liệu không
            val entity = data.find { it.date == dateString }
            val bpm = entity?.heartRateAvg ?: 0 // Giả sử cột trong Entity là heartRateAvg

            Pair(date.format(displayFormatter), bpm)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp)
    ) {
        Text(
            "Biểu đồ nhịp tim (7 ngày)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxBpm = 160f // Max trục Y (để tính tỷ lệ)
            val barWidth = size.width / (chartData.size * 2f)
            val spacing = size.width / chartData.size

            chartData.forEachIndexed { index, (day, bpm) ->
                val x = index * spacing + (spacing - barWidth) / 2
                // Tính chiều cao cột dựa trên BPM (bpm / max * height)
                val barHeight = (bpm / maxBpm) * size.height

                // 1. Vẽ Cột
                if (bpm > 0) {
                    drawRect(
                        color = barColor,
                        topLeft = Offset(x, size.height - barHeight),
                        size = Size(barWidth, barHeight)
                    )

                    // Vẽ số BPM trên đầu cột
                    drawContext.canvas.nativeCanvas.apply {
                        drawText(
                            bpm.toString(),
                            x + barWidth / 2,
                            size.height - barHeight - 10f,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 30f
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                        )
                    }
                }

                // 2. Vẽ Ngày tháng bên dưới
                drawContext.canvas.nativeCanvas.apply {
                    drawText(
                        day,
                        x + barWidth / 2,
                        size.height + 40f,
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.LTGRAY
                            textSize = 24f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
            }
        }
    }
}