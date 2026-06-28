package com.trevio.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.trevio.android.core.designsystem.theme.TrevioTheme
import com.trevio.android.core.navigation.TrevioNavGraph
import com.trevio.android.core.navigation.TrevioRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val pendingInviteCode = extractInviteCode(intent)

        setContent {
            TrevioTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    TrevioNavGraph(
                        navController = navController,
                        pendingInviteCode = pendingInviteCode
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractInviteCode(intent: Intent?): String? {
        val data = intent?.data ?: return null
        if (data.host == "trevio.app" && data.path?.startsWith("/join/") == true) {
            return data.lastPathSegment
        }
        return null
    }
}
