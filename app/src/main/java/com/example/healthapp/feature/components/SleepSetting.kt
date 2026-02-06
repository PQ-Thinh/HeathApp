package com.example.healthapp.feature.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSettingDialog(
    onDismiss: () -> Unit,
    onSave: (LocalDate, Int, Int, Int, Int) -> Unit,
    initialDate: LocalDate = LocalDate.now(),
    initialStartHour: Int = 22,
    initialStartMinute: Int = 0,
    initialEndHour: Int = 7,
    initialEndMinute: Int = 0,
    isEditing: Boolean = false
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(initialDate) }

    // Start Time
    val startState = rememberTimePickerState(
        initialHour = initialStartHour,
        initialMinute = initialStartMinute,
        is24Hour = true
    )
    val endState = rememberTimePickerState(
        initialHour = initialEndHour,
        initialMinute = initialEndMinute,
        is24Hour = true
    )
    // State lưu trữ
    var startHour by remember { mutableStateOf(22) } // Mặc định 10h tối
    var startMinute by remember { mutableStateOf(0) }
    var endHour by remember { mutableStateOf(6) } // Mặc định 6h sáng
    var endMinute by remember { mutableStateOf(0) }

    // --- DATE PICKER ---
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
        },
        selectedDate.year,
        selectedDate.monthValue - 1,
        selectedDate.dayOfMonth
    )

    // --- TIME PICKERS ---
    val startTimePicker = TimePickerDialog(
        context,
        { _, hour, minute ->
            startHour = hour
            startMinute = minute
        },
        startHour, startMinute, true
    )

    val endTimePicker = TimePickerDialog(
        context,
        { _, hour, minute ->
            endHour = hour
            endMinute = minute
        },
        endHour, endMinute, true
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isEditing) "Chỉnh sửa Giấc ngủ" else "Ghi nhận Giấc ngủ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                //CHỌN NGÀY BẮT ĐẦU
                SleepInputRow(
                    label = "Ngày bắt đầu",
                    value = selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    icon = Icons.Default.CalendarToday,
                    onClick = { datePickerDialog.show() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                //  CHỌN GIỜ NGỦ (Start)
                SleepInputRow(
                    label = "Giờ đi ngủ",
                    value = String.format("%02d:%02d", startHour, startMinute),
                    icon = Icons.Default.AccessTime,
                    onClick = { startTimePicker.show() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // CHỌN GIỜ DẬY (End)
                SleepInputRow(
                    label = "Giờ thức dậy",
                    value = String.format("%02d:%02d", endHour, endMinute),
                    icon = Icons.Default.AccessTime,
                    onClick = { endTimePicker.show() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(
                            selectedDate,
                            startState.hour, startState.minute,
                            endState.hour, endState.minute
                        )
                    },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))

                    ) { Text("Lưu", color = Color.White) }
                }
            }
        }
    }
}

@Composable
fun SleepInputRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF3F4F6))
                .clickable { onClick() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = value, fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Medium)
        }
    }
}