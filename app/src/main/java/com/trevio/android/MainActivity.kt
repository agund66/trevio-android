package com.trevio.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.trevio.android.core.designsystem.theme.TrevioTheme
import com.trevio.android.core.navigation.TrevioNavGraph
import com.trevio.android.core.navigation.TrevioRoute
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingInviteCode = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val teal = AndroidColor.parseColor("#0D9488")
        val tealLight = AndroidColor.parseColor("#14B8A6")
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(tealLight),
            navigationBarStyle = SystemBarStyle.dark(teal)
        )

        pendingInviteCode.value = extractInviteCode(intent)

        setContent {
            TrevioTheme {
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
                        val navController = rememberNavController()
                        val inviteCode by pendingInviteCode
                        TrevioNavGraph(
                            navController = navController,
                            pendingInviteCode = inviteCode
                        )
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
