package com.example.healthapp.feature.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.healthapp.ui.theme.AestheticColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStepDialog(
    onDismiss: () -> Unit,
    onSave: (startTime: Long, durationMinutes: Int, steps: Int) -> Unit,
    colors: AestheticColors,
    initialSteps: Int = 0,
    initialDuration: Int = 0,
    initialStartTime: Long = System.currentTimeMillis()
) {

    val initialDateTime = java.time.Instant.ofEpochMilli(initialStartTime)
        .atZone(java.time.ZoneId.systemDefault())
    // 1. Start Time States
    var selectedDate by remember { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by remember { mutableStateOf(initialDateTime.toLocalTime()) }

    var durationError by remember { mutableStateOf<String?>(null) }
    var stepsError by remember { mutableStateOf<String?>(null) }

    // Nếu duration > 0 thì hiện số, ngược lại để trống
    var durationStr by remember { mutableStateOf(if (initialDuration > 0) initialDuration.toString() else "") }
    // Step Count State
    var stepsStr by remember { mutableStateOf(if (initialSteps > 0) initialSteps.toString() else "") }

    // 4Metadata States (Read-only info from device)
    val recordingMethod = "Manual"
    val device = "Phone"
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL

    // Date/Time Pickers logic (Simplified using Android Views for compatibility)
    val context = LocalContext.current
    val calendar = Calendar.getInstance()

    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
    )

    val timePickerDialog = TimePickerDialog(
        context,
        { _, hour, minute -> selectedTime = LocalTime.of(hour, minute) },
        calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = MaterialTheme.shapes.large,

        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()), // Cho phép cuộn nếu màn hình nhỏ
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val title = if (initialSteps > 0) "Chỉnh sửa dữ liệu" else "Thêm dữ liệu mới"
                Text(title, style = MaterialTheme.typography.headlineSmall)

                // Start Time (Ngày & Giờ) ---
                Text("Thời gian bắt đầu", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { datePickerDialog.show() }, modifier = Modifier.weight(1f)) {
                        Text(selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                    }
                    Button(onClick = { timePickerDialog.show() }, modifier = Modifier.weight(1f)) {
                        Text(selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")))
                    }
                }

                // Duration (Thời gian di chuyển - phút) ---
                OutlinedTextField(
                    value = durationStr,
                    onValueChange = { if (it.all { char -> char.isDigit() }) durationStr = it },
                    label = { Text("Thời gian di chuyển (phút)") },
                    isError = durationError != null,
                    supportingText = { durationError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true )

                // --- HÀNG 3: Metadata (4 cột) ---
                Text("Metadata thiết bị", style = MaterialTheme.typography.labelMedium)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MetadataField("Method", recordingMethod, Modifier.weight(1f))
                        MetadataField("Device", device, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        MetadataField("Brand", manufacturer, Modifier.weight(1f))
                        MetadataField("Model", model, Modifier.weight(1f))
                    }
                }

                // --- HÀNG 4: Data Fields (Steps) ---
                OutlinedTextField(
                    value = stepsStr,
                    onValueChange = { if (it.all { char -> char.isDigit() }) stepsStr = it },
                    label = { Text("Số bước chân") },
                    isError = stepsError != null,
                    supportingText = { stepsError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), singleLine = true )

                // --- HÀNG CUỐI: Reset & Save Buttons ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            // Reset logic
                            durationStr = ""
                            stepsStr = ""
                            selectedDate = LocalDate.now()
                            selectedTime = LocalTime.now()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset")
                    }

                    Button(onClick = {
                        val duration = durationStr.toIntOrNull()
                        val steps = stepsStr.toIntOrNull()

                        var valid = true

                        if (duration == null || duration <= 0) {
                            durationError = "Bắt buộc nhập số phút hợp lệ"
                            valid = false
                        } else {
                            durationError = null
                        }

                        if (steps == null || steps <= 0) {
                            stepsError = "Bắt buộc nhập số bước chân hợp lệ"
                            valid = false
                        } else {
                            stepsError = null
                        }

                        if (valid) {
                            val startDateTime = selectedDate.atTime(selectedTime)
                            val startTimeMillis = startDateTime.atZone(ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                            onSave(startTimeMillis, duration!!, steps!!)
                        }
                    },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lưu")
                    }

                }
            }
        }
    }
}

@Composable
fun MetadataField(label: String, value: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        readOnly = true,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodySmall,
        singleLine = true
    )
}