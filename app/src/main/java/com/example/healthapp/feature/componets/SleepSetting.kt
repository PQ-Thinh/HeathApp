package com.example.healthapp.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSettingDialog(
    onDismiss: () -> Unit,
    onSave: (startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) -> Unit
) {
    val currentTime = Calendar.getInstance()

    // State quản lý thời gian
    val timePickerState = rememberTimePickerState(
        initialHour = currentTime.get(Calendar.HOUR_OF_DAY),
        initialMinute = currentTime.get(Calendar.MINUTE),
        is24Hour = true,
    )

    // State quản lý luồng: True = Đang chọn giờ ngủ, False = Đang chọn giờ dậy
    var isPickingStartTime by remember { mutableStateOf(true) }

    // Biến tạm để lưu giờ ngủ sau khi chọn xong bước 1
    var tempStartHour by remember { mutableIntStateOf(0) }
    var tempStartMinute by remember { mutableIntStateOf(0) }

    /** Determines whether the time picker is dial or input */
    var showDial by remember { mutableStateOf(true) }

    val toggleIcon = if (showDial) {
        Icons.Filled.EditCalendar
    } else {
        Icons.Filled.AccessTime
    }

    // Tiêu đề thay đổi theo bước
    val dialogTitle = if (isPickingStartTime) "Bạn bắt đầu ngủ lúc mấy giờ?" else "Bạn thức dậy lúc mấy giờ?"

    AdvancedTimePickerDialog(
        title = dialogTitle,
        onDismiss = { onDismiss() },
        onConfirm = {
            if (isPickingStartTime) {
                // Xong bước 1: Lưu giờ ngủ tạm thời -> Chuyển sang bước 2
                tempStartHour = timePickerState.hour
                tempStartMinute = timePickerState.minute
                isPickingStartTime = false
            } else {
                // Xong bước 2: Gọi callback trả về cả 4 giá trị
                onSave(
                    tempStartHour,
                    tempStartMinute,
                    timePickerState.hour,
                    timePickerState.minute
                )
            }
        },
        toggle = {
            IconButton(onClick = { showDial = !showDial }) {
                Icon(
                    imageVector = toggleIcon,
                    contentDescription = "Time picker type toggle",
                )
            }
        },
    ) {
        if (showDial) {
            TimePicker(
                state = timePickerState,
            )
        } else {
            TimeInput(
                state = timePickerState,
            )
        }
    }
}

@Composable
fun AdvancedTimePickerDialog(
    title: String = "Select Time",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    toggle: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .width(IntrinsicSize.Min)
                    .height(IntrinsicSize.Min)
                    .background(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surface
                    ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = title,
                    style = MaterialTheme.typography.labelMedium
                )
                content()
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth()
                ) {
                    toggle()
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    TextButton(onClick = onConfirm) { Text(if (title.contains("ngủ")) "Tiếp tục" else "Lưu") }
                }
            }
        }
    }
}

@Preview
@Composable
fun SleepSettingPreview() {
    SleepSettingDialog(
        onDismiss = {},
        onSave = { _, _, _, _ -> }
    )
}