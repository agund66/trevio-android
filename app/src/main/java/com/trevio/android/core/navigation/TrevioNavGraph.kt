package com.trevio.android.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.trevio.android.ui.auth.AuthScreen
import com.trevio.android.ui.auth.TermsScreen
import com.trevio.android.ui.auth.PhoneSetupScreen
import com.trevio.android.ui.splash.SplashScreen

@Composable
fun TrevioNavGraph(
    navController: NavHostController,
    startDestination: String = TrevioRoute.Splash.route,
    pendingInviteCode: String? = null
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(TrevioRoute.Splash.route) {
            SplashScreen(navController = navController)
        }

        composable(TrevioRoute.Login.route) {
            AuthScreen(navController = navController)
        }

        composable(TrevioRoute.Terms.route) {
            TermsScreen(navController = navController)
        }

        composable(TrevioRoute.PhoneSetup.route) {
            PhoneSetupScreen(navController = navController)
        }

        composable(TrevioRoute.Main.route) {
            MainShell(
                navController = navController,
                pendingInviteCode = pendingInviteCode
            )
        }
    }
}
