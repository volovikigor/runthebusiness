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
import com.example.runthebusiness.data.local.CafeDashboardRepository
import com.example.runthebusiness.data.local.CafeDashboardSnapshot
import com.example.runthebusiness.data.local.CafeArrivalRepository
import com.example.runthebusiness.data.local.CafeMenuReadinessRepository
import com.example.runthebusiness.data.local.MenuReadiness
import com.example.runthebusiness.data.local.stockShortages
import com.example.runthebusiness.data.local.CafePurchasingRepository
import com.example.runthebusiness.data.local.entity.CafeArrivalEntity
import com.example.runthebusiness.data.local.GameRepository
import com.example.runthebusiness.data.local.CafeDailyPerformanceRepository
import com.example.runthebusiness.data.local.CafeDayPerformance
import com.example.runthebusiness.data.local.CafeManagementAlertsRepository
import com.example.runthebusiness.data.local.ManagementAlert
import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.ui.components.CafeLedgerPanel
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
    CafeLedgerPanel {
        Text(
            if (shift.day == currentDay) "Today's shift" else "Last shift · Day ${shift.day}",
            style = MaterialTheme.typography.titleMedium
        )
        Text("${shift.servedGroups} of ${shift.arrivingGroups} groups served · ${shift.lostGroups} lost")
        if (shift.pendingGroups > 0) Text("Awaiting service: ${shift.pendingGroups}")
        Text("Net sales ${euro(shift.netSalesCents)} · Gross margin ${euro(shift.grossMarginCents)}")
        if (shift.noStock > 0) {
            Text("Groups lost to stock shortages · ${shift.noStock}")
            Button(onClick = { onOpenOperations(1) }) { Text("Go to purchasing") }
        }
        if (shift.declinedPrice > 0) {
            Text("Left without buying · ${shift.declinedPrice}")
            OutlinedButton(onClick = { onOpenOperations(0) }) { Text("Review menu prices") }
        }
        if (shift.noSeats > 0) Text("No seats available · ${shift.noSeats}")
        if (shift.staffCapacity > 0) Text("Staff at capacity · ${shift.staffCapacity}")
        if (shift.staffDisruption > 0) Text("Overdue payroll disruption · ${shift.staffDisruption}")
        if (shift.rentShutdown > 0) Text("Closed due to overdue rent · ${shift.rentShutdown}")
        if (shift.staffDisruption > 0 || shift.rentShutdown > 0) {
            Button(onClick = onOpenFinance) { Text("Review unpaid bills") }
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
    val cashRepo = remember(context) { CafeCashManagementRepository(context) }
    val billsRepo = remember(context) { com.example.runthebusiness.data.local.CafePaymentRegisterRepository(context) }
    var supplierDueCents by remember { mutableLongStateOf(0L) }
    var bills by remember { mutableStateOf<List<com.example.runthebusiness.data.local.ScheduledBill>>(emptyList()) }
    val consequencesRepo = remember(context) { CafePaymentConsequencesRepository(context) }
    val alertsRepo = remember(context) { CafeManagementAlertsRepository(context) }
    val performanceRepo = remember(context) { CafeDailyPerformanceRepository(context) }
    val game = remember(context) { GameRepository(context) }
    val arrivals = remember(context) { CafeArrivalRepository(context) }
    val menuReadinessRepo = remember(context) { CafeMenuReadinessRepository(context) }
    val purchasingRepo = remember(context) { CafePurchasingRepository(context) }
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CafeDashboardSnapshot?>(null) }
    var menuReadiness by remember { mutableStateOf<List<MenuReadiness>>(emptyList()) }
    var showMenuReadiness by remember { mutableStateOf(false) }
    var cashPosition by remember { mutableStateOf<CashPosition?>(null) }
    var paymentStatus by remember { mutableStateOf<PaymentConsequences?>(null) }
    var visitors by remember { mutableStateOf<List<CafeArrivalEntity>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var alerts by remember { mutableStateOf<List<ManagementAlert>>(emptyList()) }
    var dailyPerformance by remember { mutableStateOf<List<CafeDayPerformance>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var confirmNewGame by remember { mutableStateOf(false) }

    suspend fun reload() {
        state = dashboard.snapshot()
        cashPosition = if (state != null) cashRepo.position() else null
        bills = if (state != null) billsRepo.snapshot() else emptyList()
        supplierDueCents = state?.time?.day?.let { day ->
            billsRepo.supplierBills().filter { it.dueDay <= day }.sumOf { it.amountCents }
        } ?: 0L
        paymentStatus = if (state != null) consequencesRepo.load() else null
        menuReadiness = if (state != null) menuReadinessRepo.snapshot() else emptyList()
        visitors = arrivals.recent()
        alerts = if (state != null) alertsRepo.load() else emptyList()
        dailyPerformance = if (state != null) performanceRepo.recent() else emptyList()
    }
    fun run(action: suspend () -> Unit) {
        if (loading) return
        loading = true
        scope.launch {
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
            val shortages = remember(menuReadiness) { menuReadiness.stockShortages() }
            CafeLedgerPanel {
                val ready = menuReadiness.count { it.ready }
                Text("Menu readiness · $ready / ${menuReadiness.size} dishes",
                    style = MaterialTheme.typography.titleMedium)
                val recentStockLosses = dailyPerformance
                    .filter { it.day in (cafe.time.day - 7) until cafe.time.day }
                    .sumOf { it.noStock }
                if (recentStockLosses > 0) {
                    Text("Groups lost to stock shortages in the last 7 completed days · $recentStockLosses",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (shortages.isNotEmpty()) {
                    Text("Ingredients blocking the menu", style = MaterialTheme.typography.titleSmall)
                    val visible = if (showMenuReadiness) shortages else shortages.take(3)
                    visible.forEach { shortage ->
                        val supply = shortage.supply
                        HorizontalDivider()
                        Text("${supply.name} · ${shortage.dishes.size} affected",
                            style = MaterialTheme.typography.titleSmall)
                        Text(shortage.dishes.joinToString { it.name },
                            style = MaterialTheme.typography.bodySmall)
                        val cost = supply.supplierPackCents
                        if (cost != null) {
                            val affordable = cashPosition?.let {
                                it.cashCents >= cost || it.bankCents >= cost
                            } == true
                            OutlinedButton(
                                enabled = !loading && affordable,
                                onClick = { run { purchasingRepo.purchase(supply.itemId) } }
                            ) {
                                Text("Buy ${supply.name} · ${euro(cost)}")
                            }
                            if (cashPosition != null && !affordable) {
                                Text("Insufficient cash or bank funds for this pack.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text("No supplier pack available", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                TextButton(onClick = { showMenuReadiness = !showMenuReadiness }) {
                    Text(if (showMenuReadiness) "Hide menu details" else "Show menu details")
                }
                if (showMenuReadiness) {
                    menuReadiness.forEach { dish ->
                        HorizontalDivider()
                        val availability = when {
                            dish.unsupported -> "Recipe unavailable"
                            dish.ready -> "${dish.servings} servings"
                            else -> "Out of stock"
                        }
                        Text("${dish.name} · $availability", style = MaterialTheme.typography.titleSmall,
                            color = if (dish.ready) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error)
                        dish.missing.forEach { missing ->
                            Text(
                                "${missing.name} · %s / %s %s".format(
                                    Locale.ROOT, missing.available, missing.required, missing.unit
                                ),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            cashPosition?.let { position ->
                CafeLedgerPanel {
                        Text("Payments · ${euro(bills.sumOf { it.due } + supplierDueCents)} due")
                        Text("Bank · ${euro(position.bankCents)}")
                        Button(onClick = onOpenFinance, enabled = !loading) { Text("Manage payments") }
                }
            }
            val blockedIngredients = shortages.map { "stock_${it.supply.itemId}" }.toSet()
            val otherAlerts = alerts.filterNot { it.id in blockedIngredients }
            if (otherAlerts.isNotEmpty()) {
                Text("Management decisions", style = MaterialTheme.typography.titleMedium)
                otherAlerts.forEach { alert ->
                    CafeLedgerPanel {
                            Text(alert.title, style = MaterialTheme.typography.titleSmall,
                                color = if (alert.priority == 0) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface)
                            Text(alert.detail, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = {
                                if (alert.destination == "operations") onOpenOperations(alert.section)
                                else onOpenFinance()
                            }) { Text(if (alert.destination == "operations") "Open purchasing" else "Open finance") }
                    }
                }
            }
            OutlinedButton(onClick = { showSettings = !showSettings }) {
                Text(if (showSettings) "Hide game settings" else "Game settings")
            }
            if (showSettings) {
                CafeLedgerPanel {
                    Text("Save game", style = MaterialTheme.typography.titleMedium)
                    Text("Starting a new game deletes this save.", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { confirmNewGame = true }, enabled = !loading && speed == 0) {
                        Text("Start a new game")
                    }
                }
            }
        }
    }
}

private fun euro(cents: Long): String = "€%.2f".format(Locale.ROOT, cents / 100.0)
