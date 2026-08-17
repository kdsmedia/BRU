package com.altomedia.beruang.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
 * Root navigation. Decides between the auth screen and the main app based on
 * the live auth state.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val authVm: AuthViewModel = viewModel()
    val user = authVm.currentUser.collectAsState().value

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
                    navController.navigate(Routes.AUTH) {
                        popUpTo(Routes.MAIN) { inclusive = true }
                    }
                },
            )
        }
    }
}
