package com.example.runthebusiness.ui.cafe

import com.example.runthebusiness.ui.components.cafeLabel
import com.example.runthebusiness.ui.components.cafeProductName
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.runthebusiness.engine.model.GameTime
import com.example.runthebusiness.industries.cafe.simulation.CafeArrivalPlanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import com.example.runthebusiness.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import com.example.runthebusiness.core.simulation.GameSimulationEngine
import com.example.runthebusiness.core.finance.CafeTaxPolicy
import com.example.runthebusiness.industries.cafe.simulation.CafePriceDemandRules
import kotlin.math.ceil
import com.example.runthebusiness.data.local.*
import com.example.runthebusiness.ui.components.CafeSectionScreen
import com.example.runthebusiness.ui.components.CafeActionButton
import com.example.runthebusiness.ui.components.CafeLedgerPanel
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CafeDashboardScreen(modifier: Modifier = Modifier, requestedSection: Int = 0) {
    val context = LocalContext.current.applicationContext
    val menu = remember(context) { CafeMenuRepository(context) }
    val expanded = remember(context) { CafeExpandedMenuRepository(context) }
    val kitchen = remember(context) { CafeKitchenRepository(context) }
    val purchasing = remember(context) { CafePurchasingRepository(context) }
    val autoPurchasing = remember(context) { CafeAutoPurchasingRepository(context) }
    val inventoryRepo = remember(context) { CafeInventorySummaryRepository(context) }
    var inventory by remember { mutableStateOf<CafeInventorySummary?>(null) }
    var openIngredient by remember { mutableStateOf<String?>(null) }
    var confirmPriceReset by remember { mutableStateOf(false) }
    var openMenuProduct by remember { mutableStateOf<String?>(null) }
    var packCountInput by remember { mutableStateOf("1") }
    var selectedPack by remember { mutableStateOf<CafePurchasingRepository.Supply?>(null) }
    var deferredPayment by remember { mutableStateOf(false) }
    fun selectPack(supply: CafePurchasingRepository.Supply, defer: Boolean) {
        packCountInput = "1"
        deferredPayment = defer
        selectedPack = supply
    }
    val inventorySetup = remember(context) { CafeInventorySetupRepository(context) }
    val simulation = remember(context) { GameSimulationEngine(context) }
    val dashboard = remember(context) { CafeDashboardRepository(context) }
    val staffRepo = remember(context) { CafeStaffRepository(context) }
    var staff by remember { mutableStateOf<CafeStaffSnapshot?>(null) }
    val bills = remember(context) { CafePaymentRegisterRepository(context) }
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<CafeMenuItem>>(emptyList()) }
    var stock by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var supplierCreditBlocked by remember { mutableStateOf(false) }
    var currentGameDay by remember { mutableIntStateOf(1) }
    var cafeSnapshot by remember { mutableStateOf<CafeDashboardSnapshot?>(null) }
    var started by remember { mutableStateOf(false) }
    var autoPolicy by remember { mutableStateOf(autoPurchasing.policy()) }
    var autoSelected by remember { mutableStateOf(emptySet<String>()) }
    var realIngredients by remember { mutableStateOf(false) }
    var autoSettings by remember { mutableStateOf(false) }
    var kitchenInstalled by remember { mutableStateOf(false) }
    var section by remember { mutableIntStateOf(requestedSection) }
    LaunchedEffect(requestedSection) { section = requestedSection }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        cafeSnapshot = dashboard.snapshot()
        val currentDay = cafeSnapshot?.time?.day
        started = currentDay != null
        currentGameDay = currentDay ?: 1
        staff = staffRepo.snapshot()
        supplierCreditBlocked = currentDay != null && bills.supplierBills().any { it.dueDay < currentDay }
        realIngredients = inventorySetup.hasRealIngredients()
        kitchenInstalled = kitchen.installed()
        products = menu.loadMenu()
        stock = purchasing.stock()
        inventory = inventoryRepo.load()
        autoPolicy = autoPurchasing.policy()
        autoSelected = CafePurchasingRepository.supplies.filter { autoPurchasing.enabled(it.itemId) }
            .map { it.itemId }.toSet()
    }
    fun run(action: suspend () -> Unit) {
            if (busy) return
            busy = true
            scope.launch {
                error = null
                try {
                    action()
                    reload()
                } catch (e: Exception) { error = e.message ?: e.javaClass.simpleName }
                finally { busy = false }
            }
        }
        LaunchedEffect(Unit) { run { } }
    if (confirmPriceReset) {
        AlertDialog(
            onDismissRequest = { confirmPriceReset = false },
            title = { Text(stringResource(R.string.reset_prices_question)) },
            text = { Text(stringResource(R.string.reset_prices_body)) },
            confirmButton = {
                CafeActionButton(enabled = !busy, onClick = {
                    confirmPriceReset = false
                    run {
                        val count = menu.resetPrices()
                        message = "Prices reset for $count menu items."
                    }
                }) { Text(stringResource(R.string.reset_prices)) }
            },
            dismissButton = { TextButton(onClick = { confirmPriceReset = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    products.firstOrNull { it.id == openMenuProduct }?.let { product ->
        var insights by remember(product.id) { mutableStateOf<MenuPriceInsights?>(null) }
        LaunchedEffect(product.id, product.priceCents, currentGameDay) {
            insights = menu.priceInsights(product.id)
        }
        val itemIds = listOfNotNull(product.finishedStockItemId).ifEmpty {
            product.ingredients.map { it.first }
        }
        val packs = CafePurchasingRepository.supplies.filter { it.itemId in itemIds }
        AlertDialog(
            onDismissRequest = { openMenuProduct = null },
            title = { Text(cafeProductName(product.id, product.name)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.loss_price), Modifier.weight(1f))
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = "Decrease price by €0.10" },
                            enabled = !busy && product.priceCents > 50L,
                            onClick = { run { menu.updatePrice(product.id, (product.priceCents - 10L).coerceAtLeast(50L)) } }
                        ) { Text("−") }
                        Text(menuPackMoney(product.priceCents), style = MaterialTheme.typography.titleSmall)
                        IconButton(
                            modifier = Modifier.semantics { contentDescription = "Increase price by €0.10" },
                            enabled = !busy && product.priceCents < 5000L,
                            onClick = { run { menu.updatePrice(product.id, (product.priceCents + 10L).coerceAtMost(5000L)) } }
                        ) { Text("+") }
                    }
                    OutlinedButton(enabled = !busy, onClick = {
                        run { expanded.setEnabled(product.id, !product.enabled) }
                    }) { Text(stringResource(if (product.enabled) R.string.remove_menu else R.string.add_menu)) }
                    val openingPrice = CafePriceDemandRules.openingPriceCents(product.id)
                    if (product.enabled && product.priceCents > openingPrice) {
                        Text(stringResource(R.string.above_opening_price),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (product.enabled && CafePriceDemandRules.discountTrafficWeight(product.id, product.priceCents) > 0) {
                        Text(stringResource(R.string.promotion_workload),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    insights?.let { MenuPriceAnalytics(it) }
                    if (packs.isNotEmpty()) MenuPurchasingDetails(product, packs)
                }
            },
            confirmButton = { TextButton(onClick = { openMenuProduct = null }) { Text(stringResource(R.string.done)) } }
        )
    }

    selectedPack?.let { supply ->
        val packs = packCountInput.toIntOrNull()?.takeIf { it in 1..99 }
        AlertDialog(
            onDismissRequest = { selectedPack = null },
            title = { Text(stringResource(R.string.order_item, cafeProductName(supply.itemId, supply.label))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = packCountInput,
                        onValueChange = { if (it.length <= 2 && it.all { digit -> digit in '0'..'9' }) packCountInput = it },
                        label = { Text(stringResource(R.string.pack_count)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = packs == null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (packs != null) {
                        Text("${stockQuantity(supply.quantity * packs)} ${supply.unit} total · ${menuPackMoney(supply.costCents * packs)}")
                    }
                    Text(if (deferredPayment) {
                        "Stock arrives now. Pay the supplier invoice in Finance → Bills on Day ${currentGameDay + supply.creditDays}."
                    } else "Stock arrives immediately and payment is taken now.")
                }
            },
            confirmButton = {
                CafeActionButton(
                    enabled = !busy && packs != null && (!deferredPayment || !supplierCreditBlocked),
                    onClick = {
                        val count = checkNotNull(packs)
                        val invoice = deferredPayment
                        selectedPack = null
                        run {
                            purchasing.purchasePacks(supply.itemId, count, onCredit = invoice)
                            message = if (invoice) "${supply.label} ordered · invoice in Bills"
                                else "${supply.label} purchased"
                        }
                    }
                ) { Text(stringResource(if (deferredPayment) R.string.order_credit else R.string.pay_now)) }
            },
            dismissButton = { TextButton(onClick = { selectedPack = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }


        val sections = listOf("Pricing", "Purchasing", "Inventory", "Staff")
    CafeSectionScreen(
        modifier, "Operations", sections, sections.getOrElse(section) { "Inventory" },
        onSelect = { label ->
            section = sections.indexOf(label)
            message = null
        }
    ) {
        CafeGuideButton(sections.getOrElse(section) { sections[2] }, when (section) {
            0 -> R.string.guide_pricing
            1 -> R.string.guide_purchasing
            3 -> R.string.guide_staff
            else -> R.string.guide_inventory
        })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall)

        if (!started) {
            Text(stringResource(R.string.start_for_ops))
            CafeActionButton(onClick = { run {
                    simulation.startGame()
                } }, enabled = !busy) {
                Text(stringResource(R.string.start_cafe))
            }
        } else when (section) {
            0 -> {
                Text(stringResource(R.string.menu_pricing), style = MaterialTheme.typography.titleLarge)
                cafeSnapshot?.let { WeeklyDemandCard(it, products) }
                OutlinedButton(
                    enabled = !busy && products.any { it.priceCents != CafePriceDemandRules.openingPriceCents(it.id) },
                    onClick = { confirmPriceReset = true }
                ) { Text(stringResource(R.string.reset_start_prices)) }
                if (products.isEmpty()) {
                    Text(stringResource(R.string.create_menu_first))
                    CafeActionButton(onClick = { run { menu.seedMenu() } }, enabled = !busy) {
                        Text(stringResource(R.string.create_menu))
                    }
                }
                if (products.none { it.id == "caesar_salad_001" }) {
                    CafeActionButton(onClick = { run {
                        expanded.install()
                        message = "11 new menu items added. Enable and stock the ones you want to sell."
                    } }, enabled = !busy) { Text(stringResource(R.string.expand_menu)) }
                }
                Text(
                    if (currentGameDay <= 1) stringResource(R.string.sales_no_closed_days)
                    else stringResource(R.string.sales_completed_range,
                        (currentGameDay - 7).coerceAtLeast(1), currentGameDay - 1),
                    style = MaterialTheme.typography.bodySmall
                )
                products.forEach { product ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(cafeProductName(product.id, product.name), style = MaterialTheme.typography.titleSmall)
                            val status = cafeLabel(if (product.enabled) "On menu" else "Off menu")
                            Text(stringResource(R.string.items_sold, product.unitsSoldRecently, status), style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(enabled = !busy, onClick = { openMenuProduct = product.id },
                            modifier = Modifier.semantics { contentDescription = "Edit ${product.name} price and menu options" }
                        ) { Text(menuPackMoney(product.priceCents)) }
                    }
                }
            }
            1 -> {
                Text(stringResource(R.string.purchasing), style = MaterialTheme.typography.titleLarge)
                cafeSnapshot?.let { WeeklyDemandCard(it, products) }
                if (!realIngredients && products.isNotEmpty()) {
                    Text(stringResource(R.string.legacy_stock),
                        color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { run { inventorySetup.initializeRealStock() } },
                        enabled = !busy) { Text(stringResource(R.string.setup_ingredients)) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.replenishment), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = autoPolicy.enabled, enabled = !busy,
                        onCheckedChange = { checked ->
                            autoPurchasing.setEnabled(checked)
                            autoPolicy = autoPurchasing.policy()
                        })
                }
                TextButton(onClick = { autoSettings = !autoSettings }) {
                    Text(stringResource(if (autoSettings) R.string.hide_auto_order else R.string.auto_order_settings))
                }
                if (autoSettings) {
                    Text("Daily purchase budget: €%.0f".format(Locale.ROOT, autoPolicy.dailyBudgetCents / 100.0))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            autoPurchasing.setDailyBudget((autoPolicy.dailyBudgetCents - 5000L).coerceAtLeast(0L))
                            autoPolicy = autoPurchasing.policy()
                        }) { Text("− €50") }
                        OutlinedButton(onClick = {
                            autoPurchasing.setDailyBudget((autoPolicy.dailyBudgetCents + 5000L).coerceAtMost(1000000L))
                            autoPolicy = autoPurchasing.policy()
                        }) { Text("+ €50") }
                }
                OutlinedButton(onClick = {
                    autoPurchasing.enableCoreIngredients()
                    autoPolicy = autoPurchasing.policy()
                    autoSelected = CafePurchasingRepository.supplies.filter {
                        autoPurchasing.enabled(it.itemId)
                    }.map { it.itemId }.toSet()
                    message = "Auto purchasing enabled for core ingredients and kitchen supplies. Review individual switches and budget."
                }) { Text(stringResource(R.string.auto_core)) }
                }
                if (supplierCreditBlocked) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                            Text(stringResource(R.string.credit_paused), Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall)
                        }
                        Text(stringResource(R.string.pay_overdue_invoices),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                CafePurchasingRepository.supplies.filter { supply ->
                    val food = CafeExpandedMenuRepository.offerings.firstOrNull { it.stockId == supply.itemId }
                    val isKitchen = CafeKitchenRepository.ingredients.any { it.id == supply.itemId }
                    when {
                        isKitchen -> kitchenInstalled
                        food != null && food.id in CafeKitchenRepository.recipes ->
                            products.any { it.id == food.id && it.ingredients.isEmpty() }
                        food != null -> products.any { it.id == food.id }
                        else -> true
                    }
                }.forEach { supply ->
                    CafeLedgerPanel {
                        Text(cafeProductName(supply.itemId, supply.label), style = MaterialTheme.typography.titleMedium)
                        Text("%.0f %s available · %.0f %s per pack".format(Locale.ROOT,
                            stock[supply.itemId] ?: 0.0, supply.unit, supply.quantity, supply.unit),
                            style = MaterialTheme.typography.bodySmall)
                        if (supply.shelfLifeDays > 0) {
                            Text(stringResource(R.string.shelf_days, supply.shelfLifeDays), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(stringResource(R.string.pack_unit_price, menuPackMoney(supply.costCents)),
                            style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CafeActionButton(enabled = !busy, onClick = { selectPack(supply, false) }) {
                                Text(stringResource(R.string.pay_now))
                            }
                            OutlinedButton(enabled = !busy && !supplierCreditBlocked,
                                onClick = { selectPack(supply, true) }) {
                                Text(stringResource(R.string.pay_in_days, supply.creditDays))
                            }
                        }
                    }
                    if (autoSettings) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.auto_order), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(
                                modifier = Modifier.semantics { contentDescription = "Auto-order ${supply.label}" },
                                checked = supply.itemId in autoSelected,
                                onCheckedChange = { checked ->
                                    autoPurchasing.setItemEnabled(supply.itemId, checked)
                                    autoSelected = if (checked) autoSelected + supply.itemId
                                        else autoSelected - supply.itemId
                                }
                            )
                        }
                    }
                }
            }
            3 -> staff?.let { team ->
                Text(stringResource(R.string.loss_staff), style = MaterialTheme.typography.titleLarge)
                CafeLedgerPanel {
                    Text("Today · ${team.currentCount} employees · €%.2f per day".format(
                        Locale.ROOT, team.currentCostCents / 100.0))
                    Text("Day ${team.today + 1} · ${team.plannedCount} employees · €%.2f per day".format(
                        Locale.ROOT, team.plannedCostCents / 100.0))
                    Text(stringResource(R.string.daily_social),
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !busy && team.plannedCount > 1,
                            onClick = { run { staffRepo.changePlannedSize(-1) } }
                        ) { Text(stringResource(R.string.remove_one)) }
                        CafeActionButton(enabled = !busy, onClick = { run { staffRepo.changePlannedSize(1) } }) {
                            Text(stringResource(R.string.hire_one))
                        }
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.service_capacity), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.hiring_help),
                    style = MaterialTheme.typography.bodySmall)
                Text("Today %.1f · Tomorrow %.1f work minutes per 15-minute slot".format(
                    Locale.ROOT, team.currentCapacity, team.plannedCapacity))
                if (team.today > 1) {
                    Text("Day ${team.today - 1} · peak workload ${team.peakWorkload} work minutes per slot")
                    Text(stringResource(R.string.lost_capacity, team.capacityLosses))
                    Text("Last 30 completed days · ${team.recentCapacityLosses} groups lost to staff capacity")
                }
            }
            else -> inventory?.let { report ->
                Text(stringResource(R.string.stock_balance), style = MaterialTheme.typography.titleLarge)
                Text("Day ${report.fromDay}–${report.throughDay} · ending stock as of now",
                    style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.writeoffs, menuPackMoney(report.items.sumOf { it.total.wasteCents })),
                    style = MaterialTheme.typography.titleSmall)
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val tableWidth = maxWidth.coerceAtLeast(360.dp)
                        Column(Modifier.horizontalScroll(rememberScrollState()).width(tableWidth)) {
                            StockTableRow("Ingredient", listOf("Start", "Bought", "Used", "Waste", "End"))
                            report.items.forEach { item ->
                                val expanded = openIngredient == item.id
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                StockTableRow("${if (expanded) "▾" else "▸"} ${cafeProductName(item.id, item.name)} · ${item.unit}",
                                    item.total.columns(), Modifier.clickable {
                                        openIngredient = if (expanded) null else item.id
                                    }.heightIn(min = 44.dp))
                                if (expanded) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                                        Column {
                                            item.days.forEach { day ->
                                                StockTableRow("Day ${day.day}", day.columns())
                                                if (kotlin.math.abs(day.other) > 0.00001) {
                                                    Text("Other movements ${stockQuantity(day.other)} ${item.unit}",
                                                        Modifier.padding(start = 10.dp, bottom = 4.dp),
                                                        style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.stock_drilldown),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { run { } }, enabled = !busy) { Text(stringResource(R.string.refresh)) }
    }
}


private fun stockQuantity(value: Double): String =
    "%.2f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }

private fun InventoryPeriod.columns(): List<String> =
    listOf(opening, purchased, consumed, writtenOff, closing).map(::stockQuantity)

@Composable
private fun StockTableRow(label: String, values: List<String>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(cafeLabel(label), Modifier.weight(2.1f), style = MaterialTheme.typography.labelSmall)
        values.forEach { value ->
            Text(cafeLabel(value), Modifier.weight(1f), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"))
        }
    }
}

@Composable
private fun WeeklyDemandCard(cafe: CafeDashboardSnapshot, products: List<CafeMenuItem>) {
    val today = cafe.time.day
    val weekdayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val menu = products.filter { it.enabled && it.fulfillment != "BATCH_PRODUCED" }.map {
        CafePriceDemandRules.MenuPrice(it.id, it.type, it.priceCents)
    }
    CafeLedgerPanel {
        Text(stringResource(R.string.demand_outlook), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.demand_timing),
            style = MaterialTheme.typography.bodySmall)
        for (day in today..today + 2) {
            val visits = if (cafe.isOpen) CafeArrivalPlanner.planForMenu(
                GameTime(day - 1, 23, 59), GameTime(day, 23, 59),
                cafe.openingHour, cafe.closingHour, cafe.seats, menu
            ) else emptyList()
            val promo = visits.count { it.preferredProductId != null }
            val suffix = if (promo > 0) " · $promo promo groups" else ""
            Text("${weekdayNames[(day - 1) % 7]} · Day $day · ${visits.sumOf { it.guests }} guests$suffix",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MenuPurchasingDetails(product: CafeMenuItem, packs: List<CafePurchasingRepository.Supply>) {
    CafeLedgerPanel {
        packs.forEach { pack ->
            val shelfLife = if (pack.shelfLifeDays > 0) " · ${pack.shelfLifeDays} days shelf life" else ""
            Text("${cafeProductName(pack.itemId, pack.label)} · ${stockQuantity(pack.quantity)} ${pack.unit} · ${menuPackMoney(pack.costCents)}$shelfLife",
                style = MaterialTheme.typography.bodySmall)
        }
        if (product.finishedStockItemId != null) {
            val pack = packs.single()
            val units = ceil(pack.costCents * (100.0 + CafeTaxPolicy.hospitalityVatPercent) /
                (100 * product.priceCents)).toLong()
            Text("Sell $units to cover the pack before other expenses (sales excluding VAT).",
                style = MaterialTheme.typography.bodySmall)
        } else {
            Text(stringResource(R.string.shared_packs), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun menuPackMoney(cents: Long): String = "€%.2f".format(Locale.ROOT, cents / 100.0)

@Composable
private fun MenuPriceAnalytics(insights: MenuPriceInsights) {
    CafeLedgerPanel {
        Text(stringResource(R.string.price_demand), style = MaterialTheme.typography.titleSmall)
        val recent = insights.recent
        if (recent != null) {
            Text("Days ${recent.days.first}–${recent.days.last} · ${recent.sales.units} sold",
                style = MaterialTheme.typography.bodySmall)
            val low = recent.sales.minPriceCents
            val high = recent.sales.maxPriceCents
            if (low != null && high != null) {
                val price = if (low == high) menuPackMoney(low) else "${menuPackMoney(low)}–${menuPackMoney(high)}"
                Text("Recorded sale price · $price", style = MaterialTheme.typography.bodySmall)
            }
            Text("Café traffic · ${recent.traffic.groups} groups · ${recent.traffic.guests} guests",
                style = MaterialTheme.typography.bodySmall)
            if (recent.traffic.promotionGroups > 0) {
                Text("Promotion · ${recent.traffic.promotionGroups} groups · ${recent.traffic.promotionStockLosses} lost to stock · ${recent.traffic.promotionStaffLosses} lost to staffing",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        val change = insights.change
        when {
            change == null -> Text(stringResource(R.string.price_new_comparison),
                style = MaterialTheme.typography.bodySmall)
            change.previousCents == change.currentCents -> Text("Day ${change.gameDay} ended at the previous price.",
                style = MaterialTheme.typography.bodySmall)
            else -> {
                Text("Day ${change.gameDay}: ${menuPackMoney(change.previousCents)} → ${menuPackMoney(change.currentCents)}",
                    style = MaterialTheme.typography.bodySmall)
                val before = insights.before
                val after = insights.after
                if (before != null && after != null) {
                    PriceComparisonRow("Completed days",
                        "${before.days.first}–${before.days.last}", "${after.days.first}–${after.days.last}")
                    PriceComparisonRow("Units sold", before.sales.units.toString(), after.sales.units.toString())
                    PriceComparisonRow("Sales incl. VAT", menuPackMoney(before.sales.grossCents),
                        menuPackMoney(after.sales.grossCents))
                    PriceComparisonRow("Café groups", before.traffic.groups.toString(), after.traffic.groups.toString())
                    PriceComparisonRow("Café guests", before.traffic.guests.toString(), after.traffic.guests.toString())
                    PriceComparisonRow("Groups lost to staffing", before.traffic.staffLosses.toString(),
                        after.traffic.staffLosses.toString())
                    Text(stringResource(R.string.matching_windows),
                        style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(stringResource(R.string.needs_closed_day),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PriceComparisonRow(label: String, before: String, after: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val style = MaterialTheme.typography.bodySmall
        Text(cafeLabel(label), Modifier.weight(1f), style = style)
        Text("$before → $after", style = style, textAlign = TextAlign.End)
    }
}
