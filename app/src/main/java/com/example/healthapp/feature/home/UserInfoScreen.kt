package com.example.healthapp.feature.home

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

@Composable
fun UserInfoScreen(
    modifier: Modifier = Modifier,
    onStartClick: (String, String, Int, Int, Int) -> Unit
) {
    // State
    var name by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("Male") }

    // Ngày sinh (Mặc định chọn ngày hôm nay)
    val calendar = Calendar.getInstance()
    var selectedDate by remember { mutableStateOf(calendar) }

    // Format hiển thị ngày
    val dateString = remember(selectedDate) {
        "${selectedDate.get(Calendar.DAY_OF_MONTH)}/${selectedDate.get(Calendar.MONTH) + 1}/${selectedDate.get(Calendar.YEAR)}"
    }

    // Dialog chọn ngày
    val context = LocalContext.current
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            val newDate = Calendar.getInstance()
            newDate.set(year, month, dayOfMonth)
            selectedDate = newDate
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    // Animation
    var isContentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isContentVisible = true }

    // Background Animation
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing), RepeatMode.Reverse),
        label = "float"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
    ) {
        // 1. Dynamic Background
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(0.2f), Color.Transparent),
                    center = Offset(floatAnim % size.width, size.height * 0.2f)
                ), radius = 600f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD946EF).copy(0.15f), Color.Transparent),
                    center = Offset(size.width - (floatAnim % size.width), size.height * 0.8f)
                ), radius = 800f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header Text
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800)) + slideInVertically { -20 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Hồ Sơ Của Bạn",
                        style = TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            shadow = Shadow(Color.Black.copy(0.5f), Offset(2f, 2f), 10f)
                        )
                    )
                    Text(
                        text = "Hãy cho chúng tôi biết thêm về bạn",
                        color = Color.White.copy(0.7f),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main Card Form
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800, 200)) + scaleIn(initialScale = 0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(0.08f))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(32.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // 1. TÊN
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Họ và Tên", color = Color.White.copy(0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Person, null, tint = Color(0xFF6366F1)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            cursorColor = Color(0xFF6366F1)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 2. GIỚI TÍNH (2 ô chọn)
                    Text("Giới tính", color = Color.White.copy(0.8f), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GenderOption(
                            text = "Nam",
                            icon = Icons.Default.Male,
                            isSelected = selectedGender == "Male",
                            onClick = { selectedGender = "Male" },
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        GenderOption(
                            text = "Nữ",
                            icon = Icons.Default.Female,
                            isSelected = selectedGender == "Female",
                            onClick = { selectedGender = "Female" },
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3. NGÀY SINH
                    Text("Ngày sinh", color = Color.White.copy(0.8f), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.2f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = dateString, fontSize = 16.sp, color = Color.White)
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFFD946EF))
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // BUTTON TIẾP TỤC
                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                onStartClick(
                                    name,
                                    selectedGender,
                                    selectedDate.get(Calendar.DAY_OF_MONTH),
                                    selectedDate.get(Calendar.MONTH) + 1,
                                    selectedDate.get(Calendar.YEAR)
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF6366F1), Color(0xFFD946EF))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Hoàn Tất",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

// Composable phụ chọn giới tính
@Composable
fun GenderOption(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) Color(0xFF6366F1).copy(0.3f) else Color.Transparent
    val borderColor = if (isSelected) Color(0xFF6366F1) else Color.White.copy(0.2f)
    val contentColor = if (isSelected) Color.White else Color.White.copy(0.6f)

    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, color = contentColor, fontWeight = FontWeight.Bold)
        }
    }
}

@Preview
@Composable
fun UserInfoPreview() {
    UserInfoScreen(onStartClick = { _, _, _, _, _ -> })
}