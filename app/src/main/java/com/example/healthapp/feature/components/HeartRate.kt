@file:Suppress("COMPOSE_APPLIER_CALL_MISMATCH")

package com.example.healthapp.feature.components



import android.Manifest
import android.util.Log
import android.view.SurfaceView
import android.view.ViewGroup
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

    // Logic Kalman Filter
    val kalman = remember { JKalman(2, 1) }
    val m = remember { Matrix(1, 1) }

    LaunchedEffect(Unit) {
        val tr = arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0))
        kalman.transition_matrix = Matrix(tr)
        kalman.error_cov_post = kalman.error_cov_post.identity()
    }

    // Disposable để quản lý subscription
    val subscription = remember { CompositeDisposable() }

    // Hàm clean up resource
    fun stopMeasurement() {
        subscription.clear()
    }

    // Lifecycle Observer để dừng khi pause
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                stopMeasurement()
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
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            keepScreenOn = true

                            holder.addCallback(object : android.view.SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                    // Bắt đầu đo tại đây
                                    try {
                                        val bpmUpdates = HeartRateOmeter()
                                            .withAverageAfterSeconds(3)
                                            .setFingerDetectionListener { detected ->
                                                isFingerDetected = detected
                                            }
                                            .bpmUpdates(this@apply) // Truyền SurfaceView hiện tại
                                            .subscribe({ bpmData ->
                                                if (bpmData.value == 0) return@subscribe

                                                m.set(0, 0, bpmData.value.toDouble())
                                                kalman.Predict()
                                                val c = kalman.Correct(m)
                                                val filteredBpm = c.get(0, 0).toInt()

                                                currentBpm = filteredBpm
                                                Log.d("HeartRate", "BPM: $filteredBpm")
                                            }, { it.printStackTrace() })

                                        subscription.add(bpmUpdates)
                                    } catch (e: Exception) {
                                        Log.e("HeartRateScreen", "Error starting camera", e)
                                    }
                                }

                                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {}

                                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                                    stopMeasurement()
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
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

        // ... Phần code UI bên dưới giữ nguyên
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isFingerDetected) "Giữ nguyên ngón tay..." else "Đặt ngón tay che kín Camera & Đèn Flash",
            color = if (isFingerDetected) Color(0xFF00E676) else Color(0xFFFF5252),
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
            Text("Quay lại", color = Color.White)
        }
    }
}