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
import com.example.runthebusiness.core.simulation.GameSimulationEngine
import com.example.runthebusiness.data.local.CafeMenuRepository
import com.example.runthebusiness.data.local.CafeMenuItem
import com.example.runthebusiness.data.local.CafeSaleRepository
import com.example.runthebusiness.data.local.CafeInventorySetupRepository
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CafeDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dashboard = remember(context) { CafeDashboardRepository(context.applicationContext) }
    val game = remember(context) { GameRepository(context.applicationContext) }
    val simulation = remember(context) { GameSimulationEngine(context.applicationContext) }
    val menuRepository = remember(context) { CafeMenuRepository(context.applicationContext) }
    val salesRepository = remember(context) { CafeSaleRepository(context.applicationContext) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            simulation.advance(60L)
                            snapshot = dashboard.snapshot()
                        } catch (e: Exception) {
                            error = e.message ?: e.javaClass.simpleName
                        } finally { busy = false }
                    }
                }, enabled = !busy) { Text("Advance 1 hour") }
                OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
            }
            HorizontalDivider()
            Text("Menu", style = MaterialTheme.typography.titleMedium)
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
                            Button(onClick = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    try {
                                        salesRepository.sellMenuItem(product.id)
                                        snapshot = dashboard.snapshot()
                                        menuItems = menuRepository.loadMenu()
                                    } catch (e: Exception) {
                                        error = e.message ?: e.javaClass.simpleName
                                    } finally { busy = false }
                                }
                            }, enabled = !busy) { Text("Sell 1") }
                        }
                    }
                }
                Text("Set up ingredients once before selling. Croissants are supplier-delivered finished goods.",
                    style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
            Text("Café capacity", style = MaterialTheme.typography.titleMedium)
            Text("${state.tables} tables · ${state.seats} seats")
            HorizontalDivider()
            Text("Recorded sales · all game days", style = MaterialTheme.typography.titleMedium)
            Text("Orders: ${state.salesCount}")
            MetricRow("Gross sales", state.grossSalesCents)
            MetricRow("Net revenue", state.netSalesCents)
            MetricRow("Output IVA", state.outputVatCents)
            MetricRow("Cost of goods sold", state.cogsCents)
            MetricRow("Gross profit", state.grossProfitCents)
            HorizontalDivider()
            Text("Inventory", style = MaterialTheme.typography.titleMedium)
            Text("Beans (g), milk (ml) and croissants (pcs) are tracked separately in Database. Mixed stock units must not be summed.")
            Text("Sales shown here are currently diagnostic transactions. No customer arrival or real menu simulation yet.",
                style = MaterialTheme.typography.bodySmall)
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
