package com.example.healthapp.feature.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthapp.R
import com.example.healthapp.feature.components.TopBar
import com.example.healthapp.ui.theme.DarkAesthetic
import com.example.healthapp.ui.theme.LightAesthetic

@Composable
fun StepRunDetail(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit,
    isDarkTheme: Boolean,
) {
    val colors = if (isDarkTheme) DarkAesthetic else LightAesthetic

    Scaffold(
        containerColor = Color.Transparent,
        topBar ={ TopBar(
            onBackClick = onBackClick,
            colors = colors)}

    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .background(colors.background),
            contentPadding = paddingValues,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                ) {
                    Image(painter = painterResource(R.mipmap.logoapp),
                        contentDescription = null
                    , contentScale = ContentScale.FillWidth,)
                }
            }
            item {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp)
                ) {
                    Text("Đi Bộ Buổi Chiều",
                        color = colors.textPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DirectionsRun, contentDescription = null)
                        Spacer(modifier = Modifier.height(1.dp))
                        Text("Time")
                    }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.DirectionsRun, contentDescription = null)
                    Spacer(modifier = Modifier.height(1.dp))
                    Text("Số Bước Chân")
                }
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row() {
                        Icon(Icons.Default.LocationOn,contentDescription = null)
                        Spacer(modifier = Modifier.width(1.dp))
                        Text("Khoảng cách",modifier = Modifier.weight(5f))
                        Spacer(modifier = Modifier.width(20.dp))
                        Text("0Km", fontSize = 8.sp,
                            color = Color.Gray,
                            modifier = Modifier.weight(1f)
                            )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row() {
                        Icon(Icons.Default.LocationOn,contentDescription = null)
                        Spacer(modifier = Modifier.width(1.dp))
                        Text("Năng lượng Tiêu Thụ")
                        Spacer(modifier = Modifier.width(20.dp))
                        Text("0Km", fontSize = 8.sp,
                            color = Color.Gray,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row() {
                        Icon(Icons.Default.LocationOn,contentDescription = null)
                        Spacer(modifier = Modifier.width(1.dp))
                        Text("Phút vận động")
                        Spacer(modifier = Modifier.width(20.dp))
                        Text("0Km", fontSize = 8.sp,
                            color = Color.Gray,
                        )
                    }
                }
            }
        }
    }
}
@Preview
@Composable
fun StepRunDetailPreview() {
    StepRunDetail(
        onBackClick = {},
        isDarkTheme = false
    )

}