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
import java.util.concurrent.TimeUnit

@Composable
fun RunTrackingScreen(
    stepViewModel: StepViewModel,
    onClose: () -> Unit, // Hàm đóng màn hình (chỉ ẩn, không stop)
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

    // Logic update: Nếu ViewModel báo đang chạy -> Update liên tục
    LaunchedEffect(totalRealtimeSteps, isRunning) {
        if (isRunning && totalRealtimeSteps > 0) {
            stepViewModel.updateSessionSteps(totalRealtimeSteps)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // --- PHẦN 1: BẢN ĐỒ (Chiếm 6/10 màn hình) ---
        Box(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
        ) {
            OsmMapView(modifier = Modifier.fillMaxSize())

            // Nút Back nhỏ ở góc trên (để thu nhỏ màn hình nếu muốn)
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .padding(top = 48.dp, start = 16.dp)
                    .background(Color.White.copy(0.8f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
        }

        // --- PHẦN 2: THÔNG TIN (Chiếm 4/10 màn hình) ---
        Surface(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth()
                .shadow(elevation = 16.dp), // Tạo bóng đổ đè lên map
            color = colors.background,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween // Căn đều nội dung
            ) {

                // 1. Đồng hồ to
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stepViewModel.formatDuration(sessionDuration),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Thời gian chạy",
                        fontSize = 14.sp,
                        color = colors.textSecondary
                    )
                }

                // 2. Grid Thông số (Distance - Speed - Steps - Kcal)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItemCompact(value = String.format("%.2f", sessionDistance), unit = "Km", colors = colors, color = Color(0xFF10B981))
                    StatItemCompact(value = String.format("%.1f", sessionSpeed), unit = "Km/h", colors = colors)
                    StatItemCompact(value = "$sessionSteps", unit = "Steps", colors = colors)
                    StatItemCompact(value = "${stepViewModel.calculateCalories(sessionSteps.toLong())}", unit = "Kcal", colors = colors, color = Color(0xFFF59E0B))
                }

                // 3. Nút Điều khiển
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút STOP (Màu đỏ)
                    Button(
                        onClick = {
                            onToggleService(false)
                            stepViewModel.finishRunSession(totalRealtimeSteps)
                            onFinishRun(sessionSteps, stepViewModel.calculateCalories(sessionSteps.toLong()), sessionDuration)
                            onClose()
                        },
                        modifier = Modifier
                            .height(64.dp) // Nút to hình viên thuốc
                            .weight(1f),
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