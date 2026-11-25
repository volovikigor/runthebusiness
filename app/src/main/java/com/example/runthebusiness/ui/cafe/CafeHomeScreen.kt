package com.example.runthebusiness.ui.cafe

import com.example.runthebusiness.ui.components.cafeLabel
import com.example.runthebusiness.ui.components.cafeProductName
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.runthebusiness.R
import com.example.runthebusiness.data.local.GameDatabase
import com.example.runthebusiness.data.local.dao.CafeDailyNetProfit
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
import com.example.runthebusiness.ui.components.CafeActionButton
import com.example.runthebusiness.ui.components.CafeLedgerPanel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale


@Composable
private fun CafeShiftSummary(
    shift: CafeDayPerformance,
    currentDay: Int,
    onOpenOperations: (Int) -> Unit,
    onOpenFinance: () -> Unit
) {
    CafeLedgerPanel {
        Text(
            if (shift.day == currentDay) stringResource(R.string.today_shift) else stringResource(R.string.last_shift, shift.day),
            style = MaterialTheme.typography.titleMedium
        )
        Text(stringResource(R.string.shift_groups, shift.servedGroups, shift.arrivingGroups, shift.lostGroups),
                style = MaterialTheme.typography.bodySmall)
        if (shift.pendingGroups > 0) Text(stringResource(R.string.awaiting_service, shift.pendingGroups),
                style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.shift_sales, euro(shift.netSalesCents), euro(shift.grossMarginCents)),
                style = MaterialTheme.typography.bodySmall)
        if (shift.noStock > 0) {
            Text(stringResource(R.string.lost_stock, shift.noStock),
                style = MaterialTheme.typography.bodySmall)
            CafeActionButton(onClick = { onOpenOperations(1) }) { Text(stringResource(R.string.go_purchasing)) }
        }
        if (shift.declinedPrice > 0) {
            Text(stringResource(R.string.lost_price, shift.declinedPrice),
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onOpenOperations(0) }) { Text(stringResource(R.string.review_prices)) }
        }
        if (shift.noSeats > 0) Text(stringResource(R.string.lost_seats, shift.noSeats),
                style = MaterialTheme.typography.bodySmall)
        if (shift.staffCapacity > 0) {
            Text(stringResource(R.string.lost_capacity, shift.staffCapacity),
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onOpenOperations(3) }) { Text(stringResource(R.string.review_staff)) }
        }
        if (shift.staffDisruption > 0) Text(stringResource(R.string.lost_payroll, shift.staffDisruption),
                style = MaterialTheme.typography.bodySmall)
        if (shift.rentShutdown > 0) Text(stringResource(R.string.lost_rent, shift.rentShutdown),
                style = MaterialTheme.typography.bodySmall)
        if (shift.staffDisruption > 0 || shift.rentShutdown > 0) {
            CafeActionButton(onClick = onOpenFinance) { Text(stringResource(R.string.review_unpaid)) }
        }
    }
}

@Composable
private fun CafeKpiPanel(
    completedDays: Int,
    profitCents: Long,
    bankAfterBills: Long?,
    days: List<CafeDayPerformance>,
    reachedProfit: Boolean,
    reachedTarget: Boolean,
    onOpenFinance: () -> Unit
) {
    val arrivals = days.sumOf { it.arrivingGroups }
    val served = days.sumOf { it.servedGroups }
    val losses = listOf(
        R.string.loss_stock to days.sumOf { it.noStock },
        R.string.loss_staff to days.sumOf { it.staffCapacity + it.staffDisruption },
        R.string.loss_price to days.sumOf { it.declinedPrice },
        R.string.loss_seats to days.sumOf { it.noSeats },
        R.string.loss_rent to days.sumOf { it.rentShutdown }
    ).filter { it.second > 0 }
    val top = losses.maxByOrNull { it.second }
    CafeLedgerPanel {
        Text(stringResource(R.string.cafe_overview), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.kpi_completed_window, completedDays),
            style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiValue(stringResource(R.string.kpi_profit), if (completedDays > 0) euro(profitCents) else "—",
                Modifier.weight(1f))
            KpiValue(stringResource(R.string.kpi_served), if (arrivals > 0) "${(served * 100.0 / arrivals).toInt()}%" else "—",
                Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiValue(stringResource(R.string.kpi_bank_after_bills), bankAfterBills?.let(::euro) ?: "—", Modifier.weight(1f))
            KpiValue(stringResource(R.string.kpi_top_loss),
                top?.let { stringResource(R.string.kpi_loss_value, stringResource(it.first), it.second) } ?: "—",
                Modifier.weight(1f))
        }
        if (bankAfterBills != null && bankAfterBills < 0) {
            TextButton(onClick = onOpenFinance) { Text(stringResource(R.string.kpi_review_bills)) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(stringResource(R.string.cafe_milestones), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(if (reachedProfit) R.string.goal_profitable_done else R.string.goal_profitable),
            style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(if (reachedTarget) R.string.goal_target_done else R.string.goal_target, euro(200_000L)),
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun KpiValue(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
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
    val accounting = remember(context) { GameDatabase.getInstance(context).accountingDao() }
    var profitHistory by remember { mutableStateOf<List<CafeDailyNetProfit>?>(null) }
    LaunchedEffect(time?.day) {
        profitHistory = time?.day?.let { accounting.dailyNetProfit("debug_session_001", it) }
    }
    var showShift by remember { mutableStateOf(false) }
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
        dailyPerformance = if (state != null) performanceRepo.recent(days = 31) else emptyList()
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
            title = { Text(stringResource(R.string.start_new_question)) },
            text = { Text(stringResource(R.string.reset_details)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmNewGame = false
                    onStartGame(true)
                }) { Text(stringResource(R.string.delete_save)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewGame = false }) { Text(stringResource(R.string.cancel)) }
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
            Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineMedium)
            CafeLedgerPanel {
                Text(stringResource(R.string.guide_intro_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.guide_intro), style = MaterialTheme.typography.bodyMedium)
            }
            CafeActionButton(onClick = { onStartGame(false) }, enabled = !loading && time == null) {
                Text(stringResource(R.string.start_cafe))
            }
        } else {
            CafeScene(
                name = cafe.cafeName,
                time = cafe.time,
                speed = speed,
                latestOrder = cafe.latestOrder,
                latestPayment = cafe.latestPayment,
                latestDelivery = cafe.latestDelivery,
                isOpen = cafe.isTrading && paymentStatus?.rentShutdown != true,
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
                onOpenOperations = onOpenOperations,
                onOpenFinance = onOpenFinance,
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
                    Text(stringResource(if (cafe.isOpen) R.string.close_manually else R.string.resume_schedule))
                }
            }
            CafeGuideButton(stringResource(R.string.cafe_overview), R.string.guide_cafe)
            val shiftDay = if (cafe.time.hour < cafe.openingHour && cafe.time.day > 1) {
                cafe.time.day - 1
            } else {
                cafe.time.day
            }
            val window = (cafe.time.day - 30).coerceAtLeast(1) until cafe.time.day
            profitHistory?.let { profits ->
                val completed = dailyPerformance.filter { it.day in window }
                val monthProfits = profits.filter { it.day <= (cafe.time.day - 1) / 30 * 30 }
                    .groupBy { (it.day - 1) / 30 }.values.map { month -> month.sumOf { it.cents } }
                CafeKpiPanel(
                    completedDays = window.count(),
                    profitCents = profits.filter { it.day in window }.sumOf { it.cents },
                    bankAfterBills = cashPosition?.let { it.bankCents - bills.sumOf { bill -> bill.due } - supplierDueCents },
                    days = completed,
                    reachedProfit = monthProfits.any { it > 0 },
                    reachedTarget = monthProfits.any { it >= 200_000L },
                    onOpenFinance = onOpenFinance
                )
            }
            TextButton(onClick = { showShift = !showShift }) {
                Text(stringResource(if (showShift) R.string.hide_shift_details else R.string.show_shift_details))
            }
            if (showShift) dailyPerformance.firstOrNull { it.day == shiftDay }?.let { shift ->
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
                Text(stringResource(R.string.menu_readiness, ready, menuReadiness.size),
                    style = MaterialTheme.typography.titleMedium)
                if (shortages.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(stringResource(R.string.ingredients_blocking), style = MaterialTheme.typography.titleSmall)
                            val visible = if (showMenuReadiness) shortages else shortages.take(3)
                            visible.forEach { shortage ->
                                val supply = shortage.supply
                                HorizontalDivider()
                                Text("${cafeProductName(supply.itemId, supply.name)} · ${shortage.dishes.size} affected",
                                    style = MaterialTheme.typography.titleSmall)
                                Text(shortage.dishes.map { cafeProductName(it.productId, it.name) }.joinToString(),
                                    style = MaterialTheme.typography.bodySmall)
                                val cost = supply.supplierPackCents
                                if (cost != null) {
                                    val affordable = cashPosition?.let {
                                        it.cashCents >= cost || it.bankCents >= cost
                                    } == true
                                    Button(
                                        enabled = !loading && affordable,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.70f),
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ),
                                        onClick = { run { purchasingRepo.purchase(supply.itemId) } }
                                    ) {
                                        Text(stringResource(R.string.buy_pack_price, euro(cost)), maxLines = 1)
                                    }
                                    if (cashPosition != null && !affordable) {
                                        Text(stringResource(R.string.insufficient_pack),
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                } else {
                                    Text(stringResource(R.string.no_supplier_pack), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                TextButton(onClick = { showMenuReadiness = !showMenuReadiness }) {
                    Text(stringResource(if (showMenuReadiness) R.string.hide_menu_details else R.string.show_menu_details))
                }
                if (showMenuReadiness) {
                    menuReadiness.forEach { dish ->
                        HorizontalDivider()
                        val availability = when {
                            dish.unsupported -> stringResource(R.string.recipe_unavailable)
                            dish.ready -> stringResource(R.string.availability_servings, dish.servings)
                            else -> stringResource(R.string.out_of_stock)
                        }
                        Text("${cafeProductName(dish.productId, dish.name)} · $availability", style = MaterialTheme.typography.titleSmall,
                            color = if (dish.ready) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error)
                        dish.missing.forEach { missing ->
                            Text(
                                "${cafeProductName(missing.itemId, missing.name)} · %s / %s %s".format(
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
                        CafeActionButton(onClick = onOpenFinance, enabled = !loading) { Text(stringResource(R.string.manage_payments)) }
                }
            }
            val blockedIngredients = shortages.map { "stock_${it.supply.itemId}" }.toSet()
            val otherAlerts = alerts.filterNot { it.id in blockedIngredients }
            if (otherAlerts.isNotEmpty()) {
                Text(stringResource(R.string.management_decisions), style = MaterialTheme.typography.titleMedium)
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
                Text(stringResource(if (showSettings) R.string.hide_settings else R.string.game_settings))
            }
            if (showSettings) {
                CafeLedgerPanel {
                    Text(stringResource(R.string.save_game), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.new_game_warning), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { confirmNewGame = true }, enabled = !loading && speed == 0) {
                        Text(stringResource(R.string.start_new))
                    }
                }
            }
        }
    }
}

private fun euro(cents: Long): String = "€%.2f".format(Locale.ROOT, cents / 100.0)
