package com.example.healthapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val PrimaryGreen = Color(0xFF4CAF50)

// --- BẢNG MÀU SÁNG (Light Mode) ---
val LightBackground = Color(0xFFF5F5F5) // Màu nền sáng (Xám rất nhạt)
val LightSurface = Color(0xFFFFFFFF)    // Màu nền các Card/Box (Trắng)
val LightOnBackground = Color(0xFF000000) // Màu chữ trên nền sáng (Đen)

// --- BẢNG MÀU TỐI (Dark Mode) ---
val DarkBackground = Color(0xFF121212)  // Màu nền tối (Đen dịu)
val DarkSurface = Color(0xFF1E1E1E)     // Màu nền các Card/Box tối (Xám đậm)
val DarkOnBackground = Color(0xFFFFFFFF) // Màu chữ trên nền tối (Trắng)

// 1. ĐỊNH NGHĨA PALETTE MÀU RIÊNG (Để giữ vẻ đẹp Gradient/Glass)
data class AestheticColors(
    val background: Color,
    val gradientOrb1: Color, // Đốm sáng 1
    val gradientOrb2: Color, // Đốm sáng 2
    val glassContainer: Color, // Màu nền của các mục cài đặt
    val glassBorder: Color,    // Viền mỏng
    val textPrimary: Color,
    val textSecondary: Color,
    val iconTint: Color,
    val accent: Color
)

// Màu cho Dark Mode (Giữ nguyên như cũ của bạn)
val DarkAesthetic = AestheticColors(
    background = Color(0xFF0F172A),
    gradientOrb1 = Color(0xFF6366F1), // Indigo
    gradientOrb2 = Color(0xFFD946EF), // Fuchsia
    glassContainer = Color.White.copy(0.06f),
    glassBorder = Color.White.copy(0.1f),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(0.4f),
    iconTint = Color.White.copy(0.7f),
    accent = Color(0xFF6366F1)
)

// Màu cho Light Mode (Thanh thoát, sạch sẽ nhưng vẫn có gradient)
val LightAesthetic = AestheticColors(
    background = Color(0xFFF1F5F9), // Xám xanh rất nhạt
    gradientOrb1 = Color(0xFF60A5FA).copy(0.6f), // Xanh dương nhạt
    gradientOrb2 = Color(0xFFF472B6).copy(0.6f), // Hồng nhạt
    glassContainer = Color.White.copy(0.7f), // Trắng đục cao hơn để nổi trên nền sáng
    glassBorder = Color(0xFF94A3B8).copy(0.3f),
    textPrimary = Color(0xFF1E293B), // Xanh đen đậm
    textSecondary = Color(0xFF64748B), // Xám xanh
    iconTint = Color(0xFF475569),
    accent = Color(0xFF4F46E5) // Indigo đậm hơn một chút
)
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    background = DarkBackground, // Màu nền đen
    surface = DarkSurface,       // Màu box xám đậm
    onBackground = DarkOnBackground, // Chữ trắng
    onSurface = DarkOnBackground
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    background = LightBackground, // Màu nền trắng
    surface = LightSurface,
    onBackground = LightOnBackground, // Chữ đen
    onSurface = LightOnBackground

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)


@Composable
fun HealthAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    //dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    //Logic chọn bảng màu: Nếu darkTheme = true thì lấy DarkColorScheme
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Đổi màu thanh trạng thái (Status Bar) cho đồng bộ
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            // Nếu nền sáng -> icon đen, Nền tối -> icon trắng
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Cung cấp bộ màu này cho toàn bộ ứng dụng
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}