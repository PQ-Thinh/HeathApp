package com.example.healthapp

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.healthapp.core.service.StepForegroundService
import com.example.healthapp.core.viewmodel.HeartViewModel
import com.example.healthapp.core.viewmodel.MainViewModel
import com.example.healthapp.core.viewmodel.SleepViewModel
import com.example.healthapp.core.viewmodel.StepViewModel
import com.example.healthapp.core.viewmodel.UserViewModel
import com.example.healthapp.feature.auth.ForgotPasswordScreen
import com.example.healthapp.feature.auth.LoginScreen
import com.example.healthapp.feature.auth.SignUpScreen
import com.example.healthapp.feature.components.HeartRateScreen
import com.example.healthapp.feature.detail.FullProfileScreen
import com.example.healthapp.feature.detail.HeartDetailScreen
import com.example.healthapp.feature.detail.SleepDetailScreen
import com.example.healthapp.feature.detail.StepDetailScreen
import com.example.healthapp.feature.home.HealthDashboardScreen
import com.example.healthapp.feature.home.HeightPickerScreen
import com.example.healthapp.feature.home.IntroScreen
import com.example.healthapp.feature.home.NotificationsScreen
import com.example.healthapp.feature.home.ProfileScreen
import com.example.healthapp.feature.home.SettingsScreen
import com.example.healthapp.feature.home.UserInfoScreen
import com.example.healthapp.feature.home.WeightScreen
import com.example.healthapp.ui.theme.HealthAppTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val userViewModel: UserViewModel = hiltViewModel()
            val sleepViewModel: SleepViewModel = hiltViewModel()
            val heartViewModel: HeartViewModel = hiltViewModel()
            val stepViewModel: StepViewModel = hiltViewModel()
            val healthConnectManager = mainViewModel.healthConnectManager
            val healthState by mainViewModel.healthConnectState.collectAsState()
            val context = LocalContext.current

            val isServiceRunning by mainViewModel.isServiceRunning.collectAsState()
            val lifecycleOwner = LocalLifecycleOwner.current
            var showPermissionRationaleDialog by remember { mutableStateOf(false) }
            var showInstallDialog by remember { mutableStateOf(false) }

            // Launcher Xin Quyền
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
            ) { _ ->
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
                        mainViewModel.checkHealthConnectStatus()
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
                        showPermissionRationaleDialog = true
                    }
                    1 -> {
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

            // Lắng nghe Start Destination từ ViewModel
            val startDestination by mainViewModel.startDestination.collectAsState()

            HealthAppTheme {
                val systemUiController = rememberSystemUiController()
                SideEffect {
                    systemUiController.setStatusBarColor(
                        color = (Color(0xFF0F172A)),
                        darkIcons = false
                    )
                }

                //Khởi tạo là null để hiện Loading
                var currentScreen by remember { mutableStateOf<String?>(null) }

                // Lắng nghe điểm đến từ ViewModel để set màn hình đầu tiên
                LaunchedEffect(startDestination) {
                    // Chỉ set màn hình nếu hiện tại đang là null (lần đầu mở app)
                    if (currentScreen == null && startDestination != null) {
                        currentScreen = startDestination
                    }
                }

                //Logic kiểm tra thông tin User (chỉ chạy khi đã Login)
                LaunchedEffect(isLoggedIn, userInfo) {
                    if (isLoggedIn == true) {
                        if (userInfo == null) {
                            // Đang tải user info, chờ...
                        } else if (userInfo?.name.isNullOrBlank() == true) {
                            currentScreen = "name"
                        } else if ((userInfo?.height ?: 0f) == 0f) {
                            currentScreen = "height"
                        } else if ((userInfo?.weight ?: 0f) == 0f) {
                            currentScreen = "weight"
                        } else {
                            // Nếu đang ở các màn hình chờ, chuyển vào dashboard
                            if (currentScreen == null || currentScreen == "intro" || currentScreen == "login") {
                                currentScreen = "dashboard"
                            }
                        }
                    }
                    // Nếu isLoggedIn == false, startDestination sẽ lo việc chuyển về Intro/Login
                }

                BackHandler {
                    when (currentScreen) {
                        "login" -> currentScreen = "intro"
                        "signup", "forgot" -> currentScreen = "login"
                        "profile", "notifications", "settings", "heart_detail", "sleep_detail", "step_detail" -> currentScreen = "dashboard"
                        "heart_rate" -> currentScreen = "heart_detail"
                        "height" -> currentScreen = "name"
                        "weight" -> currentScreen = "height"
                        "intro" -> finish()
                        "dashboard" -> finish() // Cho phép thoát app từ dashboard
                    }
                }

                //Hiển thị màn hình chờ nếu currentScreen null
                if (currentScreen == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF0F172A)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    Scaffold { innerPadding ->
                        when (currentScreen!!) {
                            "intro" -> IntroScreen(
                                modifier = Modifier.padding(innerPadding),
                                onStartClick = {
                                    mainViewModel.completeIntro()
                                    currentScreen = "login"
                                }
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
                            "name" -> UserInfoScreen(
                                modifier = Modifier.padding(innerPadding),
                                onStartClick = { name, gender, day, mouth, year ->
                                    userViewModel.updateUserInfo(name, gender, day, mouth, year)
                                    currentScreen = "height"
                                }
                            )
                            "height" -> HeightPickerScreen(
                                modifier = Modifier.padding(innerPadding),
                                onStartClick = { height ->
                                    userViewModel.addHeight(height)
                                    currentScreen = "weight"
                                }
                            )
                            "weight" -> WeightScreen(
                                modifier = Modifier.padding(innerPadding),
                                onStartClick = { weight ->
                                    userViewModel.addWeight(weight)
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
                                onStepDetailClick = { currentScreen = "step_detail" },
                                mainViewModel,
                                userViewModel,
                                sleepViewModel,
                                stepViewModel,
                                isServiceRunning = isServiceRunning,
                                onToggleService = { shouldStart ->
                                    if (shouldStart) {
                                        val intent = Intent(this@MainActivity, StepForegroundService::class.java).apply {
                                            action = StepForegroundService.ACTION_START
                                        }
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                            startForegroundService(intent)
                                        } else {
                                            startService(intent)
                                        }
                                        mainViewModel.setServiceRunningStatus(true)
                                    } else {
                                        val intent = Intent(this@MainActivity, StepForegroundService::class.java).apply {
                                            action = StepForegroundService.ACTION_STOP
                                        }
                                        startService(intent)
                                        mainViewModel.setServiceRunningStatus(false)
                                    }
                                },
                                onNavigateToRun = { currentScreen = "run" }
                            )
                            "sleep_detail" -> SleepDetailScreen(
                                onBackClick = { currentScreen = "dashboard" },
                                sleepViewModel,
                                isDark,
                                modifier = Modifier.padding(innerPadding)
                            )
                            "heart_detail" -> HeartDetailScreen(
                                onBackClick = { currentScreen = "dashboard" },
                                isDarkTheme = isDark,
                                modifier = Modifier.padding(innerPadding),
                                onHeartRateClick = { currentScreen = "heart_rate" }
                            )
                            "heart_rate" -> HeartRateScreen(
                                onBackClick = { heartRate ->
                                    heartViewModel.saveHeartRateRecord(heartRate)
                                    currentScreen = "heart_detail"
                                },
                            )
                            "step_detail" -> StepDetailScreen(
                                onBackClick = { currentScreen = "dashboard" },
                                mainViewModel = mainViewModel,
                                stepViewModel = stepViewModel,
                                isDarkTheme = isDark,
                                modifier = Modifier.padding(innerPadding)
                            )
                            "profile" -> ProfileScreen(
                                modifier = Modifier.padding(innerPadding),
                                onBackClick = { currentScreen = "dashboard" },
                                isDarkTheme = isDark,
                                onChangeLogin = {
                                    mainViewModel.logout()
                                    currentScreen = "login"
                                },
                                userViewModel = userViewModel,
                                onProfileDetail = {currentScreen = "profile_detail"}
                            )
                            "profile_detail" -> FullProfileScreen(
                                modifier = Modifier.padding(innerPadding),
                                userViewModel = userViewModel,
                                isDarkTheme = isDark,
                                onBackClick = { currentScreen = "profile" }

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
                                isDark,
                                onChangePassword = { currentScreen = "forgot" },
                            )
                        }
                    }
                }
            }
        }
    }
}