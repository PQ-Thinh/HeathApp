package com.example.healthapp.feature.detail.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.model.entity.SleepSessionEntity
import com.example.healthapp.core.viewmodel.SleepViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SleepHistoryDetailDialog(
    session: SleepSessionEntity,
    onDismiss: () -> Unit,
    onDelete: (SleepSessionEntity) -> Unit,
    onEdit: (SleepSessionEntity) -> Unit,
    sleepViewModel: SleepViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isMyData = session.source == context.packageName

    // Tính toán thời lượng tổng
    val durationMillis = session.endTime - session.startTime
    val durationMinutes = durationMillis / 60000
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60

    val evaluation = sleepViewModel.evaluateSessionQuality(session)

    val dateFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale("vi", "VN"))
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- Header ---
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

                Spacer(modifier = Modifier.height(8.dp))
                Text(dateFormat.format(Date(session.startTime)), color = Color.Gray, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(24.dp))

                // --- Tổng thời gian ---
                Text(
                    text = "${hours}h ${minutes}m",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF6366F1),
                    lineHeight = 56.sp
                )
                Text(text = evaluation.text, color = Color(evaluation.colorHex))

                Spacer(modifier = Modifier.height(24.dp))

                // --- PHẦN MỚI: BIỂU ĐỒ GIAI ĐOẠN (Chỉ hiện nếu có dữ liệu) ---
                if (session.hasDetailedStages()) {
                    SleepStagesChart(session)
                    Spacer(modifier = Modifier.height(24.dp))

                    // Grid thông số chi tiết
                    Row (horizontalArrangement = Arrangement.SpaceBetween) {
                       Column( verticalArrangement = Arrangement.Center) {
                            StageStatItem("Ngủ sâu", session.deepSleepDuration, Color(0xFF4F46E5))
                            StageStatItem("REM", session.remSleepDuration, Color(0xFF8B5CF6))
                        }
                        Spacer(Modifier.width(24.dp))
                        Column(verticalArrangement = Arrangement.Center) {
                            StageStatItem("Ngủ nông", session.lightSleepDuration, Color(0xFF60A5FA))
                            StageStatItem("Đã thức", session.awakeDuration, Color(0xFFF59E0B))
                        }
                    }


                    Spacer(modifier = Modifier.height(24.dp))
                }

                HorizontalDivider(color = Color.LightGray.copy(0.3f))
                Spacer(modifier = Modifier.height(16.dp))

                // --- Thông tin thời gian ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TimeInfoItem("Đi ngủ", timeFormat.format(Date(session.startTime)))
                    TimeInfoItem("Thức dậy", timeFormat.format(Date(session.endTime)))
                }

                Spacer(modifier = Modifier.height(24.dp))

                // --- Buttons ---
                if (isMyData) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { onEdit(session) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEFF6FF), contentColor = Color(0xFF3B82F6))
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sửa")
                        }
                        Button(
                            onClick = { onDelete(session); onDismiss() },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEF2F2), contentColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Xóa")
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nguồn: ${session.source.ifEmpty { "Health Connect" }}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

// --- Các Composable phụ trợ ---

@Composable
fun SleepStagesChart(session: SleepSessionEntity) {
    val total = (session.deepSleepDuration + session.remSleepDuration + session.lightSleepDuration + session.awakeDuration).toFloat()
    if (total == 0f) return

    Column(modifier = Modifier.fillMaxWidth()) {
        // Thanh Bar Chart
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFE5E7EB))
        ) {
            if (session.deepSleepDuration > 0) Box(Modifier.weight(session.deepSleepDuration.toFloat()).fillMaxHeight().background(Color(0xFF4F46E5)))
            if (session.remSleepDuration > 0) Box(Modifier.weight(session.remSleepDuration.toFloat()).fillMaxHeight().background(Color(0xFF8B5CF6)))
            if (session.lightSleepDuration > 0) Box(Modifier.weight(session.lightSleepDuration.toFloat()).fillMaxHeight().background(Color(0xFF60A5FA)))
            if (session.awakeDuration > 0) Box(Modifier.weight(session.awakeDuration.toFloat()).fillMaxHeight().background(Color(0xFFF59E0B)))
        }
    }
}

@Composable
fun StageStatItem(
    label: String, minutes: Long, color: Color,
    sleepViewModel: SleepViewModel = hiltViewModel()
) {

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label + " :", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.width(4.dp))
        Text(
            text = sleepViewModel.formatMinToHr(minutes),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black
        )
    }


}

@Composable
fun TimeInfoItem(label: String, time: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(time, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}