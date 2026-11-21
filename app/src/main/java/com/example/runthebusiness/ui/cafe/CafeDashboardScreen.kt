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
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CafeDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dashboard = remember(context) { CafeDashboardRepository(context.applicationContext) }
    val game = remember(context) { GameRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<CafeDashboardSnapshot?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            busy = true
            error = null
            try {
                snapshot = dashboard.snapshot()
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
            Text("No café initialized. Open Database Inspector and tap Seed opening entries.")
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
                            game.advanceGameTime(60L)
                            snapshot = dashboard.snapshot()
                        } catch (e: Exception) {
                            error = e.message ?: e.javaClass.simpleName
                        } finally { busy = false }
                    }
                }, enabled = !busy) { Text("Advance 1 hour") }
                OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
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
            Text("Placeholder inventory", style = MaterialTheme.typography.titleMedium)
            Text("%.3f / %.3f units remaining".format(
                Locale.ROOT, state.stockRemaining, state.stockOriginal
            ))
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
