package com.example.healthapp.feature.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.model.entity.StepRecordEntity
import com.example.healthapp.core.viewmodel.StepViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun StepHistoryDetailDialog(
    record: StepRecordEntity,
    onDismiss: () -> Unit,
    onDelete: (StepRecordEntity) -> Unit,
    stepViewModel: StepViewModel = hiltViewModel()
) {

    val distanceKm = (record.count * 0.7) / 1000.0

    // Format thời gian
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val startStr = dateFormat.format(Date(record.startTime))
    val endStr = dateFormat.format(Date(record.endTime))

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
                    Text(
                        text = "Chi tiết hoạt động",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Số bước (To, rõ)
                Text(
                    text = "${record.count}",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF4CAF50) // Màu xanh lá
                )
                Text(text = "Bước chân", fontSize = 16.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(24.dp))

                // Thông tin chi tiết (Grid hoặc Row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InfoItem(label = "Calories", value = "${stepViewModel.calculateCalories(record.count.toLong())} kcal")
                    InfoItem(label = "Quãng đường", value = String.format("%.2f km", distanceKm))
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Metadata (Nguồn & Thời gian)
                DetailRow(label = "Bắt đầu:", value = startStr)
                DetailRow(label = "Kết thúc:", value = endStr)

                // --- PHẦN METADATA QUAN TRỌNG ---
                DetailRow(
                    label = "Nguồn dữ liệu:",
                    value = formatSourceName(record.source),
                    isHighlight = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Nút Xóa
                Button(
                    onClick = { onDelete(record); onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Xóa bản ghi này", color = Color.Red)
                }
            }
        }
    }
}

@Composable
fun InfoItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(text = label, fontSize = 12.sp, color = Color.Gray)
    }
}

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
            color = if (isHighlight) Color(0xFF1976D2) else Color.Black // Highlight màu xanh dương
        )
    }
}

// Hàm làm đẹp tên nguồn
fun formatSourceName(packageName: String): String {
    return when {
        packageName.contains("com.google.android.apps.healthdata") -> "Health Connect Tool"
        packageName.contains("androidx.health.connect.client.devtool") -> "Health Connect Tool"
        packageName.contains("fitness") -> "Google Fit"
        packageName.contains("example.healthapp") -> "Nhập thủ công"
        else -> packageName // Hiển thị nguyên tên gói nếu lạ
    }
}