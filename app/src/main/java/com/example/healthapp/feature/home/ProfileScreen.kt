package com.example.healthapp.feature.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.ui.theme.AestheticColors
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.core.viewmodel.UserViewModel


@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    //onLogoutClick: () -> Unit = {},
    onBackClick: () -> Unit = {},
    isDarkTheme: Boolean,
    onChangeLogin: () -> Unit = {},
    userViewModel: UserViewModel,
    onProfileDetail: ()-> Unit = {}
) {

    val user by userViewModel.currentUserInfo.collectAsState()

    val isPreview = LocalInspectionMode.current
    var isVisible by remember { mutableStateOf(isPreview) }


    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic

    LaunchedEffect(Unit) {
        if (!isPreview) isVisible = true
    }

    // Dynamic background animation
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(40000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background) // Dùng màu động
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb1.copy(0.15f), Color.Transparent),
                    center = Offset(floatAnim % size.width, size.height * 0.8f)
                ),
                radius = 700f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.gradientOrb2.copy(0.15f), Color.Transparent),
                    center = Offset(size.width - (floatAnim % size.width), size.height * 0.2f)
                ),
                radius = 600f
            )
        }
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                ProfileTopBar(onBackClick, colors)
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .statusBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Profile Header
                item {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.8f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 40.dp, bottom = 32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                colors.gradientOrb1, // Gradient động theo theme
                                                colors.gradientOrb2
                                            )
                                        )
                                    )
                                    .border(4.dp, colors.glassBorder, CircleShape)
                                    .shadow(20.dp, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color.White, // Icon trong vòng tròn luôn màu trắng
                                    modifier = Modifier.size(60.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "${user?.name ?: "It'me"}",
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary, // Chữ đậm màu theo theme
                                    shadow = if (isDarkTheme) Shadow(
                                        Color.Black.copy(0.5f),
                                        blurRadius = 8f
                                    ) else null
                                )
                            )
                            Text(
                                text = "${user?.email ?: "Unknown"}",
                                color = colors.textSecondary, // Chữ nhạt màu theo theme
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // 3. Settings Items Container (Hiệu ứng kính)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(colors.glassContainer) // Kính mờ
                            .border(
                                1.dp,
                                colors.glassBorder,
                                RoundedCornerShape(32.dp)
                            ) // Viền mỏng
                            .padding(vertical = 8.dp)
                    ) {
                        ProfileMenuItem(
                            Icons.Default.PersonOutline,
                            "Thông Tin Cá Nhân",
                            isVisible,
                            1,
                            colors,
                            onClickListener = onProfileDetail
                        )
                        ProfileMenuItem(
                            Icons.Default.History,
                            "Lịch Sử",
                            isVisible,
                            2,
                            colors
                        )
                        ProfileMenuItem(
                            Icons.Default.Shield,
                            "Bảo Mật",
                            isVisible,
                            3,
                            colors
                        )
                        ProfileMenuItem(Icons.Default.HelpOutline, "Liên Hệ", isVisible, 4, colors)
                    }
                }

                // Logout Button
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = fadeIn(tween(800, 500))
                    ) {
                        Button(
                            onClick = {
                                //onLogoutClick
                                onChangeLogin()
                                      },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                1.dp,
                                Color(0xFFEF4444).copy(0.7f)
                            ) // Màu đỏ giữ nguyên
                        ) {
                            Text("Đăng Xuất", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun ProfileTopBar(onBackClick: () -> Unit, colors: AestheticColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
        }
        Text(
            text = "Hồ sơ",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                // Giảm bóng đổ ở Light mode để trông sạch hơn
                shadow = if (colors.background == DarkAesthetic.background)
                    Shadow(Color.Black.copy(0.3f), blurRadius = 4f)
                else null
            ),
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    visible: Boolean,
    index: Int,
    colors: AestheticColors,
    onClickListener: () -> Unit = {}
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, index * 100)) + slideInHorizontally { it / 10 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clickable{ onClickListener() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon dùng màu Accent (Indigo/Blue)
            Icon(icon, null, tint = colors.accent, modifier = Modifier.size(24.dp))

            Spacer(modifier = Modifier.width(16.dp))

            // Text dùng màu chính
            Text(title, color = colors.textPrimary, modifier = Modifier.weight(1f))

            // Mũi tên dùng màu phụ
            Icon(Icons.Default.ChevronRight, null, tint = colors.textSecondary.copy(alpha = 0.5f))
        }
    }
}

//// Preview cho cả 2 chế độ
//@Preview(name = "Dark Profile")
//@Composable
//fun ProfileScreenDarkPreview() {
//    ProfileScreen(isDarkTheme = true, isLoggingIn = false)
//}
//
//@Preview(name = "Light Profile")
//@Composable
//fun ProfileScreenLightPreview() {
//    ProfileScreen(isDarkTheme = false,isLoggingIn = false)
//}