package com.example.healthapp


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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview


@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val isPreview = LocalInspectionMode.current
    var isVisible by remember { mutableStateOf(isPreview) }

    // State for demo purposes
    var notificationsEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(true) }
    var biometricEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!isPreview) isVisible = true
    }

    // Dynamic background animation matching other screens
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
            .background(Color(0xFF0F172A))
    ) {
        // 1. Consistent Background Design
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(0.12f), Color.Transparent),
                    center = Offset(size.width * 0.2f, floatAnim % size.height)
                ),
                radius = 600f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD946EF).copy(0.15f), Color.Transparent),
                    center = Offset(size.width - (floatAnim % size.width), size.height * 0.5f)
                ),
                radius = 700f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                SettingsTopBar(onBackClick)
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 2. Preferences Group
                item {
                    SettingsSection(title = "App Preferences", visible = isVisible, delay = 100) {
                        ToggleSettingItem(
                            icon = Icons.Default.NotificationsActive,
                            title = "Push Notifications",
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it }
                        )
                        Divider(color = Color.White.copy(0.05f), thickness = 1.dp)
                        ToggleSettingItem(
                            icon = Icons.Default.DarkMode,
                            title = "Dark Mode",
                            checked = darkModeEnabled,
                            onCheckedChange = { darkModeEnabled = it }
                        )
                    }
                }

                // 3. Security Group
                item {
                    SettingsSection(title = "Security", visible = isVisible, delay = 300) {
                        ToggleSettingItem(
                            icon = Icons.Default.Fingerprint,
                            title = "Biometric Lock",
                            checked = biometricEnabled,
                            onCheckedChange = { biometricEnabled = it }
                        )
                        Divider(color = Color.White.copy(0.05f), thickness = 1.dp)
                        ActionSettingItem(
                            icon = Icons.Default.VpnKey,
                            title = "Change Password"
                        )
                    }
                }

                // 4. Data & Localization
                item {
                    SettingsSection(title = "Localization", visible = isVisible, delay = 500) {
                        ActionSettingItem(
                            icon = Icons.Default.Language,
                            title = "Language",
                            value = "English (US)"
                        )
                        Divider(color = Color.White.copy(0.05f), thickness = 1.dp)
                        ActionSettingItem(
                            icon = Icons.Default.Straighten,
                            title = "Units of Measure",
                            value = "Metric"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTopBar(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Text(
            text = "Settings",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                shadow = Shadow(Color.Black.copy(0.3f), blurRadius = 4f)
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
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800, delay)) + slideInVertically(tween(800, delay)) { 20 }
    ) {
        Column {
            Text(
                text = title,
                color = Color(0xFF6366F1),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(0.06f))
                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
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
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = Color.White, fontSize = 16.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF6366F1),
                uncheckedThumbColor = Color.White.copy(0.5f),
                uncheckedTrackColor = Color.White.copy(0.1f),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun ActionSettingItem(
    icon: ImageVector,
    title: String,
    value: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = Color.White, fontSize = 16.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(value, color = Color.White.copy(0.4f), fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Color.White.copy(0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Preview
@Composable
fun SettingsScreenPreview() {
    SettingsScreen()
}

