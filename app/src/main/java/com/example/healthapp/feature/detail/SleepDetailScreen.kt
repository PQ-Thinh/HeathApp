package com.example.healthapp.feature.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.viewmodel.SleepViewModel
import com.example.healthapp.feature.chart.SleepChart
import com.example.healthapp.feature.components.HistoryListSection
import com.example.healthapp.feature.components.SleepSettingDialog
import com.example.healthapp.feature.components.formatDateTime
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic
import java.time.Duration

@Composable
fun SleepDetailScreen(
    onBackClick: () -> Unit,
    sleepViewModel: SleepViewModel = hiltViewModel(),
    isDarkTheme: Boolean,
    modifier: Modifier
) {

    val historyList by sleepViewModel.sleepHistory.collectAsStateWithLifecycle()
    val duration by sleepViewModel.sleepDuration.collectAsState()
    val assessment by sleepViewModel.sleepAssessment.collectAsState()
    val chartData by sleepViewModel.chartData.collectAsState()
    var showSleepDialog by remember { mutableStateOf(false) }
    val selectedTimeRange by sleepViewModel.selectedTimeRange.collectAsState()
    // Lấy bộ màu dựa trên Theme hiện tại
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic

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
        modifier = modifier
            .fillMaxSize()
            .background(colors.background) // 1. Màu nền động
    ) {
        // Background Animation (Giữ nguyên logic)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb1.copy(0.15f), Color.Transparent),
                    center = Offset(size.width * 0.2f, floatAnim % size.height)
                ),
                radius = 600f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb2.copy(0.15f), Color.Transparent),
                    center = Offset(size.width - (floatAnim % size.width), size.height * 0.5f)
                ),
                radius = 700f
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { SleepTopBar(onBackClick, colors) }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.glassContainer)
                            .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ){
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Bedtime,
                                contentDescription = null,
                                tint = colors.accent, // 5. Màu Accent
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = sleepViewModel.formatDuration(duration),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary // Text chính
                            )
                            Text(
                                text = "Đánh giá: $assessment",
                                fontSize = 18.sp,
                                color = if (assessment.contains("Tốt")) Color(0xFF4CAF50) else Color(
                                    0xFFFFC107
                                )
                            )
                        }
                    }
                }
                // Card Tổng quan (Assessment)
                item {
                    Text(
                        "Biểu đồ giấc ngủ",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    TimeRangeSelector(
                        selectedRange = selectedTimeRange,
                        onRangeSelected = { newRange ->
                            sleepViewModel.setTimeRange(newRange)
                        },
                        activeColor = colors.accent,
                        inactiveColor = colors.textSecondary
                    )
                }

                item {
                    SleepChart(
                        data = chartData,
                        timeRange = selectedTimeRange
                    )
                }

                item {
                    Button(
                        onClick = { showSleepDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent, // 6. Màu nút theo Accent
                            contentColor = Color.White // Chữ trong nút luôn trắng cho nổi bật
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Nhập thời gian ngủ", fontSize = 16.sp)
                    }

                    // Nút mở Setting (Nhập giờ ngủ)
                    // Hiển thị SleepSetting Dialog
                    if (showSleepDialog) {
                        SleepSettingDialog(
                            onDismiss = { showSleepDialog = false },
                            onSave = { sH, sM, eH, eM ->
                                sleepViewModel.saveSleepTime(sH, sM, eH, eM)
                                showSleepDialog = false
                            }
                        )
                    }
                }
                item {
                    HistoryListSection(
                        title = "Lịch sử giấc ngủ",
                        historyData = historyList
                    ) { session ->
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Bắt Đầu: ${formatDateTime(session.startTime ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Text(
                                    text = "Kết Thúc: ${formatDateTime(session.endTime ?: 0L)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                            val durationMin = (session.endTime - session.startTime) / 60000
                            val h = durationMin / 60
                            val m = durationMin % 60
                            Text(
                                text = "${h}h ${m}m",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }
                }
            }
        }


    }
}
@Composable
fun TimeRangeSelector(
    selectedRange: ChartTimeRange,
    onRangeSelected: (ChartTimeRange) -> Unit,
    activeColor: Color,
    inactiveColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(inactiveColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Tạo nút cho từng loại
        listOf(ChartTimeRange.WEEK, ChartTimeRange.MONTH, ChartTimeRange.YEAR).forEach { range ->
            val isSelected = range == selectedRange
            val label = when (range) {
                ChartTimeRange.WEEK -> "Tuần"
                ChartTimeRange.MONTH -> "Tháng"
                ChartTimeRange.YEAR -> "Năm"
                else -> ""
            }

            Button(
                onClick = { onRangeSelected(range) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) activeColor else Color.Transparent,
                    contentColor = if (isSelected) Color.White else inactiveColor
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
                elevation = if (isSelected) ButtonDefaults.buttonElevation(defaultElevation = 2.dp) else null
            ) {
                Text(text = label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
@Composable
fun SleepTopBar(onBackClick: () -> Unit, colors: AestheticColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
        }
        Text(
            text = "Giấc Ngủ",
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