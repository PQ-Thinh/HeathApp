package com.example.healthapp.feature.detail.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.healthapp.core.model.entity.SleepSessionEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SleepHistoryDetailDialog(
    session: SleepSessionEntity,
    onDismiss: () -> Unit,
    onDelete: (SleepSessionEntity) -> Unit,
    onEdit: (SleepSessionEntity) -> Unit
) {
    val context = LocalContext.current
    // Check nguồn dữ liệu
    val isMyData = session.source == context.packageName

    // Tính toán thời lượng
    val durationMillis = session.endTime - session.startTime
    val durationMinutes = durationMillis / 60000
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60

    // Đánh giá chất lượng giấc ngủ
    val totalHours = durationMinutes / 60.0
    val (assessmentText, assessmentColor) = when {
        totalHours < 5 -> "Kém (Quá ít)" to Color(0xFFEF4444) // Đỏ
        totalHours in 5.0..6.5 -> "Khá (Cần thêm)" to Color(0xFFF59E0B) // Vàng
        totalHours in 6.5..9.0 -> "Tốt (Lý tưởng)" to Color(0xFF22C55E) // Xanh lá
        else -> "Ngủ nhiều" to Color(0xFF3B82F6) // Xanh dương
    }

    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Chi tiết giấc ngủ", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Hiển thị Giờ ngủ to rõ
                Text(
                    text = "${hours}h ${minutes}m",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF6366F1) // Màu tím
                )

                // Hiển thị Đánh giá
                Text(
                    text = assessmentText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = assessmentColor
                )

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Thông tin chi tiết
                DetailRow(label = "Ngày:", value = dateFormat.format(Date(session.startTime)))
                DetailRow(label = "Bắt đầu:", value = timeFormat.format(Date(session.startTime)))
                DetailRow(label = "Thức dậy:", value = timeFormat.format(Date(session.endTime)))

                // Nguồn dữ liệu
                val sourceText = if (isMyData) "Nhập thủ công" else session.source.ifEmpty { "Nguồn ngoài" }
                DetailRow(label = "Nguồn:", value = sourceText, isHighlight = true)

                Spacer(modifier = Modifier.height(24.dp))

                // Nút bấm (Chỉ hiện nếu là data của mình)
                if (isMyData) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onEdit(session) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Icon(Icons.Default.Edit, null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sửa")
                        }
                        Button(
                            onClick = { onDelete(session); onDismiss() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xóa", color = Color.Red)
                        }
                    }
                } else {
                    Text(
                        "* Dữ liệu nguồn ngoài chỉ có thể xem.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

// Helper Row
@Composable
fun DetailRow(label: String, value: String, isHighlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color.Gray)
        Text(
            text = value,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
            color = if (isHighlight) Color(0xFF1976D2) else Color.Black
        )
    }
}