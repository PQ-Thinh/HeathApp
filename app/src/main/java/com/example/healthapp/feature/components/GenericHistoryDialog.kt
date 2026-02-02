package com.example.healthapp.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dialog hiển thị lịch sử chung cho HeartRate, Sleep, Steps.
 * @param T: Kiểu dữ liệu của item (VD: HeartRateRecordEntity)
 * @param itemContent: Hàm Composable để quy định cách hiển thị nội dung từng dòng
 */
@Composable
fun <T> GenericHistoryDialog(
    title: String,
    dataList: List<T>,
    onDismiss: () -> Unit,
    onDelete: (T) -> Unit,
    // Content color và Background color sẽ tự động xử lý bên trong
    isDarkTheme: Boolean,
    itemContent: @Composable (item: T, textColor: Color) -> Unit
) {
    // Theme Colors
    val backgroundColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
    val contentColor = if (isDarkTheme) Color.White else Color(0xFF1E293B)
    val itemBackgroundColor = if (isDarkTheme) Color(0xFF334155) else Color(0xFFF1F5F9)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp) // Giới hạn chiều cao, scroll nếu dài
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 1. Header
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )

                // 2. Danh sách
                if (dataList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chưa có lịch sử", color = contentColor.copy(0.5f))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        items(dataList) { item ->
                            HistoryItemRow(
                                item = item,
                                backgroundColor = itemBackgroundColor,
                                contentColor = contentColor,
                                isDarkTheme = isDarkTheme,
                                onDelete = { onDelete(item) },
                                content = { itemContent(item, contentColor) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Nút Đóng
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
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Nội dung item (Ngày giờ, chỉ số...)
        Box(modifier = Modifier.weight(1f)) {
            content()
        }

        // Menu 3 chấm
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

            // Dropdown Menu
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(
                    if (isDarkTheme) Color(0xFF1E293B) else Color.White
                )
            ) {
                DropdownMenuItem(
                    text = { Text("Xóa", color = Color(0xFFEF4444)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFEF4444)
                        )
                    }
                )
            }
        }
    }
}