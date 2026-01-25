package com.example.healthapp.feature.detail

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.viewmodel.HeartViewModel
import com.example.healthapp.feature.chart.HeartChart
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.ui.theme.AestheticColors

@Composable
fun HeartDetailScreen(
    onBackClick: () -> Unit,
    isDarkTheme: Boolean,
    heartViewModel: HeartViewModel = hiltViewModel(),
    modifier: Modifier,
    onHeartRateClick: () -> Unit
) {
    // Collect State từ ViewModel
    val latestHeartRate by heartViewModel.latestHeartRate.collectAsState()
    val chartData by heartViewModel.heartRateData.collectAsState()
    val assessment by heartViewModel.assessment.collectAsState()
    val selectedTimeRange by heartViewModel.selectedTimeRange.collectAsState()

    // Setup Theme màu sắc
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic
    val heartColor = Color(0xFFFF5252) // Màu đỏ đặc trưng cho tim

    // Animation nền (Giống Sleep)
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Vẽ nền động
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(heartColor.copy(0.1f), Color.Transparent),
                    center = Offset(size.width * 0.8f, floatAnim % size.height)
                ),
                radius = 500f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb1.copy(0.1f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height - (floatAnim % size.height))
                ),
                radius = 600f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = { HeartTopBar(onBackClick, colors) }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Card hiển thị BPM và Đánh giá
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.glassContainer)
                            .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = heartColor,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "$latestHeartRate BPM",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = assessment,
                                fontSize = 18.sp,
                                color = if (latestHeartRate in 60..100) Color.Green else Color(
                                    0xFFFFCC00
                                )
                            )
                        }
                    }
                }

                // 2. Thanh chọn Ngày - Tuần - Tháng - Năm
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ChartTimeRange.values().forEach { range ->
                            FilterChip(
                                selected = range == selectedTimeRange,
                                onClick = { heartViewModel.setTimeRange(range) },
                                label = {
                                    Text(
                                        when (range) {
                                            ChartTimeRange.DAY -> "Ngày"
                                            ChartTimeRange.WEEK -> "Tuần"
                                            ChartTimeRange.MONTH -> "Tháng"
                                            ChartTimeRange.YEAR -> "Năm"
                                        }
                                    )
                                }
                            )
                        }
                    }
                }

                // 3. Biểu đồ Cột (MPAndroidChart)
                item {
                    Text(
                        "Biểu đồ nhịp tim",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                        // .background(...) đã xử lý bên trong HeartChart
                    ) {
                        HeartChart(
                            data = chartData,
                            timeRange = selectedTimeRange,
                            barColor = android.graphics.Color.parseColor("#EF4444")
                        )
                    }
                }

                // 4. Nút Đo Nhịp Tim
                item {
                    Button(
                        onClick = onHeartRateClick,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = heartColor
                        )
                    ) {
                        Text("Đo nhịp tim ngay")
                    }
                }
            }
        }
    }
}
@Composable
fun HeartTopBar(onBackClick: () -> Unit, colors: AestheticColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
        }
        Text(
            text = "Nhịp Tim",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                // Giảm bóng đổ ở Light mode để trông sạch hơn
                shadow = if (colors.background == DarkAesthetic.background)
                    Shadow(Color.Black.copy(0.3f), blurRadius = 4f)
                else null
            ),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}