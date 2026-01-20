package com.example.healthapp

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.tooling.preview.Preview



@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onLogoutClick: () -> Unit = {}
) {
    val isPreview = LocalInspectionMode.current
    var isVisible by remember { mutableStateOf(isPreview) }

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
            .background(Color(0xFF0F172A))
    ) {
        // 1. Consistent Background Orbs
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(0.15f), Color.Transparent),
                    center = Offset(floatAnim % size.width, size.height * 0.8f)
                ),
                radius = 700f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD946EF).copy(0.1f), Color.Transparent),
                    center = Offset(size.width - (floatAnim % size.width), size.height * 0.2f)
                ),
                radius = 600f
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // 2. Profile Header
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
                                            Color(0xFF6366F1),
                                            Color(0xFFD946EF)
                                        )
                                    )
                                )
                                .border(4.dp, Color.White.copy(0.1f), CircleShape)
                                .shadow(20.dp, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(60.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Alex Johnson",
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                shadow = Shadow(Color.Black.copy(0.5f), blurRadius = 8f)
                            )
                        )
                        Text(
                            text = "alex.johnson@example.com",
                            color = Color.White.copy(0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 3. Settings Items
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(0.06f))
                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(32.dp))
                        .padding(vertical = 8.dp)
                ) {
                    ProfileMenuItem(Icons.Default.PersonOutline, "Personal Info", isVisible, 1)
                    ProfileMenuItem(Icons.Default.History, "Health History", isVisible, 2)
                    ProfileMenuItem(Icons.Default.Shield, "Privacy & Security", isVisible, 3)
                    ProfileMenuItem(Icons.Default.HelpOutline, "Support", isVisible, 4)
                }
            }

            // 4. Logout Button
            item {
                Spacer(modifier = Modifier.height(32.dp))
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(800, 500))
                ) {
                    Button(
                        onClick = onLogoutClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(0.5f))
                    ) {
                        Text("Log Out", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileMenuItem(
    icon: ImageVector,
    title: String,
    visible: Boolean,
    index: Int
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(600, index * 100)) + slideInHorizontally { it / 10 }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color(0xFF6366F1), modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, color = Color.White, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = Color.White.copy(0.3f))
        }
    }
}

@Preview
@Composable
fun ProfileScreenPreview() {
    ProfileScreen()
}
