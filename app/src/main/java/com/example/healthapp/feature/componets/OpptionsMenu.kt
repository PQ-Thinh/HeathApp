package com.example.healthapp.feature.componets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.ui.theme.AestheticColors
import androidx.compose.ui.text.font.FontWeight

@Composable
fun CustomTopMenu(
    colors: AestheticColors,
    selectedMode: String, // 👈 Nhận giá trị từ ViewModel
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val options = listOf("Chạy Bộ", "Đạp xe", "Leo Núi")
    val rotateAnim by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "arrow")

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.glassContainer)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(16.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Chế độ luyện tập", color = colors.textSecondary, fontSize = 12.sp)
                Text(
                    text = selectedMode, // 👈 Hiển thị giá trị truyền vào
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.rotate(rotateAnim)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(colors.glassContainer)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(12.dp)),
            offset = DpOffset(0.dp, 8.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = if (selectedMode == option) colors.accent else colors.textPrimary,
                            fontWeight = if (selectedMode == option) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onOptionSelected(option)
                    }
                )
            }
        }
    }
}