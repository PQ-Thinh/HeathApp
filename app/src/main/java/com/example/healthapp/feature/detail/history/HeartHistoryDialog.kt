package com.example.healthapp.feature.detail.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.model.entity.HeartRateRecordEntity
import com.example.healthapp.core.viewmodel.HeartViewModel
import com.example.healthapp.feature.components.DetailRow
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HeartHistoryDetailDialog(
    record: HeartRateRecordEntity,
    onDismiss: () -> Unit,
    onDelete: (HeartRateRecordEntity) -> Unit,
    onEdit: (HeartRateRecordEntity) -> Unit,
    heartViewModel: HeartViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    // Logic check đơn giản: So sánh package name
    val isMyData = record.source == context.packageName

    val dateFormat = SimpleDateFormat("HH:mm - dd/MM/yyyy", Locale.getDefault())
    val assessment by heartViewModel.assessment.collectAsState()
    val latestHeartRate by heartViewModel.latestHeartRate.collectAsState()



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
                    Text("Chi tiết nhịp tim", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${record.bpm}",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFEF4444)
                        )
                        Text("BPM", fontSize = 16.sp, color = Color.Gray)

                    }
                    Text(
                        text = assessment,
                        fontSize = 18.sp,
                        color = if (latestHeartRate in 60..100) Color(0xFF22C55E) else Color(0xFFEAB308)
                        ,modifier = Modifier.weight(1f)
                    )                }
                // BPM To

                Spacer(modifier = Modifier.height(24.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))
                // Thông tin
                DetailRow(label = "Thời gian:", value = dateFormat.format(Date(record.time)))
                DetailRow(
                    label = "Nguồn:",
                    value = if (isMyData) "Đo thủ công" else record.source.ifEmpty { "Nguồn Ngoài" },
                    isHighlight = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Nút bấm (Chỉ hiện nếu là data của mình)
                if (isMyData) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onEdit(record) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Icon(Icons.Default.Edit, null, tint = Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sửa")
                        }
                        Button(
                            onClick = { onDelete(record); onDismiss() },
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
                        "* Dữ liệu từ nguồn ngoài chỉ có thể xem.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}