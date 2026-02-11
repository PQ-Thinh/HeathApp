package com.example.healthapp.feature.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.healthapp.ui.theme.AestheticColors

@Composable
fun FabMenu(
    isRunActive: Boolean = false,
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onRunClick: () -> Unit,
    colors: AestheticColors
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 135f else 0f,
        label = "fab_rotation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Các nút con (Chỉ hiện khi Expanded)
        if (expanded) {
            FabMenuItem(
                icon = Icons.Default.DirectionsRun,
                label = "Chạy bộ",
                onClick = {
                    onExpandChange(false)
                    onRunClick()
                },
                delay = 0
            )
            FabMenuItem(
                icon = Icons.Default.History,
                label = "Lịch sử",
                onClick = { /* Todo: Open history */ },
                delay = 50
            )
        }


        // Nút chính (FAB)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.accent)
                .clickable { onExpandChange(!expanded) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Menu",
                tint = Color.White,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotation)
            )
            if (isRunActive) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                       // .align(Alignment.End)
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }
    }
}

@Composable
fun FabMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    delay: Int
) {
    // Animation scale xuất hiện
    val scale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delay.toLong())
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.scale(scale.value)
    ) {
        // Label
        Box(
            modifier = Modifier
                .padding(end = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            androidx.compose.material3.Text(
                text = label,
                color = Color.Black,
                style = MaterialTheme.typography.labelMedium
            )
        }

        // Icon Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFFF59E0B) // Màu cam chủ đạo
            )
        }
    }
}