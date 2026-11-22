package com.example.runthebusiness.ui.cafe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.data.local.CafeDashboardRepository
import com.example.runthebusiness.data.local.CafeDashboardSnapshot
import com.example.runthebusiness.data.local.CafeSaleRepository
import com.example.runthebusiness.data.local.CafeArrivalRepository
import com.example.runthebusiness.core.simulation.GameSimulationEngine
import com.example.runthebusiness.data.local.CafeDiagnosticsRepository
import com.example.runthebusiness.data.local.CafeDiagnostics
import com.example.runthebusiness.data.local.CafeMenuRepository
import com.example.runthebusiness.data.local.CafeInventorySetupRepository
import com.example.runthebusiness.data.local.entity.CafeArrivalEntity
import com.example.runthebusiness.data.local.GameRepository
import com.example.runthebusiness.data.local.entity.CustomerOrderEntity
import kotlinx.coroutines.launch
import java.util.Locale

private val EspressoBrown = Color(0xFF3B241A)
private val Honey = Color(0xFFDDAF72)
private val Cream = Color(0xFFFFF2D9)

@Composable
fun CafeHomeScreen(
    modifier: Modifier = Modifier,
    speed: Int = 0,
    onSpeedChange: (Int) -> Unit = {},
    clockRevision: Int = 0,
    clockError: String? = null
) {
    val context = LocalContext.current.applicationContext
    val dashboard = remember(context) { CafeDashboardRepository(context) }
    val game = remember(context) { GameRepository(context) }
    val sales = remember(context) { CafeSaleRepository(context) }
    val arrivals = remember(context) { CafeArrivalRepository(context) }
    val simulation = remember(context) { GameSimulationEngine(context) }
    val diagnosticsRepo = remember(context) { CafeDiagnosticsRepository(context) }
    val menuSetup = remember(context) { CafeMenuRepository(context) }
    val stockSetup = remember(context) { CafeInventorySetupRepository(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CafeDashboardSnapshot?>(null) }
    var recent by remember { mutableStateOf<List<CustomerOrderEntity>>(emptyList()) }
    var visitors by remember { mutableStateOf<List<CafeArrivalEntity>>(emptyList()) }
    var diagnostics by remember { mutableStateOf<CafeDiagnostics?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        state = dashboard.snapshot()
        recent = sales.loadDemoSales().take(6)
        visitors = arrivals.recent()
        diagnostics = diagnosticsRepo.inspect()
    }
    fun run(action: suspend () -> Unit) {
        scope.launch {
            loading = true
            error = null
            try {
                action()
                reload()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { run { simulation.resumePending() } }
    LaunchedEffect(clockRevision) {
        if (clockRevision > 0) reload()
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        val cafe = state
        if (cafe == null) {
            Text("Welcome to Maison Café", style = MaterialTheme.typography.headlineMedium)
            Text("Start your business to create the first café and its opening balance sheet.")
            Button(onClick = { run { game.initializeOpeningGame() } }, enabled = !loading) {
                Text("Start café")
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(EspressoBrown, Color(0xFF815338))),
                    RoundedCornerShape(20.dp)
                ).padding(22.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MAISON CAFÉ", color = Honey, style = MaterialTheme.typography.labelLarge)
                    Text(cafe.cafeName, color = Cream, style = MaterialTheme.typography.headlineMedium)
                    Text("Day ${cafe.time.day}  •  %02d:%02d".format(
                        Locale.ROOT, cafe.time.hour, cafe.time.minute
                    ), color = Cream)
                    Spacer(Modifier.height(8.dp))
                    Text("☕     ◯  ◯  ◯", color = Honey, style = MaterialTheme.typography.headlineLarge)
                    Text("${cafe.tables} tables  •  ${cafe.seats} seats",
                        color = Cream, style = MaterialTheme.typography.bodyMedium)
                    Text("Your café is ready for the next business day.",
                        color = Color(0xFFE6D3B9), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Café status: ${if (cafe.isOpen) "OPEN" else "CLOSED"} • Hours %02d:00–%02d:00".format(
                Locale.ROOT, cafe.openingHour, cafe.closingHour
            ), style = MaterialTheme.typography.titleMedium,
                color = if (cafe.isOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            if (!cafe.isOpen) {
                Text("The café is closed. No visitors will arrive until you open it.",
                    color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(
                onClick = { run { game.setCafeOpen(!cafe.isOpen) } },
                enabled = !loading
            ) { Text(if (cafe.isOpen) "Close café" else "Open café") }
            Text("Game clock", style = MaterialTheme.typography.titleMedium)
            if (clockError != null) {
                Text("Automatic clock paused: $clockError",
                    color = MaterialTheme.colorScheme.error)
            }
            Text("Automatic time • 1 real second = 1 / 5 / 20 game minutes",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "Pause", 1 to "×1", 5 to "×5", 20 to "×20").forEach { (value, label) ->
                    FilterChip(
                        selected = speed == value,
                        onClick = { onSpeedChange(value) },
                        label = { Text(label) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run { simulation.advance(15L) } }, enabled = !loading) {
                    Text("+15 min")
                }
                Button(onClick = { run { simulation.advance(60L) } }, enabled = !loading) {
                    Text("+1 hour")
                }
                OutlinedButton(onClick = { run { } }, enabled = !loading) { Text("Refresh") }
            }
            Text("Business at a glance", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryTile("Sales", euro(cafe.grossSalesCents), Modifier.weight(1f))
                SummaryTile("Gross profit", euro(cafe.grossProfitCents), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryTile("Orders", "${cafe.salesCount}", Modifier.weight(1f))
                SummaryTile("Output IVA", euro(cafe.outputVatCents), Modifier.weight(1f))
            }
            Text("Visitor arrivals", style = MaterialTheme.typography.titleMedium)
            val todayVisitors = visitors.filter { it.gameDay == cafe.time.day }
            Text("${todayVisitors.sumOf { it.guests }} visitors in ${todayVisitors.size} arrival events today")
            if (visitors.isEmpty()) {
                Text("Advance the clock during opening hours to generate visitors.")
            } else {
                visitors.take(5).forEach { visitor ->
                    Text("Day ${visitor.gameDay} • %02d:%02d — ${visitor.guests} guests arrived".format(
                        Locale.ROOT, visitor.gameHour, visitor.gameMinute
                    ), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Served groups: ${todayVisitors.count { it.status == "SERVED" }} • Turned away: ${todayVisitors.count { it.status == "NO_SEATS" || it.status == "NO_STOCK" }}",
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Café setup", style = MaterialTheme.typography.titleMedium)
            val report = diagnostics
            if (report != null) {
                if (!report.realIngredientsInstalled) {
                    Text("Real ingredients are not installed. Sales may still use legacy placeholder recipes.",
                        color = MaterialTheme.colorScheme.error)
                    if (report.menuCount > 0) {
                        OutlinedButton(onClick = { run { stockSetup.initializeRealStock() } }, enabled = !loading) {
                            Text("Install real ingredients")
                        }
                    }
                }
                if (report.menuCount == 0) {
                    Text("No menu: visitors cannot order.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { run { menuSetup.seedMenu() } }, enabled = !loading) {
                        Text("Create starter menu")
                    }
                }
            }
            HorizontalDivider()
            Text("Recent sales", style = MaterialTheme.typography.titleMedium)
            if (recent.isEmpty()) {
                Text("No sales yet. Start the clock during opening hours.")
            } else {
                recent.forEach { order ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Day ${order.gameDay} • %02d:%02d".format(
                                    Locale.ROOT, order.gameHour, order.gameMinute
                                ), fontWeight = FontWeight.Medium)
                                Text("COGS ${euro(order.cogsCents)}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text(euro(order.grossCents), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text("Sales totals include all recorded days. Time now serves eligible visitor groups; staff queues and recurring costs are not yet simulated.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun euro(cents: Long): String = "€%.2f".format(Locale.ROOT, cents / 100.0)
