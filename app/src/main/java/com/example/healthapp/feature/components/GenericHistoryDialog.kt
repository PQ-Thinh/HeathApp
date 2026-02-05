package com.example.healthapp.feature.components

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Dialog hiển thị lịch sử chung với bộ lọc theo ngày.
 * @param dateExtractor: Hàm lambda giúp Dialog lấy timestamp (Long) từ item T để so sánh ngày.
 */
@Composable
fun <T> GenericHistoryDialog(
    title: String,
    dataList: List<T>,
    onDismiss: () -> Unit,
    onDelete: (T) -> Unit,
   // onEdit: (T) -> Unit,
    isDarkTheme: Boolean,
    onItemClick: (T) -> Unit,
    dateExtractor: (T) -> Long,
    itemContent: @Composable (item: T, textColor: Color) -> Unit
) {
    val context = LocalContext.current
    val backgroundColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
    val contentColor = if (isDarkTheme) Color.White else Color(0xFF1E293B)
    val itemBgColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9)

    // State quản lý ngày đang chọn (Mặc định là hôm nay)
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    // DatePicker Dialog hệ thống
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
        },
        selectedDate.year,
        selectedDate.monthValue - 1,
        selectedDate.dayOfMonth
    )

    // Lọc danh sách dữ liệu theo ngày đã chọn
    val filteredList = remember(dataList, selectedDate) {
        dataList.filter { item ->
            val itemTime = Instant.ofEpochMilli(dateExtractor(item))
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            itemTime.isEqual(selectedDate)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .padding(vertical = 24.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // 1. Tiêu đề
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Bộ chọn ngày (Giao diện lịch)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(itemBgColor, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Nút lùi ngày
                    IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                        Icon(Icons.Default.ChevronLeft, null, tint = contentColor)
                    }

                    // Hiển thị ngày (Bấm vào để mở lịch)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { datePickerDialog.show() }
                    ) {
                        Icon(Icons.Default.DateRange, null, tint = contentColor.copy(0.7f), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = contentColor
                        )
                    }

                    // Nút tiến ngày
                    IconButton(
                        onClick = { selectedDate = selectedDate.plusDays(1) },
                        enabled = selectedDate.isBefore(LocalDate.now()) // Không cho chọn tương lai
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            null,
                            tint = if(selectedDate.isBefore(LocalDate.now())) contentColor else contentColor.copy(0.3f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Danh sách dữ liệu (Đã lọc)
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Không có dữ liệu",
                                color = contentColor.copy(0.5f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "ngày ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM"))}",
                                color = contentColor.copy(0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(filteredList) { item ->
                            HistoryItemRow(
                                item = item,
                                backgroundColor = itemBgColor,
                                contentColor = contentColor,
                                isDarkTheme = isDarkTheme,
                                onDelete = { onDelete(item) },
                               // onEdit = {onEdit(item)},
                                content = { itemContent(item, contentColor) },
                                modifier = Modifier.clickable { onItemClick(item) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkTheme) Color(0xFF6366F1) else Color(0xFF0F172A),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Đóng", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun <T> HistoryItemRow(
    item: T,
    backgroundColor: Color,
    contentColor: Color,
    isDarkTheme: Boolean,
    onDelete: () -> Unit,
    //onEdit: () -> Unit,
    content: @Composable () -> Unit,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.weight(1f)) {
            content()
        }

        Box {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = contentColor.copy(0.6f)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(if (isDarkTheme) Color(0xFF1E293B) else Color.White)
            ) {
                DropdownMenuItem(
                    text = { Text("Xóa", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444))
                    }
                )
            }
        }
    }
}