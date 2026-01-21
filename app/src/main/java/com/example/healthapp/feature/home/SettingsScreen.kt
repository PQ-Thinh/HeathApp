package com.example.healthapp.feature.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

//
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onThemeChanged: (Boolean) -> Unit,
    isDarkTheme: Boolean,
    onChangePassword: () -> Unit = {}

) {
    val isPreview = LocalInspectionMode.current
    var isVisible by remember { mutableStateOf(isPreview) }

    // State for demo purposes
    var notificationsEnabled by remember { mutableStateOf(true) }
    var biometricEnabled by remember { mutableStateOf(false) }

    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic

    LaunchedEffect(Unit) {
        if (!isPreview) isVisible = true
    }

    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background) // Sử dụng màu động
    ) {
        // Background Animation (Giữ nguyên logic, thay màu)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb1.copy(0.15f), Color.Transparent),
                    center = Offset(size.width * 0.2f, floatAnim % size.height)
                ),
                radius = 600f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb2.copy(0.15f), Color.Transparent),
                    center = Offset(size.width - (floatAnim % size.width), size.height * 0.5f)
                ),
                radius = 700f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SettingsTopBar(onBackClick, colors)
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Preferences Group
                item {
                    SettingsSection(title = "App Preferences", visible = isVisible, delay = 100, colors = colors) {
                        ToggleSettingItem(
                            icon = Icons.Default.NotificationsActive,
                            title = "Push Notifications",
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it },
                            colors = colors
                        )
                        Divider(color = colors.glassBorder, thickness = 1.dp)
                        ToggleSettingItem(
                            icon =  if (!isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            title =  if (!isDarkTheme) "Light Mode" else "Dark Mode",
                            checked = isDarkTheme,
                            onCheckedChange = { onThemeChanged(it) },
                            colors = colors
                        )
                    }
                }

                // Security Group
                item {
                    SettingsSection(title = "Security", visible = isVisible, delay = 300, colors = colors) {
                        ToggleSettingItem(
                            icon = Icons.Default.Fingerprint,
                            title = "Biometric Lock",
                            checked = biometricEnabled,
                            onCheckedChange = { biometricEnabled = it },
                            colors = colors
                        )
                        Divider(color = colors.glassBorder, thickness = 1.dp)
                        ActionSettingItem(
                            icon = Icons.Default.VpnKey,
                            title = "Đổi Mật Khẩu",
                            colors = colors,
                            onChangePassword = onChangePassword
                        )
                    }
                }

                // Data & Localization
                item {
                    SettingsSection(title = "Localization", visible = isVisible, delay = 500, colors = colors) {
                        ActionSettingItem(
                            icon = Icons.Default.Language,
                            title = "Language",
                            value = "English (US)",
                            colors = colors
                        )
                        Divider(color = colors.glassBorder, thickness = 1.dp)
                        ActionSettingItem(
                            icon = Icons.Default.Straighten,
                            title = "Units of Measure",
                            value = "Metric",
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTopBar(onBackClick: () -> Unit, colors: AestheticColors) {
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
            text = "Cài Đặt",
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

@Composable
fun SettingsSection(
    title: String,
    visible: Boolean,
    delay: Int,
    colors: AestheticColors,
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800, delay)) + slideInVertically(tween(800, delay)) { 20 }
    ) {
        Column {
            Text(
                text = title,
                color = colors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.glassContainer)
                    .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
            ) {
                content()
            }
        }
    }
}

@Composable
fun ToggleSettingItem(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    colors: AestheticColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.iconTint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = colors.textPrimary, fontSize = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = colors.accent,
                uncheckedThumbColor = if (colors == LightAesthetic) Color.Gray else Color.White.copy(0.5f),
                uncheckedTrackColor = if (colors == LightAesthetic) Color.LightGray else Color.White.copy(0.1f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun ActionSettingItem(
    icon: ImageVector,
    title: String,
    value: String? = null,
    colors: AestheticColors,
    onChangePassword: () -> Unit = {}

) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = colors.iconTint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = colors.textPrimary, fontSize = 16.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(value, color = colors.textSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview(name = "Dark Mode")
@Composable
fun SettingsScreenDarkPreview() {
    SettingsScreen(
        onBackClick = {},
        onThemeChanged = {},
        isDarkTheme = true
    )
}

@Preview(name = "Light Mode")
@Composable
fun SettingsScreenLightPreview() {
    SettingsScreen(
        onBackClick = {},
        onThemeChanged = {},
        isDarkTheme = false
    )
}