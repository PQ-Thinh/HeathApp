package com.example.healthapp.feature.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.core.model.entity.StepRecordEntity
import com.example.healthapp.ui.theme.AestheticColors
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun LatestActivityCard(
    modifier: Modifier = Modifier,
    record: StepRecordEntity,
    colors: AestheticColors,
    visible: Boolean,
    delay: Int,
    onClick: (String) -> Unit // Callback trả về ID để điều hướng
) {
    // Tính toán thông số hiển thị
    val durationSeconds = (record.endTime - record.startTime) / 1000
    val durationString = if (durationSeconds > 3600) {
        "${durationSeconds / 3600}h ${(durationSeconds % 3600) / 60}m"
    } else {
        "${durationSeconds / 60}m ${durationSeconds % 60}s"
    }

    // Ước lượng Calo (0.04 cal/bước - công thức cơ bản)
    val calories = (record.count * 0.04).toInt()

    // Format ngày tháng
    val dateFormat = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
    val dateString = dateFormat.format(record.startTime)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(800, delay)) + slideInHorizontally { 50 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(32.dp))
                .background(colors.glassContainer)
                .border(1.dp, colors.glassBorder, RoundedCornerShape(32.dp))
                .clickable { onClick(record.id) } // Click vào card
                .padding(24.dp)
        ) {
            Column {
                // Header: Tiêu đề + Ngày giờ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DirectionsRun,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hoạt động gần nhất",
                            style = TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                        )
                    }

                    // Ngày giờ nhỏ góc phải
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, tint = colors.textSecondary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = dateString,
                            style = TextStyle(fontSize = 12.sp, color = colors.textSecondary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${record.count}",
                        style = TextStyle(
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                            color = colors.textPrimary
                        ),

                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "bước",
                        style = TextStyle(fontSize = 14.sp, color = colors.textSecondary),

                    )
                }
                // Số bước chân lớn


                Spacer(modifier = Modifier.height(20.dp))

                // Row thông tin phụ: Calo & Thời gian
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Badge Calo
                    InfoBadge(
                        icon = Icons.Default.LocalFireDepartment,
                        value = "$calories Kcal",
                        color = Color(0xFFF59E0B),
                        backgroundColor = Color(0xFFF59E0B).copy(alpha = 0.1f),
                        textColor = colors.textPrimary
                    )

                    // Badge Thời gian
                    InfoBadge(
                        icon = Icons.Default.Timer,
                        value = durationString,
                        color = Color(0xFF3B82F6),
                        backgroundColor = Color(0xFF3B82F6).copy(alpha = 0.1f),
                        textColor = colors.textPrimary
                    )
                }
            }
        }
    }
}

// Component phụ hiển thị Badge nhỏ (Calo/Time)
@Composable
fun InfoBadge(icon: ImageVector, value: String, color: Color, backgroundColor: Color, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
        )
    }
}