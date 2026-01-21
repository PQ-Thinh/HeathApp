package com.example.healthapp.feature.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview



@Composable
fun ForgotPasswordScreen(
    modifier: Modifier = Modifier,
    onBackToLoginClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }

    val isPreview = LocalInspectionMode.current
    var isContentVisible by remember { mutableStateOf(isPreview) }

    LaunchedEffect(Unit) {
        if (!isPreview) {
            isContentVisible = true
        }
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
            .background(Color(0xFF0F172A))
    ) {

        // 1. Dynamic Background Orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF6366F1).copy(0.2f),
                        Color.Transparent
                    ),
                    center = Offset(
                        size.width - (floatAnim % size.width),
                        floatAnim % size.height
                    )
                ),
                radius = 600f
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFD946EF).copy(0.15f),
                        Color.Transparent
                    ),
                    center = Offset(
                        floatAnim % size.width,
                        size.height - (floatAnim % size.height)
                    )
                ),
                radius = 800f
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

            // 2. Animated Procedural Logo
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.7f)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(20.dp, RoundedCornerShape(24.dp))
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    Color(0xFF6366F1),
                                    Color(0xFFD946EF)
                                )
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(40.dp)) {
                        drawCircle(
                            color = Color.White,
                            radius = size.minDimension / 3.5f,
                            style = Stroke(width = 10f)
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(
                                size.width / 1.8f,
                                size.height / 1.8f
                            ),
                            size = size / 4f
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Header Text
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800, 100)) + slideInVertically { -20 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Quên Mật Khẩu",
                        style = TextStyle(
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            shadow = Shadow(
                                color = Color.Black.copy(0.5f),
                                blurRadius = 10f,
                                offset = Offset(2f, 2f)
                            )
                        )
                    )

                    Text(
                        text = "Nhập email để khôi phục mật khẩu",
                        color = Color.White.copy(0.7f),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 4. Input Card
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800, 250)) + slideInVertically { 30 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(0.08f))
                        .border(
                            1.dp,
                            Color.White.copy(0.1f),
                            RoundedCornerShape(32.dp)
                        )
                        .padding(24.dp)
                ) {

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = {
                            Text(
                                "Email Address",
                                color = Color.White.copy(0.6f)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Email,
                                null,
                                tint = Color(0xFF6366F1)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            focusedLabelColor = Color(0xFF6366F1)
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Reset Button
                    Button(
                        onClick = { },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(16.dp, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF6366F1),
                                            Color(0xFFD946EF)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Quên Mật Khẩu",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 5. Back to Login Link
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800, 400))
            ) {
                TextButton(onClick = onBackToLoginClick) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Quay Lại Đăng Nhập",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                    }
                }
            }


        }

    }
}

@Preview(showBackground = true)
@Composable
fun ForgotPasswordScreenPreview() {
    ForgotPasswordScreen(onBackToLoginClick = {})
}