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
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import com.example.healthapp.feature.componets.HeartRateScreen
import com.example.healthapp.feature.detail.HeartDetailScreen
import com.example.healthapp.feature.home.HeightPickerScreen
import com.example.healthapp.feature.home.NameScreen
import com.example.healthapp.feature.home.WeightScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val healthConnectManager = mainViewModel.healthConnectManager
            val healthState by mainViewModel.healthConnectState.collectAsState()
            val context = LocalContext.current // Lấy context ở đây để dùng cho Toast/Intent

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

            val activityRecognitionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // Dù user đồng ý hay từ chối, ta đều tiếp tục kiểm tra Health Connect
                // Để tránh app bị kẹt nếu user từ chối quyền đếm bước chân phần cứng
                if (isGranted) {
                mainViewModel.startSensorTracking()
                }
                // BẮT ĐẦU kiểm tra Health Connect SAU KHI popup này tắt
                mainViewModel.checkHealthConnectStatus()
            }


            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        // Mỗi khi user quay lại app (từ Setting hoặc CH Play), tự động check lại
                        mainViewModel.checkHealthConnectStatus()
                        mainViewModel.startSensorTracking()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            LaunchedEffect(Unit) {

                activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)

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
            val isDark by mainViewModel.isDarkMode.collectAsState()
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
                    mutableStateOf(if (!isLoggedIn) "dashboard" else "intro")
                }

                BackHandler {
                    when (currentScreen) {
                        "login"->currentScreen= "intro"
                        "signup", "forgot" -> currentScreen = "login"
                        "profile", "notifications", "settings","heart_rate" -> currentScreen = "dashboard"
                        //"dashboard" -> currentScreen = "login"
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
                                        currentScreen = "name" // Vào thẳng dashboard
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
                        "name" -> NameScreen(
                            modifier = Modifier.padding(innerPadding),
                            onStartClick = {name->
                                mainViewModel.addName(name)
                                currentScreen = "height"
                            }
                        )
                        "height" -> HeightPickerScreen(
                            modifier = Modifier.padding(innerPadding),
                            onStartClick = {height->
                                mainViewModel.addHeight(height)
                                currentScreen = "weight"
                            }
                        )
                        "weight" -> WeightScreen(
                            modifier = Modifier.padding(innerPadding),
                            onStartClick = {
                                weight -> mainViewModel.addWeight(weight)
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
                                    mainViewModel
                        )
                        "heart_detail"-> HeartDetailScreen(
                            modifier = Modifier.padding(innerPadding),
                            onHeartRateClick = { currentScreen = "heart_rate" },
                            isDark,
                            onBackClick = { currentScreen = "dashboard" },
                            mainViewModel
                        )

                        "heart_rate" -> HeartRateScreen(
                            onBackClick = { heartRate ->
                                mainViewModel.saveHeartRateRecord(heartRate)
                                currentScreen = "heart_detail" },
                            mainViewModel = mainViewModel
                        )

                        "profile" -> ProfileScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBackClick = { currentScreen = "dashboard" },
                            //onLogoutClick = {  },
                            isDarkTheme = isDark,
                            onChangeLogin = { isLoggedIn ->
                                mainViewModel.updateLoginStatus(isLoggedIn = isLoggedIn)
                                currentScreen = "intro"
                            },
                            isLoggingIn = isLoggedIn,
                            mainViewModel = mainViewModel
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
                                mainViewModel.toggleTheme(isDarkMode)
                            },
                            isDark
                            ,
                            onChangePassword = { currentScreen = "forgot" }
                        )
                    }
                }
            }
        }

    }
}