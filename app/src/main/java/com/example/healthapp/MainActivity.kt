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
import com.example.healthapp.core.ViewModel.MainViewModel
import com.example.healthapp.feature.auth.ForgotPasswordScreen
import com.example.healthapp.feature.auth.LoginScreen
import com.example.healthapp.feature.auth.SignUpScreen
import com.example.healthapp.feature.home.HealthDashboardScreen
import com.example.healthapp.feature.home.IntroScreen
import com.example.healthapp.feature.home.NotificationsScreen
import com.example.healthapp.feature.home.ProfileScreen
import com.example.healthapp.feature.home.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        setContent {
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
                        "profile", "notifications", "settings" -> currentScreen = "dashboard"
                        "dashboard" -> currentScreen = "login"
                        "login" -> finish()
                    }
                }


                Scaffold { innerPadding ->
                    when (currentScreen) {
                        "intro"-> IntroScreen (
                            modifier = Modifier.padding(innerPadding),
                            onStartClick = { currentScreen = "login" }
                        )
                        // ... Bên trong Scaffold -> when (currentScreen) ...

                        "login" -> LoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            onSignUpClick = { currentScreen = "signup" },
                            onForgotPasswordClick = { currentScreen = "forgot" },
                            onLogin = { email, password ->
                                mainViewModel.loginUser(email, password) { isSuccess ->
                                    if (isSuccess) {
                                        currentScreen = "dashboard"
                                    } else {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Email hoặc mật khẩu không đúng",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            isLoggingIn = (isLoggedIn == true)
                            , onChangeLogin = { isLoggedIn ->
                                mainViewModel.setIsLoggedIn(isLoggedIn)
                                currentScreen = "intro"
                            }
                        )

                        "signup" -> SignUpScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLoginClick = { currentScreen = "login" },
                            onSignUp = { email, password ->
                                mainViewModel.registerUser(email, password)

                                Toast.makeText(this@MainActivity, "Đăng ký thành công! Hãy đăng nhập.", Toast.LENGTH_SHORT).show()

                                currentScreen = "login"
                            }
                        )

                        "forgot" -> ForgotPasswordScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBackToLoginClick = { currentScreen = "login" }
                        )

                        "dashboard" -> HealthDashboardScreen(
                            modifier = Modifier.padding(innerPadding),
                            onProfileClick = { currentScreen = "profile" },
                            onNotificationsClick = { currentScreen = "notifications" },
                            onSettingsClick = { currentScreen = "settings" },
                            isDark
                        )

                        "profile" -> ProfileScreen(
                            modifier = Modifier.padding(innerPadding),
                            onBackClick = { currentScreen = "dashboard" },
                            //onLogoutClick = {  },
                            isDarkTheme = isDark,
                            onChangeLogin = { isLoggedIn ->
                                mainViewModel.setIsLoggedIn(isLoggedIn)
                                currentScreen = "intro"
                            },
                            isLoggingIn = isLoggedIn
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