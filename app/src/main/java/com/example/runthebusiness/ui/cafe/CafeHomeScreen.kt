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
import com.example.runthebusiness.data.local.CafeCashManagementRepository
import com.example.runthebusiness.data.local.CafePaymentConsequencesRepository
import com.example.runthebusiness.data.local.CashPosition
import com.example.runthebusiness.data.local.PaymentConsequences
import com.example.runthebusiness.data.local.CafeCustomerFlowRepository
import com.example.runthebusiness.data.local.CafeCustomerFlow
import com.example.runthebusiness.data.local.CafeDashboardRepository
import com.example.runthebusiness.data.local.CafeDashboardSnapshot
import com.example.runthebusiness.data.local.CafeSaleRepository
import com.example.runthebusiness.data.local.CafeArrivalRepository
import com.example.runthebusiness.core.simulation.GameSimulationEngine
import com.example.runthebusiness.data.local.CafeDiagnosticsRepository
import com.example.runthebusiness.data.local.CafeDiagnostics
import com.example.runthebusiness.data.local.CafeMenuReadinessRepository
import com.example.runthebusiness.data.local.MenuReadiness
import com.example.runthebusiness.data.local.CafePurchasingRepository
import com.example.runthebusiness.data.local.CafeMenuRepository
import com.example.runthebusiness.data.local.CafeInventorySetupRepository
import com.example.runthebusiness.data.local.CafeExpandedMenuRepository
import com.example.runthebusiness.data.local.entity.CafeArrivalEntity
import com.example.runthebusiness.data.local.GameRepository
import com.example.runthebusiness.data.local.CafeDailyCloseRepository
import com.example.runthebusiness.data.local.CafeAuditExportRepository
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
    val flowRepo = remember(context) { CafeCustomerFlowRepository(context) }
    val cashRepo = remember(context) { CafeCashManagementRepository(context) }
    val consequencesRepo = remember(context) { CafePaymentConsequencesRepository(context) }
    val alertsRepo = remember(context) { CafeManagementAlertsRepository(context) }
    val performanceRepo = remember(context) { CafeDailyPerformanceRepository(context) }
    val breakEvenRepo = remember(context) { CafeBreakEvenRepository(context) }
    val auditExport = remember(context) { CafeAuditExportRepository(context) }
    val game = remember(context) { GameRepository(context) }
    val dailyClose = remember(context) { CafeDailyCloseRepository(context) }
    val sales = remember(context) { CafeSaleRepository(context) }
    val arrivals = remember(context) { CafeArrivalRepository(context) }
    val simulation = remember(context) { GameSimulationEngine(context) }
    val diagnosticsRepo = remember(context) { CafeDiagnosticsRepository(context) }
    val menuSetup = remember(context) { CafeMenuRepository(context) }
    val menuReadinessRepo = remember(context) { CafeMenuReadinessRepository(context) }
    val purchasingRepo = remember(context) { CafePurchasingRepository(context) }
    val stockSetup = remember(context) { CafeInventorySetupRepository(context) }
    val expandedMenu = remember(context) { CafeExpandedMenuRepository(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CafeDashboardSnapshot?>(null) }
    var recent by remember { mutableStateOf<List<CustomerOrderEntity>>(emptyList()) }
    var menuReadiness by remember { mutableStateOf<List<MenuReadiness>>(emptyList()) }
    var showMenuReadiness by remember { mutableStateOf(false) }
    var cashPosition by remember { mutableStateOf<CashPosition?>(null) }
    var paymentStatus by remember { mutableStateOf<PaymentConsequences?>(null) }
    var visitors by remember { mutableStateOf<List<CafeArrivalEntity>>(emptyList()) }
    var diagnostics by remember { mutableStateOf<CafeDiagnostics?>(null) }
    var loading by remember { mutableStateOf(false) }
    var weeklyProgress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var weeklyGroups by remember { mutableIntStateOf(0) }
    var lastClosedDay by remember { mutableIntStateOf(0) }
    var lastEventMessage by remember { mutableStateOf<String?>(null) }
    var alerts by remember { mutableStateOf<List<ManagementAlert>>(emptyList()) }
    var dailyPerformance by remember { mutableStateOf<List<CafeDayPerformance>>(emptyList()) }
    var showPerformance by remember { mutableStateOf(false) }
    var breakEven by remember { mutableStateOf<CafeBreakEven?>(null) }
    var showBreakEven by remember { mutableStateOf(false) }
    var customerFlow by remember { mutableStateOf<CafeCustomerFlow?>(null) }
    var showCustomerFlow by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmNewGame by remember { mutableStateOf(false) }

    suspend fun reload() {
        state = dashboard.snapshot()
        cashPosition = if (state != null) cashRepo.position() else null
        paymentStatus = if (state != null) consequencesRepo.load() else null
        recent = sales.loadDemoSales().take(6)
        menuReadiness = if (state != null) menuReadinessRepo.snapshot() else emptyList()
        visitors = arrivals.recent()
        diagnostics = diagnosticsRepo.inspect()
        lastClosedDay = dailyClose.lastClosedDay()
        alerts = if (state != null) alertsRepo.load() else emptyList()
        dailyPerformance = if (state != null) performanceRepo.recent() else emptyList()
        breakEven = if (state != null) breakEvenRepo.snapshot() else null
        customerFlow = if (state != null) flowRepo.lastCompletedDays() else null
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
            } finally { loading = false; weeklyProgress = null }
        }
    }
    LaunchedEffect(Unit) { run { simulation.resumePending() } }
    LaunchedEffect(clockRevision) {
        if (clockRevision > 0) reload()
    }

    if (confirmNewGame) {
        AlertDialog(
            onDismissRequest = { confirmNewGame = false },
            title = { Text("Start a new game?") },
            text = { Text("This permanently deletes your current café, purchases, stock, sales, journals and game progress. A new café will start at Day 1 with kitchen recipes installed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmNewGame = false
                    onSpeedChange(0)
                    run {
                        simulation.startFreshGame()
                        lastEventMessage = "New game started. Buy kitchen ingredients and enable dishes to test recipes."
                    }
                }) { Text("Delete save & start new") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewGame = false }) { Text("Cancel") }
            }
        )
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
            CafeScene(
                name = cafe.cafeName,
                day = cafe.time.day,
                hour = cafe.time.hour,
                minute = cafe.time.minute,
                isOpen = cafe.isOpen,
                tables = cafe.tables,
                seats = cafe.seats,
                activeGuests = visitors.filter {
                    it.status == "SERVED" &&
                    ((cafe.time.day - it.gameDay) * 1440L +
                        (cafe.time.hour - it.gameHour) * 60L +
                        cafe.time.minute - it.gameMinute) in 0L..59L
                }.sumOf { it.guests }.coerceAtMost(cafe.seats),
                readyDishes = menuReadiness.count { it.ready },
                totalDishes = menuReadiness.size,
                bankCents = cashPosition?.bankCents ?: 0L,
                cashCents = cashPosition?.cashCents ?: 0L,
                hasUrgentBills = paymentStatus?.rentShutdown == true ||
                    paymentStatus?.staffDisruption == true,
                modifier = Modifier.fillMaxWidth()
            )
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val ready = menuReadiness.count { it.ready }
                    Text("Menu readiness · $ready / ${menuReadiness.size} dishes",
                        style = MaterialTheme.typography.titleMedium)
                    Text("Availability is based on real recipe ingredients and current stock.",
                        style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { showMenuReadiness = !showMenuReadiness }) {
                        Text(if (showMenuReadiness) "Hide dishes" else "Inspect dishes & restock")
                    }
                    if (showMenuReadiness) {
                        menuReadiness.forEach { dish ->
                            HorizontalDivider()
                            Text("${dish.name} · ${if (dish.ready) "${dish.servings} servings" else "OUT OF STOCK"}",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (dish.ready) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.error)
                            if (dish.unsupported) Text("No supported recipe configured",
                                style = MaterialTheme.typography.bodySmall)
                            dish.missing.forEach { missing ->
                                Text("Missing ${missing.name}: ${missing.available.toInt()} / ${missing.required.toInt()} ${missing.unit}",
                                    style = MaterialTheme.typography.bodySmall)
                                if (missing.supplierPackCents != null) {
                                    OutlinedButton(
                                        enabled = !loading,
                                        onClick = { run { purchasingRepo.purchase(missing.itemId) } }
                                    ) {
                                        Text("Buy ${missing.name} · ${euro(missing.supplierPackCents)}")
                                    }
                                } else {
                                    Text("No supplier pack available",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            cashPosition?.let { position ->
                val payment = paymentStatus
                val overdue = payment?.rentDaysLate ?: 0
                val payrollLate = payment?.payrollDaysLate ?: 0
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Bills & continuity", style = MaterialTheme.typography.titleMedium)
                        Text("Bank: €%.2f · Cash: €%.2f".format(Locale.ROOT,
                            position.bankCents / 100.0, position.cashCents / 100.0))
                        Text("Rent payable: €%.2f · %d days late".format(Locale.ROOT,
                            position.rentPayableCents / 100.0, overdue))
                        Text("Payroll payable: €%.2f · %d days late".format(Locale.ROOT,
                            position.salariesPayableCents / 100.0, payrollLate))
                        if (payment?.rentShutdown == true) {
                            Text("Trading blocked by overdue rent. Pay rent to reopen.",
                                color = MaterialTheme.colorScheme.error)
                        } else if (overdue >= 5) {
                            Text("Rent shutdown starts after 7 days overdue.",
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (payment?.staffDisruption == true) {
                            Text("Late payroll is disrupting service.",
                                color = MaterialTheme.colorScheme.error)
                        } else if (payrollLate >= 1) {
                            Text("Payroll disruption starts after 2 days overdue.",
                                color = MaterialTheme.colorScheme.error)
                        }
                        if (position.bankCents < position.totalDueCents &&
                            position.cashCents > 0L) {
                            Text("Bank funds are insufficient for all bills. Deposit cash before paying.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (position.cashCents > 0L && position.totalDueCents > position.bankCents) {
                            OutlinedButton(enabled = !loading, onClick = { run {
                                val transfer = minOf(position.cashCents,
                                    (position.totalDueCents - position.bankCents).coerceAtLeast(0L))
                                if (transfer > 0L) cashRepo.transferCashToBank(transfer)
                            } }) { Text("Deposit cash needed for bills") }
                        }
                        if (position.rentPayableCents > 0L) {
                            Button(enabled = !loading && position.bankCents >= position.rentPayableCents,
                                onClick = { run { cashRepo.pay("2000") } }) {
                                Text("Pay rent · €%.2f".format(Locale.ROOT, position.rentPayableCents / 100.0))
                            }
                        }
                        if (position.salariesPayableCents > 0L) {
                            Button(enabled = !loading && position.bankCents >= position.salariesPayableCents,
                                onClick = { run { cashRepo.pay("2300") } }) {
                                Text("Pay payroll · €%.2f".format(Locale.ROOT, position.salariesPayableCents / 100.0))
                            }
                        }
                        Text("Bills are accrued in P&L already; settlement only reduces bank and payables.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
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
                        GameSimulationEngine.JumpMode.NEXT_WEEK to "Advance 7 days"
                    ).forEach { (mode, label) ->
                        OutlinedButton(
                            enabled = !loading && speed == 0,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                run {
                                    if (mode == GameSimulationEngine.JumpMode.NEXT_WEEK) {
                                        weeklyProgress = 0 to cafe.time.day
                                        weeklyGroups = 0
                                    }
                                    val result = simulation.jump(mode, onProgress = { day, completed, groups ->
                                        weeklyProgress = completed to day
                                        weeklyGroups = groups
                                    })
                                    lastEventMessage = result.description
                                    if (mode == GameSimulationEngine.JumpMode.NEXT_WEEK) {
                                        auditExport.saveDesktopReport()
                                        lastEventMessage += " · Diagnostic CSV ready for ADB transfer"
                                    }
                                }
                            }
                        ) { Text(label) }
                    }
                    weeklyProgress?.let { (completed, day) ->
                        Text("Simulating day $day · $completed / 7 days completed",
                            style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(
                            progress = { (completed / 7f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("$weeklyGroups customer groups served so far",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (speed != 0) Text("Pause the clock to jump.",
                        style = MaterialTheme.typography.bodySmall)
                    if (lastEventMessage != null) {
                        Text("Jump result: $lastEventMessage",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Seven-day advance continues through stock shortages and financial alerts. Their effects still apply; check management alerts and Finance after the run.",
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
                Text("Customer flow · last 14 days", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showCustomerFlow = !showCustomerFlow }) {
                    Text(if (showCustomerFlow) "Hide" else "View")
                }
            }
            if (showCustomerFlow) {
                customerFlow?.let { funnel ->
                    Text("Completed days: ${funnel.days}")
                    Text("Arriving groups: ${funnel.groups} · guests: ${funnel.guests}")
                    Text("Served groups: ${funnel.servedGroups} · guests: ${funnel.servedGuests}")
                    val days = funnel.days.coerceAtLeast(1)
                    Text("Arriving guests/day: %.1f".format(Locale.ROOT, funnel.guests.toDouble() / days))
                    Text("Served guests/day: %.1f".format(Locale.ROOT, funnel.servedGuests.toDouble() / days))
                    Text("Orders/day: %.1f".format(Locale.ROOT, funnel.orders.toDouble() / days))
                    Text("Average guests/order: %.2f".format(Locale.ROOT,
                        if (funnel.orders > 0) funnel.servedGuests.toDouble() / funnel.orders else 0.0))
                    Text("Average ticket (net): ${euro(if (funnel.orders > 0) funnel.netSalesCents / funnel.orders else 0L)}")
                    Text("Net revenue/served guest: ${euro(if (funnel.servedGuests > 0) funnel.netSalesCents / funnel.servedGuests else 0L)}")
                    Text("Items/order: %.2f".format(Locale.ROOT,
                        if (funnel.orders > 0) funnel.orderLines.toDouble() / funnel.orders else 0.0))
                    Text("Card: ${funnel.cardOrders} orders · ${euro(funnel.cardGrossCents)} gross")
                    Text("Cash: ${funnel.orders - funnel.cardOrders} orders · ${euro(funnel.cashGrossCents)} gross")
                    val conversion = if (funnel.groups > 0)
                        100.0 * funnel.servedGroups / funnel.groups else 0.0
                    Text("Group conversion: %.1f%%".format(Locale.ROOT, conversion))
                    funnel.byStatus.forEach { (status, count) ->
                        Text("$status: $count groups")
                    }
                    Text("Orders are group transactions; one order may include items for multiple guests. Items/order counts order lines, not quantities.",
                        style = MaterialTheme.typography.bodySmall)
                } ?: Text("No completed-day arrival data yet")
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
                    Text("Expired inventory (waste): ${euro(economics.wasteCents)}")
                    Text("Waste/day: ${euro(economics.wasteDailyCents)}")
                    val wasteRatio = if (economics.netSalesCents > 0)
                        economics.wasteCents.toDouble() * 100.0 / economics.netSalesCents else 0.0
                    Text("Waste as %% of net sales: %.1f%%".format(Locale.ROOT, wasteRatio))
                    Text("Contribution/order: €%.2f".format(Locale.ROOT, economics.avgContributionCents / 100.0))
                    Text("Daily rent: ${euro(economics.rentDailyCents)}")
                    Text("Daily payroll: ${euro(economics.payrollDailyCents)}")
                    Text("Daily depreciation: ${euro(economics.depreciationDailyCents)}")
                    Text("Total fixed cost/day: ${euro(economics.fixedDailyCents)}")
                    Text("Fixed cost + observed waste/day: ${euro(economics.fixedDailyCents + economics.wasteDailyCents)}")
                    if (economics.ordersNeededPerDay == null) {
                        Text("No break-even: observed contribution per order is non-positive.",
                            color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Break-even orders/day: ${economics.ordersNeededPerDay}")
                        economics.targetNetSalesDailyCents?.let {
                            Text("Indicative net revenue/day: ${euro(it)}")
                        }
                    }
                    Text("Diagnostic, not a forecast. Break-even includes observed average expiry waste as a daily cost, assuming it stays constant; excludes utilities, financing, taxes and unmodelled costs.",
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
            HorizontalDivider()
            Text("Game save", style = MaterialTheme.typography.titleMedium)
            Text("Start over to test a clean café. Your current progress will be permanently deleted.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirmNewGame = true },
                enabled = !loading && speed == 0) { Text("New game / Reset progress") }
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
