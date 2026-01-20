package com.example.healthapp


import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Timer
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


data class NotificationItem(
    val id: Int,
    val title: String,
    val description: String,
    val time: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun NotificationsScreen(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {}
) {
    val isPreview = LocalInspectionMode.current
    var isVisible by remember { mutableStateOf(isPreview) }

    LaunchedEffect(Unit) {
        if (!isPreview) isVisible = true
    }

    val notifications = remember {
        listOf(
            NotificationItem(
                1,
                "Goal Achieved!",
                "You've reached your 10,000 steps goal for today. Keep it up!",
                "10m ago",
                Icons.Default.LocalFireDepartment,
                Color(0xFF10B981)
            ),
            NotificationItem(
                2,
                "Heart Rate Alert",
                "Your heart rate was slightly higher during your morning rest.",
                "1h ago",
                Icons.Default.Favorite,
                Color(0xFFEF4444)
            ),
            NotificationItem(
                3,
                "Sleep Analysis",
                "You had 2 hours of deep sleep last night. That's a 15% increase!",
                "3h ago",
                Icons.Default.NightsStay,
                Color(0xFF8B5CF6)
            ),
            NotificationItem(
                4,
                "Hydration Reminder",
                "Time to drink some water! You're 500ml away from your goal.",
                "5h ago",
                Icons.Default.Info,
                Color(0xFF3B82F6)
            ),
            NotificationItem(
                5,
                "Activity Inactive",
                "You've been sitting for over an hour. Time for a quick stretch!",
                "Yesterday",
                Icons.Default.Timer,
                Color(0xFFF59E0B)
            ),
            NotificationItem(
                6,
                "Weekly Summary",
                "Your weekly health report is ready to view.",
                "Yesterday",
                Icons.Default.Notifications,
                Color(0xFF6366F1)
            )
        )
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
            .background(Color(0xFF0F172A))
    ) {
        // 1. Consistent Dynamic Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD946EF).copy(0.12f), Color.Transparent),
                    center = Offset(floatAnim % size.width, size.height * 0.2f)
                ),
                radius = 700f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(0.15f), Color.Transparent),
                    center = Offset(size.width - (floatAnim % size.width), size.height * 0.8f)
                ),
                radius = 600f
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                OptiTopBar(title = "Notifications", onBackClick = onBackClick)
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(notifications) { index, notification ->
                    NotificationCard(
                        notification = notification,
                        visible = isVisible,
                        index = index
                    )
                }
            }
        }
    }
}

@Composable
fun OptiTopBar(title: String, onBackClick: () -> Unit) {
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
            text = title,
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
fun NotificationCard(
    notification: NotificationItem,
    visible: Boolean,
    index: Int
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, index * 100)) + slideInHorizontally(
            tween(
                600,
                index * 100
            )
        ) { it / 4 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(0.07f))
                .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                .padding(16.dp),

            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(notification.color.copy(alpha = 0.15f)),
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
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = notification.time,
                        color = Color.White.copy(0.4f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = notification.description,
                    color = Color.White.copy(0.7f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Preview
@Composable
fun NotificationsPreview() {
    NotificationsScreen()
}
