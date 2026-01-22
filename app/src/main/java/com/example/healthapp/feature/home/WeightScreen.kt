package com.example.healthapp.feature.home


import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeightScreen(
    modifier: Modifier = Modifier,
    onStartClick: (Float) -> Unit,
    minKg: Float = 40f,
    maxKg: Float = 150f,
    initialKg: Float = 60f
) {
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
    var currentWeightKg by remember { mutableFloatStateOf(initialKg.coerceIn(minKg, maxKg)) }
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
        Box(
            modifier = modifier
                .fillMaxSize()

        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Cân Nặng Của Bạn?",
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

                Spacer(modifier = Modifier.height(24.dp))

                // Giá trị hiện tại
                Text(
                    text = "${String.format("%.1f", currentWeightKg)} kg",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Thanh cuộn chọn cân nặng
                RulerWeightPicker(
                    minKg = minKg,
                    maxKg = maxKg,
                    current = currentWeightKg,
                    onWeightChange = { currentWeightKg = it }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Nút hành động
                Button(
                    onClick = { onStartClick(currentWeightKg) },
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
    }
}

@Composable
private fun RulerWeightPicker(
    minKg: Float,
    maxKg: Float,
    current: Float,
    onWeightChange: (Float) -> Unit,
) {
    val unitWidth = 32.dp
    val unitPx = with(LocalDensity.current) { unitWidth.toPx() }
    val totalItems = ((maxKg - minKg) * 10).toInt() + 1
    val initialIndex = ((current - minKg) * 10).toInt().coerceIn(0, totalItems - 1)

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
                val idx = item.index // mỗi index = 0.1 kg
                val newKg = (minKg + idx / 10f).coerceIn(minKg, maxKg)
                if (newKg != current) onWeightChange(newKg)
            }
        }
    }

    Box(
        modifier = Modifier
            .height(200.dp)
            .width(400.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(0.06f))
            .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp)
    ) {
        // padding để tâm viewport trùng với vạch chỉ báo
        val sidePadding = 200.dp - 12.dp // nửa width Box trừ padding
        LazyRow(
            state = listState,
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(horizontal = sidePadding),
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(totalItems) { i ->
                val kg = minKg + i / 10f
                val isMajor = kg % 1f == 0f
                val tickHeight = if (isMajor) 56.dp else 36.dp
                val tickColor = if (isMajor) Color.White else Color.White.copy(0.5f)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(unitWidth)
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(tickHeight)
                            .background(tickColor)
                    )
                    if (isMajor) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = String.format("%.1f", kg),
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Vạch chỉ báo trung tâm
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(6.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF22C55E))
        )
    }
}


@Preview
@Composable
fun WeightScreenPreview() {
    WeightScreen(onStartClick = {})
}