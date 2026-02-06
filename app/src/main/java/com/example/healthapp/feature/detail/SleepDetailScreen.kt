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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.model.entity.SleepSessionEntity
import com.example.healthapp.core.viewmodel.SleepViewModel
import com.example.healthapp.feature.chart.SleepChart
import com.example.healthapp.feature.components.GenericHistoryDialog
import com.example.healthapp.feature.components.SleepSettingDialog
import com.example.healthapp.feature.components.TopBar
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic
import java.time.LocalTime
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

@Composable
fun SleepDetailScreen(
    onBackClick: () -> Unit,
    sleepViewModel: SleepViewModel = hiltViewModel(),
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {


    // Collect State
    val historyList by sleepViewModel.sleepHistory.collectAsStateWithLifecycle()
    val duration by sleepViewModel.sleepDuration.collectAsState()
    val assessment by sleepViewModel.sleepAssessment.collectAsState()
    val chartData by sleepViewModel.chartData.collectAsState()
    val selectedTimeRange by sleepViewModel.selectedTimeRange.collectAsState()

    // State điều khiển Dialogs
    var showSleepDialog by remember { mutableStateOf(false) } // Dialog nhập giờ ngủ
    var showHistoryDialog by remember { mutableStateOf(false) } // Dialog xem lịch sử

    // Theme Setup
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic
    val accentColor = Color(0xFF6366F1) // Màu tím đặc trưng cho giấc ngủ

    var recordToEdit by remember { mutableStateOf<SleepSessionEntity?>(null) }

    // Animation nền
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

    // --- DIALOG LỊCH SỬ (XEM THÊM) ---
    if (showHistoryDialog) {
        GenericHistoryDialog(
            title = "Lịch sử Giấc ngủ",
            dataList = historyList,
            onDismiss = { showHistoryDialog = false },
            onDelete = { session -> sleepViewModel.deleteSleepRecord(session) },
            isDarkTheme = isDarkTheme,
            dateExtractor = { it.startTime },
            onItemClick = {},
            onEdit = {
                recordToEdit = it
            },
            itemContent = { item, textColor ->
                // Nội dung trong Dialog
                val start = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(item.startTime))
                val end = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(item.endTime))
                val totalHours = (item.endTime - item.startTime) / (1000 * 60 * 60f)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = String.format("%.1f giờ", totalHours),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Text(
                            text = "$start - $end",
                            fontSize = 13.sp,
                            color = textColor.copy(0.6f)
                        )
                    }
                }
            }
        )
    }

    if (showSleepDialog || recordToEdit != null) {
        // Chuẩn bị dữ liệu khởi tạo
        val initialDate = if (recordToEdit != null)
            Instant.ofEpochMilli(recordToEdit!!.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
        else LocalDate.now()

        val startLocal = if (recordToEdit != null)
            Instant.ofEpochMilli(recordToEdit!!.startTime).atZone(ZoneId.systemDefault()).toLocalTime()
        else LocalTime.of(22,0)

        val endLocal = if (recordToEdit != null)
            Instant.ofEpochMilli(recordToEdit!!.endTime).atZone(ZoneId.systemDefault()).toLocalTime()
        else LocalTime.of(7,0)

        SleepSettingDialog(
            onDismiss = {
                showSleepDialog = false
                recordToEdit = null
            },
            initialDate = initialDate,
            initialStartHour = startLocal.hour,
            initialStartMinute = startLocal.minute,
            initialEndHour = endLocal.hour,
            initialEndMinute = endLocal.minute,
            isEditing = recordToEdit != null,
            onSave = { date, sH, sM, eH, eM ->
                if (recordToEdit != null) {
                    // EDIT
                    sleepViewModel.editSleepSession(recordToEdit!!, date, sH, sM, eH, eM)
                } else {
                    // ADD NEW
                    sleepViewModel.saveSleepTime(date, sH, sM, eH, eM)
                }
                showSleepDialog = false
                recordToEdit = null
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Background Animation
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
            topBar = { TopBar(onBackClick, colors,"Giấc ngủ") },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showSleepDialog = true },
                    containerColor = accentColor,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Bedtime, contentDescription = "Nhập giờ ngủ")
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 1. Card Tổng quan
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
                            Text(
                                text = "Thời lượng ngủ hôm nay",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row() {
                                Icon(
                                    Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = sleepViewModel.formatDuration(duration),
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }

                            Text(
                                text = "Đánh giá: $assessment",
                                fontSize = 18.sp,
                                color = if (assessment.contains("Tốt") || assessment.contains("Khá")) Color(0xFF4CAF50) else Color(0xFFFFC107)
                            )
                        }
                    }
                }

                // 2. Thanh chọn Thời gian & Biểu đồ
                item {
                    Text(
                        "Biểu đồ giấc ngủ",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                        fontWeight = FontWeight.Bold
                    )

                    TimeRangeSelector(
                        selectedRange = selectedTimeRange,
                        onRangeSelected = { newRange ->
                            sleepViewModel.setTimeRange(newRange)
                        },
                        activeColor = accentColor,
                        inactiveColor = colors.textSecondary
                    )
                }

                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.glassContainer)
                            .padding(16.dp)
                    ) {
                        SleepChart(
                            data = chartData,
                            timeRange = selectedTimeRange
                        )
                    }
                }

                // 4. Lịch sử (Giao diện mới)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lịch sử giấc ngủ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        if (historyList.size > 3) {
                            TextButton(onClick = { showHistoryDialog = true }) {
                                Text("Xem thêm", color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (historyList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Chưa có dữ liệu giấc ngủ", color = colors.textSecondary)
                        }
                    } else {
                        // HIỂN THỊ TỐI ĐA 3 DÒNG
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            historyList.take(3).forEach { session ->
                                SimpleSleepHistoryRow(
                                    session = session,
                                    colors = colors,
                                    accentColor = accentColor,
                                    onDelete = {sleepViewModel.deleteSleepRecord(session)},
                                    onEdit = {
                                        recordToEdit = session
                                    }
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

// Composable hiển thị 1 dòng lịch sử giấc ngủ (Ngoài màn hình chính)
@Composable
fun SimpleSleepHistoryRow(
    session: SleepSessionEntity,
    colors: AestheticColors,
    accentColor: Color,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    var context = LocalContext.current
    val isMyData = session.source == context.packageName
    var expanded by remember { mutableStateOf(false) } // State Menu

    val start = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.startTime))
    val end = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.endTime))
    val date = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(session.startTime))

    val durationMillis = session.endTime - session.startTime
    val h = durationMillis / (1000 * 60 * 60)
    val m = (durationMillis / (1000 * 60)) % 60

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.glassContainer)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "$start - $end",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 16.sp
                )
                Text(
                    text = "$date • ${h}h ${m}m", // Gộp ngày và thời lượng cho gọn
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        // --- MENU XÓA ---
        if (isMyData) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = null, tint = colors.textSecondary)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Sửa") },
                        onClick = { expanded = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Xóa", color = Color.Red) },
                        onClick = { expanded = false; onDelete() },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    )
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