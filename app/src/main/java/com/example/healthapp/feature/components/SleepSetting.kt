package com.example.healthapp.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSettingDialog(
    onDismiss: () -> Unit,
    // Callback trả về 4 giá trị để ViewModel xử lý
    onSave: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit
) {
    // 1. Khởi tạo giá trị mặc định (Ví dụ: 22:00 đi ngủ, 07:00 dậy)
    var startHour by remember { mutableIntStateOf(22) }
    var startMinute by remember { mutableIntStateOf(0) }
    var endHour by remember { mutableIntStateOf(7) }
    var endMinute by remember { mutableIntStateOf(0) }

    // 2. State quản lý việc hiện TimePicker
    var showTimePicker by remember { mutableStateOf(false) }
    var isEditingStartTime by remember { mutableStateOf(true) } // True: Sửa giờ ngủ, False: Sửa giờ dậy

    val timePickerState = rememberTimePickerState(
        initialHour = if (isEditingStartTime) startHour else endHour,
        initialMinute = if (isEditingStartTime) startMinute else endMinute,
        is24Hour = true
    )

    // Reset state picker khi chuyển đổi giữa giờ ngủ/dậy
    LaunchedEffect(showTimePicker) {
        if (showTimePicker) {
            if (isEditingStartTime) {
                timePickerState.hour = startHour
                timePickerState.minute = startMinute
            } else {
                timePickerState.hour = endHour
                timePickerState.minute = endMinute
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                //.width(IntrinsicSize.Min)
                .size(330.dp)
                //.padding(8.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraLarge)
                .padding(8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(6.dp)
            ) {
                Text(
                    "Thiết lập giấc ngủ",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- Hàng 1: Giờ đi ngủ ---
                TimeDisplayRow(
                    label = "Đi ngủ",
                    hour = startHour,
                    minute = startMinute,
                    icon = Icons.Default.Bedtime,
                    iconColor = Color(0xFF6366F1), // Màu tím/indigo
                    onClick = {
                        isEditingStartTime = true
                        showTimePicker = true
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // --- Hàng 2: Giờ thức dậy ---
                TimeDisplayRow(
                    label = "Thức dậy",
                    hour = endHour,
                    minute = endMinute,
                    icon = Icons.Default.WbSunny,
                    iconColor = Color(0xFFF59E0B), // Màu vàng cam
                    onClick = {
                        isEditingStartTime = false
                        showTimePicker = true
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- Tính tổng thời gian ngủ dự kiến ---
                val durationText = calculateDuration(startHour, startMinute, endHour, endMinute)
                Text(
                    text = "Tổng thời gian: $durationText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // --- Buttons ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Button(
                        onClick = { onSave(startHour, startMinute, endHour, endMinute) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }

    // --- Dialog con: Time Picker (chỉ hiện khi showTimePicker = true) ---
    if (showTimePicker) {
        AdvancedTimePickerDialog(
            title = if (isEditingStartTime) "Chọn giờ đi ngủ" else "Chọn giờ thức dậy",
            onDismiss = { showTimePicker = false },
            onConfirm = {
                if (isEditingStartTime) {
                    startHour = timePickerState.hour
                    startMinute = timePickerState.minute
                } else {
                    endHour = timePickerState.hour
                    endMinute = timePickerState.minute
                }
                showTimePicker = false
            }
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

// Component hiển thị 1 dòng thời gian (Icon + Text + Time)
@Composable
fun TimeDisplayRow(
    label: String,
    hour: Int,
    minute: Int,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Hiển thị giờ định dạng HH:mm
        Text(
            text = String.format("%02d:%02d", hour, minute),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// Logic tính khoảng thời gian (xử lý qua đêm)
fun calculateDuration(sH: Int, sM: Int, eH: Int, eM: Int): String {
    var startMinutes = sH * 60 + sM
    var endMinutes = eH * 60 + eM

    // Nếu giờ dậy nhỏ hơn giờ ngủ -> Đã qua ngày mới (cộng thêm 24h)
    if (endMinutes < startMinutes) {
        endMinutes += 24 * 60
    }

    val diff = endMinutes - startMinutes
    val hours = diff / 60
    val minutes = diff % 60
    return "${hours}h ${minutes}p"
}

// Tái sử dụng Wrapper Dialog cũ của bạn (giữ nguyên hoặc chỉnh sửa nhẹ)
@Composable
fun AdvancedTimePickerDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraLarge)
               ,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                content()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    TextButton(onClick = onConfirm) { Text("OK") }
                }
            }
        }
    }
}