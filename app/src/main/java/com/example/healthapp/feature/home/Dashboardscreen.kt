package com.example.healthapp.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.helperEnum.RunState
import com.example.healthapp.core.model.entity.UserEntity
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.core.viewmodel.SleepViewModel
import com.example.healthapp.core.viewmodel.SocialViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.core.viewmodel.UserViewModel
import com.example.healthapp.feature.components.EditTargetDialog
import com.example.healthapp.feature.components.FabMenu
import com.example.healthapp.feature.detail.LatestActivityCard
import com.example.healthapp.feature.detail.StepRunDetail
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthDashboardScreen(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isDarkTheme: Boolean,
    onHeartDetailClick: () -> Unit = {},
    onSleepDetailClick: () -> Unit = {},
    onStepDetailClick: () -> Unit = {},
    mainViewModel: MainViewModel,
    userViewModel: UserViewModel,
    sleepViewModel: SleepViewModel,
    stepViewModel: StepViewModel,
    onToggleService: (Boolean) -> Unit = {},
    isServiceRunning: Boolean = false,
    socialViewModel: SocialViewModel = hiltViewModel()
) {
    val runState by stepViewModel.runState.collectAsState()
    val isRunningBackground = runState == RunState.RUNNING || runState == RunState.PAUSED
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    var isContentVisible by remember { mutableStateOf(isPreview) }

    val steps by mainViewModel.realtimeSteps.collectAsState()
    val todayHealth by mainViewModel.todayHealthData.collectAsState()

    val realtimeBpm by mainViewModel.realtimeHeartRate.collectAsState()
    val displayBpm = if (realtimeBpm > 0) realtimeBpm else (todayHealth?.heartRateAvg ?: 0)
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic

    val user by userViewModel.currentUserInfo.collectAsState()
    val duration by sleepViewModel.sleepDuration.collectAsState()

    // --- STATE UI ---
    var isFabExpanded by remember { mutableStateOf(false) }
    var isRunModeActive by remember { mutableStateOf(false) }

    var showResultScreen by remember { mutableStateOf(false) }
    var resultSteps by remember { mutableIntStateOf(0) }
    var resultCalories by remember { mutableIntStateOf(0) }
    var resultTime by remember { mutableLongStateOf(0L) }

    var showTargetDialog by remember { mutableStateOf(false) }

    val isRefreshing by mainViewModel.isRefreshing.collectAsState()
    val targetSteps = if ((todayHealth?.targetSteps ?: 0) > 0) todayHealth!!.targetSteps else 10000

    val stepHistory by stepViewModel.stepHistory.collectAsState(initial = emptyList())
    val latestRecord = stepHistory.firstOrNull()

    // --- SOCIAL DATA ---
    val invitations by socialViewModel.incomingInvitations.collectAsState()
    val allUsers by socialViewModel.users.collectAsState()

    LaunchedEffect(Unit) {
        if (!isPreview) {
            isContentVisible = true
            socialViewModel.loadUsers()
        }
    }

    LaunchedEffect(Unit) {
        val currentState = stepViewModel.runState.value
        val isRunning = currentState == RunState.RUNNING || currentState == RunState.PAUSED
        if (mainViewModel.shouldAutoOpenRunScreen(isRunning)) {
            isRunModeActive = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val notifGranted = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
            val locGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            if (notifGranted || locGranted || (Build.VERSION.SDK_INT < 33)) {
                mainViewModel.resetNavigationFlag()
                isRunModeActive = true
                onToggleService(true)
            }
        }
    )

    fun onRunClick() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            mainViewModel.resetNavigationFlag()
            isRunModeActive = true
            onToggleService(true)
        }
    }

    LaunchedEffect(Unit) {
        if (!isPreview) isContentVisible = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(45000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // --- BACKGROUND CANVAS ---
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb1.copy(0.15f), Color.Transparent),
                    center = Offset(floatAnim % size.width, floatAnim % size.height)
                ),
                radius = 800f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb2.copy(0.1f), Color.Transparent),
                    center = Offset(
                        size.width - (floatAnim % size.width),
                        size.height - (floatAnim % size.height)
                    )
                ),
                radius = 600f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                DashboardTopBar(
                    onProfileClick = onProfileClick,
                    onNotificationsClick = onNotificationsClick,
                    onSettingsClick = onSettingsClick,
                    colors = colors
                )
            }
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { mainViewModel.refreshDashboard() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 100.dp),
                ) {
                    item {
                        AnimatedVisibility(
                            visible = isContentVisible,
                            enter = fadeIn() + slideInVertically { -20 }
                        ) {
                            Column {
                                Text(
                                    text = "Xin Chào, ${user?.name ?: "User"}!",
                                    style = TextStyle(
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Black,
                                        color = colors.textPrimary,
                                        shadow = if (isDarkTheme) Shadow(Color.Black.copy(0.3f), blurRadius = 8f) else null
                                    )
                                )
                                Text(
                                    text = "Đây là bản tóm tắt sức khỏe của bạn hôm nay.",
                                    color = colors.textSecondary,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }

                    // --- BANNER VỚI HIỆU ỨNG PULSE ---
                    item {
                        AnimatedVisibility(
                            visible = invitations.isNotEmpty(),
                            enter = expandVertically() + fadeIn()
                        ) {
                            InvitationAlertBanner(
                                count = invitations.size,
                                onClick = onNotificationsClick
                            )
                        }
                    }

                    item {
                        StepProgressCard(
                            modifier = Modifier.fillMaxWidth(),
                            currentSteps = steps,
                            targetSteps = targetSteps,
                            colors = colors,
                            visible = isContentVisible,
                            delay = 400,
                            onClick = { onStepDetailClick() },
                            onEditTargetClick = { showTargetDialog = true }
                        )

                        if (showTargetDialog) {
                            EditTargetDialog(
                                initialTarget = targetSteps,
                                onDismiss = { showTargetDialog = false },
                                onConfirm = { newTarget ->
                                    mainViewModel.updateTargetSteps(newTarget)
                                    showTargetDialog = false
                                }
                            )
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            HealthStatCard(
                                modifier = Modifier.weight(1f).clickable { onHeartDetailClick() },
                                title = "Nhịp Tim",
                                value = displayBpm.toString(),
                                unit = "BPM",
                                icon = Icons.Default.Favorite,
                                accentColor = Color(0xFFEF4444),
                                colors = colors,
                                visible = isContentVisible,
                                delay = 200
                            )
                            HealthStatCard(
                                modifier = Modifier.weight(1f).clickable { onSleepDetailClick() },
                                title = "Ngủ",
                                value = sleepViewModel.formatDuration(duration),
                                unit = "",
                                icon = Icons.Default.NightsStay,
                                accentColor = Color(0xFF8B5CF6),
                                colors = colors,
                                visible = isContentVisible,
                                delay = 300
                            )
                        }
                    }

                    item {
                        HealthStatCard(
                            modifier = Modifier.fillMaxWidth(),
                            title = "BMI",
                            value = user?.bmi?.let { String.format("%.2f", it) } ?: "...",
                            unit = "Kg/m²",
                            icon = Icons.Default.MonitorWeight,
                            accentColor = Color(0xFF8B5CF6),
                            colors = colors,
                            visible = isContentVisible,
                            delay = 300
                        )
                    }

                    item {
                        if (latestRecord != null) {
                            LatestActivityCard(
                                modifier = Modifier.fillMaxWidth(),
                                record = latestRecord,
                                colors = colors,
                                visible = isContentVisible,
                                delay = 400,
                                onClick = { _ ->
                                    resultSteps = latestRecord.count
                                    resultCalories = (latestRecord.count * 0.04).toInt()
                                    resultTime = (latestRecord.endTime - latestRecord.startTime) / 1000
                                    showResultScreen = true
                                }
                            )
                        } else {
                            HealthStatCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onStepDetailClick() },
                                title = "Bước Đếm",
                                value = steps.toString(),
                                unit = "bước hôm nay",
                                icon = Icons.Default.DirectionsRun,
                                accentColor = Color(0xFF10B981),
                                colors = colors,
                                visible = isContentVisible,
                                delay = 400,
                                isLarge = true
                            )
                        }
                    }

                    item {
                        AnimatedVisibility(
                            visible = isContentVisible,
                            enter = fadeIn(tween(600, delayMillis = 500)) + slideInVertically(
                                initialOffsetY = { 20 },
                                animationSpec = tween(600, delayMillis = 500)
                            )
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Thử thách bạn bè",
                                    style = TextStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.textPrimary
                                    ),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                if (allUsers.isEmpty()) {
                                    Text(
                                        text = "Đang tải danh sách...",
                                        color = colors.textSecondary,
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    itemsIndexed(items = allUsers, key = { _, item -> item.id }) { index, userItem ->
                        val itemDelay = 600 + (index * 100)

                        AnimatedVisibility(
                            visible = isContentVisible,
                            enter = fadeIn(tween(600, delayMillis = itemDelay)) +
                                    slideInHorizontally( // Trượt nhẹ từ phải sang cho sinh động
                                        initialOffsetX = { 50 },
                                        animationSpec = tween(600, delayMillis = itemDelay)
                                    )
                        ) {
                            UserInviteDashboardItem(
                                user = userItem,
                                colors = colors,
                                onInvite = {
                                    val myName = user?.name ?: "Unknown"
                                    socialViewModel.sendInvite(userItem, targetSteps, myName)
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- FAB SCRIM OVERLAY (HIỆU ỨNG MỜ NỀN) ---
        // Thay vì Box thường, dùng AnimatedVisibility để Fade In/Out
        AnimatedVisibility(
            visible = isFabExpanded,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    // Tắt click propagation để khi ấn ra ngoài sẽ đóng menu
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isFabExpanded = false }
            )
        }

        FabMenu(
            isRunActive = isRunningBackground,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp), // Tăng padding một chút cho thoáng
            expanded = isFabExpanded,
            onExpandChange = { isFabExpanded = it },
            onRunClick = {
                onRunClick()
                isFabExpanded = false
            },
            colors = colors,
        )

        // --- LỚP PHỦ RUN TRACKING ---
        AnimatedVisibility(
            visible = isRunModeActive,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = Spring.StiffnessLow) // Hiệu ứng nảy nhẹ khi lên
            ),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            RunTrackingScreen(
                stepViewModel = stepViewModel,
                onClose = { isRunModeActive = false },
                onToggleService = onToggleService,
                colors = colors,
                mainViewModel = mainViewModel,
                onFinishRun = { s, c, t ->
                    resultSteps = s
                    resultCalories = c
                    resultTime = t
                    showResultScreen = true
                },
            )
        }

        AnimatedVisibility(
            visible = showResultScreen,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            StepRunDetail(
                steps = resultSteps,
                calories = resultCalories,
                timeSeconds = resultTime,
                onBackClick = { showResultScreen = false },
                colors = colors
            )
        }
    }
}

@Composable
fun DashboardTopBar(
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    colors: AestheticColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.background(colors.glassContainer, CircleShape)
        ) {
            Icon(Icons.Default.Settings, null, tint = colors.textPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNotificationsClick) {
                Icon(Icons.Default.Notifications, null, tint = colors.textPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.gradientOrb1, colors.gradientOrb2)))
                    .border(1.dp, colors.glassBorder, CircleShape)
                    .clickable { onProfileClick() }, contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun HealthStatCard(
    modifier: Modifier = Modifier,
    title: String, value: String,
    unit: String, icon: ImageVector,
    accentColor: Color, colors: AestheticColors,
    visible: Boolean,
    delay: Int,
    isLarge: Boolean = false
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, delay)) + slideInVertically(
            animationSpec = tween(600, delay),
            initialOffsetY = { 50 } // Slide từ dưới lên nhẹ nhàng
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(colors.glassContainer)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(32.dp))
                .padding(24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(if (isLarge) 32.dp else 24.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, color = colors.textSecondary, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = colors.textPrimary,
                    fontSize = if (isLarge) 42.sp else 28.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (unit.isNotEmpty()) {
                    Text(
                        text = unit,
                        color = accentColor.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StepProgressCard(
    modifier: Modifier = Modifier,
    currentSteps: Int,
    targetSteps: Int,
    colors: AestheticColors,
    visible: Boolean,
    delay: Int,
    onClick: () -> Unit,
    onEditTargetClick: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800, delay)) + scaleIn(initialScale = 0.95f, animationSpec = tween(800, delay)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(colors.glassContainer)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(32.dp))
                .clickable { onClick() }
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            val progress = if (targetSteps > 0) currentSteps.toFloat() / targetSteps else 0f
            val animatedProgress by animateFloatAsState(
                targetValue = progress.coerceIn(0f, 1f),
                animationSpec = tween(1500, delayMillis = 500, easing = FastOutSlowInEasing),
                label = "stepProgress"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsRun, null, tint = Color(0xFF10B981), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bước chân", color = colors.textSecondary, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "$currentSteps",
                        style = TextStyle(
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "/ $targetSteps mục tiêu",
                            style = TextStyle(fontSize = 14.sp, color = colors.textSecondary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onEditTargetClick, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Sửa mục tiêu",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Vòng tròn tiến độ
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .clickable { onEditTargetClick() }
                ) {
                    Canvas(modifier = Modifier.size(120.dp)) {
                        val strokeWidth = 14.dp.toPx()

                        // --- CẤU HÌNH MÀU SẮC ---
                        // Màu cơ bản (0-100%)
                        val colorBaseBg = Color(0xFF10B981).copy(alpha = 0.2f)
                        val gradientBase = Brush.verticalGradient(
                            colors = listOf(Color(0xFF34D399), Color(0xFF10B981))
                        )

                        // Màu "Vượt ngưỡng" (>100%): Vàng cam rực rỡ
                        val gradientOver = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFFD700), Color(0xFFFF8C00)) // Gold -> Dark Orange
                        )

                        // --- LOGIC VẼ ---

                        // 1. Vẽ đường ray nền (Background Track)
                        drawArc(
                            color = colorBaseBg,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        if (animatedProgress <= 1f) {
                            // TRƯỜNG HỢP 1: Chưa đạt mục tiêu (0% - 100%)
                            drawArc(
                                brush = gradientBase,
                                startAngle = -90f,
                                sweepAngle = 360 * animatedProgress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        } else {
                            // TRƯỜNG HỢP 2: Đã vượt mục tiêu (> 100%)

                            // Bước A: Vẽ full vòng màu xanh làm nền (vì đã xong 100%)
                            drawArc(
                                brush = gradientBase,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )

                            // Bước B: Tính phần dư ra (Ví dụ 1.2 -> dư 0.2)
                            // Dùng toán tử % 1f để nó quay vòng lại nếu vượt 200%, 300%
                            val excessProgress = animatedProgress % 1f
                            // Nếu chia hết cho 1 (vd đúng 200%) thì coi như full vòng
                            val sweep = if (excessProgress == 0f && animatedProgress > 1f) 360f else 360 * excessProgress

                            // Bước C: Vẽ vòng "Bonus" màu Vàng đè lên trên
                            drawArc(
                                brush = gradientOver,
                                startAngle = -90f,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // TEXT HIỂN THỊ
                    // Nếu vượt 100% thì đổi màu chữ sang màu cam/vàng cho nổi bật
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black, // Đậm hơn chút nữa
                                // Nếu > 100% thì dùng màu Cam, ngược lại dùng màu chính của theme
                                color = if (progress > 1f) Color(0xFFFF8C00) else colors.textPrimary
                            )
                        )
                        // Thêm chữ nhỏ "Excellent" nếu vượt
                        if (progress > 1f) {
                            Text(
                                text = "Tuyệt vời!",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD700)
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
fun InvitationAlertBanner(count: Int, onClick: () -> Unit) {
    // Hiệu ứng "nhịp đập" nhẹ (Pulse)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .scale(scale) // Áp dụng pulse scale
            .border(1.dp, Color(0xFFFFCC80), RoundedCornerShape(12.dp)) // Thêm viền nhẹ
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon rung nhẹ
            Icon(
                Icons.Outlined.DirectionsRun,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Lời mời thách đấu!",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                    fontSize = 16.sp
                )
                Text(
                    text = "Bạn có $count lời mời đang chờ.",
                    color = Color(0xFFE65100).copy(0.8f),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun UserInviteDashboardItem(
    user: UserEntity,
    colors: AestheticColors,
    onInvite: () -> Unit,
    socialViewModel: SocialViewModel = hiltViewModel()
) {
    val sentList by socialViewModel.sentInvitations.collectAsState()
    val loadingIds by socialViewModel.inviteLoadingIds.collectAsState()

    val isInvited = remember(sentList, user.id) {
        sentList.any { invite -> invite.receiverId == user.id && invite.status == "PENDING" }
    }
    val isLoading = loadingIds.contains(user.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.glassContainer)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.name?.take(1)?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = user.name ?: "Unknown",
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                // Thêm trạng thái nhỏ bên dưới tên
                AnimatedVisibility(visible = isInvited) {
                    Text(
                        text = "Đã gửi lời mời",
                        color = Color(0xFF10B981),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Button với hiệu ứng AnimatedContent chuyển đổi giữa Text và Loading
        Button(
            onClick = {
                if (!isLoading && !isInvited) {
                    onInvite()
                }
            },
            enabled = !isLoading && !isInvited,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isInvited) colors.surface.copy(alpha = 0.5f) else Color(0xFF10B981),
                disabledContainerColor = if (isInvited) colors.surface.copy(alpha = 0.5f) else Color(0xFF10B981).copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .height(40.dp)
                .width(110.dp)
        ) {
            AnimatedContent(
                targetState = when {
                    isLoading -> "LOADING"
                    isInvited -> "SENT"
                    else -> "IDLE"
                },
                label = "ButtonState",
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                            scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                }
            ) { state ->
                when (state) {
                    "LOADING" -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                    "SENT" -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Đã mời", fontSize = 13.sp)
                        }
                    }
                    else -> {
                        Text("Thách đấu", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}