package com.example.healthapp.feature.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.helperEnumAndData.NotificationItem
import com.example.healthapp.core.model.entity.InvitationEntity
import com.example.healthapp.core.viewmodel.SocialViewModel
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale



@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    isDarkTheme: Boolean,
    socialViewModel: SocialViewModel = hiltViewModel() // Inject ViewModel
) {
    val isPreview = LocalInspectionMode.current
    var isVisible by remember { mutableStateOf(isPreview) }

    // 1. CHỌN MÀU THEO THEME
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic

    // 2. Lấy danh sách lời mời từ ViewModel
    val invitations by socialViewModel.incomingInvitations.collectAsState()
    val isRefreshing by socialViewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(Unit) {
        if (!isPreview) isVisible = true
    }

    // Dummy data cho thông báo thường
    val notifications = remember {
        listOf(
            NotificationItem(
                1,
                "Goal Achieved!",
                "You've reached your 10,000 steps goal.",
                "10m ago",
                Icons.Default.LocalFireDepartment,
                Color(0xFF10B981)
            ),
            NotificationItem(2, "Heart Rate Alert", "Your heart rate was slightly higher.", "1h ago", Icons.Default.Favorite, Color(0xFFEF4444)),
            NotificationItem(3, "Sleep Analysis", "You had 2 hours of deep sleep last night.", "3h ago", Icons.Default.NightsStay, Color(0xFF8B5CF6)),
            NotificationItem(4, "Hydration", "Time to drink water! 500ml left.", "5h ago", Icons.Default.Info, Color(0xFF3B82F6)),
            NotificationItem(5, "Inactive", "You've been sitting too long.", "Yesterday", Icons.Default.Timer, Color(0xFFF59E0B)),
            NotificationItem(6, "Weekly Summary", "Your report is ready.", "Yesterday", Icons.Default.Notifications, Color(0xFF6366F1))
        )
    }

    // Animation nền
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
            .background(colors.background)
    ) {
        // Background Orb Animation
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb1.copy(0.12f), Color.Transparent),
                    center = Offset(floatAnim % size.width, size.height * 0.2f)
                ),
                radius = 700f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb2.copy(0.15f), Color.Transparent),
                    center = Offset(size.width - (floatAnim % size.width), size.height * 0.8f)
                ),
                radius = 600f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                OptiTopBar(
                    title = "Notifications",
                    onBackClick = onBackClick,
                    colors = colors
                )
            }
        ) { paddingValues ->
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { socialViewModel.refreshNotifications() },
                state = pullRefreshState,
                modifier = Modifier.padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // --- PHẦN 1: LỜI MỜI THÁCH ĐẤU (ƯU TIÊN) ---
                    if (invitations.isNotEmpty()) {
                        item {
                            Text(
                                text = "Lời mời thách đấu (${invitations.size})",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEF4444) // Màu đỏ nổi bật
                                ),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        items(invitations) { invite ->
                            InvitationCard(
                                invite = invite,
                                colors = colors,
                                onAccept = { socialViewModel.acceptInvitation(invite) },
                                onReject = { socialViewModel.rejectInvitation(invite) }
                            )
                        }
                        item {
                            Divider(
                                modifier = Modifier.padding(vertical = 16.dp),
                                color = colors.glassBorder
                            )
                        }
                    }

                    // --- PHẦN 2: THÔNG BÁO HỆ THỐNG ---
                    item {
                        Text(
                            text = "Gần đây",
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            ),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    itemsIndexed(notifications) { index, notification ->
                        NotificationCard(
                            notification = notification,
                            visible = isVisible,
                            index = index,
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

// --- ITEM CARD CHO LỜI MỜI ---
@Composable
fun InvitationCard(
    invite: InvitationEntity,
    colors: AestheticColors,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.glassContainer) // Kính mờ
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f), RoundedCornerShape(24.dp)) // Viền đỏ nhẹ
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Icon Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF9800).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${invite.senderName} thách đấu bạn!",
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                text = "Mục tiêu: ${invite.targetSteps} bước",
                color = colors.textSecondary,
                fontSize = 14.sp
            )
            Text(
                text = formatTime(invite.timestamp),
                color = colors.textSecondary.copy(alpha = 0.6f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Buttons Action
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), // Green
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nhận", fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Từ chối", fontSize = 13.sp)
                }
            }
        }
    }
}

// --- ITEM CARD CHO THÔNG BÁO THƯỜNG ---
@Composable
fun NotificationCard(
    notification: NotificationItem,
    visible: Boolean,
    index: Int,
    colors: AestheticColors
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, index * 100)) + slideInHorizontally(
            tween(600, index * 100)
        ) { it / 4 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.glassContainer)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(24.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(notification.color.copy(alpha = if (colors == LightAesthetic) 0.1f else 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = notification.icon,
                    contentDescription = null,
                    tint = notification.color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = notification.time,
                        color = colors.textSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.description,
                    color = colors.textSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
fun OptiTopBar(
    title: String,
    onBackClick: () -> Unit,
    colors: AestheticColors
) {
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
            text = title,
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                shadow = if (colors.background == DarkAesthetic.background)
                    Shadow(Color.Black.copy(0.3f), blurRadius = 4f)
                else null
            ),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())
    return sdf.format(Date(timestamp))
}