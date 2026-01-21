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
import com.example.healthapp.feature.Auth.ForgotPasswordScreen
import com.example.healthapp.feature.Auth.LoginScreen
import com.example.healthapp.feature.Auth.SignUpScreen
import com.example.healthapp.feature.Home.HealthDashboardScreen
import com.example.healthapp.feature.Home.NotificationsScreen
import com.example.healthapp.feature.Home.ProfileScreen
import com.example.healthapp.feature.Home.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint


@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val isDark by mainViewModel.isDarkMode.collectAsState()
            HealthAppTheme {

                val systemUiController = rememberSystemUiController()

                SideEffect {
                    systemUiController.setStatusBarColor(
                        color = (Color(0xFF0F172A)),
                        darkIcons = false
                    )
                }

                var currentScreen by remember { mutableStateOf("login") }
                val demoUsers = remember { mutableStateListOf<Pair<String, String>>() }

                BackHandler {
                    when (currentScreen) {
                        "signup", "forgot" -> currentScreen = "login"
                        "profile", "notifications", "settings" -> currentScreen = "dashboard"
                        "dashboard" -> currentScreen = "login"
                        "login" -> finish()
                    }
                }


                Scaffold { innerPadding ->
                    when (currentScreen) {
                        "login" -> LoginScreen(
                            modifier = Modifier.padding(innerPadding),
                            onSignUpClick = { currentScreen = "signup" },
                            onForgotPasswordClick = { currentScreen = "forgot" },
                            onLogin = { email, password ->
                                val isValidUser =
                                    demoUsers.any { it.first == email && it.second == password }

                                if (isValidUser) {
                                    currentScreen = "dashboard"
                                } else {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Invalid email or password",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        )

                        "signup" -> SignUpScreen(
                            modifier = Modifier.padding(innerPadding),
                            onLoginClick = { currentScreen = "login" },
                            onSignUp = { email, password ->
                                demoUsers.add(email to password)
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
                            onLogoutClick = { currentScreen = "login" },
                            isDark
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
                        )
                    }
                }
            }
        }

    }
}