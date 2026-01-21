package com.example.healthapp.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Input
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeightScreen(
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit
) {

        var height by remember { mutableStateOf(0) }
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
                            text = "Hãy Cho Tôi Biết Cân Nặng Của Bạn?",
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
                            text = " $!",
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

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {onStartClick()},
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
                                    "Tiếp Tục",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }