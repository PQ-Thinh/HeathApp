package com.example.healthapp.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

@Composable
fun RunTrackingScreen(
    stepViewModel: StepViewModel,
    onClose: () -> Unit,
    onToggleService: (Boolean) -> Unit, // Hàm để bật/tắt service
    isServiceRunning: Boolean,
    colors: AestheticColors,
    mainViewModel: MainViewModel,
) {
    val context = LocalContext.current

    // State quản lý thời gian (Timer)
    var timeSeconds by remember { mutableLongStateOf(0L) }
    var isRunning by remember { mutableStateOf(false) }
    val step by mainViewModel.realtimeSteps.collectAsState()

    // Timer Effect
    LaunchedEffect(isRunning) {
        val startTime = System.currentTimeMillis() - (timeSeconds * 1000)
        while (isRunning) {
            delay(1000)
            timeSeconds = (System.currentTimeMillis() - startTime) / 1000
        }
    }

    // Định dạng thời gian HH:MM:SS
    fun formatTime(seconds: Long): String {
        val h = TimeUnit.SECONDS.toHours(seconds)
        val m = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val s = seconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = { RunTrackingTopBar(onBackClick = onClose, colors = colors) }
    ) {paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Timer Lớn
                Text(
                    text = formatTime(timeSeconds),
                    fontSize = 80.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "Thời gian chạy",
                    color = colors.textSecondary,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(48.dp))

                // 2. Thông số (Steps & Calo) - Bỏ nhịp tim
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DataColumn(value = "${step.toLong()}", label = "Bước chân", colors = colors)
                    DataColumn(
                        value = "${stepViewModel.calculateCalories(step.toLong())}",
                        label = "Kcal",
                        colors = colors
                    )
                }

                Spacer(modifier = Modifier.height(64.dp))

                // 3. Control Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút Pause/Resume
                    Button(
                        onClick = {
                            isRunning = !isRunning
                            if (isRunning) onToggleService(true) else onToggleService(false) // Pause service logic
                        },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)) // Amber
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Pause",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Nút Stop (Kết thúc)
                    if (!isRunning) {
                        Button(
                            onClick = {
                                onToggleService(false) // Tắt hẳn service
                                onClose() // Đóng màn hình chạy
                            },
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)) // Red
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
            }
        }
    }
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