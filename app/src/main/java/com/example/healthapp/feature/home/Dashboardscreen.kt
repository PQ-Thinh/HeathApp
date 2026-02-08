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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
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
import com.example.healthapp.core.helperEnum.RunState
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.core.viewmodel.SleepViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.core.viewmodel.UserViewModel
import com.example.healthapp.feature.components.FabMenu
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
    isServiceRunning: Boolean = false
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
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
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

    // (Phần Animation nền giữ nguyên...)
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(bottom = 80.dp),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // (Phần Card hiển thị giữ nguyên...)
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
                    HealthStatCard(
                        modifier = Modifier.fillMaxWidth().clickable { onStepDetailClick() },
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            expanded = isFabExpanded,
            onExpandChange = { isFabExpanded = it },
            onRunClick = {
                onRunClick()
                isFabExpanded = false
            },
            colors = colors,
        )

        // --- LỚP PHỦ RUN TRACKING ---
        // Gọi trực tiếp ở đây, KHÔNG chuyển qua MainActivity
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
fun DashboardTopBar(onProfileClick: () -> Unit, onNotificationsClick: () -> Unit, onSettingsClick: () -> Unit, colors: AestheticColors) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onSettingsClick, modifier = Modifier.background(colors.glassContainer, CircleShape)) {
            Icon(Icons.Default.Settings, null, tint = colors.textPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNotificationsClick) { Icon(Icons.Default.Notifications, null, tint = colors.textPrimary) }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(colors.gradientOrb1, colors.gradientOrb2))).border(1.dp, colors.glassBorder, CircleShape).clickable { onProfileClick() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun HealthStatCard(modifier: Modifier = Modifier, title: String, value: String, unit: String, icon: ImageVector, accentColor: Color, colors: AestheticColors, visible: Boolean, delay: Int, isLarge: Boolean = false) {
    AnimatedVisibility(visible = visible, enter = fadeIn(tween(800, delay)) + scaleIn(initialScale = 0.9f, animationSpec = tween(800, delay)), modifier = modifier) {
        Column(modifier = Modifier.clip(RoundedCornerShape(32.dp)).background(colors.glassContainer).border(1.dp, colors.glassBorder, RoundedCornerShape(32.dp)).padding(24.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(if (isLarge) 32.dp else 24.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, color = colors.textSecondary, fontSize = 14.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = value, color = colors.textPrimary, fontSize = if (isLarge) 42.sp else 28.sp, fontWeight = FontWeight.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = unit, color = accentColor.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}