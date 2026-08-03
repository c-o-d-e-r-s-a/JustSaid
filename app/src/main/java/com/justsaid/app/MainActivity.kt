package com.justsaid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.justsaid.app.data.repo.HistoryAutoCleanup
import com.justsaid.app.ui.AppGateViewModel
import com.justsaid.app.ui.StartRoute
import com.justsaid.app.ui.capture.HomeScreen
import com.justsaid.app.ui.history.HistoryDetailScreen
import com.justsaid.app.ui.history.HistoryScreen
import com.justsaid.app.ui.onboarding.DownloadScreen
import com.justsaid.app.ui.onboarding.LegalScreen
import com.justsaid.app.ui.settings.SettingsScreen
import com.justsaid.app.ui.theme.JustSaidTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav route ids. */
object Routes {
    const val LEGAL = "legal"
    const val DOWNLOAD = "download"
    const val HOME = "home"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val SETTINGS = "settings"

    fun historyDetail(id: Long) = "history/$id"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var autoCleanup: HistoryAutoCleanup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { autoCleanup.runIfEnabled() }
        setContent {
            JustSaidTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val gateViewModel: AppGateViewModel = hiltViewModel()
                    val startRoute by gateViewModel.startRoute.collectAsState()
                    when (val route = startRoute) {
                        null -> LoadingScreen()
                        else -> AppNavHost(startRoute = route)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** Hosts onboarding gate and companion home (capture + unsaved summary inline). */
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
                onComplete = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onOpenSummary = { id -> navController.navigate(Routes.historyDetail(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            HistoryDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onDownloadNeeded = { navController.navigate(Routes.DOWNLOAD) },
            )
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
