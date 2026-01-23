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
import androidx.compose.runtime.mutableStateListOf
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
import com.example.healthapp.feature.detail.HeartRateScreen
import com.example.healthapp.feature.home.HeightPickerScreen
import com.example.healthapp.feature.home.NameScreen
import com.example.healthapp.feature.home.WeightScreen
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val permissionState = rememberPermissionState(Manifest.permission.ACTIVITY_RECOGNITION)

            LaunchedEffect(Unit) {
                permissionState.launchPermissionRequest()
            }
            val mainViewModel: MainViewModel = hiltViewModel()
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
                val demoUsers = remember { mutableStateListOf<Pair<String, String>>() }

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
                            onHeartRateClick = { currentScreen = "heart_rate" },
                                    mainViewModel
                        )

                        "heart_rate" -> HeartRateScreen(
                            onBackClick = { heartRate ->
                                mainViewModel.saveHeartRateRecord(heartRate)
                                currentScreen = "dashboard" },
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