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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.util.concurrent.TimeUnit

@Composable
fun RunTrackingScreen(
    stepViewModel: StepViewModel,
    onClose: () -> Unit,
    onToggleService: (Boolean) -> Unit, // Hàm để bật/tắt service
    isServiceRunning: Boolean,
    colors: AestheticColors,
    mainViewModel: MainViewModel,
    onFinishRun: (Int, Int, Long) -> Unit,
) {
    val context = LocalContext.current

    // State quản lý thời gian (Timer)
    var timeSeconds by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    // Lấy dữ liệu Realtime gốc từ MainViewModel
    val totalRealtimeSteps by mainViewModel.realtimeSteps.collectAsState()

    // Lấy dữ liệu Session (đã trừ đi bước ban đầu) từ StepViewModel
    val sessionSteps by stepViewModel.sessionSteps.collectAsState()

    // Khởi tạo Session khi màn hình mở ra lần đầu
    LaunchedEffect(Unit) {
        // Cấu hình OSM
        Configuration.getInstance()
            .load(context, PreferenceManager.getDefaultSharedPreferences(context))

        // Bắt đầu tính phiên chạy (đặt mốc 0)
        stepViewModel.startRunSession(totalRealtimeSteps)
    }

    // Cập nhật session steps mỗi khi tổng bước thay đổi
    LaunchedEffect(totalRealtimeSteps) {
        stepViewModel.updateSessionSteps(totalRealtimeSteps)
    }
    // Timer Effect
    LaunchedEffect(isRunning) {
        val startTime = System.currentTimeMillis() - (timeSeconds * 1000)
        while (isRunning) {
            delay(1000)
            timeSeconds = (System.currentTimeMillis() - startTime) / 1000
        }
    }

    // Định dạng thời gian HH:MM:SS

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { RunTrackingTopBar(onBackClick = onClose, colors = colors) }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // BẢN ĐỒ OSM (Nền dưới cùng)
            OsmMapView(modifier = Modifier.fillMaxSize())

            // LỚP PHỦ THÔNG TIN (Overlay phía dưới)
            // Dùng Surface kính mờ hoặc Card bo tròn
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .background(colors.background.copy(alpha = 0.95f)) // Nền mờ che bản đồ
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Thanh gạch nhỏ để trông giống BottomSheet
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.textSecondary.copy(alpha = 0.3f))
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = formatTime(timeSeconds),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Thời gian",
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DataColumn(value = "$sessionSteps", label = "Bước chân", colors = colors)
                    DataColumn(value = "${stepViewModel.calculateCalories(sessionSteps.toLong())}", label = "Kcal", colors = colors)
                    // Có thể thêm Distance nếu tính toán được từ GPS
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pause/Resume
                    Button(
                        onClick = {
                            isRunning = !isRunning
                            onToggleService(isRunning)
                        },
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Stop Button -> Navigate to Detail
                    if (!isRunning) {
                        Button(
                            onClick = {
                                onToggleService(false)
                                // Gọi callback để chuyển màn hình, truyền dữ liệu vừa chạy xong
                                onFinishRun(sessionSteps, stepViewModel.calculateCalories(sessionSteps.toLong()), timeSeconds)
                                onClose() // Đóng màn hình chạy
                            },
                            modifier = Modifier.size(72.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun OsmMapView(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // State giữ lại MapView để không bị reload khi recompose
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK) // Style bản đồ mặc định
            setMultiTouchControls(true)
            controller.setZoom(18.0)

            // Setup MyLocation (Blue Dot)
            val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
            locationOverlay.enableMyLocation()
            locationOverlay.enableFollowLocation()
            overlays.add(locationOverlay)
        }
    }

    // Quản lý Lifecycle cho MapView (Quan trọng để tránh leak memory)
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}




@Composable
fun DataColumn(value: String, label: String, colors: AestheticColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Text(
            text = label,
            fontSize = 14.sp,
            color = colors.textSecondary
        )
    }
}
@Composable
fun RunTrackingTopBar(onBackClick: () -> Unit, colors: AestheticColors) {
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
            text = "Quay Lại",
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
fun formatTime(seconds: Long): String {
    val h = TimeUnit.SECONDS.toHours(seconds)
    val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}