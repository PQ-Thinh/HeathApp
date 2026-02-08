package com.example.healthapp.feature.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.ui.theme.AestheticColors
import kotlinx.serialization.StringFormat

@Composable
fun StepRunDetail(
    steps: Int,
    calories: Int,
    timeSeconds: Long,
    onBackClick: () -> Unit,
    stepViewModel: StepViewModel = hiltViewModel(),
    colors: AestheticColors
) {
    // Animation đơn giản khi mở màn hình
    var startAnim by remember { mutableStateOf(false) }
    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(1000)
    )

    LaunchedEffect(Unit) { startAnim = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // 1. Success Icon Header
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.gradientOrb1, colors.gradientOrb2)))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tuyệt vời!",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Text(
                text = "Bạn vừa hoàn thành một phiên chạy.",
                fontSize = 16.sp,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // 2. Stats Grid (Thẻ kết quả)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(colors.glassContainer)
                    .border(1.dp, colors.glassBorder, RoundedCornerShape(32.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Hàng 1: Thời gian
                ResultRow(
                    icon = Icons.Default.Timer,
                    label = "Thời gian",
                    value = stepViewModel.formatDuration(timeSeconds),
                    color = Color(0xFF3B82F6), // Blue
                    colors = colors
                )
                Divider(color = colors.glassBorder)

                // Hàng 2: Bước chân
                ResultRow(
                    icon = Icons.Default.DirectionsRun,
                    label = "Tổng bước",
                    value = "$steps",
                    unit = "bước",
                    color = Color(0xFF10B981), // Green
                    colors = colors
                )
                Divider(color = colors.glassBorder)

                // Hàng 3: Calo
                ResultRow(
                    icon = Icons.Default.LocalFireDepartment,
                    label = "Đốt cháy",
                    value = "$calories",
                    unit = "kcal",
                    color = Color(0xFFEF4444), // Red
                    colors = colors
                )
                Divider(color = colors.glassBorder)

                // Hàng 3: Calo
                ResultRow(
                    icon = Icons.Default.LocationOn,
                    label = "Đi Được",
                    value = String.format("%.2f","${(steps*0.7)/1000.0}"),
                    unit = "km/h",
                    color = Color(0xFFEF4444), // Red
                    colors = colors
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 3. Button Home
            Button(
                onClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent)
            ) {
                Text("Quay về trang chủ", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ResultRow(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String = "",
    color: Color,
    colors: AestheticColors
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, fontSize = 16.sp, color = colors.textSecondary)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            if (unit.isNotEmpty()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, fontSize = 14.sp, color = colors.textSecondary, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}