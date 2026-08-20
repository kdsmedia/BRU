package com.altomedia.beruang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.altomedia.beruang.ui.auth.AuthViewModel
import com.altomedia.beruang.ui.auth.LoginScreen

object Routes {
    const val AUTH = "auth"
    const val MAIN = "main"
}

/**
 * Root navigation. Shows the 20-second data-loading splash once per launch,
 * then decides between the auth screen and the main app based on the live
 * auth state.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val authVm: AuthViewModel = viewModel()
    val user = authVm.currentUser.collectAsState().value
    val authReady = authVm.authReady.collectAsState().value

    var splashDone by remember { mutableStateOf(false) }
    if (!splashDone) {
        SplashLoadingScreen(onComplete = { splashDone = true })
        return
    }
    // Keep a blank (splash-colored) gate until the persisted session has been
    // restored; otherwise the graph would start on AUTH and a successful login
    // would navigate into a MAIN graph whose ViewModels belong to the AUTH
    // back-stack entry, leaving a white screen.
    if (!authReady) {
        Box(modifier = Modifier.fillMaxSize().background(Color.White))
        return
    }

    val start = if (user != null) Routes.MAIN else Routes.AUTH

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.AUTH) {
            LoginScreen(
                authVm = authVm,
                onAuthed = {
                    navController.navigate(Routes.MAIN) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.MAIN) {
            val me = user ?: return@composable
            MainScaffold(
                me = me,
                authVm = authVm,
                onLogout = {
                    authVm.logout()
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }
    }
}
