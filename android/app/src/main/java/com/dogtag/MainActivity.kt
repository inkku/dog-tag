package com.dogtag

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dogtag.fence.FenceEvaluatorService
import com.dogtag.ui.calibration.CalibrationScreen
import com.dogtag.ui.dogs.DogsScreen
import com.dogtag.ui.fences.FencesScreen
import com.dogtag.ui.map.MapScreen
import com.dogtag.ui.settings.SettingsScreen
import com.dogtag.ui.theme.DogTagTheme

private sealed class Screen(val route: String, val label: String) {
    data object MapTab : Screen("map", "Map")
    data object Dogs : Screen("dogs", "Dogs")
    data object Fences : Screen("fences", "Fences")
    data object Calibrate : Screen("calibrate", "Calibrate")
    data object Settings : Screen("settings", "Settings")
}

private val bottomNavScreens = listOf(
    Screen.MapTab, Screen.Dogs, Screen.Fences, Screen.Calibrate, Screen.Settings,
)

class MainActivity : ComponentActivity() {

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            startFenceMonitoring()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions.launch(requiredPermissions())

        setContent {
            DogTagTheme {
                DogTagApp()
            }
        }
    }

    private fun requiredPermissions(): Array<String> {
        val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        return perms.toTypedArray()
    }

    private fun startFenceMonitoring() {
        val hasAll = requiredPermissions().all {
            ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (hasAll) {
            startForegroundService(Intent(this, FenceEvaluatorService::class.java))
        }
    }
}

@Composable
private fun DogTagApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = backStackEntry?.destination
                bottomNavScreens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(iconFor(screen), contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.MapTab.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Screen.MapTab.route) { MapScreen() }
            composable(Screen.Dogs.route) { DogsScreen() }
            composable(Screen.Fences.route) { FencesScreen() }
            composable(Screen.Calibrate.route) { CalibrationScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun iconFor(screen: Screen) = when (screen) {
    Screen.MapTab -> Icons.Filled.Map
    Screen.Dogs -> Icons.Filled.Pets
    Screen.Fences -> Icons.Filled.Tune
    Screen.Calibrate -> Icons.Filled.Straighten
    Screen.Settings -> Icons.Filled.Settings
}
