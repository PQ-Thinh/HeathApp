package com.example.healthapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.healthapp.ui.theme.HealthAppTheme
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.feature.auth.ForgotPasswordScreen
import com.example.healthapp.feature.auth.LoginScreen
import com.example.healthapp.feature.auth.SignUpScreen
import com.example.healthapp.feature.home.HealthDashboardScreen
import com.example.healthapp.feature.home.IntroScreen
import com.example.healthapp.feature.home.NotificationsScreen
import com.example.healthapp.feature.home.ProfileScreen
import com.example.healthapp.feature.home.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import com.example.healthapp.feature.components.HeartRateScreen
import com.example.healthapp.feature.detail.HeartDetailScreen
import com.example.healthapp.feature.home.HeightPickerScreen
import com.example.healthapp.feature.home.UserInfoScreen
import com.example.healthapp.feature.home.WeightScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.healthapp.core.service.StepForegroundService
import com.example.healthapp.core.viewmodel.HeartViewModel
import com.example.healthapp.core.viewmodel.SleepViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.core.viewmodel.UserViewModel
import com.example.healthapp.feature.detail.SleepDetailScreen
import com.example.healthapp.feature.detail.StepDetailScreen
import kotlin.jvm.java

@OptIn(ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val userViewModel : UserViewModel = hiltViewModel()
            val sleepViewModel : SleepViewModel = hiltViewModel()
            val heartViewModel : HeartViewModel = hiltViewModel()
            val stepViewModel : StepViewModel = hiltViewModel()
            val healthConnectManager = mainViewModel.healthConnectManager
            val healthState by mainViewModel.healthConnectState.collectAsState()
            val context = LocalContext.current // Lấy context ở đây để dùng cho Toast/Intent


            val isServiceRunning by mainViewModel.isServiceRunning.collectAsState()
            val lifecycleOwner = LocalLifecycleOwner.current // Lấy LifecycleOwner để lắng nghe sự kiện
            var showPermissionRationaleDialog by remember { mutableStateOf(false) }
            var showInstallDialog by remember { mutableStateOf(false) }
            // Launcher Xin Quyền (Popup hệ thống)
            val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
                contract = PermissionController.createRequestPermissionResultContract()
            ) { granted ->
                if (granted.containsAll(healthConnectManager.permissions)) {
                    Toast.makeText(context, "Đã cấp quyền!", Toast.LENGTH_SHORT).show()
                    mainViewModel.checkHealthConnectStatus()
                } else {
                    Toast.makeText(context, "Cần quyền để đồng bộ dữ liệu", Toast.LENGTH_SHORT).show()
                }
            }
            val userInfo by userViewModel.currentUserInfo.collectAsState()
            val activityRecognitionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // Dù user đồng ý hay từ chối, ta đều tiếp tục kiểm tra Health Connect
                // Để tránh app bị kẹt nếu user từ chối quyền đếm bước chân phần cứng
//                if (isGranted) {
//                mainViewModel.startSensorTracking()
//                }
                // BẮT ĐẦU kiểm tra Health Connect SAU KHI popup này tắt
                mainViewModel.checkHealthConnectStatus()
            }

            @Suppress("DEPRECATION")
            fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                    if (serviceClass.name == service.service.className) {
                        return true
                    }
                }
                return false
            }
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        // Mỗi khi user quay lại app (từ Setting hoặc CH Play), tự động check lại
                        mainViewModel.checkHealthConnectStatus()
                       // mainViewModel.startSensorTracking()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            LaunchedEffect(Unit) {

                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                mainViewModel.setServiceRunningStatus(
                    isServiceRunning(context, StepForegroundService::class.java)
                )

            }
            LaunchedEffect(healthState) {
                Log.d("MainActivity", "Health Connect State changed to: $healthState")
                showInstallDialog = false
                showPermissionRationaleDialog = false
                when (healthState) {
                    2, 3 -> {
                        showInstallDialog = true
                    }
                    4 -> {
                        // Chỉ khi nào healthState = 4 (Cần quyền) mới gọi launcher này
                        showPermissionRationaleDialog = true                    }
                    1 -> {
                        // Đã có đủ quyền và SDK sẵn sàng
                        Log.d("MainActivity", "Health Connect Ready!")
                        mainViewModel.syncData()
                    }
                }
            }

            if (showInstallDialog) {
                AlertDialog(
                    onDismissRequest = { showInstallDialog = false },
                    title = { Text("Cài đặt Health Connect") },
                    text = { Text("Ứng dụng cần Health Connect để đồng bộ dữ liệu sức khỏe. Bạn có muốn cài đặt ngay không?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showInstallDialog = false
                                healthConnectManager.openHealthConnectInPlayStore(context)
                            }
                        ) { Text("Cài đặt") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showInstallDialog = false }) { Text("Để sau") }
                    }
                )
            }
            if (showPermissionRationaleDialog) {
                AlertDialog(
                    onDismissRequest = { showPermissionRationaleDialog = false },
                    title = { Text("Yêu cầu quyền truy cập") },
                    text = { Text("Để theo dõi nhịp tim và bước chân, ứng dụng cần bạn cấp quyền đọc/ghi dữ liệu trong Health Connect.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showPermissionRationaleDialog = false
                            // Lúc này người dùng đã sẵn sàng, mới gọi Popup hệ thống
                            healthConnectPermissionLauncher.launch(healthConnectManager.permissions)
                        }) { Text("Cấp quyền") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showPermissionRationaleDialog = false }) { Text("Huỷ") }
                    }
                )
            }
            val isDark by userViewModel.isDarkMode.collectAsState()
            val isLoggedIn by mainViewModel.isLoggedIn.collectAsState()

            HealthAppTheme {

                val systemUiController = rememberSystemUiController()

                SideEffect {
                    systemUiController.setStatusBarColor(
                        color = (Color(0xFF0F172A)),
                        darkIcons = false
                    )
                }

                var currentScreen by remember {
                    mutableStateOf( "intro")
                }
                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn == true) {
                        if (userInfo == null) {
                        } else if (userInfo?.name.isNullOrBlank()) {
                            currentScreen = "name"
                        } else if (userInfo?.height == 0f) {
                            currentScreen = "height"
                        } else {
                            // Đã có đủ thông tin -> vào Dashboard
                            currentScreen = "dashboard"
                        }
                    } else if (isLoggedIn == false) {
                        currentScreen = "login"
                    }
                }
                BackHandler {
                    when (currentScreen) {
                        "login"->currentScreen= "intro"
                        "signup", "forgot" -> currentScreen = "login"
                        "profile", "notifications", "settings","heart_detail","sleep_detail","step_detail"-> currentScreen = "dashboard"
                        "heart_rate" -> currentScreen = "heart_detail"
                        "height" -> currentScreen = "name"
                        "weight" -> currentScreen = "height"
                        "intro" -> finish()
                        //"login" -> finish()
                    }
                }


                Scaffold { innerPadding ->
                    when (currentScreen) {
                        "intro"-> IntroScreen (
                            modifier = Modifier.padding(innerPadding),
                            onStartClick = { currentScreen = "login" }
                        )

                        "login" -> LoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            onSignUpClick = { currentScreen = "signup" },
                            onForgotPasswordClick = { currentScreen = "forgot" },


                            onLogin = { email, password ->
                                mainViewModel.loginUser(
                                    email = email,
                                    pass = password,
                                    onSuccess = {
                                        Toast.makeText(this@MainActivity, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                        currentScreen = "dashboard"
                                    },
                                    onError = { message ->
                                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                        )

                        "signup" -> SignUpScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLoginClick = { currentScreen = "login" },
                            onSignUp = { email, password ->
                                mainViewModel.registerUser(
                                    email = email,
                                    pass = password,
                                    onSuccess = {
                                        Toast.makeText(this@MainActivity, "Đăng ký thành công!", Toast.LENGTH_SHORT).show()
                                        currentScreen = "name"
                                    },
                                    onError = { message ->
                                        Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        )

                        "forgot" -> ForgotPasswordScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBackToLoginClick = { currentScreen = "login" }
                        )
                        "name" -> UserInfoScreen (
                            modifier = Modifier.padding(innerPadding),
                            onStartClick = {name,gender,day,mouth,year->
                                userViewModel.updateUserInfo(name,gender,day,mouth,year)
                                currentScreen = "height"
                            }
                        )
                        "height" -> HeightPickerScreen(
                            modifier = Modifier.padding(innerPadding),
                            onStartClick = {height->
                                userViewModel.addHeight(height)
                                currentScreen = "weight"
                            }
                        )
                        "weight" -> WeightScreen(
                            modifier = Modifier.padding(innerPadding),
                            onStartClick = {
                                weight -> userViewModel.addWeight(weight)
                                currentScreen = "dashboard"

                            }
                        )
                        "dashboard" -> HealthDashboardScreen(
                            modifier = Modifier.padding(innerPadding),
                            onProfileClick = { currentScreen = "profile" },
                            onNotificationsClick = { currentScreen = "notifications" },
                            onSettingsClick = { currentScreen = "settings" },
                            isDark,
                           onHeartDetailClick = { currentScreen = "heart_detail" },
                            onSleepDetailClick = { currentScreen = "sleep_detail" },
                            onStepDetailClick = {currentScreen = "step_detail"},
                            mainViewModel,
                            userViewModel,
                            sleepViewModel,
                            stepViewModel,
                            isServiceRunning = isServiceRunning, // State từ MainActivity
                            onToggleService = { shouldStart ->
                                if (shouldStart) {
                                    // Start Service
                                    val intent = Intent(
                                        this@MainActivity,
                                        StepForegroundService::class.java
                                    ).apply {
                                        action = StepForegroundService.ACTION_START
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(intent)
                                    } else {
                                        startService(intent)
                                    }
                                    mainViewModel.setServiceRunningStatus(true)
                                } else {
                                    // Stop Service
                                    val intent = Intent(this@MainActivity, StepForegroundService::class.java).apply {
                                        action = StepForegroundService.ACTION_STOP
                                    }
                                    startService(intent)
                                    mainViewModel.setServiceRunningStatus(false)
                                }
                            }

                        )
                        "sleep_detail"-> SleepDetailScreen(
                            onBackClick = { currentScreen = "dashboard" },
                            sleepViewModel,
                            isDark,
                            modifier = Modifier.padding(innerPadding)

                        )
                        "heart_detail"-> HeartDetailScreen(
                            onBackClick = { currentScreen = "dashboard" },
                            isDarkTheme = isDark,
                            modifier = Modifier.padding(innerPadding),
                            onHeartRateClick = { currentScreen = "heart_rate" }
                        )

                        "heart_rate" -> HeartRateScreen(
                            onBackClick = { heartRate ->
                                heartViewModel.saveHeartRateRecord(heartRate)
                                currentScreen = "heart_detail" },
                        )
                        "step_detail"-> StepDetailScreen(
                            onBackClick = { currentScreen = "dashboard" },
                            mainViewModel = mainViewModel,
                            stepViewModel = stepViewModel,
                            isDarkTheme = isDark,
                            modifier = Modifier.padding(innerPadding)
                        )

                        "profile" -> ProfileScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBackClick = { currentScreen = "dashboard" },
                            //onLogoutClick = {  },
                            isDarkTheme = isDark,
                            onChangeLogin = {
                                mainViewModel.logout()
                            },
                            userViewModel = userViewModel
                        )

                        "notifications" -> NotificationsScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBackClick = { currentScreen = "dashboard" },
                            isDark
                        )

                        "settings" -> SettingsScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBackClick = { currentScreen = "dashboard" },
                            onThemeChanged = { isDarkMode ->
                                userViewModel.toggleTheme(isDarkMode)
                            },
                            isDark
                            ,
                            onChangePassword = { currentScreen = "forgot" },

                        )
                    }
                }
            }
        }

    }
}