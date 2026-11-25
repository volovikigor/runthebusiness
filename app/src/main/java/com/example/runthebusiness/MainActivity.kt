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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import com.example.runthebusiness.data.local.CafeAuditExportRepository
import androidx.compose.ui.Modifier
import com.example.runthebusiness.ui.cafe.CafeDashboardScreen
import com.example.runthebusiness.ui.cafe.CafeFinanceScreen
import com.example.runthebusiness.ui.cafe.CafeHomeScreen
import com.example.runthebusiness.ui.cafe.CafeRunMusic
import com.example.runthebusiness.ui.cafe.CafeMusicTrack
import com.example.runthebusiness.ui.components.CafeBottomNavigation
import com.example.runthebusiness.ui.theme.RunthebusinessTheme

class MainActivity : ComponentActivity() {
    private var appForeground by mutableStateOf(false)
    private var gameSpeed by mutableIntStateOf(0)
    private var progressionJob: Job? = null
    private var jumpMode by mutableStateOf<GameSimulationEngine.JumpMode?>(null)

    override fun onResume() {
        super.onResume()
        appForeground = true
    }

    override fun onPause() {
        appForeground = false
        progressionJob?.cancel()
        gameSpeed = 0
        jumpMode = null
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunthebusinessTheme {
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabHistory = remember { mutableStateListOf<Int>() }
                var operationsSection by remember { mutableIntStateOf(0) }
                var financeTarget by remember { mutableStateOf("Forecast") }
                var clockError by remember { mutableStateOf<String?>(null) }
                val context = LocalContext.current.applicationContext
                val game = remember(context) { GameRepository(context) }
                val simulation = remember(context) { GameSimulationEngine(context) }
                val scope = rememberCoroutineScope()
                val audit = remember(context) { CafeAuditExportRepository(context) }
                val savedTime = remember(game) { game.observeGameTime() }
                val time by savedTime.collectAsState(initial = null)
                var restoring by remember { mutableStateOf(true) }
                var settingUp by remember { mutableStateOf(false) }
                val jumping = jumpMode != null
                val audioSettings = remember(context) { context.getSharedPreferences("cafe_audio", MODE_PRIVATE) }
                var musicEnabled by remember { mutableStateOf(audioSettings.getBoolean("music", true)) }
                var selectedTrack by remember {
                    val savedTrack = audioSettings.getString("track", null)
                    mutableStateOf(CafeMusicTrack.entries.firstOrNull { it.name == savedTrack })
                }
                var showMusic by remember { mutableStateOf(false) }
                CafeRunMusic(context, musicEnabled && appForeground && selectedTab == 0 &&
                    !restoring && !settingUp && (gameSpeed > 0 || jumping), selectedTrack)
                fun selectMusic(enabled: Boolean, track: CafeMusicTrack?) {
                    musicEnabled = enabled
                    selectedTrack = track
                    audioSettings.edit().putBoolean("music", enabled).putString("track", track?.name).apply()
                    showMusic = false
                }
                var skipMenu by remember { mutableStateOf(false) }
                var progress by remember { mutableIntStateOf(0) }
                var groups by remember { mutableIntStateOf(0) }
                var resultMessage by remember { mutableStateOf<String?>(null) }

                fun pauseForNavigation() {
                    progressionJob?.cancel()
                    gameSpeed = 0
                    jumpMode = null
                    skipMenu = false
                    showMusic = false
                }
                fun navigateTab(tab: Int) {
                    if (selectedTab != tab && !settingUp && !restoring) {
                        pauseForNavigation()
                        tabHistory.add(selectedTab)
                        selectedTab = tab
                    }
                }
                BackHandler(enabled = tabHistory.isNotEmpty() || settingUp || restoring) {
                    if (!settingUp && !restoring) {
                        pauseForNavigation()
                        selectedTab = tabHistory.removeAt(tabHistory.lastIndex)
                    }
                }
                LaunchedEffect(simulation) {
                    try {
                        simulation.resumePending()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        clockError = e.message ?: e.javaClass.simpleName
                    } finally {
                        restoring = false
                    }
                }
                fun startGame(reset: Boolean) {
                    if (settingUp || restoring) return
                    pauseForNavigation()
                    settingUp = true
                    clockError = null
                    resultMessage = null
                    scope.launch {
                        try {
                            simulation.startGame(reset)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            clockError = e.message ?: e.javaClass.simpleName
                        } finally {
                            settingUp = false
                        }
                    }
                }
                LaunchedEffect(appForeground) {
                    if (!appForeground) skipMenu = false
                }
                LaunchedEffect(gameSpeed, jumpMode, appForeground, selectedTab, restoring, settingUp) {
                    val speed = gameSpeed
                    val mode = jumpMode
                    if (!appForeground || selectedTab != 0 || restoring || settingUp ||
                        (speed == 0 && mode == null)) return@LaunchedEffect
                    val job = currentCoroutineContext().job
                    progressionJob = job
                    try {
                        if (mode != null) {
                            val result = simulation.jump(mode, onProgress = { _, completed, served ->
                                progress = completed
                                groups = served
                            })
                            if (mode == GameSimulationEngine.JumpMode.NEXT_DAY) simulation.skipClosedHours()
                            resultMessage = result.description
                            if (mode == GameSimulationEngine.JumpMode.NEXT_WEEK) audit.saveDesktopReport()
                        } else {
                            simulation.skipClosedHours()
                            // Advance 15 game minutes per tick. x1 = 15s, x5 = 3s, x20 = 0.75s.
                            while (true) {
                                delay(15_000L / speed)
                                if (!appForeground || selectedTab != 0 || gameSpeed != speed || settingUp) break
                                simulation.advancePlayback()
                                clockError = null
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        clockError = e.message ?: e.javaClass.simpleName
                        gameSpeed = 0
                    } finally {
                        if (progressionJob === job) {
                            progressionJob = null
                            if (jumpMode == mode) jumpMode = null
                        }
                    }
                }
                val canControlTime = appForeground && !jumping && !restoring && !settingUp && time != null
                fun startJump(mode: GameSimulationEngine.JumpMode) {
                    if (!canControlTime) return
                    progressionJob?.cancel()
                    gameSpeed = 0
                    skipMenu = false
                    clockError = null
                    resultMessage = null
                    progress = 0
                    groups = 0
                    jumpMode = mode
                }
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        if (selectedTab == 0) Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(time?.let { "Day ${it.day} · %02d:%02d".format(it.hour, it.minute) } ?: "Maison Café",
                                    Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Box {
                                    TextButton(onClick = { showMusic = true },
                                        contentPadding = PaddingValues(horizontal = 4.dp)) {
                                        Text(if (musicEnabled) "Music ▾" else "Music off ▾")
                                    }
                                    DropdownMenu(expanded = showMusic, onDismissRequest = { showMusic = false }) {
                                        DropdownMenuItem(text = {
                                            Text(if (musicEnabled && selectedTrack == null) "✓  Playlist" else "Playlist")
                                        }, onClick = { selectMusic(true, null) })
                                        CafeMusicTrack.entries.forEach { track ->
                                            DropdownMenuItem(text = {
                                                Text(if (musicEnabled && selectedTrack == track) "✓  ${track.title}" else track.title)
                                            }, onClick = { selectMusic(true, track) })
                                        }
                                        DropdownMenuItem(text = {
                                            Text(if (musicEnabled) "Off" else "✓  Off")
                                        }, onClick = { selectMusic(false, selectedTrack) })
                                    }
                                }
                                Box {
                                    TextButton(enabled = canControlTime && gameSpeed == 0,
                                        onClick = { skipMenu = true }) { Text("Skip ▾") }
                                    DropdownMenu(expanded = skipMenu, onDismissRequest = { skipMenu = false }) {
                                        listOf(GameSimulationEngine.JumpMode.NEXT_EVENT to "Next event",
                                            GameSimulationEngine.JumpMode.NEXT_DECISION to "Next decision",
                                            GameSimulationEngine.JumpMode.NEXT_DAY to "Next day",
                                            GameSimulationEngine.JumpMode.NEXT_WEEK to "Next week").forEach { (mode, label) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                                startJump(mode)
                                            })
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0 to "Pause", 1 to "×1", 5 to "×5", 20 to "×20").forEach { (value, label) ->
                                    FilterChip(selected = gameSpeed == value, enabled = canControlTime,
                                        onClick = {
                                            if (gameSpeed != value) {
                                                progressionJob?.cancel()
                                                gameSpeed = value
                                            }
                                        }, label = { Text(label) })
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
                        CafeBottomNavigation(selectedTab, ::navigateTab)
                    }
                ) { padding ->
                    if (restoring || settingUp || jumping) {
                        val message = when {
                            restoring -> "Loading saved café…"
                            settingUp -> "Starting café…"
                            else -> "Simulating business…"
                        }
                        Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
                    } else when (selectedTab) {
                        0 -> CafeHomeScreen(
                            modifier = Modifier.padding(padding),
                            speed = gameSpeed,
                            time = time,
                            onStartGame = ::startGame,
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
