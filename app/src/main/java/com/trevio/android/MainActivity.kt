package com.trevio.android

import android.content.Intent
import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.trevio.android.core.designsystem.theme.ThemeViewModel
import com.trevio.android.core.designsystem.theme.TrevioTheme
import com.trevio.android.core.navigation.TrevioNavGraph
import com.trevio.android.core.security.AppLockScreen
import com.trevio.android.core.security.AppLockViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Extends [FragmentActivity] (instead of ComponentActivity) because
 * [androidx.biometric.BiometricPrompt] requires a FragmentActivity host.
 * FragmentActivity is a subclass of ComponentActivity, so all existing
 * Compose / Hilt functionality continues to work unchanged.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private var pendingInviteCode = mutableStateOf<String?>(null)
    // Pair of (route, nonce) — the nonce increments on each notification so
    // the same route can re-trigger navigation (e.g. two notifications for
    // the same group on different days).
    private var pendingNavRoute = mutableStateOf<Pair<String, Int>?>(null)
    private var navRouteNonce = 0

    // Held as a field so the splash-screen keep condition can read
    // isReady synchronously before setContent runs.
    private lateinit var appLockViewModel: AppLockViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        // Create the AppLockViewModel early so we can keep the splash
        // screen visible until DataStore preferences have loaded. This
        // prevents any app content from flashing before the lock gate
        // decision is made.
        appLockViewModel = ViewModelProvider(this)[AppLockViewModel::class.java]
        splashScreen.setKeepOnScreenCondition { !appLockViewModel.isReady.value }

        val teal = AndroidColor.parseColor("#0D9488")
        val tealLight = AndroidColor.parseColor("#14B8A6")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(tealLight),
            navigationBarStyle = SystemBarStyle.dark(teal)
        )

        pendingInviteCode.value = extractInviteCode(intent)
        intent?.getStringExtra("nav_route")?.let { route ->
            pendingNavRoute.value = route to ++navRouteNonce
        }

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()

            // Same instance as the field-level appLockViewModel (shared ViewModelStore)
            val lockViewModel: AppLockViewModel = hiltViewModel()
            val isLocked by lockViewModel.isLocked.collectAsState()
            val appLockEnabled by lockViewModel.appLockEnabled.collectAsState()

            TrevioTheme(themeMode = themeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(tealLight))
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding(),
                        color = Color(tealLight)
                    ) {
                        // The nav graph is always composed so navigation
                        // state is preserved across lock/unlock cycles.
                        // The lock screen is drawn on top as an overlay.
                        val navController = rememberNavController()
                        val inviteCode by pendingInviteCode
                        val navRoutePair by pendingNavRoute
                        TrevioNavGraph(
                            navController = navController,
                            pendingInviteCode = inviteCode,
                            pendingNavRoute = navRoutePair
                        )

                        if (isLocked && appLockEnabled) {
                            AppLockScreen(viewModel = lockViewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val code = extractInviteCode(intent)
        if (code != null) {
            pendingInviteCode.value = code
        }
        intent.getStringExtra("nav_route")?.let { route ->
            pendingNavRoute.value = route to ++navRouteNonce
        }
    }

    private fun extractInviteCode(intent: Intent?): String? {
        val data = intent?.data ?: return null
        val supportedHosts = listOf("trevio.app", "trevio-split.netlify.app", "trevio-split.firebaseapp.com")
        if (data.host in supportedHosts && data.path?.startsWith("/join/") == true) {
            return data.lastPathSegment
        }
        return null
    }
}
