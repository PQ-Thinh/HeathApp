package com.example.healthapp.feature.home

import android.preference.PreferenceManager
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.ui.theme.AestheticColors
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun RunTrackingScreen(
    stepViewModel: StepViewModel,
    onClose: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    colors: AestheticColors,
    mainViewModel: MainViewModel,
    onFinishRun: (Int, Int, Long) -> Unit,
) {
    val context = LocalContext.current

    // State
    val rawSensorSteps by mainViewModel.rawSensorSteps.collectAsState(initial = 0)
    val sessionSteps by stepViewModel.sessionSteps.collectAsState()
    val sessionDuration by stepViewModel.sessionDuration.collectAsState()
    val sessionDistance by stepViewModel.sessionDistance.collectAsState()
    val sessionSpeed by stepViewModel.sessionSpeed.collectAsState()
    val isRunning by stepViewModel.isRunning.collectAsState()
    val isCountdownActive by stepViewModel.isCountdownActive.collectAsState()

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        // Khi mới vào, nếu chưa chạy thì bật chế độ chuẩn bị (hiện nút Start overlay)
        if (!stepViewModel.isRunning.value && sessionSteps == 0) {
            stepViewModel.prepareRun()
        }
    }

    LaunchedEffect(rawSensorSteps) {
        Log.d("StepDebug", "UI Received: $rawSensorSteps")
        stepViewModel.updateSessionSteps(rawSensorSteps)
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // --- LỚP UI CHÍNH (Map + Stats) ---
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Map
            Box(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            ) {
                OsmMapView(modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .padding(top = 48.dp, start = 16.dp)
                        .background(Color.White.copy(0.8f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
            }

            // 2. Stats & Controls
            Surface(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxWidth()
                    .shadow(16.dp),
                color = colors.background,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Timer
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stepViewModel.formatDuration(sessionDuration),
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            letterSpacing = 2.sp
                        )
                        Text("Thời gian chạy", fontSize = 14.sp, color = colors.textSecondary)
                    }

                    // Stats Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItemCompact(String.format("%.2f", sessionDistance), "Km", colors, Color(0xFF10B981))
                        StatItemCompact(String.format("%.1f", sessionSpeed), "Km/h", colors)
                        StatItemCompact("$sessionSteps", "Steps", colors)
                        StatItemCompact("${stepViewModel.calculateCalories(sessionSteps.toLong())}", "Kcal", colors, Color(0xFFF59E0B))
                    }

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isRunning) {
                                    stepViewModel.pauseRunSession()
                                    // Service tự update do lắng nghe DataStore, không cần gọi onToggleService(false) cũng được,
                                    // nhưng nếu service logic cần pause thì gọi:
                                    // onToggleService(false) // Tùy logic service của bạn
                                } else {
                                    stepViewModel.resumeRunSession()
                                    // onToggleService(true)
                                }
                            },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) Color(0xFFF59E0B) else Color(0xFF10B981)
                            )
                        ) {
                            Icon(if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isRunning) "TẠM DỪNG" else "TIẾP TỤC", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onToggleService(false)
                                stepViewModel.finishRunSession(rawSensorSteps)
                                onFinishRun(sessionSteps, stepViewModel.calculateCalories(sessionSteps.toLong()), sessionDuration)
                                onClose()
                            },
                            modifier = Modifier.weight(1f).height(64.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Stop, null, tint = Color.White)
                            Text("KẾT THÚC", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- LỚP PHỦ MỜ (OVERLAY) ĐẾM NGƯỢC ---
        AnimatedVisibility(
            visible = isCountdownActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            CountDownOverlay(
                colors = colors,
                onStartClick = {
                    // Logic đếm ngược 3s rồi Start
                },
                onCancel = {
                    stepViewModel.cancelRunPreparation()
                    onClose()
                },
                onFinished = {
                    stepViewModel.startRunSession(rawSensorSteps)
                    onToggleService(true)
                }
            )
        }
    }
}

@Composable
fun CountDownOverlay(
    colors: AestheticColors,
    onStartClick: () -> Unit,
    onCancel: () -> Unit,
    onFinished: () -> Unit
) {
    // State quản lý đếm ngược
    var count by remember { mutableStateOf(3) }
    var isCounting by remember { mutableStateOf(false) }

    // Logic đếm ngược
    LaunchedEffect(isCounting) {
        if (isCounting) {
            while (count > 0) {
                delay(1000)
                count--
            }
            // Hết giờ -> Gọi callback finish
            onFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.95f)) // Nền mờ che hết UI dưới
            .clickable(enabled = false) {}, // Chặn click xuống dưới
        contentAlignment = Alignment.Center
    ) {
        if (!isCounting) {
            // Hiển thị nút Bắt đầu
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = { isCounting = true }, // Bắt đầu đếm
                    modifier = Modifier.size(200.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text("BẮT ĐẦU", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(32.dp))
                TextButton(onClick = onCancel) {
                    Text("Hủy bỏ", color = colors.textSecondary, fontSize = 18.sp)
                }
            }
        } else {
            // Hiển thị số đếm ngược
            Text(
                text = if (count > 0) "$count" else "GO!",
                fontSize = 120.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (count > 0) colors.textPrimary else Color(0xFF10B981)
            )
        }
    }
}

// ... OsmMapView và StatItemCompact giữ nguyên ...
@Composable
fun StatItemCompact(value: String, unit: String, colors: AestheticColors, color: Color? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = color ?: colors.textPrimary
        )
        Text(
            text = unit,
            fontSize = 12.sp,
            color = colors.textSecondary.copy(alpha = 0.8f)
        )
    }
}

@Composable
fun OsmMapView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(19.0)
            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
            locationOverlay.enableMyLocation()
            locationOverlay.enableFollowLocation()
            overlays.add(locationOverlay)
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}