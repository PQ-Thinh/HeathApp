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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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
   // onRecordClick: (String) -> Unit = {}
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
    // Biến này sẽ bật/tắt lớp phủ màn hình chạy
    var isRunModeActive by remember { mutableStateOf(false) }

    var showResultScreen by remember { mutableStateOf(false) }
    var resultSteps by remember { mutableIntStateOf(0) }
    var resultCalories by remember { mutableIntStateOf(0) }
    var resultTime by remember { mutableLongStateOf(0L) }

    var showTargetDialog by remember { mutableStateOf(false) }

    val isRefreshing by mainViewModel.isRefreshing.collectAsState()

    // Lấy target từ todayHealth, nếu chưa có (null hoặc 0) thì fallback về 10000
    val targetSteps = if ((todayHealth?.targetSteps ?: 0) > 0) todayHealth!!.targetSteps else 10000

    val stepHistory by stepViewModel.stepHistory.collectAsState(initial = emptyList())
    val latestRecord = stepHistory.firstOrNull() // Lấy phần tử đầu tiên (mới nhất)
    var resultTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // --- SOCIAL DATA ---
    val invitations by socialViewModel.incomingInvitations.collectAsState()
    val allUsers by socialViewModel.users.collectAsState() // Danh sách user để hiển thị

    // Load danh sách user khi vào Dashboard
    LaunchedEffect(Unit) {
        if (!isPreview) {
            isContentVisible = true
            socialViewModel.loadUsers()
        }
    }
    // Kiểm tra ngay khi vào Dashboard, nếu đang chạy ngầm thì mở lại RunTrackingScreen
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
                // Đủ quyền -> Mở lớp phủ chạy bộ
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
            // Đủ quyền -> Mở lớp phủ
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
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(
                        top = 24.dp,
                        start = 24.dp,
                        end = 24.dp,
                        bottom = 100.dp
                    ),
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
                                        shadow = if (isDarkTheme) Shadow(
                                            Color.Black.copy(0.3f),
                                            blurRadius = 8f
                                        ) else null
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
                                record = latestRecord!!,
                                colors = colors,
                                visible = isContentVisible,
                                delay = 400,
                                onClick = { _ ->
                                    resultSteps = latestRecord!!.count
                                    resultCalories = (latestRecord!!.count * 0.04).toInt()
                                    resultTime =
                                        (latestRecord!!.endTime - latestRecord!!.startTime) / 1000
                                    // Lưu thời gian bắt đầu để hiển thị ngày giờ
                                    resultTimestamp = latestRecord!!.startTime
                                    // Bật màn hình chi tiết lên
                                    showResultScreen = true
                                }
                            )
                        } else {
                            HealthStatCard(
                                modifier = Modifier.fillMaxWidth()
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
                        // Hiển thị Banner nếu có lời mời
                        if (invitations.isNotEmpty()) {
                            InvitationAlertBanner(
                                count = invitations.size,
                                onClick = onNotificationsClick

                            )
                        }
                    }
                    item {
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
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Hiển thị danh sách user dưới dạng các item của LazyColumn
                    items(items = allUsers, key = { it.id }) { userItem ->
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

        if (isFabExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isFabExpanded = false }
            )
        }

        FabMenu(
            isRunActive = isRunningBackground,
            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
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
            enter = slideInVertically(initialOffsetY = { it }),
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
    colors: AestheticColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier.background(colors.glassContainer, CircleShape)) {
            Icon(Icons.Default.Settings, null, tint = colors.textPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNotificationsClick) {
                Icon(Icons.Default.Notifications, null, tint = colors.textPrimary) }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(40.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.gradientOrb1, colors.gradientOrb2)))
                .border(1.dp, colors.glassBorder, CircleShape)
                .clickable { onProfileClick() }, contentAlignment = Alignment.Center) {
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
    isLarge: Boolean = false) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800, delay)) + scaleIn(initialScale = 0.9f,
            animationSpec = tween(800, delay)), modifier = modifier) {
        Column(modifier = Modifier.clip(RoundedCornerShape(32.dp))
            .background(colors.glassContainer)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(32.dp))
            .padding(24.dp)) {
            Icon(imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(if (isLarge) 32.dp else 24.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, color = colors.textSecondary, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value,
                    color = colors.textPrimary,
                    fontSize = if (isLarge) 42.sp else 28.sp,
                    fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = unit,
                    color = accentColor.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp))
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
        enter = fadeIn(tween(800, delay)) + scaleIn(initialScale = 0.9f, animationSpec = tween(800, delay)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(colors.glassContainer)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(32.dp))
                .clickable { onClick() } // Click toàn card -> xem chi tiết
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
                // --- Phần Text bên trái ---
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

                    // Dòng hiển thị mục tiêu + Nút Edit
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "/ $targetSteps mục tiêu",
                            style = TextStyle(fontSize = 14.sp, color = colors.textSecondary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Nút Edit nhỏ
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Sửa mục tiêu",
                            tint = colors.textSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onEditTargetClick() } // Click vào bút -> Sửa
                        )
                    }
                }

                // --- Phần Vòng tròn bên phải ---
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(120.dp)
                        .clickable { onEditTargetClick() } // Click vào vòng tròn -> Sửa
                ) {
                    Canvas(modifier = Modifier.size(120.dp)) {
                        val strokeWidth = 12.dp.toPx()

                        // 1. Vòng tròn Target (Nền mờ - Vòng 1)
                        drawArc(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )

                        // 2. Vòng tròn Steps (Tiến độ thực tế - Vòng 2)
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(Color(0xFF10B981), Color(0xFF34D399), Color(0xFF10B981))
                            ),
                            startAngle = -90f,
                            sweepAngle = 360 * animatedProgress,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }
                    // Số % ở giữa
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = TextStyle(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    )
                }
            }
        }
    }
}
@Composable
fun InvitationAlertBanner(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)), // Cam nhạt
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.DirectionsRun, contentDescription = null, tint = Color(0xFFFF9800))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Bạn có $count lời mời thách đấu mới!",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100)
            )
        }
    }
}
@Composable
fun UserInviteDashboardItem(
    user: UserEntity,
    colors: AestheticColors,
    onInvite: () -> Unit
) {
    var isInvited by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)) // Bo góc giống các card khác
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
                    .background(Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF8B5CF6)))), // Gradient tím
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
//                Text(
//                    text = "Mục tiêu: ${user.targetSteps} bước",
//                    color = colors.textSecondary,
//                    fontSize = 14.sp
//                )
            }
        }

        Button(
            onClick = {
                onInvite()
                isInvited = true
            },
            enabled = !isInvited,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isInvited) Color.Gray else Color(0xFF10B981) // Màu xanh
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Text(if (isInvited) "Đã mời" else "Thách đấu")
        }
    }
}