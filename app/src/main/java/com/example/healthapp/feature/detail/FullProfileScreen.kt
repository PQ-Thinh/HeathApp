package com.example.healthapp.feature.detail

import android.app.DatePickerDialog
import android.widget.DatePicker
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.core.viewmodel.UserViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullProfileScreen(
    onBackClick: () -> Unit,
    userViewModel: UserViewModel,
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val userInfo by userViewModel.currentUserInfo.collectAsState(initial = null)
    val context = LocalContext.current

    // --- CẤU HÌNH MÀU SẮC THEO THEME ---
    val backgroundColor = if (isDarkTheme) Color(0xFF0F172A) else Color(0xFFF8FAFC)
    val contentColor = if (isDarkTheme) Color.White else Color(0xFF1E293B)
    val cardColor = if (isDarkTheme) Color.White.copy(0.1f) else Color.White
    val secondaryTextColor = if (isDarkTheme) Color.White.copy(0.7f) else Color.Gray
    val borderColor = if (isDarkTheme) Color.White.copy(0.2f) else Color.Gray.copy(0.3f)
    // ------------------------------------

    var isEditing by remember { mutableStateOf(false) }

    // State lưu dữ liệu
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var targetSteps by remember { mutableStateOf("") }

    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(userInfo) {
        userInfo?.let { user ->
            name = user.name ?: ""
            email = user.email ?: ""
            gender = user.gender ?: "Male"
            height = user.height.toString()
            weight = user.weight.toString()
            targetSteps = user.targetSteps.toString()

//            if (user.birthYear > 0) {
//                selectedDate.set(user.birthYear, (user.birthMonth - 1).coerceAtLeast(0), user.birthDay)
//            }
        }
    }

    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, day: Int ->
            selectedDate = Calendar.getInstance().apply { set(year, month, day) }
        },
        selectedDate.get(Calendar.YEAR),
        selectedDate.get(Calendar.MONTH),
        selectedDate.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thông Tin Cá Nhân", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            if (name.isBlank()) {
                                Toast.makeText(context, "Tên không được để trống", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }
                            userViewModel.updateFullProfile(
                                name = name,
                                gender = gender,
                                day = selectedDate.get(Calendar.DAY_OF_MONTH),
                                month = selectedDate.get(Calendar.MONTH) + 1,
                                year = selectedDate.get(Calendar.YEAR),
                                height = height.toFloatOrNull() ?: 0f,
                                weight = weight.toFloatOrNull() ?: 0f,
                                targetSteps = targetSteps.toIntOrNull() ?: 10000
                            )
                            isEditing = false
                            Toast.makeText(context, "Đã cập nhật!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Check, contentDescription = "Save", tint = Color(0xFF22C55E))
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = contentColor,
                    navigationIconContentColor = contentColor,
                    actionIconContentColor = contentColor
                )
            )
        },
        containerColor = backgroundColor
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(if (isDarkTheme) Color.White.copy(0.1f) else Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = if (isDarkTheme) Color.White else Color(0xFF6366F1)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Email (Read Only)
            ProfileField(
                label = "Email",
                value = email,
                icon = Icons.Default.Email,
                enabled = false,
                isDarkTheme = isDarkTheme
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Tên
            ProfileField(
                label = "Họ và Tên",
                value = name,
                onValueChange = { name = it },
                icon = Icons.Default.Person,
                enabled = isEditing,
                isDarkTheme = isDarkTheme
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Giới tính & Ngày sinh
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = if(gender == "Male") "Nam" else "Nữ",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Giới tính") },
                            leadingIcon = { Icon(if(gender == "Male") Icons.Default.Male else Icons.Default.Female, null) },
                            trailingIcon = {
                                IconButton(onClick = { gender = if(gender == "Male") "Female" else "Male" }) {
                                    Icon(Icons.Default.SwapHoriz, null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors(isDarkTheme)
                        )
                    } else {
                        ProfileField(
                            label = "Giới tính",
                            value = if(gender == "Male") "Nam" else "Nữ",
                            icon = if(gender == "Male") Icons.Default.Male else Icons.Default.Female,
                            enabled = false,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                val dateString = "${selectedDate.get(Calendar.DAY_OF_MONTH)}/${selectedDate.get(Calendar.MONTH) + 1}/${selectedDate.get(Calendar.YEAR)}"
                Box(modifier = Modifier.weight(1f).clickable(enabled = isEditing) { datePickerDialog.show() }) {
                    OutlinedTextField(
                        value = dateString,
                        onValueChange = {},
                        label = { Text("Ngày sinh") },
                        leadingIcon = { Icon(Icons.Default.Cake, null) },
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors(isDarkTheme, forceTextColor = contentColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chiều cao & Cân nặng
            Row(modifier = Modifier.fillMaxWidth()) {
                ProfileField(
                    label = "Chiều cao (cm)",
                    value = height,
                    onValueChange = { if (it.all { char -> char.isDigit() }) height = it },
                    icon = Icons.Default.Height,
                    enabled = isEditing,
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    isDarkTheme = isDarkTheme
                )
                Spacer(modifier = Modifier.width(16.dp))
                ProfileField(
                    label = "Cân nặng (kg)",
                    value = weight,
                    onValueChange = {  weight = it },
                    icon = Icons.Default.MonitorWeight,
                    enabled = isEditing,
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                    isDarkTheme = isDarkTheme
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            ProfileField(
                label = "Mục tiêu (bước/ngày)",
                value = targetSteps,
                onValueChange = { if (it.all { char -> char.isDigit() }) targetSteps = it },
                icon = Icons.Default.DirectionsRun,
                enabled = isEditing,
                keyboardType = KeyboardType.Number,
                isDarkTheme = isDarkTheme
            )

            Spacer(modifier = Modifier.height(24.dp))
            val h = height.toFloatOrNull() ?: 0f
            val w = weight.toFloatOrNull() ?: 0f
            val bmi = if(h > 0) w / ((h/100)*(h/100)) else 0f

            Card(
                colors = CardDefaults.cardColors(containerColor = cardColor),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isDarkTheme) 0.dp else 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Chỉ số BMI hiện tại", color = secondaryTextColor, fontSize = 14.sp)
                    Text(
                        text = String.format("%.1f", bmi),
                        color = Color(0xFF22C55E),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if(bmi < 18.5) "Thiếu cân" else if(bmi < 22.9) "Bình thường" else "Thừa cân",
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit = {},
    icon: ImageVector,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.fillMaxWidth(),
        colors = fieldColors(isDarkTheme)
    )
}

// Hàm Helper để tạo màu cho TextField dựa trên theme
@Composable
fun fieldColors(isDarkTheme: Boolean, forceTextColor: Color? = null): TextFieldColors {
    val textColor = forceTextColor ?: (if (isDarkTheme) Color.White else Color.Black)
    val labelColor = if (isDarkTheme) Color.White.copy(0.5f) else Color.Gray
    val iconColor = if (isDarkTheme) Color.White.copy(0.5f) else Color.Gray
    val borderColor = if (isDarkTheme) Color.White.copy(0.2f) else Color.Gray.copy(0.3f)
    val disabledColor = if (isDarkTheme) Color.White.copy(0.8f) else Color.Black.copy(0.8f)

    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = textColor,
        unfocusedTextColor = textColor,
        disabledTextColor = disabledColor,

        cursorColor = Color(0xFF6366F1),

        focusedBorderColor = Color(0xFF6366F1),
        unfocusedBorderColor = borderColor,
        disabledBorderColor = borderColor,

        focusedLabelColor = Color(0xFF6366F1),
        unfocusedLabelColor = labelColor,
        disabledLabelColor = labelColor,

        focusedLeadingIconColor = Color(0xFF6366F1),
        unfocusedLeadingIconColor = iconColor,
        disabledLeadingIconColor = iconColor
    )
}