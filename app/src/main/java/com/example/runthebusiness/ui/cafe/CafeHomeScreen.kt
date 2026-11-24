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
import com.example.runthebusiness.data.local.CafeDiagnosticsRepository
import com.example.runthebusiness.data.local.CafeDiagnostics
import com.example.runthebusiness.data.local.CafeMenuRepository
import com.example.runthebusiness.data.local.CafeInventorySetupRepository
import com.example.runthebusiness.data.local.CafeMenuReadinessRepository
import com.example.runthebusiness.data.local.MenuReadiness
import com.example.runthebusiness.data.local.CafePurchasingRepository
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
import com.example.runthebusiness.engine.model.GameTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

private val EspressoBrown = Color(0xFF3B241A)
private val Honey = Color(0xFFDDAF72)
private val Cream = Color(0xFFFFF2D9)

@Composable
private fun CafeShiftSummary(
    shift: CafeDayPerformance,
    currentDay: Int,
    onOpenOperations: (Int) -> Unit,
    onOpenFinance: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (shift.day == currentDay) "Today's shift" else "Last shift · Day ${shift.day}",
                style = MaterialTheme.typography.titleMedium
            )
            Text("${shift.servedGroups} of ${shift.arrivingGroups} groups served · ${shift.lostGroups} lost")
            if (shift.pendingGroups > 0) Text("Awaiting service: ${shift.pendingGroups}")
            Text("Net sales ${euro(shift.netSalesCents)} · Gross margin ${euro(shift.grossMarginCents)}")
            if (shift.noStock > 0) {
                Text("Out of stock · ${shift.noStock}")
                TextButton(onClick = { onOpenOperations(1) }) { Text("Go to purchasing") }
            }
            if (shift.declinedPrice > 0) {
                Text("Left without buying · ${shift.declinedPrice}")
                TextButton(onClick = { onOpenOperations(0) }) { Text("Review menu prices") }
            }
            if (shift.noSeats > 0) Text("No seats available · ${shift.noSeats}")
            if (shift.staffCapacity > 0) Text("Staff at capacity · ${shift.staffCapacity}")
            if (shift.staffDisruption > 0) Text("Overdue payroll disruption · ${shift.staffDisruption}")
            if (shift.rentShutdown > 0) Text("Closed due to overdue rent · ${shift.rentShutdown}")
            if (shift.staffDisruption > 0 || shift.rentShutdown > 0) {
                TextButton(onClick = onOpenFinance) { Text("Review unpaid bills") }
            }
        }
    }
}

@Composable
fun CafeHomeScreen(
    modifier: Modifier = Modifier,
    speed: Int = 0,
    time: GameTime? = null,
    onStartGame: (Boolean) -> Unit = {},
    onOpenOperations: (Int) -> Unit = {},
    onOpenFinance: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val dashboard = remember(context) { CafeDashboardRepository(context) }
    val flowRepo = remember(context) { CafeCustomerFlowRepository(context) }
    val cashRepo = remember(context) { CafeCashManagementRepository(context) }
    val billsRepo = remember(context) { com.example.runthebusiness.data.local.CafePaymentRegisterRepository(context) }
    var bills by remember { mutableStateOf<List<com.example.runthebusiness.data.local.ScheduledBill>>(emptyList()) }
    val consequencesRepo = remember(context) { CafePaymentConsequencesRepository(context) }
    val alertsRepo = remember(context) { CafeManagementAlertsRepository(context) }
    val performanceRepo = remember(context) { CafeDailyPerformanceRepository(context) }
    val breakEvenRepo = remember(context) { CafeBreakEvenRepository(context) }
    val game = remember(context) { GameRepository(context) }
    val dailyClose = remember(context) { CafeDailyCloseRepository(context) }
    val sales = remember(context) { CafeSaleRepository(context) }
    val arrivals = remember(context) { CafeArrivalRepository(context) }
    val diagnosticsRepo = remember(context) { CafeDiagnosticsRepository(context) }
    val menuSetup = remember(context) { CafeMenuRepository(context) }
    val stockSetup = remember(context) { CafeInventorySetupRepository(context) }
    val menuReadinessRepo = remember(context) { CafeMenuReadinessRepository(context) }
    val purchasingRepo = remember(context) { CafePurchasingRepository(context) }
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
    var lastClosedDay by remember { mutableIntStateOf(0) }
    var alerts by remember { mutableStateOf<List<ManagementAlert>>(emptyList()) }
    var dailyPerformance by remember { mutableStateOf<List<CafeDayPerformance>>(emptyList()) }
    var showPerformance by remember { mutableStateOf(false) }
    var breakEven by remember { mutableStateOf<CafeBreakEven?>(null) }
    var showBreakEven by remember { mutableStateOf(false) }
    var customerFlow by remember { mutableStateOf<CafeCustomerFlow?>(null) }
    var showCustomerFlow by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDetails by remember { mutableStateOf(false) }
    var confirmNewGame by remember { mutableStateOf(false) }

    suspend fun reload() {
        state = dashboard.snapshot()
        cashPosition = if (state != null) cashRepo.position() else null
        bills = if (state != null) billsRepo.snapshot() else emptyList()
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally { loading = false }
        }
    }
    LaunchedEffect(time) {
        reload()
    }

    if (confirmNewGame) {
        AlertDialog(
            onDismissRequest = { confirmNewGame = false },
            title = { Text("Start a new game?") },
            text = { Text("This permanently deletes your current café, purchases, stock, sales, journals and game progress. A new café will start at 08:00 on Day 1 with kitchen recipes installed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmNewGame = false
                    onStartGame(true)
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
            Button(onClick = { onStartGame(false) }, enabled = !loading && time == null) {
                Text("Start café")
            }
        } else {
            CafeScene(
                name = cafe.cafeName,
                day = cafe.time.day,
                hour = cafe.time.hour,
                minute = cafe.time.minute,
                isOpen = cafe.isTrading,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (cafe.isOpen) "Scheduled hours · %02d:00–%02d:00".format(Locale.ROOT, cafe.openingHour, cafe.closingHour)
                    else "Scheduled opening is disabled.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { run { game.setCafeOpen(!cafe.isOpen) } }, enabled = !loading) {
                    Text(if (cafe.isOpen) "Close manually" else "Resume schedule")
                }
            }
            val shiftDay = if (cafe.time.hour < cafe.openingHour && cafe.time.day > 1) {
                cafe.time.day - 1
            } else {
                cafe.time.day
            }
            dailyPerformance.firstOrNull { it.day == shiftDay }?.let { shift ->
                CafeShiftSummary(
                    shift = shift,
                    currentDay = cafe.time.day,
                    onOpenOperations = onOpenOperations,
                    onOpenFinance = onOpenFinance
                )
            }
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Payments · ${euro(bills.sumOf { it.due })} due")
                        Text("Bank · ${euro(position.bankCents)}")
                        if (paymentStatus?.staffDisruption == true || paymentStatus?.rentShutdown == true)
                            Text("Overdue bills affecting service", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onOpenFinance, enabled = !loading) { Text("Manage payments") }
                    }
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
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(if (showDetails) "Hide reports & settings" else "Reports & settings")
            }
            if (showDetails) {
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
                        day.staffCapacity.takeIf { it > 0 }?.let { "staff capacity $it" },
                        day.staffDisruption.takeIf { it > 0 }?.let { "staff $it" },
                        day.rentShutdown.takeIf { it > 0 }?.let { "rent $it" }
                    )
                    if (reasons.isNotEmpty()) Text("Lost: ${reasons.joinToString(" · ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                }
            }
            if (visitors.isNotEmpty()) {
                Text("Recent arrivals", style = MaterialTheme.typography.titleMedium)
                visitors.take(5).forEach { visitor ->
                    Text("Day ${visitor.gameDay} • %02d:%02d — ${visitor.guests} guests arrived".format(
                        Locale.ROOT, visitor.gameHour, visitor.gameMinute
                    ), style = MaterialTheme.typography.bodyMedium)
                }
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
            Text("Sales totals include all recorded days.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
