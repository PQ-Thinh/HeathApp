package com.example.healthapp.feature.home

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.R


@Composable
fun HeightPickerScreen(
    modifier: Modifier = Modifier,
    minCm: Int = 120,
    maxCm: Int = 220,
    initialCm: Int = 170,
    onStartClick: (Int) -> Unit
) {
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
    var currentHeightCm by remember { mutableIntStateOf(initialCm.coerceIn(minCm, maxCm)) }

    // Map cm -> image height dp (tuyến tính)
    fun cmToImageHeightDp(cm: Int): Dp {
        val minDp = 160.dp
        val maxDp = 580.dp
        val ratio = (cm - minCm).toFloat() / (maxCm - minCm).toFloat()
        return minDp + (maxDp - minDp) * ratio
    }


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
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Left: Person + label
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {


                Text(
                    text = "Chiều Cao Của Bạn?",
                    style = TextStyle(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        shadow = Shadow(
                            color = Color.Black.copy(0.5f),
                            blurRadius = 10f,
                            offset = Offset(2f, 2f)
                        )
                    )
                )
                Spacer(modifier = Modifier.height(20.dp))
                // Label trên đầu
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(0.12f))
                        .border(1.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${currentHeightCm} cm",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(cmToImageHeightDp(currentHeightCm))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(0.06f))
                        .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.man),
                        contentDescription = null,
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .fillMaxHeight()
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onStartClick(currentHeightCm) },
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

            Spacer(modifier = Modifier.width(12.dp))

            // Right: Y-axis ruler (thước cm)
            RulerYAxis(
                minCm = minCm,
                maxCm = maxCm,
                current = currentHeightCm,
                onHeightChange = { currentHeightCm = it }
            )
        }
    }
}

@SuppressLint("FrequentlyChangingValue")
@Composable
private fun RulerYAxis(
    minCm: Int,
    maxCm: Int,
    current: Int,
    onHeightChange: (Int) -> Unit,
) {
    val unitHeight = 16.dp // 1cm
    //val unitPx = with(LocalDensity.current) { unitHeight.toPx() }
    val totalItems = maxCm - minCm + 1
    val initialIndex = (current - minCm).coerceIn(0, totalItems - 1)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(listState)

    // Cập nhật giá trị theo item gần tâm viewport
    LaunchedEffect(listState.layoutInfo) {
        val layout = listState.layoutInfo
        if (layout.visibleItemsInfo.isNotEmpty()) {
            val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
            val closest = layout.visibleItemsInfo.minByOrNull { item ->
                val itemCenter = item.offset + item.size / 2f
                kotlin.math.abs(itemCenter - viewportCenter)
            }
            closest?.let { item ->
                val cm = (minCm + item.index).coerceIn(minCm, maxCm)
                if (cm != current) onHeightChange(cm)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(96.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.06f))
            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp)
    ) {
        // Vạch chỉ báo trung tâm
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(6.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF22C55E))
        )

        // Tính padding để vạch nằm giữa viewport
        val sidePadding = 200.dp - 12.dp // nửa chiều cao Box trừ padding

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(vertical = sidePadding),
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            items(totalItems) { i ->
                val cm = minCm + i
                val isMajor = cm % 10 == 0//10cm
                val tickWidth = if (isMajor) 56.dp else 36.dp
                val tickColor = if (isMajor) Color.White else Color.White.copy(0.7f)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(unitHeight)
                ) {
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(tickWidth)
                            .background(tickColor)
                    )
                    if (isMajor) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$cm cm",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun HeightPickerScreenPreview() {
    HeightPickerScreen(){}
}