package com.altomedia.beruang.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    var splashDone by remember { mutableStateOf(false) }
    if (!splashDone) {
        SplashLoadingScreen(onComplete = { splashDone = true })
        return
    }

    val start = if (user != null) Routes.MAIN else Routes.AUTH

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.AUTH) {
            LoginScreen(
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
