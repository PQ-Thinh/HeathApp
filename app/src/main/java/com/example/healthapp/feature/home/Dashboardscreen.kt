package com.example.healthapp.feature.home

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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic

@Composable
fun HealthDashboardScreen(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    isDarkTheme: Boolean // Thêm tham số này
) {
    val isPreview = LocalInspectionMode.current
    var isContentVisible by remember { mutableStateOf(isPreview) }

    // 1. CHỌN MÀU THEO THEME
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic

    LaunchedEffect(Unit) {
        if (!isPreview) isContentVisible = true
    }

    // Matching background animation
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
            .background(colors.background) // Màu nền động
    ) {
        // 2. Consistent Dynamic Background
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
                    colors = colors // Truyền colors vào TopBar
                )
            }
        )
        { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 3. Welcome Section
                item {
                    AnimatedVisibility(
                        visible = isContentVisible,
                        enter = fadeIn() + slideInVertically { -20 }
                    ) {
                        Column {
                            Text(
                                text = "Hello, Alex!",
                                style = TextStyle(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = colors.textPrimary, // Màu chữ động
                                    shadow = if (isDarkTheme) Shadow(Color.Black.copy(0.3f), blurRadius = 8f) else null
                                )
                            )
                            Text(
                                text = "Here is your health summary for today.",
                                color = colors.textSecondary,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                // 4. Main Stats Grid
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HealthStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Heart Rate",
                            value = "72",
                            unit = "BPM",
                            icon = Icons.Default.Favorite,
                            accentColor = Color(0xFFEF4444), // Đỏ (Heart) giữ nguyên
                            colors = colors,
                            visible = isContentVisible,
                            delay = 200
                        )
                        HealthStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Sleep",
                            value = "7.5",
                            unit = "Hrs",
                            icon = Icons.Default.NightsStay,
                            accentColor = Color(0xFF8B5CF6), // Tím (Sleep) giữ nguyên
                            colors = colors,
                            visible = isContentVisible,
                            delay = 300
                        )
                    }
                }

                // 5. Large Activity Card
                item {
                    HealthStatCard(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Steps Taken",
                        value = "12,480",
                        unit = "steps today",
                        icon = Icons.Default.DirectionsRun,
                        accentColor = Color(0xFF10B981), // Xanh lá (Steps) giữ nguyên
                        colors = colors,
                        visible = isContentVisible,
                        delay = 400,
                        isLarge = true
                    )
                }

                // 6. Static Chart Placeholder
                item {
                    AnimatedVisibility(
                        visible = isContentVisible,
                        enter = fadeIn(tween(800, 500)) + slideInVertically { 40 }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(32.dp))
                                .background(colors.glassContainer) // Kính mờ
                                .border(1.dp, colors.glassBorder, RoundedCornerShape(32.dp))
                                .padding(24.dp)
                        ) {
                            Text(
                                "Weekly Activity",
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            // Simple bar chart representation
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f, 0.75f).forEach { scale ->
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .fillMaxHeight(scale)
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        colors.gradientOrb1, // Gradient cột biểu đồ theo theme
                                                        colors.gradientOrb2.copy(0.5f)
                                                    )
                                                )
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
        // Nút Settings bên trái
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

            // Avatar Profile bên phải
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(colors.gradientOrb1, colors.gradientOrb2)
                        )
                    )
                    .border(1.dp, colors.glassBorder, CircleShape)
                    .clickable { onProfileClick() }, // Thêm sự kiện click
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = Color.White, // Icon avatar luôn trắng để nổi trên nền gradient
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun HealthStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color, // Màu riêng của từng loại chỉ số (Tim, Ngủ, Bước chân)
    colors: AestheticColors, // Màu chung của theme
    visible: Boolean,
    delay: Int,
    isLarge: Boolean = false
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800, delay)) + scaleIn(
            initialScale = 0.9f,
            animationSpec = tween(800, delay)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(colors.glassContainer) // Kính mờ
                .border(1.dp, colors.glassBorder, RoundedCornerShape(32.dp))
                .padding(24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor, // Giữ màu đặc trưng (Đỏ/Tím/Xanh)
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

@Preview(name = "Dark Dashboard")
@Composable
fun HealthDashboardDarkPreview() {
    HealthDashboardScreen(isDarkTheme = true)
}

@Preview(name = "Light Dashboard")
@Composable
fun HealthDashboardLightPreview() {
    HealthDashboardScreen(isDarkTheme = false)
}