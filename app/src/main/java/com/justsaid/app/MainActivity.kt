package com.justsaid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.justsaid.app.telecom.DialerRole
import com.justsaid.app.ui.AppGateViewModel
import com.justsaid.app.ui.StartRoute
import com.justsaid.app.ui.incall.InCallScreen
import com.justsaid.app.ui.incall.InCallViewModel
import com.justsaid.app.ui.onboarding.DownloadScreen
import com.justsaid.app.ui.onboarding.HomeScreen
import com.justsaid.app.ui.onboarding.LegalScreen
import com.justsaid.app.ui.theme.JustSaidTheme
import dagger.hilt.android.AndroidEntryPoint

/** Nav route ids. */
object Routes {
    const val LEGAL = "legal"
    const val DOWNLOAD = "download"
    const val HOME = "home"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JustSaidTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val gateViewModel: AppGateViewModel = hiltViewModel()
                        val startRoute by gateViewModel.startRoute.collectAsState()
                        when (val route = startRoute) {
                            null -> LoadingScreen()
                            else -> AppNavHost(startRoute = route)
                        }

                        // Single-activity host: the in-call screen is an overlay driven by the
                        // global CallStateHolder, shown above whatever route is active.
                        val inCallViewModel: InCallViewModel = hiltViewModel()
                        val inCall by inCallViewModel.state.collectAsState()
                        if (inCall.visible) {
                            InCallScreen(viewModel = inCallViewModel)
                        }
                    }
                    DefaultDialerPrompt()
                }
            }
        }
    }
}

/**
 * Prompts the user (once per launch) to make JustSaid the default phone app. Without the
 * dialer role the telecom framework will not bind our InCallService.
 */
@Composable
private fun DefaultDialerPrompt() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* result ignored: the framework reflects role state on next check */ }

    LaunchedEffect(Unit) {
        DialerRole.requestIntent(context)?.let(launcher::launch)
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Hosts the onboarding gate. The [startRoute] is decided once by [AppGateViewModel];
 * the legal screen has no back/skip route to Home, and Home is only reachable after
 * download completes (or when models were already present).
 */
@Composable
private fun AppNavHost(startRoute: StartRoute) {
    val navController = rememberNavController()
    val start = when (startRoute) {
        StartRoute.LEGAL -> Routes.LEGAL
        StartRoute.DOWNLOAD -> Routes.DOWNLOAD
        StartRoute.HOME -> Routes.HOME
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Routes.LEGAL) {
            LegalScreen(
                onAgreed = { navController.navigateReplacing(Routes.DOWNLOAD, popFrom = Routes.LEGAL) },
            )
        }
        composable(Routes.DOWNLOAD) {
            DownloadScreen(
                onComplete = { navController.navigateReplacing(Routes.HOME, popFrom = Routes.DOWNLOAD) },
            )
        }
        composable(Routes.HOME) {
            HomeScreen()
        }
    }
}

/** Navigate to [destination] and remove [popFrom] from the back stack (no return path). */
private fun NavHostController.navigateReplacing(destination: String, popFrom: String) {
    navigate(destination) {
        popUpTo(popFrom) { inclusive = true }
        launchSingleTop = true
    }
}
