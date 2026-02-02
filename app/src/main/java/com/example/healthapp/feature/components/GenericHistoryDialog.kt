package com.example.healthapp.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dialog hiển thị danh sách lịch sử chung cho mọi loại dữ liệu.
 * @param T Kiểu dữ liệu của item (HeartRateRecordEntity, SleepSessionEntity, v.v...)
 */
@Composable
fun <T> GenericHistoryDialog(
    title: String,
    dataList: List<T>,
    onDismiss: () -> Unit,
    onDelete: (T) -> Unit,
    onEdit: (T) -> Unit,
    // Hàm render nội dung của từng item.
    // contentColor được truyền vào để text tự chỉnh màu theo theme popup
    itemContent: @Composable (item: T, contentColor: Color) -> Unit,
    isDarkTheme: Boolean
) {
    val backgroundColor = if (isDarkTheme) Color(0xFF1E293B) else Color.White
    val contentColor = if (isDarkTheme) Color.White else Color.Black

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp) // Giới hạn chiều cao
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (dataList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chưa có dữ liệu", color = contentColor.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false) // Cho phép co giãn nhưng không chiếm hết màn hình nếu ít item
                    ) {
                        items(dataList) { item ->
                            GenericHistoryItem(
                                item = item,
                                isDarkTheme = isDarkTheme,
                                contentColor = contentColor,
                                onDelete = { onDelete(item) },
                                onEdit = { onEdit(item) },
                                content = { itemContent(item, contentColor) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Button Close
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                ) {
                    Text("Đóng", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun <T> GenericHistoryItem(
    item: T,
    isDarkTheme: Boolean,
    contentColor: Color,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Màu nền item nhạt hơn nền dialog một chút
    val itemBgColor = if (isDarkTheme) Color.White.copy(0.05f) else Color(0xFFF1F5F9)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(itemBgColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Nội dung chính (Title, Time...) do màn hình cha truyền vào
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

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(if (isDarkTheme) Color(0xFF334155) else Color.White)
            ) {
                DropdownMenuItem(
                    text = { Text("Sửa", color = contentColor) },
                    onClick = {
                        expanded = false
                        onEdit()
                    },
                    leadingIcon = { Icon(Icons.Default.Edit, null, tint = contentColor) }
                )
                DropdownMenuItem(
                    text = { Text("Xóa", color = Color(0xFFEF4444)) },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444)) }
                )
            }
        }
    }
}