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
import com.example.runthebusiness.data.local.CafeExpandedMenuRepository
import com.example.runthebusiness.data.local.entity.CafeArrivalEntity
import com.example.runthebusiness.data.local.GameRepository
import com.example.runthebusiness.data.local.CafeDailyCloseRepository
import com.example.runthebusiness.data.local.CafeBreakEvenRepository
import com.example.runthebusiness.data.local.CafeBreakEven
import com.example.runthebusiness.data.local.CafeDailyPerformanceRepository
import com.example.runthebusiness.data.local.CafeDayPerformance
import com.example.runthebusiness.data.local.CafeManagementAlertsRepository
import com.example.runthebusiness.data.local.ManagementAlert
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
    clockError: String? = null,
    onOpenOperations: (Int) -> Unit = {},
    onOpenFinance: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val dashboard = remember(context) { CafeDashboardRepository(context) }
    val alertsRepo = remember(context) { CafeManagementAlertsRepository(context) }
    val performanceRepo = remember(context) { CafeDailyPerformanceRepository(context) }
    val breakEvenRepo = remember(context) { CafeBreakEvenRepository(context) }
    val game = remember(context) { GameRepository(context) }
    val dailyClose = remember(context) { CafeDailyCloseRepository(context) }
    val sales = remember(context) { CafeSaleRepository(context) }
    val arrivals = remember(context) { CafeArrivalRepository(context) }
    val simulation = remember(context) { GameSimulationEngine(context) }
    val diagnosticsRepo = remember(context) { CafeDiagnosticsRepository(context) }
    val menuSetup = remember(context) { CafeMenuRepository(context) }
    val stockSetup = remember(context) { CafeInventorySetupRepository(context) }
    val expandedMenu = remember(context) { CafeExpandedMenuRepository(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CafeDashboardSnapshot?>(null) }
    var recent by remember { mutableStateOf<List<CustomerOrderEntity>>(emptyList()) }
    var visitors by remember { mutableStateOf<List<CafeArrivalEntity>>(emptyList()) }
    var diagnostics by remember { mutableStateOf<CafeDiagnostics?>(null) }
    var loading by remember { mutableStateOf(false) }
    var lastClosedDay by remember { mutableIntStateOf(0) }
    var lastEventMessage by remember { mutableStateOf<String?>(null) }
    var alerts by remember { mutableStateOf<List<ManagementAlert>>(emptyList()) }
    var dailyPerformance by remember { mutableStateOf<List<CafeDayPerformance>>(emptyList()) }
    var showPerformance by remember { mutableStateOf(false) }
    var breakEven by remember { mutableStateOf<CafeBreakEven?>(null) }
    var showBreakEven by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        state = dashboard.snapshot()
        recent = sales.loadDemoSales().take(6)
        visitors = arrivals.recent()
        diagnostics = diagnosticsRepo.inspect()
        lastClosedDay = dailyClose.lastClosedDay()
        alerts = if (state != null) alertsRepo.load() else emptyList()
        dailyPerformance = if (state != null) performanceRepo.recent() else emptyList()
        breakEven = if (state != null) breakEvenRepo.snapshot() else null
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
            Button(onClick = { run {
                game.initializeOpeningGame()
                menuSetup.seedMenu()
                stockSetup.initializeRealStock()
                expandedMenu.install()
            } }, enabled = !loading) {
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
            HorizontalDivider()
            Text("Daily close", style = MaterialTheme.typography.titleMedium)
            Text("Last financially closed day: ${if (lastClosedDay == 0) "none" else "Day $lastClosedDay"}",
                style = MaterialTheme.typography.bodyMedium)
            if (cafe.time.hour >= cafe.closingHour) {
                Text("Trading hours ended. Finish the day to post rent, salaries and depreciation.",
                    style = MaterialTheme.typography.bodySmall)
                Button(
                    enabled = !loading && speed == 0,
                    onClick = {
                        run {
                            val remaining = (24 - cafe.time.hour) * 60L - cafe.time.minute
                            simulation.advance(remaining)
                        }
                    }
                ) { Text("Finish day & post expenses") }
                if (speed != 0) Text("Pause the clock to finish the day manually.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Day ${cafe.time.day} in progress. Closing becomes available after ${cafe.closingHour}:00.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Time navigation", style = MaterialTheme.typography.titleMedium)
                    Text("All jumps simulate intermediate orders, stock usage, journal entries and daily closes.",
                        style = MaterialTheme.typography.bodySmall)
                    listOf(
                        GameSimulationEngine.JumpMode.NEXT_EVENT to "Next routine event",
                        GameSimulationEngine.JumpMode.NEXT_DECISION to "Next management decision",
                        GameSimulationEngine.JumpMode.NEXT_DAY to "Finish current day",
                        GameSimulationEngine.JumpMode.NEXT_WEEK to "+7 game days"
                    ).forEach { (mode, label) ->
                        OutlinedButton(
                            enabled = !loading && speed == 0,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                run {
                                    val result = simulation.jump(mode)
                                    lastEventMessage = result.description
                                }
                            }
                        ) { Text(label) }
                    }
                    if (speed != 0) Text("Pause the clock to jump.",
                        style = MaterialTheme.typography.bodySmall)
                    if (lastEventMessage != null) {
                        Text("Jump result: $lastEventMessage",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Weekly jump stops early on a newly arising critical alert. Routine events and ordinary reminders are processed along the way.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("Management decisions", style = MaterialTheme.typography.titleMedium)
            if (alerts.isEmpty()) {
                Text("No immediate management alerts.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                alerts.forEach { alert ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(alert.title, style = MaterialTheme.typography.titleSmall,
                                color = if (alert.priority == 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface)
                            Text(alert.detail, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = {
                                if (alert.destination == "operations") onOpenOperations(alert.section)
                                else onOpenFinance()
                            }) { Text(if (alert.destination == "operations") "Open purchasing" else "Open finance") }
                        }
                    }
                }
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
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Break-even diagnostics", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showBreakEven = !showBreakEven }) {
                    Text(if (showBreakEven) "Hide" else "View")
                }
            }
            if (showBreakEven) {
                val economics = breakEven
                if (economics == null || economics.observedDays == 0 || economics.orders == 0) {
                    Text("Insufficient completed-day sales to estimate contribution per order.",
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Last ${economics.observedDays} completed days · ${economics.orders} orders")
                    Text("Actual orders/day: %.1f".format(Locale.ROOT, economics.actualOrdersPerDay))
                    Text("Net sales (excl. IVA): ${euro(economics.netSalesCents)}")
                    Text("COGS: ${euro(economics.cogsCents)}")
                    Text("Contribution/order: €%.2f".format(Locale.ROOT, economics.avgContributionCents / 100.0))
                    Text("Daily rent: ${euro(economics.rentDailyCents)}")
                    Text("Daily payroll: ${euro(economics.payrollDailyCents)}")
                    Text("Daily depreciation: ${euro(economics.depreciationDailyCents)}")
                    Text("Total fixed cost/day: ${euro(economics.fixedDailyCents)}")
                    if (economics.ordersNeededPerDay == null) {
                        Text("No break-even: observed contribution per order is non-positive.",
                            color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Break-even orders/day: ${economics.ordersNeededPerDay}")
                        economics.targetNetSalesDailyCents?.let {
                            Text("Indicative net revenue/day: ${euro(it)}")
                        }
                    }
                    Text("Diagnostic, not a forecast. Based on observed order mix and current rent, payroll and depreciation; excludes utilities, financing, taxes and unmodelled costs.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Daily performance", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showPerformance = !showPerformance }) {
                    Text(if (showPerformance) "Hide" else "Last 14 days")
                }
            }
            if (showPerformance) {
                Text("Revenue is gross sales, not profit. Lost groups are observed arrivals that were not served; hypothetical lost revenue is not estimated.",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth()) {
                    Text("Day", Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall)
                    Text("Served / arrived", Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall)
                    Text("Lost", Modifier.weight(0.6f), style = MaterialTheme.typography.labelSmall)
                    Text("Revenue", Modifier.weight(1.2f), style = MaterialTheme.typography.labelSmall)
                }
                dailyPerformance.forEach { day ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${day.day}", Modifier.weight(0.6f))
                        Text("${day.servedGroups} / ${day.arrivingGroups}", Modifier.weight(1.4f))
                        Text("${day.lostGroups}", Modifier.weight(0.6f))
                        Text(euro(day.revenueCents), Modifier.weight(1.2f))
                    }
                    val reasons = listOfNotNull(
                        day.noStock.takeIf { it > 0 }?.let { "stock $it" },
                        day.noSeats.takeIf { it > 0 }?.let { "seats $it" },
                        day.declinedPrice.takeIf { it > 0 }?.let { "no purchase $it" },
                        day.staffDisruption.takeIf { it > 0 }?.let { "staff $it" },
                        day.rentShutdown.takeIf { it > 0 }?.let { "rent $it" }
                    )
                    if (reasons.isNotEmpty()) Text("Lost: ${reasons.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                }
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
            Text("Served groups: ${todayVisitors.count { it.status == "SERVED" }} • Turned away: ${todayVisitors.count { it.status == "NO_SEATS" || it.status == "NO_STOCK" || it.status == "STAFF_DISRUPTION" || it.status == "RENT_SHUTDOWN" }}",
                style = MaterialTheme.typography.bodySmall)
            val staffLost = todayVisitors.count { it.status == "STAFF_DISRUPTION" }
            val rentLost = todayVisitors.count { it.status == "RENT_SHUTDOWN" }
            if (staffLost + rentLost > 0) {
                Text("Lost groups today: $staffLost from unpaid payroll · $rentLost from rent suspension",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
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
