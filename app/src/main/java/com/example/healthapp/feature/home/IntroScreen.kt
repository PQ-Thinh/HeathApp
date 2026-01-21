package com.example.healthapp.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.R

@Composable
fun IntroScreen(
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit = {},
) {
    var isContentVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isContentVisible = true
    }

    AnimatedVisibility(
        visible = isContentVisible,
        enter = fadeIn(tween(800, 300)) + slideInVertically { 30 }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Logo với glow
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(70.dp))
                    .background(Color.White.copy(alpha = 0.8f))
                    .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(70.dp))
                    .shadow(12.dp, RoundedCornerShape(70.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.health),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Title + tagline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Chào mừng đến với",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Health App",
                    color = Color.Green,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Theo dõi sức khỏe của bạn mọi lúc, mọi nơi!",
                    color = Color.Gray,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Button nổi bật
            Button(
                onClick = onStartClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .shadow(8.dp, RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD946EF))
            ) {
                Text(
                    text = "Bắt đầu ngay",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}



@Preview
@Composable
fun IntroScreenPreview() {
    IntroScreen()
}