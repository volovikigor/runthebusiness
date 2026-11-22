package com.example.runthebusiness

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.runthebusiness.data.local.GameRepository
import com.example.runthebusiness.core.simulation.GameSimulationEngine
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import com.example.runthebusiness.ui.cafe.CafeDashboardScreen
import com.example.runthebusiness.ui.cafe.CafeFinanceScreen
import com.example.runthebusiness.ui.cafe.CafeHomeScreen
import com.example.runthebusiness.ui.theme.RunthebusinessTheme

class MainActivity : ComponentActivity() {
    private var appForeground by mutableStateOf(false)

    override fun onStart() { super.onStart(); appForeground = true }
    override fun onStop() { appForeground = false; super.onStop() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunthebusinessTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabHistory = remember { mutableStateListOf<Int>() }
                fun navigateTab(tab: Int) {
                    if (selectedTab != tab) {
                        tabHistory.add(selectedTab)
                        selectedTab = tab
                    }
                }
                BackHandler(enabled = tabHistory.isNotEmpty()) {
                    selectedTab = tabHistory.removeAt(tabHistory.lastIndex)
                }
                var operationsSection by remember { mutableIntStateOf(0) }
                var gameSpeed by remember { mutableIntStateOf(0) }
                var clockRevision by remember { mutableIntStateOf(0) }
                var clockError by remember { mutableStateOf<String?>(null) }
                val context = LocalContext.current.applicationContext
                val game = remember(context) { GameRepository(context) }
                val simulation = remember(context) { GameSimulationEngine(context) }
                LaunchedEffect(appForeground) {
                    if (!appForeground) gameSpeed = 0
                }
                LaunchedEffect(gameSpeed, appForeground) {
                    if (!appForeground || gameSpeed == 0) return@LaunchedEffect
                    // Advance 15 game minutes per tick. x1 = 15s; x5 = 3s; x20 = 0.75s.
                    while (true) {
                        delay(15_000L / gameSpeed)
                        try {
                            if (game.loadGameTime() != null) {
                                simulation.advance(15L)
                                clockRevision++
                                clockError = null
                            }
                        } catch (e: Exception) {
                            clockError = e.message ?: e.javaClass.simpleName
                            gameSpeed = 0
                            break
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = selectedTab == 0,
                                onClick = { navigateTab(0) },
                                icon = { Text("☕") },
                                label = { Text("Café") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 1,
                                onClick = { navigateTab(1) },
                                icon = { Text("▤") },
                                label = { Text("Operations") }
                            )
                            NavigationBarItem(
                                selected = selectedTab == 2,
                                onClick = { navigateTab(2) },
                                icon = { Text("€") },
                                label = { Text("Finance") }
                            )
                        }
                    }
                ) { padding ->
                    when (selectedTab) {
                        0 -> CafeHomeScreen(
                            modifier = Modifier.padding(padding),
                            speed = gameSpeed,
                            onSpeedChange = { gameSpeed = it },
                            clockRevision = clockRevision,
                            clockError = clockError,
                            onOpenOperations = { section -> operationsSection = section; navigateTab(1) },
                            onOpenFinance = { navigateTab(2) }
                        )
                        1 -> CafeDashboardScreen(modifier = Modifier.padding(padding), requestedSection = operationsSection)
                        2 -> CafeFinanceScreen(modifier = Modifier.padding(padding))
                        else -> CafeFinanceScreen(modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}
