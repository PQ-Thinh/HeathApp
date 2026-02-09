package com.example.healthapp.feature.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.ui.theme.AestheticColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StepRunDetail(
    steps: Int,
    calories: Int,
    timeSeconds: Long,
    timestamp: Long = System.currentTimeMillis(),
    onBackClick: () -> Unit,
    stepViewModel: StepViewModel = hiltViewModel(),
    colors: AestheticColors
) {
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }

    // Tính toán quãng đường (km)
    val distanceKm = (steps * 0.7) / 1000.0
    // Tính toán tốc độ trung bình (km/h) - tránh chia cho 0
    val avgSpeed = if (timeSeconds > 0) (distanceKm / (timeSeconds / 3600.0)) else 0.0

    val dateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy - HH:mm", Locale("vi", "VN"))
    val dateString = dateFormat.format(Date(timestamp))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Background Glow Effect (Trang trí nền)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb1.copy(0.2f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.1f),
                    radius = 500f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb2.copy(0.15f), Color.Transparent),
                    center = Offset(0f, size.height * 0.6f),
                    radius = 600f
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- TOP BAR ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .background(colors.glassContainer, CircleShape)
                        .border(1.dp, colors.glassBorder, CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Chi tiết hoạt động",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- HERO SECTION: STEPS RING ---
            AnimatedVisibility(
                visible = startAnim,
                enter = fadeIn(tween(800)) + slideInVertically { 50 }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Vòng tròn Gradient
                    Canvas(modifier = Modifier.size(220.dp)) {
                        val strokeWidth = 20.dp.toPx()
                        // Nền mờ
                        drawCircle(
                            color = colors.glassBorder,
                            style = Stroke(width = strokeWidth)
                        )
                        // Vòng chính
                        drawArc(
                            brush = Brush.sweepGradient(
                                listOf(colors.gradientOrb1, colors.gradientOrb2, colors.gradientOrb1)
                            ),
                            startAngle = -90f,
                            sweepAngle = 360f, // Luôn vẽ full vòng tròn cho đẹp ở trang detail
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DirectionsRun,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "$steps",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary,
                            lineHeight = 56.sp
                        )
                        Text(
                            text = "BƯỚC",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- DATE TIME ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.glassContainer)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.CalendarToday, null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = dateString, color = colors.textSecondary, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- BENTO GRID STATS ---
            // Chia thành 2 hàng, mỗi hàng 2 cột
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Hàng 1: Calo & Thời gian
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocalFireDepartment,
                        title = "Calo",
                        value = "$calories",
                        unit = "Kcal",
                        color = Color(0xFFEF4444),
                        colors = colors,
                        delay = 200
                    )
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Timer,
                        title = "Thời gian",
                        value = stepViewModel.formatDuration(timeSeconds),
                        unit = "", // Unit đã nằm trong formatDuration
                        color = Color(0xFF3B82F6),
                        colors = colors,
                        delay = 300
                    )
                }

                // Hàng 2: Quãng đường & Tốc độ
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.LocationOn,
                        title = "Quãng đường",
                        value = String.format("%.2f", distanceKm),
                        unit = "Km",
                        color = Color(0xFFF59E0B),
                        colors = colors,
                        delay = 400
                    )
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Speed,
                        title = "Tốc độ TB",
                        value = String.format("%.1f", avgSpeed),
                        unit = "Km/h",
                        color = Color(0xFF10B981),
                        colors = colors,
                        delay = 500
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    unit: String,
    color: Color,
    colors: AestheticColors,
    delay: Int
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800, delay)) + slideInVertically(initialOffsetY = { 50 }, animationSpec = tween(800, delay)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(colors.glassContainer)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, color = colors.textSecondary, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
    }
}