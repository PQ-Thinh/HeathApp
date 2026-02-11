package com.example.healthapp.feature.detail

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.example.healthapp.core.model.entity.StepRecordEntity
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.feature.chart.StepChart
import com.example.healthapp.feature.components.AddStepDialog
import com.example.healthapp.feature.components.GenericHistoryDialog
import com.example.healthapp.feature.detail.history.StepHistoryDetailDialog
import com.example.healthapp.feature.components.TopBar
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StepDetailScreen(
    onBackClick: () -> Unit,
    mainViewModel: MainViewModel,
    stepViewModel: StepViewModel = hiltViewModel(),
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {

    // Collect State
    val historyList by stepViewModel.stepHistory.collectAsStateWithLifecycle()
    val currentSteps by mainViewModel.realtimeSteps.collectAsState()
    val chartData by stepViewModel.chartData.collectAsState()
    val selectedTimeRange by stepViewModel.selectedTimeRange.collectAsState()
    var selectedRecord by remember { mutableStateOf<StepRecordEntity?>(null) }

    var recordToEdit by remember { mutableStateOf<StepRecordEntity?>(null) }

    val currentCalories = stepViewModel.calculateCalories(currentSteps.toLong())

    val isRefreshing by stepViewModel.isRefreshing.collectAsState()

    // Theme Setup
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic
    val stepColor = Color(0xFFF59E0B)

    // State Dialog
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

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
            title = "Lịch sử Chạy bộ",
            dataList = historyList,
            onDismiss = { showHistoryDialog = false },
            onDelete = { record -> stepViewModel.deleteStepRecord(record) },
            onEdit = {
                recordToEdit = it
                showAddDialog = true
            },
            isDarkTheme = isDarkTheme,
            dateExtractor = { it.startTime },
            onItemClick = { selectedRecord = it },
            itemContent = { item, textColor ->
                val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(item.startTime))
                // Nội dung Dialog
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = stepColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "${item.count} bước",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Text(
                            text = date,
                            fontSize = 13.sp,
                            color = textColor.copy(0.6f)
                        )
                    }
                }
            }
        )
    }
    if (selectedRecord != null) {
        StepHistoryDetailDialog(
            record = selectedRecord!!,
            onDismiss = { selectedRecord = null },
            onDelete = {
                stepViewModel.deleteStepRecord(it)
                selectedRecord = null
            },
            onEdit = { record ->
                recordToEdit = record // Lưu bản ghi cần sửa
                selectedRecord = null // Đóng dialog detail
                showAddDialog = true  // Mở dialog nhập liệu
            }
        )
    }
    if (showAddDialog) {
        // Chuẩn bị dữ liệu: Nếu đang sửa (recordToEdit != null) thì lấy thông tin cũ
        // Nếu thêm mới thì mặc định là 0

        AddStepDialog(
            onDismiss = {
                showAddDialog = false
                recordToEdit = null
            },
            // TRUYỀN DỮ LIỆU CŨ VÀO ĐÂY
            initialSteps = recordToEdit?.count ?: 0,
            initialStartTime = recordToEdit?.startTime ?: System.currentTimeMillis(),
            // Tính duration cũ (nếu có), không thì mặc định 30
            initialDuration = if (recordToEdit != null)
                ((recordToEdit!!.endTime - recordToEdit!!.startTime) / 60000).toInt().coerceAtLeast(1)
            else 30,// Mặc định 30p nếu thêm mới
            colors = colors,
            onSave = { startTime, duration, steps ->
                if (recordToEdit != null) {
                    // Logic Sửa
                    stepViewModel.editStepRecord(recordToEdit!!, startTime, duration, steps)
                } else {
                    // Logic Thêm
                    stepViewModel.saveManualStepRecord(startTime, duration, steps)
                }
                showAddDialog = false
                recordToEdit = null
            }
        )
    }
    Box(
        modifier = modifier
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
            topBar = { TopBar(onBackClick, colors, "Bước Chân") },
            // --- FAB (NÚT TRÒN ĐỂ TRỐNG) ---
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = stepColor,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Nhập dữ liệu thủ công",
                        tint = Color.White
                    )
                }
            }
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { stepViewModel.refresh() },
                modifier = Modifier.padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // 1. Card Tổng quan (Steps + Calories)
//                item {
//                    Box(
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .clip(RoundedCornerShape(24.dp))
//                            .background(colors.glassContainer)
//                            .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
//                            .padding(24.dp),
//                        contentAlignment = Alignment.Center
//                    ) {
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceAround,
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            // Cột Bước chân
//                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                                Icon(
//                                    Icons.Default.DirectionsRun,
//                                    contentDescription = null,
//                                    tint = stepColor,
//                                    modifier = Modifier.size(32.dp)
//                                )
//                                Spacer(modifier = Modifier.height(8.dp))
//                                Text(
//                                    text = "$currentSteps",
//                                    fontSize = 32.sp,
//                                    fontWeight = FontWeight.Bold,
//                                    color = colors.textPrimary
//                                )
//                                Text("Bước", color = colors.textSecondary)
//                            }
//                            // Đường kẻ dọc
//                            Divider(
//                                modifier = Modifier
//                                    .height(60.dp)
//                                    .width(1.dp),
//                                color = colors.textSecondary.copy(alpha = 0.5f)
//                            )
//
//                            // Cột Calories
//                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                                Icon(
//                                    Icons.Default.LocalFireDepartment,
//                                    contentDescription = null,
//                                    tint = Color(0xFFEF4444),
//                                    modifier = Modifier.size(32.dp)
//                                )
//                                Spacer(modifier = Modifier.height(8.dp))
//                                Text(
//                                    text = "$currentCalories",
//                                    fontSize = 32.sp,
//                                    fontWeight = FontWeight.Bold,
//                                    color = colors.textPrimary
//                                )
//                                Text("Kcal", color = colors.textSecondary)
//                            }
//                        }
//                    }
//                }

                    // 2. Biểu đồ
                    item {
                        Text(
                            "Thống kê hoạt động",
                            color = colors.textSecondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                            fontWeight = FontWeight.Bold
                        )

                        TimeRangeSelector(
                            selectedRange = selectedTimeRange,
                            onRangeSelected = { stepViewModel.setTimeRange(it) },
                            activeColor = stepColor,
                            inactiveColor = colors.textSecondary
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.glassContainer)
                                .padding(16.dp)
                        ) {
                            StepChart(
                                data = chartData,
                                timeRange = selectedTimeRange,
                            )
                        }
                    }


                    // 4. Lịch sử (Giao diện mới)
                    item {
                        // Header + Nút Xem thêm
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lịch sử hoạt động",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )

                            if (historyList.size > 3) {
                                TextButton(onClick = { showHistoryDialog = true }) {
                                    Text(
                                        "Xem thêm",
                                        color = stepColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (historyList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Chưa có dữ liệu chạy bộ", color = colors.textSecondary)
                            }
                        } else {
                            // Hiển thị 3 item mới nhất
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                historyList.take(3).forEach { record ->
                                    SimpleStepHistoryRow(
                                        record = record,
                                        colors = colors,
                                        stepColor = stepColor,
                                        onDelete = { stepViewModel.deleteStepRecord(record) },
                                        onEdit = {
                                            recordToEdit = record
                                            selectedRecord = null
                                            showAddDialog = true
                                        },
                                        modifier = Modifier.clickable { selectedRecord = record }
                                    )
                                }
                            }
                        }
                    }

                    // Padding bottom để tránh FAB che
                    item { Spacer(modifier = Modifier.height(50.dp)) }
                }
            }
        }
    }
}

// Item lịch sử đơn giản
@Composable
fun SimpleStepHistoryRow(
    record: StepRecordEntity,
    colors: AestheticColors,
    stepColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Kiểm tra quyền sở hữu
    val isMyData = record.source == context.packageName
    Row(
        modifier = modifier
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
                    .background(stepColor.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsRun,
                    contentDescription = null,
                    tint = stepColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "${record.count} bước",
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    fontSize = 16.sp
                )
                Text(
                    text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(
                        Date(
                            record.startTime
                        )
                    ),
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        // --- MENU XÓA ---
        if (isMyData) {
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = colors.textSecondary
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(colors.glassContainer)
                ) {
                    // Item 1: Sửa
                    DropdownMenuItem(
                        text = { Text("Sửa", color = Color.Black) },
                        onClick = {
                            expanded = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = Color.Black) }
                    )

                    // Item 2: Xóa
                    DropdownMenuItem(
                        text = { Text("Xóa", color = Color.Red) },
                        onClick = {
                            expanded = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                    )
                }
            }
        }
    }
}