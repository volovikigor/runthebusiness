package com.example.runthebusiness.ui.cafe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.data.local.CafeDashboardRepository
import com.example.runthebusiness.data.local.CafeDashboardSnapshot
import com.example.runthebusiness.data.local.GameRepository
import com.example.runthebusiness.data.local.CafeMenuRepository
import com.example.runthebusiness.data.local.CafeMenuItem
import com.example.runthebusiness.data.local.CafeInventorySetupRepository
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CafeDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dashboard = remember(context) { CafeDashboardRepository(context.applicationContext) }
    val game = remember(context) { GameRepository(context.applicationContext) }
    val menuRepository = remember(context) { CafeMenuRepository(context.applicationContext) }
    val inventorySetup = remember(context) { CafeInventorySetupRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var menuItems by remember { mutableStateOf<List<CafeMenuItem>>(emptyList()) }
    var snapshot by remember { mutableStateOf<CafeDashboardSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            busy = true
            error = null
            try {
                snapshot = dashboard.snapshot()
                menuItems = menuRepository.loadMenu()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Maison Café · Operations", style = MaterialTheme.typography.headlineSmall)
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        val state = snapshot
        if (state == null) {
            Text("No café initialized yet.")
            Button(onClick = {
                scope.launch {
                    busy = true
                    error = null
                    try {
                        game.initializeOpeningGame()
                        snapshot = dashboard.snapshot()
                        menuItems = menuRepository.loadMenu()
                    } catch (e: Exception) {
                        error = e.message ?: e.javaClass.simpleName
                    } finally { busy = false }
                }
            }, enabled = !busy) { Text("Start café") }
            OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
        } else {
            Text(state.cafeName, style = MaterialTheme.typography.titleLarge)
            Text("Day ${state.time.day} · %02d:%02d".format(
                Locale.ROOT, state.time.hour, state.time.minute
            ))
            HorizontalDivider()
            Text("Menu pricing", style = MaterialTheme.typography.titleMedium)
            Text("Higher prices may reduce purchases. Adjust by €0.10; sales use the latest saved price.", style = MaterialTheme.typography.bodySmall)
            if (menuItems.isNotEmpty()) {
                OutlinedButton(onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            inventorySetup.initializeRealStock()
                            snapshot = dashboard.snapshot()
                            menuItems = menuRepository.loadMenu()
                        } catch (e: Exception) {
                            error = e.message ?: e.javaClass.simpleName
                        } finally { busy = false }
                    }
                }, enabled = !busy) { Text("Set up ingredients and supplier croissants") }
            }
            if (menuItems.isEmpty()) {
                Text("No menu yet. Set up the starter menu to begin serving products.")
                Button(onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            menuRepository.seedMenu()
                            menuItems = menuRepository.loadMenu()
                        } catch (e: Exception) {
                            error = e.message ?: e.javaClass.simpleName
                        } finally { busy = false }
                    }
                }, enabled = !busy) { Text("Create starter menu") }
            } else {
                menuItems.forEach { product ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text("€%.2f · %d min".format(
                                    Locale.ROOT, product.priceCents / 100.0, product.preparationMinutes
                                ))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            try {
                                                menuRepository.updatePrice(product.id, (product.priceCents - 10L).coerceAtLeast(50L))
                                                menuItems = menuRepository.loadMenu()
                                            } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
                                            finally { busy = false }
                                        }
                                    },
                                    enabled = !busy && product.priceCents > 50L
                                ) { Text("− €0.10") }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            try {
                                                menuRepository.updatePrice(product.id, (product.priceCents + 10L).coerceAtMost(5000L))
                                                menuItems = menuRepository.loadMenu()
                                            } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
                                            finally { busy = false }
                                        }
                                    },
                                    enabled = !busy && product.priceCents < 5000L
                                ) { Text("+ €0.10") }
                            }
                        }
                    }
                }
                Text("Set up ingredients once before selling. Croissants are supplier-delivered finished goods.",
                    style = MaterialTheme.typography.bodySmall)
            }

        }
    }
}

@Composable
private fun MetricRow(label: String, cents: Long) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text("€%.2f".format(Locale.ROOT, cents / 100.0))
    }
}
