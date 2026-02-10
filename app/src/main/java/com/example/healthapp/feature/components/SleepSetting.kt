package com.example.healthapp.feature.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
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
import com.example.healthapp.core.model.entity.SleepSessionEntity
import com.example.healthapp.core.viewmodel.SleepStageInput
import com.example.healthapp.core.viewmodel.SleepViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSettingDialog(
    onDismiss: () -> Unit,
    sleepViewModel: SleepViewModel,
    sessionToEdit: SleepSessionEntity? = null,
    isDarkTheme: Boolean = false
) {
    val context = LocalContext.current
    val backgroundColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
    val contentColor = if (isDarkTheme) Color.White else Color.Black

    // --- KHỞI TẠO GIÁ TRỊ (Nếu Edit thì lấy từ record, nếu Add thì lấy hiện tại) ---
    val initialDate = if (sessionToEdit != null) {
        Instant.ofEpochMilli(sessionToEdit.startTime).atZone(ZoneId.systemDefault()).toLocalDate()
    } else LocalDate.now()

    val initialTime = if (sessionToEdit != null) {
        Instant.ofEpochMilli(sessionToEdit.startTime).atZone(ZoneId.systemDefault()).toLocalTime()
    } else LocalTime.of(22, 0) // Mặc định 10h tối

    // --- STATES ---
    var selectedDate by remember { mutableStateOf(initialDate) }
    var startHour by remember { mutableIntStateOf(initialTime.hour) }
    var startMinute by remember { mutableIntStateOf(initialTime.minute) }

    // Danh sách các stage
    val addedStages = remember { mutableStateListOf<SleepStageInput>() }

    // Logic đổ dữ liệu cũ vào danh sách (Nếu đang Edit)
    LaunchedEffect(sessionToEdit) {
        if (sessionToEdit != null) {
            addedStages.clear()
            if (sessionToEdit.lightSleepDuration > 0) addedStages.add(SleepStageInput("Light", sessionToEdit.lightSleepDuration.toInt(), Color(0xFF60A5FA)))
            if (sessionToEdit.deepSleepDuration > 0) addedStages.add(SleepStageInput("Deep", sessionToEdit.deepSleepDuration.toInt(), Color(0xFF4F46E5)))
            if (sessionToEdit.remSleepDuration > 0) addedStages.add(SleepStageInput("REM", sessionToEdit.remSleepDuration.toInt(), Color(0xFF8B5CF6)))
            if (sessionToEdit.awakeDuration > 0) addedStages.add(SleepStageInput("Awake", sessionToEdit.awakeDuration.toInt(), Color(0xFFF59E0B)))

            // Fallback: Nếu record cũ chưa có chi tiết, chuyển toàn bộ thời gian thành Light
            if (addedStages.isEmpty()) {
                val totalMin = (sessionToEdit.endTime - sessionToEdit.startTime) / 60000
                if (totalMin > 0) {
                    addedStages.add(SleepStageInput("Light", totalMin.toInt(), Color(0xFF60A5FA)))
                }
            }
        } else {
            // Nếu thêm mới: Mặc định thêm 1 cái Light 480p (8 tiếng) cho tiện
            // addedStages.add(SleepStageInput("Light", 480, Color(0xFF60A5FA)))
        }
    }

    // Input cho Stage mới
    val stageTypes = listOf("Light", "Deep", "REM", "Awake")
    val stageColors = listOf(Color(0xFF60A5FA), Color(0xFF4F46E5), Color(0xFF8B5CF6), Color(0xFFF59E0B))
    var selectedStageIndex by remember { mutableIntStateOf(0) }
    var durationHour by remember { mutableIntStateOf(0) }
    var durationMinute by remember { mutableIntStateOf(30) } // Mặc định 30p

    val durationPickerDialog = TimePickerDialog(
        context,
        { _, h, m ->
            durationHour = h
            durationMinute = m
        },
        durationHour,
        durationMinute,
        true // true = 24h view (để tránh hiện AM/PM gây hiểu nhầm là chọn giờ trong ngày)
    )

    // Tính toán tổng & End Time
    val totalDurationMinutes = addedStages.sumOf { it.durationMinutes }
    val startTime = LocalTime.of(startHour, startMinute)
    val endTime = startTime.plusMinutes(totalDurationMinutes.toLong())
    val isNextDay = (startHour * 60 + startMinute + totalDurationMinutes) >= 24 * 60

    // Pickers
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
        selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth
    )

    val timePickerDialog = TimePickerDialog(
        context,
        { _, h, m -> startHour = h; startMinute = m },
        startHour, startMinute, true
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier.fillMaxWidth().heightIn(max = 750.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (sessionToEdit != null) "Chỉnh sửa giấc ngủ" else "Thêm giấc ngủ",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = contentColor
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 1. CHỌN NGÀY (Giữ nguyên theo yêu cầu)
                SleepInputRow(
                    label = "Ngày bắt đầu",
                    value =
                        if(selectedDate > LocalDate.now()) "Ngày hiện tại"
                        else selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    icon = Icons.Default.CalendarToday,
                    onClick = { datePickerDialog.show() }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 2. CHỌN GIỜ BẮT ĐẦU
                SleepInputRow(
                    label = "Giờ đi ngủ",
                    value = String.format("%02d:%02d", startHour, startMinute),
                    icon = Icons.Default.AccessTime,
                    onClick = { timePickerDialog.show() }
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 3. HIỂN THỊ GIỜ THỨC DẬY (Tự tính toán)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Thức dậy lúc (dự kiến):", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = "${endTime.format(DateTimeFormatter.ofPattern("HH:mm"))} ${if(isNextDay) "(+1 ngày)" else ""}",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF10B981)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Tổng thời gian:", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = "${totalDurationMinutes / 60}h ${totalDurationMinutes % 60}m",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF6366F1)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), color = Color.LightGray.copy(0.3f))

                // 4. BỘ NHẬP STAGE
                Text("Chi tiết các giai đoạn:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = contentColor, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(8.dp))

                // Chip chọn loại
                ScrollableTabRow(
                    selectedTabIndex = selectedStageIndex,
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    indicator = {},
                    divider = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    stageTypes.forEachIndexed { index, type ->
                        FilterChip(
                            selected = selectedStageIndex == index,
                            onClick = {
                                selectedStageIndex = index
                                durationHour = 0
                                durationMinute = 30
                            },
                            label = { Text(type) },
                            modifier = Modifier.padding(end = 4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = stageColors[index].copy(alpha = 0.2f),
                                selectedLabelColor = stageColors[index],
                                labelColor = contentColor

                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    OutlinedTextField(
                        value = if (durationHour > 0) "${durationHour}h ${durationMinute}m" else "${durationMinute} phút",
                        onValueChange = {},
                        label = { Text("Phút") },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = stageColors[selectedStageIndex],
                            focusedLabelColor = stageColors[selectedStageIndex],
                            unfocusedTextColor = contentColor,
                            focusedTextColor = contentColor,
                        ),
                        readOnly = true,
                        leadingIcon = {
                            IconButton(onClick = { durationPickerDialog.show() }) {
                                Icon(Icons.Default.AccessTime, null, tint = stageColors[selectedStageIndex])
                            }
                        },
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val mins = (durationHour * 60 + durationMinute)
                            if (mins > 0) {
                                addedStages.add(SleepStageInput(stageTypes[selectedStageIndex], mins, stageColors[selectedStageIndex]))
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(64.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = stageColors[selectedStageIndex]),
                        contentPadding = PaddingValues(0.dp) // Căn icon vào giữa nếu nút bị nhỏ
                    ) {
                        Icon(Icons.Default.Add, null)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. DANH SÁCH STAGES ĐÃ THÊM
                if (addedStages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp) // Giới hạn chiều cao list
                            .background(if (isDarkTheme) Color.Black.copy(0.2f) else Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            items(addedStages) { stage ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(stage.color))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stage.type, fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text("${sleepViewModel.formatMinToHr(stage.durationMinutes.toLong())}", color = Color.Gray, fontSize = 14.sp)
                                    IconButton(onClick = { addedStages.remove(stage) }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text("Chưa có giai đoạn nào.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 6. NÚT LƯU
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Hủy", color = Color.Gray) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (addedStages.isEmpty()) {
                                Toast.makeText(context, "Vui lòng thêm ít nhất 1 giai đoạn ngủ", Toast.LENGTH_SHORT).show()
                            } else {
                                if (sessionToEdit == null) {
                                    sleepViewModel.saveSleepWithStages(
                                        date = selectedDate,
                                        startHour = startHour,
                                        startMinute = startMinute,
                                        stages = addedStages
                                    )
                                } else {
                                    sleepViewModel.editSleepSessionWithStages(
                                        oldRecord = sessionToEdit,
                                        newDate = selectedDate,
                                        startHour = startHour,
                                        startMinute = startMinute,
                                        stages = addedStages
                                    )
                                }
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                    ) {
                        Text(if(sessionToEdit != null) "Cập nhật" else "Lưu lại", color = Color.White)
                    }
                }
            }
        }
    }
}

// Helper Row
@Composable
fun SleepInputRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF1F5F9))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Text(text = value, fontSize = 16.sp, color = Color.Black, fontWeight = FontWeight.Medium)
        }
    }
}