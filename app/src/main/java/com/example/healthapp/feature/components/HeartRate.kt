@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.healthapp.feature.components

import android.Manifest
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoCameraFront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.delay
import net.kibotu.heartrateometer.HeartRateOmeter
import net.kibotu.kalmanrx.jama.Matrix
import net.kibotu.kalmanrx.jkalman.JKalman

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HeartRateScreen(
    onBackClick: (Int) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    var currentBpm by remember { mutableIntStateOf(0) }
    var isFingerDetected by remember { mutableStateOf(false) }

    // State hiển thị loading
    var isLoading by remember { mutableStateOf(false) }

    // Logic Kalman Filter
    val kalman = remember { JKalman(2, 1) }
    val m = remember { Matrix(1, 1) }

    LaunchedEffect(Unit) {
        val tr = arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))
        kalman.transition_matrix = Matrix(tr)
        kalman.error_cov_post = kalman.error_cov_post.identity()
    }

    // Quản lý Disposable
    val subscription = remember { CompositeDisposable() }
    var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }

    // Hàm start đo đạc tách biệt
    fun startMeasurement(view: SurfaceView) {
        subscription.clear()

        try {
            val bpmUpdates = HeartRateOmeter()
                .withAverageAfterSeconds(3)
                .setFingerDetectionListener { detected ->
                    isFingerDetected = detected
                }
                .bpmUpdates(view)
                .subscribe({ bpmData ->
                    // Khi nhận được dữ liệu đầu tiên (kể cả 0), tắt loading
                    if (isLoading) isLoading = false

                    if (bpmData.value == 0) return@subscribe

                    m.set(0, 0, bpmData.value.toDouble())
                    kalman.Predict()
                    val c = kalman.Correct(m)
                    val filteredBpm = c.get(0, 0).toInt()

                    currentBpm = filteredBpm
                }, { error ->
                    Log.e("HeartRateScreen", "Lỗi đo nhịp tim", error)
                    // Nếu lỗi cũng tắt loading để user biết
                    isLoading = false
                })

            subscription.add(bpmUpdates)
            Log.d("HeartRateScreen", "Đã bắt đầu đo")
        } catch (e: Exception) {
            Log.e("HeartRateScreen", "Không thể khởi động camera", e)
            isLoading = false
        }
    }

    fun stopMeasurement() {
        subscription.clear()
        isFingerDetected = false
    }

    // Xử lý khi vừa cấp quyền xong:
    // Khi quyền chuyển từ Denied -> Granted, ta bật loading và delay 1 chút để Camera khởi động
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        if (cameraPermissionState.status.isGranted) {
            isLoading = true
            // Delay 1.5 giây để đảm bảo surface view đã sẵn sàng và quyền đã ăn sâu vào hệ thống
            delay(1500)
            surfaceViewRef?.let {
                startMeasurement(it)
            }
            // Sau khi start xong, đợi thêm chút xíu cho chắc chắn rồi tắt load
            delay(500)
            isLoading = false
        }
    }

    // Lifecycle Observer
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (cameraPermissionState.status.isGranted) {
                        surfaceViewRef?.let { startMeasurement(it) }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> stopMeasurement()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopMeasurement()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Đo Nhịp Tim",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- KHUNG CAMERA TRÒN ---
        Box(
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
                .align(Alignment.CenterHorizontally),
            contentAlignment = Alignment.Center
        ) {
            if (cameraPermissionState.status.isGranted) {
                // Key này quan trọng: Nó buộc AndroidView vẽ lại nếu quyền thay đổi
                // Giúp "refresh" lại SurfaceView
                key(cameraPermissionState.status) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                keepScreenOn = true

                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        surfaceViewRef = this@apply
                                        // Chỉ start nếu không đang trong trạng thái chờ delay của LaunchedEffect bên trên
                                        // Tuy nhiên gọi thừa cũng không sao vì hàm start có clear()
                                        startMeasurement(this@apply)
                                    }
                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        stopMeasurement()
                                        surfaceViewRef = null
                                    }
                                })
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // --- LỚP HIỆU ỨNG LOADING ---
                // Hiển thị đè lên Camera khi đang load
                this@Column.AnimatedVisibility(
                    visible = isLoading,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.8f)), // Nền tối mờ che camera đen
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E676),
                            strokeWidth = 4.dp
                        )
                    }
                }

            } else {
                IconButton(onClick = { cameraPermissionState.launchPermissionRequest() }) {
                    Icon(
                        Icons.Default.VideoCameraFront,
                        contentDescription = "Cấp quyền",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = when {
                !cameraPermissionState.status.isGranted -> "Cần cấp quyền Camera"
                isLoading -> "Đang khởi động Camera..."
                isFingerDetected -> "Giữ nguyên ngón tay..."
                else -> "Đặt ngón tay che kín Camera & Đèn Flash"
            },
            color = when {
                isLoading -> Color.Yellow
                isFingerDetected -> Color(0xFF00E676)
                else -> Color(0xFFFF5252)
            },
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "$currentBpm",
            color = Color.White,
            fontSize = 80.sp,
            fontWeight = FontWeight.Black
        )
        Text("BPM", color = Color.Gray, fontSize = 20.sp)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { onBackClick(currentBpm) },
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("Lưu và Quay lại", color = Color.White)
        }
    }
}