package com.example.healthapp.feature.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EditHeartDialog(
    initialBpm: Int,
    initialTime: Long,
    onDismiss: () -> Unit,
    onSave: (bpm: Int, time: Long) -> Unit,
    isEditing: Boolean = true
) {
    val context = LocalContext.current

    val initialDateTime = Instant.ofEpochMilli(initialTime).atZone(ZoneId.systemDefault())

    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(initialDateTime.toLocalTime()) }

    val initialBpmStr = if (initialBpm > 0) initialBpm.toString() else ""
    var bpmStr by remember { mutableStateOf(initialBpmStr) }

    val datePickerDialog = DatePickerDialog(
        context,
        { _, y, m, d -> selectedDate = java.time.LocalDate.of(y, m + 1, d) },
        selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth
    )
    val timePickerDialog = TimePickerDialog(
        context,
        { _, h, m -> selectedTime = java.time.LocalTime.of(h, m) },
        selectedTime.hour, selectedTime.minute, true
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // [CẬP NHẬT] Tiêu đề thay đổi theo chế độ
                val title = if (isEditing) "Chỉnh sửa Nhịp tim" else "Thêm Nhịp tim"
                Text(title, style = MaterialTheme.typography.titleLarge)

                // 1. Chọn ngày giờ
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    }
                    Button(
                        onClick = { timePickerDialog.show() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }

                OutlinedTextField(
                    value = bpmStr,
                    onValueChange = { if (it.all { c -> c.isDigit() }) bpmStr = it },
                    label = { Text("Nhịp tim (BPM)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. Nút Lưu
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Hủy") }
                    Button(
                        onClick = {
                            val bpm = bpmStr.toIntOrNull()
                            if (bpm != null && bpm > 0) {
                                val finalTime = selectedDate.atTime(selectedTime)
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                onSave(bpm, finalTime)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) { Text("Lưu") }
                }
            }
        }
    }
}