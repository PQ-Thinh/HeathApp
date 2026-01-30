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
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.feature.chart.StepChart
import com.example.healthapp.feature.components.CustomTopMenu
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthapp.core.service.StepForegroundService
import com.example.healthapp.feature.components.HistoryListSection
import com.example.healthapp.feature.components.formatDateTime

@Composable
fun StepDetailScreen(
    onBackClick: () -> Unit,
    mainViewModel: MainViewModel, // Dùng để lấy số bước realtime hôm nay
    stepViewModel: StepViewModel = hiltViewModel(), // Dùng cho Chart
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val historyList by stepViewModel.stepHistory.collectAsStateWithLifecycle()
    // Lấy dữ liệu Realtime
    val currentSteps by mainViewModel.realtimeSteps.collectAsState()
    val currentMode by mainViewModel.currentMode.collectAsState()
    // Lấy dữ liệu Chart
    val chartData by stepViewModel.chartData.collectAsState()
    val selectedTimeRange by stepViewModel.selectedTimeRange.collectAsState()

    val currentCalories = stepViewModel.calculateCalories(currentSteps.toLong())
    val context = LocalContext.current
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic
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
                    colors = listOf(colors.gradientOrb1.copy(0.15f), Color.Transparent),
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
            topBar = { StepTopBar(onBackClick, colors) }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.padding(paddingValues).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                item {

                    // Card Tổng quan (Steps + Calories)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(colors.glassContainer)
                            .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(24.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Cột Bước chân
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.DirectionsRun,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B), // Màu cam
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "$currentSteps",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text("Bước", color = colors.textSecondary)
                            }
                            // Đường kẻ dọc
                            Divider(
                                modifier = Modifier
                                    .height(60.dp)
                                    .width(1.dp),
                                color = colors.textSecondary.copy(alpha = 0.5f)
                            )

                            // Cột Calories
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444), // Màu đỏ lửa
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "$currentCalories",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                                Text("Kcal", color = colors.textSecondary)
                            }
                        }
                    }
                }

                item {
                    // Chart Title
                    Text(
                        "Thống kê hoạt động",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Selector (Tái sử dụng component TimeRangeSelector bạn đã có)
                    TimeRangeSelector(
                        selectedRange = selectedTimeRange,
                        onRangeSelected = { stepViewModel.setTimeRange(it) },
                        activeColor = Color(0xFFF59E0B), // Màu cam Active
                        inactiveColor = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Chart
                    StepChart(
                        data = chartData,
                        timeRange = selectedTimeRange
                    )


                }
                item {
                    Text(
                        "Chế độ luyện tập",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp, start = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CustomTopMenu(
                        colors = colors,
                        selectedMode = currentMode, // UI sẽ tự cập nhật khi DataStore thay đổi
                        onOptionSelected = { selected ->
                            // Gửi lệnh cho Service
                            val intent = Intent(context, StepForegroundService::class.java).apply {
                                action = StepForegroundService.ACTION_SWITCH_MODE
                                putExtra(StepForegroundService.EXTRA_MODE, selected)
                            }
                            if (android.os.Build.VERSION.SDK_INT >= 26) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                }
                item {
                    HistoryListSection(
                        title = "Lịch sử Hoạt Động",
                        historyData = historyList
                    ) { record ->
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDateTime(record.startTime ?: 0L),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${record.count} steps",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
    @Composable
    fun StepTopBar(onBackClick: () -> Unit, colors: AestheticColors) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textPrimary
                )
            }
            Text(
                text = "Bước Đếm",
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