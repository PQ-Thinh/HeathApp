package com.example.healthapp.feature.detail

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.healthapp.core.data.responsitory.ChartTimeRange
import com.example.healthapp.core.model.entity.HeartRateRecordEntity
import com.example.healthapp.core.viewmodel.HeartViewModel
import com.example.healthapp.feature.chart.HeartChart
import com.example.healthapp.feature.components.GenericHistoryDialog
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartDetailScreen(
    onBackClick: () -> Unit,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    onHeartRateClick: () -> Unit,
    heartViewModel: HeartViewModel = hiltViewModel(),
) {
    // Collect State từ ViewModel
    val historyList by heartViewModel.heartHistory.collectAsStateWithLifecycle()
    val latestHeartRate by heartViewModel.latestHeartRate.collectAsState()
    val chartData by heartViewModel.heartRateData.collectAsState()
    val assessment by heartViewModel.assessment.collectAsState()
    val selectedTimeRange by heartViewModel.selectedTimeRange.collectAsState()

    var showHistoryDialog by remember { mutableStateOf(false) }

    // Setup Theme màu sắc
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic
    val heartColor = Color(0xFFEF4444) // Màu đỏ đặc trưng cho tim

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

    // --- DIALOG LỊCH SỬ ---
    if (showHistoryDialog) {
        GenericHistoryDialog(
            title = "Lịch sử Nhịp tim",
            dataList = historyList,
            onDismiss = { showHistoryDialog = false },
            onDelete = { record -> heartViewModel.deleteHeartRecord(record) },
            isDarkTheme = isDarkTheme,
            dateExtractor = { it.time },
            onItemClick = {},
            onEdit = {},
            itemContent = { item, textColor ->
                // Nội dung hiển thị trong Dialog (Chi tiết hơn)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = heartColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${item.bpm} BPM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Text(
                            text = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault()).format(Date(item.time)),
                            fontSize = 13.sp,
                            color = textColor.copy(0.6f)
                        )
                    }
                }
            }
        )
    }

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
            topBar = { HeartTopBar(onBackClick, colors) },
            // Nút FAB đo nhịp tim
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onHeartRateClick,
                    containerColor = heartColor,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = "Đo tim")
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp),
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
                            Text(
                                text = "Nhịp tim Trung Bình Hôm Nay",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row() {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = heartColor,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "$latestHeartRate BPM",
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary
                                )
                            }

                            Text(
                                text = assessment,
                                fontSize = 18.sp,
                                color = if (latestHeartRate in 60..100) Color(0xFF22C55E) else Color(0xFFEAB308)
                            )
                        }
                    }
                }

                // 2. Thanh chọn Thời gian (Ngày/Tuần/Tháng/Năm)
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
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = heartColor.copy(alpha = 0.2f),
                                    selectedLabelColor = heartColor
                                )
                            )
                        }
                    }
                }

                // 3. Biểu đồ
                item {
                    Text(
                        "Biểu đồ nhịp tim",
                        color = colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp),
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.glassContainer)
                            .padding(16.dp)
                    ) {
                        HeartChart(
                            data = chartData,
                            timeRange = selectedTimeRange,
                        )
                    }
                }

                // 4. Lịch sử (Thay thế HistoryListSection cũ bằng giao diện mới)
                item {
                    // Header Lịch sử + Nút Xem thêm
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lịch sử đo gần đây",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        // Chỉ hiện nút "Xem thêm" nếu list dài hơn 3
                        if (historyList.size > 3) {
                            TextButton(onClick = { showHistoryDialog = true }) {
                                Text("Xem thêm", color = heartColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (historyList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Chưa có dữ liệu đo", color = colors.textSecondary)
                        }
                    } else {
                        // CHỈ HIỂN THỊ TỐI ĐA 3 ITEMS MỚI NHẤT
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            historyList.take(3).forEach { item ->
                                SimpleHeartHistoryRow(
                                    item = item,
                                    colors = colors,
                                    heartColor = heartColor,
                                    onDelete = { heartViewModel.deleteHeartRecord(item) }
                                )
                            }
                        }
                    }
                }

                // Khoảng trống dưới cùng để không bị FAB che
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

// Composable hiển thị 1 dòng lịch sử đơn giản (ở màn hình chính)
@Composable
fun SimpleHeartHistoryRow(
    item: HeartRateRecordEntity,
    colors: AestheticColors,
    heartColor: Color,
    onDelete: () -> Unit // <--- 1. Thêm tham số callback xóa
) {
    var expanded by remember { mutableStateOf(false) } // <--- 2. State cho menu

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
                    .background(heartColor.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.WatchLater,
                    contentDescription = null,
                    tint = heartColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "${item.bpm} BPM",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 16.sp
                )
                Text(
                    text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.time)),
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }


        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = colors.textSecondary
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(colors.glassContainer) // Background theo theme
            ) {
                DropdownMenuItem(
                    text = { Text("Xóa", color = Color.Red) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    }
                )
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
                shadow = if (colors.background == DarkAesthetic.background)
                    Shadow(Color.Black.copy(0.3f), blurRadius = 4f)
                else null
            ),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}