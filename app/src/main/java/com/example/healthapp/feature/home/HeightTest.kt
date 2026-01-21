package com.example.healthapp.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
) {
    var currentHeightCm by remember { mutableStateOf(initialCm.coerceIn(minCm, maxCm)) }

    // Map cm -> image height dp (tuyến tính)
    fun cmToImageHeightDp(cm: Int): Dp {
        val minDp = 160.dp
        val maxDp = 280.dp
        val ratio = (cm - minCm).toFloat() / (maxCm - minCm).toFloat()
        return minDp + (maxDp - minDp) * ratio
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
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

            // Ảnh người co giãn theo chiều cao
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cmToImageHeightDp(currentHeightCm))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White.copy(0.06f))
                    .border(1.dp, Color.White.copy(0.12f), RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Image(
                    painter = painterResource(id = R.drawable.man),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .fillMaxHeight()
                )
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

@Composable
private fun RulerYAxis(
    minCm: Int,
    maxCm: Int,
    current: Int,
    onHeightChange: (Int) -> Unit,
) {
    // Chiều cao mỗi đơn vị cm trên thước
    val unitHeight = 16.dp

    // Tính index ban đầu để scroll tới vị trí current
    val initialIndex = (current - minCm).coerceIn(0, maxCm - minCm)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    // Đồng bộ khi cuộn: lấy item đang ở giữa để suy ra cm
    LaunchedEffect(listState.firstVisibleItemIndex, listState.isScrollInProgress) {
        val idx = listState.firstVisibleItemIndex
        val cm = (minCm + idx).coerceIn(minCm, maxCm)
        if (cm != current) onHeightChange(cm)
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
        // Vạch chỉ báo trung tâm (điểm đo)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(6.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF22C55E))
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            items((maxCm - minCm + 1)) { i ->
                val cm = minCm + i
                val isMajor = cm % 10 == 0
                val tickWidth = if (isMajor) 56.dp else 36.dp
                val tickColor = if (isMajor) Color.White else Color.White.copy(0.7f)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.height(unitHeight)
                ) {
                    // Tick line
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(tickWidth)
                            .background(tickColor)
                    )

                    // Label mỗi 10 cm
                    if (isMajor) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$cm",
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
fun HeightPickerScreenPreview(modifier: Modifier = Modifier) {
    HeightPickerScreen()
}