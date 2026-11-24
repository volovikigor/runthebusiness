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
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.example.runthebusiness.data.local.CafeAuditExportRepository
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
                var financeTarget by remember { mutableStateOf("P&L") }
                var gameSpeed by remember { mutableIntStateOf(0) }
                LaunchedEffect(selectedTab) { if (selectedTab != 0) gameSpeed = 0 }
                var clockRevision by remember { mutableIntStateOf(0) }
                var clockError by remember { mutableStateOf<String?>(null) }
                val context = LocalContext.current.applicationContext
                val game = remember(context) { GameRepository(context) }
                val simulation = remember(context) { GameSimulationEngine(context) }
                val scope = rememberCoroutineScope()
                val audit = remember(context) { CafeAuditExportRepository(context) }
                var time by remember { mutableStateOf<com.example.runthebusiness.engine.model.GameTime?>(null) }
                var jumping by remember { mutableStateOf(false) }
                var skipMenu by remember { mutableStateOf(false) }
                var progress by remember { mutableIntStateOf(0) }
                var groups by remember { mutableIntStateOf(0) }
                var resultMessage by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(clockRevision, selectedTab) { time = game.loadGameTime() }
                fun jump(mode: GameSimulationEngine.JumpMode) {
                    if (jumping || gameSpeed != 0) return
                    jumping = true
                    progress = 0
                    groups = 0
                    resultMessage = null
                    scope.launch {
                        try {
                            val result = simulation.jump(mode, onProgress = { _, completed, served ->
                                progress = completed
                                groups = served
                            })
                            resultMessage = result.description
                            if (mode == GameSimulationEngine.JumpMode.NEXT_WEEK) audit.saveDesktopReport()
                        } catch (e: Exception) { clockError = e.message ?: e.javaClass.simpleName }
                        finally { jumping = false; clockRevision++ }
                    }
                }
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
                    topBar = {
                        if (selectedTab == 0) Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(time?.let { "Day ${it.day} · %02d:%02d".format(it.hour, it.minute) } ?: "Maison Café",
                                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Box {
                                    TextButton(enabled = !jumping && gameSpeed == 0 && time != null,
                                        onClick = { skipMenu = true }) { Text("Skip ▾") }
                                    DropdownMenu(expanded = skipMenu, onDismissRequest = { skipMenu = false }) {
                                        listOf(GameSimulationEngine.JumpMode.NEXT_EVENT to "Next event",
                                            GameSimulationEngine.JumpMode.NEXT_DECISION to "Next decision",
                                            GameSimulationEngine.JumpMode.NEXT_DAY to "Next day",
                                            GameSimulationEngine.JumpMode.NEXT_WEEK to "Next week").forEach { (mode, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = { skipMenu = false; jump(mode) })
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to "Pause", 1 to "×1", 5 to "×5", 20 to "×20").forEach { (value, label) ->
                                    FilterChip(selected = gameSpeed == value, enabled = !jumping && time != null,
                                        onClick = { gameSpeed = value }, label = { Text(label) })
                                }
                            }
                            if (jumping) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text("Simulating · $progress days · $groups groups", style = MaterialTheme.typography.bodySmall)
                            }
                            clockError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            resultMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                        }
                    },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
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
                    if (jumping) {
                        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Simulating business…") }
                    } else when (selectedTab) {
                        0 -> CafeHomeScreen(
                            modifier = Modifier.padding(padding),
                            speed = gameSpeed,
                            onSpeedChange = { gameSpeed = it },
                            clockRevision = clockRevision,
                            clockError = clockError,
                            onOpenOperations = { section -> operationsSection = section; navigateTab(1) },
                            onOpenFinance = { financeTarget = "Bills"; navigateTab(2) }
                        )
                        1 -> CafeDashboardScreen(modifier = Modifier.padding(padding), requestedSection = operationsSection)
                        2 -> CafeFinanceScreen(modifier = Modifier.padding(padding), requestedSection = financeTarget)
                        else -> CafeFinanceScreen(modifier = Modifier.padding(padding))
                    }
                }
            }
        }
    }
}
