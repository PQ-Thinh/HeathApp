package com.example.healthapp

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff



@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onSignUpClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    onLogin: (String, String) -> Unit
)
{
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Controlled visibility state for entrance animations
    var isContentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isContentVisible = true
    }

    // Infinite animation for background floating effect
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    val floatAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(30000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // Deep Modern Navy
    ) {
        // 1. Dynamic Background Orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(0.25f), Color.Transparent),
                    center = Offset(floatAnim % size.width, (floatAnim * 0.5f) % size.height)
                ),
                radius = 600f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD946EF).copy(0.2f), Color.Transparent),
                    center = Offset(
                        size.width - (floatAnim % size.width),
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

            // 2. Animated Logo
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800)) + scaleIn(initialScale = 0.8f)
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
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
                    Canvas(modifier = Modifier.size(45.dp)) {
                        drawCircle(
                            color = Color.White,
                            radius = size.minDimension / 3.5f,
                            style = Stroke(width = 10f)
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(size.width / 1.8f, size.height / 1.8f),
                            size = size / 4f
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Welcome Text
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800, 150)) + slideInVertically { -20 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Chào mừng trở lại",
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
                        text = "Đăng nhập tài khoản",
                        color = Color.White.copy(0.7f),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 4. Input Card (Improved Visibility)
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800, 300)) + slideInVertically { 30 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(0.08f)) // Slightly more opaque glass
                        .border(
                            1.dp,
                            Color.White.copy(0.1f),
                            RoundedCornerShape(32.dp)
                        ) // Subtle border for definition
                        .padding(24.dp)
                ) {
                    // Email Field
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Địa Chỉ Email", color = Color.White.copy(0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Email, null, tint = Color(0xFF6366F1)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            focusedLabelColor = Color(0xFF6366F1),
                            unfocusedLabelColor = Color.White.copy(0.6f)
                        )
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Password Field
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật Khẩu", color = Color.White.copy(0.6f)) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF6366F1)) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = Color.White.copy(0.6f)
                                )
                            }
                        },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color.White.copy(0.2f),
                            focusedLabelColor = Color(0xFF6366F1),
                            unfocusedLabelColor = Color.White.copy(0.6f)
                        )
                    )

                    TextButton(
                        onClick = onForgotPasswordClick,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Quên Mật Khẩu?", color = Color(0xFFD946EF), fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 5. Sign In Button
                    Button(
                        onClick = {
                            // Call the MainActivity login callback
                            onLogin(email.trim(), password)
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
                                "Đăng Nhập",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 6. Footer
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(800, 500))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chưa có tài khoản?", color = Color.White.copy(0.7f))
                    TextButton(onClick = onSignUpClick) {
                        Text("Đăng Ký", color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

        }


        }
    }



@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    LoginScreen(
        onSignUpClick = {},
        onForgotPasswordClick = {},
        onLogin = { email, password -> /* do nothing for preview */ }
    )
}