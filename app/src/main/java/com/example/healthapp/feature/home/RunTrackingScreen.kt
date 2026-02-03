package com.example.healthapp.feature.home

import android.preference.PreferenceManager
import androidx.compose.foundation.background
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

    // State từ ViewModel
    val totalRealtimeSteps by mainViewModel.realtimeSteps.collectAsState()
    val sessionSteps by stepViewModel.sessionSteps.collectAsState()
    val sessionDuration by stepViewModel.sessionDuration.collectAsState()
    val sessionDistance by stepViewModel.sessionDistance.collectAsState()
    val sessionSpeed by stepViewModel.sessionSpeed.collectAsState()
    val isRunning by stepViewModel.isRunning.collectAsState()

    // Khởi tạo Map
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
    }

    // Logic update
    LaunchedEffect(totalRealtimeSteps, isRunning) {
        if (isRunning && totalRealtimeSteps > 0) {
            stepViewModel.updateSessionSteps(totalRealtimeSteps)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- PHẦN 1: BẢN ĐỒ (60%) ---
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

        // --- PHẦN 2: THÔNG TIN (40%) ---
        Surface(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth()
                .shadow(elevation = 16.dp),
            color = colors.background,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {

                // 1. Đồng hồ
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stepViewModel.formatDuration(sessionDuration),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 2.sp
                    )
                    Text(text = "Thời gian chạy", fontSize = 14.sp, color = colors.textSecondary)
                }

                // 2. Thông số
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItemCompact(String.format("%.2f", sessionDistance), "Km", colors, Color(0xFF10B981))
                    StatItemCompact(String.format("%.1f", sessionSpeed), "Km/h", colors)
                    StatItemCompact("$sessionSteps", "Steps", colors)
                    StatItemCompact("${stepViewModel.calculateCalories(sessionSteps.toLong())}", "Kcal", colors, Color(0xFFF59E0B))
                }

                // 3. NÚT ĐIỀU KHIỂN (Đã sửa lại có 2 nút)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp) // Khoảng cách giữa 2 nút
                ) {
                    // --- NÚT 1: TẠM DỪNG / TIẾP TỤC ---
                    Button(
                        onClick = {
                            if (isRunning) {
                                // Đang chạy -> Bấm để Tạm dừng
                                stepViewModel.pauseRunSession()
                                onToggleService(false) // Tắt service đếm nền
                            } else {
                                // Đang dừng -> Bấm để Tiếp tục
                                stepViewModel.resumeRunSession()
                                onToggleService(true) // Bật lại service
                            }
                        },
                        modifier = Modifier
                            .weight(1f) // Chiếm 50% chiều ngang
                            .height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        // Màu vàng nếu đang chạy (để pause), Màu xanh nếu đang dừng (để resume)
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFF59E0B) else Color(0xFF10B981)
                        )
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Toggle",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isRunning) "TẠM DỪNG" else "TIẾP TỤC",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // --- NÚT 2: KẾT THÚC (STOP) ---
                    Button(
                        onClick = {
                            onToggleService(false)
                            stepViewModel.finishRunSession(totalRealtimeSteps)
                            onFinishRun(sessionSteps, stepViewModel.calculateCalories(sessionSteps.toLong()), sessionDuration)
                            onClose()
                        },
                        modifier = Modifier
                            .weight(1f) // Chiếm 50% chiều ngang còn lại
                            .height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Icon(Icons.Default.Stop, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("KẾT THÚC", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ... Các hàm helper OsmMapView và StatItemCompact giữ nguyên ...

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