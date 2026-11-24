package com.example.runthebusiness.ui.cafe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.core.simulation.GameSimulationEngine
import com.example.runthebusiness.data.local.*
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
    val writeOff = remember(context) { CafeWriteOffRepository(context) }
    val movementsRepo = remember(context) { CafeTransactionDetailRepository(context) }
    val inventorySetup = remember(context) { CafeInventorySetupRepository(context) }
    val simulation = remember(context) { GameSimulationEngine(context) }
    val dashboard = remember(context) { CafeDashboardRepository(context) }
    val staffRepo = remember(context) { CafeStaffRepository(context) }
    var staff by remember { mutableStateOf<CafeStaffSnapshot?>(null) }
    val bills = remember(context) { CafePaymentRegisterRepository(context) }
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<CafeMenuItem>>(emptyList()) }
    var stock by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var movements by remember { mutableStateOf<List<com.example.runthebusiness.data.local.entity.StockMovementEntity>>(emptyList()) }
    var supplierCreditBlocked by remember { mutableStateOf(false) }
    var started by remember { mutableStateOf(false) }
    var autoPolicy by remember { mutableStateOf(autoPurchasing.policy()) }
    var autoSelected by remember { mutableStateOf(emptySet<String>()) }
    var realIngredients by remember { mutableStateOf(false) }
    var kitchenInstalled by remember { mutableStateOf(false) }
    var section by remember { mutableIntStateOf(requestedSection) }
    LaunchedEffect(requestedSection) { section = requestedSection }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val currentDay = dashboard.snapshot()?.time?.day
        started = currentDay != null
        staff = staffRepo.snapshot()
        supplierCreditBlocked = currentDay != null && bills.supplierBills().any { it.dueDay < currentDay }
        realIngredients = inventorySetup.hasRealIngredients()
        kitchenInstalled = kitchen.installed()
        products = menu.loadMenu()
        stock = purchasing.stock()
        autoPolicy = autoPurchasing.policy()
        autoSelected = CafePurchasingRepository.supplies.filter { autoPurchasing.enabled(it.itemId) }
            .map { it.itemId }.toSet()
        movements = movementsRepo.movements()
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

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Operations", style = MaterialTheme.typography.headlineMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Pricing", "Purchasing", "Inventory", "Staff").forEachIndexed { index, title ->
                SegmentedButton(
                    selected = section == index,
                    onClick = { section = index; message = null },
                    shape = SegmentedButtonDefaults.itemShape(index, 4),
                    label = { Text(title) }
                )
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (message != null) Text(message!!, color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall)

        if (!started) {
            Text("Start the café to manage operations.")
            Button(onClick = { run {
                    simulation.startGame()
                } }, enabled = !busy) {
                Text("Start café")
            }
        } else when (section) {
            0 -> {
                Text("Menu & pricing", style = MaterialTheme.typography.titleLarge)
                Text("Change prices to influence the chance of a sale. Prices persist between sessions.",
                    style = MaterialTheme.typography.bodySmall)
                if (products.isEmpty()) {
                    Text("Create the starter menu first.")
                    Button(onClick = { run { menu.seedMenu() } }, enabled = !busy) {
                        Text("Create starter menu")
                    }
                }
                if (products.none { it.id == "caesar_salad_001" }) {
                    Button(onClick = { run {
                        expanded.install()
                        message = "11 new menu items added. Enable and stock the ones you want to sell."
                    } }, enabled = !busy) { Text("Expand menu: cakes, sandwiches & salads") }
                }
                Text("Enable products to offer them to visitors. New items need supplier stock before sales.",
                    style = MaterialTheme.typography.bodySmall)
                if (products.any { it.id == "ham_cheese_001" }) {
                    OutlinedButton(onClick = { run {
                        val result = kitchen.activate()
                        val waitingText = if (result.waitingForStock.isEmpty())
                            "All eligible meals use ingredients."
                        else "Sell remaining ready-made stock first: ${result.waitingForStock.joinToString()}"
                        message = "Kitchen recipes activated: ${result.converted}. $waitingText"
                    } }, enabled = !busy) { Text("Activate kitchen recipes for sandwiches & salads") }
                }
                products.forEach { product ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.weight(1f))
                                Switch(checked = product.enabled, enabled = !busy,
                                    onCheckedChange = { checked -> run {
                                        expanded.setEnabled(product.id, checked)
                                    } })
                            }
                            Text(product.type, style = MaterialTheme.typography.labelSmall)
                            Text("Current price", style = MaterialTheme.typography.labelSmall)
                            Text("€%.2f".format(Locale.ROOT, product.priceCents / 100.0),
                                style = MaterialTheme.typography.headlineSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    enabled = !busy && product.priceCents > 50L,
                                    onClick = {
                                        run {
                                            menu.updatePrice(product.id, (product.priceCents - 10L).coerceAtLeast(50L))
                                            message = "${product.name}: price reduced to €%.2f".format(
                                                Locale.ROOT, (product.priceCents - 10L).coerceAtLeast(50L) / 100.0)
                                        }
                                    }
                                ) { Text("− €0.10") }
                                Button(
                                    enabled = !busy && product.priceCents < 5000L,
                                    onClick = {
                                        run {
                                            menu.updatePrice(product.id, (product.priceCents + 10L).coerceAtMost(5000L))
                                            message = "${product.name}: price increased to €%.2f".format(
                                                Locale.ROOT, (product.priceCents + 10L).coerceAtMost(5000L) / 100.0)
                                        }
                                    }
                                ) { Text("+ €0.10") }
                            }
                        }
                    }
                }
            }
            1 -> {
                Text("Purchasing", style = MaterialTheme.typography.titleLarge)
                Text("Supplier packs arrive immediately. Purchases increase inventory and reduce cash.",
                    style = MaterialTheme.typography.bodySmall)
                if (!realIngredients && products.isNotEmpty()) {
                    Text("Legacy placeholder inventory is still in use. Convert it to beans, milk and croissants before assessing margins.",
                        color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { run { inventorySetup.initializeRealStock() } },
                        enabled = !busy) { Text("Set up real ingredients") }
                } else if (realIngredients) {
                    Text("Real ingredient recipes and FIFO stock are active.",
                        style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Text("Automatic replenishment", style = MaterialTheme.typography.titleLarge)
                Text("Optional daily check at the start of each game day. Buys one supplier pack only when remaining stock is 20% or less. Purchases use real cash/bank and count toward a daily spending limit; no money is charged if nothing is bought.",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Enable auto purchasing")
                    Switch(checked = autoPolicy.enabled, enabled = !busy,
                        onCheckedChange = { checked ->
                            autoPurchasing.setEnabled(checked)
                            autoPolicy = autoPurchasing.policy()
                        })
                }
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
                }) { Text("Enable core ingredients auto-order") }
                Text("Select supplier packs to replenish automatically:",
                    style = MaterialTheme.typography.labelLarge)
                if (supplierCreditBlocked) {
                    Text("Supplier credit paused · pay overdue invoices in Bills.",
                        color = MaterialTheme.colorScheme.error)
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(supply.label, Modifier.weight(1f))
                        Switch(checked = supply.itemId in autoSelected,
                            onCheckedChange = { checked ->
                                autoPurchasing.setItemEnabled(supply.itemId, checked)
                                autoSelected = if (checked) autoSelected + supply.itemId
                                    else autoSelected - supply.itemId
                            })
                    }
                }
                HorizontalDivider()
                CafePurchasingRepository.supplies.filter { supply ->
                    val isKitchen = CafeKitchenRepository.ingredients.any { it.id == supply.itemId }
                    val food = CafeExpandedMenuRepository.offerings.firstOrNull { it.stockId == supply.itemId }
                    when {
                        isKitchen -> kitchenInstalled
                        food != null && food.id in CafeKitchenRepository.recipes ->
                            products.any { it.id == food.id && it.ingredients.isEmpty() }
                        food != null -> products.any { it.id == food.id }
                        else -> true
                    }
                }.forEach { supply ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(supply.label, style = MaterialTheme.typography.titleMedium)
                            Text("Available: %.0f %s".format(Locale.ROOT,
                                stock[supply.itemId] ?: 0.0, supply.unit))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("%.0f %s · €%.2f".format(Locale.ROOT,
                                    supply.quantity, supply.unit, supply.costCents / 100.0))
                                Button(enabled = !busy, onClick = {
                                    run {
                                        purchasing.purchase(supply.itemId)
                                        message = "Purchased %.0f %s of %s".format(Locale.ROOT,
                                            supply.quantity, supply.unit, supply.label)
                                    }
                                }) { Text("Buy") }
                            }
                            val shelfLife = if (supply.shelfLifeDays > 0) {
                                "Shelf life ${supply.shelfLifeDays} days"
                            } else {
                                "Shelf-stable"
                            }
                            Text(shelfLife, style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(enabled = !busy && !supplierCreditBlocked, onClick = {
                                run {
                                    purchasing.purchase(supply.itemId, onCredit = true)
                                    message = "Purchased ${supply.label} · invoice added to Bills"
                                }
                            }) { Text("Buy · pay in ${supply.creditDays} days") }
                        }
                    }
                }
            }
            3 -> staff?.let { team ->
                Text("Staff", style = MaterialTheme.typography.titleLarge)
                Text("Today · ${team.currentCount} employees · €%.2f per day".format(
                    Locale.ROOT, team.currentCostCents / 100.0))
                Text("Day ${team.today + 1} · ${team.plannedCount} employees · €%.2f per day".format(
                    Locale.ROOT, team.plannedCostCents / 100.0))
                Text("Daily cost includes employer social contributions.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !busy && team.plannedCount > 1,
                        onClick = { run { staffRepo.changePlannedSize(-1) } }
                    ) { Text("Remove 1") }
                    Button(enabled = !busy, onClick = { run { staffRepo.changePlannedSize(1) } }) {
                        Text("Hire 1")
                    }
                }
                HorizontalDivider()
                Text("Service capacity", style = MaterialTheme.typography.titleMedium)
                Text("Today %.1f · Tomorrow %.1f work minutes per 15-minute slot".format(
                    Locale.ROOT, team.currentCapacity, team.plannedCapacity))
                if (team.today > 1) {
                    Text("Day ${team.today - 1} · peak workload ${team.peakWorkload} work minutes per slot")
                    Text("Groups lost to staff capacity · ${team.capacityLosses}")
                }
            }
            else -> {
                Text("Inventory", style = MaterialTheme.typography.titleLarge)
                Text("Current stock by ingredient. Stock is consumed by sales using FIFO.",
                    style = MaterialTheme.typography.bodySmall)
                CafePurchasingRepository.supplies.forEach { supply ->
                    ListItem(
                        headlineContent = { Text(supply.label) },
                        supportingContent = { Text(supply.unit) },
                        trailingContent = {
                            Text("%.0f".format(Locale.ROOT, stock[supply.itemId] ?: 0.0))
                        }
                    )
                }
                Text("Manual spoilage: write off one unit from the oldest eligible FIFO batch.",
                    style = MaterialTheme.typography.bodySmall)
                CafePurchasingRepository.supplies.forEach { supply ->
                    OutlinedButton(
                        enabled = !busy && (stock[supply.itemId] ?: 0.0) >= 1.0,
                        onClick = {
                            run {
                                val expense = writeOff.writeOffOne(supply.itemId)
                                message = "Written off 1 ${supply.unit} of ${supply.label} · €%.2f".format(
                                    Locale.ROOT, expense / 100.0)
                            }
                        }
                    ) { Text("Write off 1 ${supply.unit} · ${supply.label}") }
                }
                HorizontalDivider()
                Text("Recent stock movements", style = MaterialTheme.typography.titleMedium)
                if (movements.isEmpty()) Text("No movements recorded yet. Earlier transactions predate stock history.")
                movements.take(30).forEach { movement ->
                    ListItem(
                        headlineContent = { Text(movement.inventoryItemName) },
                        supportingContent = {
                            Text("Day ${movement.gameDay} · ${if (movement.quantityDelta > 0) "Purchase" else "Sale consumption"}")
                        },
                        trailingContent = {
                            Text(if (movement.quantityDelta % 1.0 == 0.0)
                                "%+.0f".format(Locale.ROOT, movement.quantityDelta)
                            else "%+.2f".format(Locale.ROOT, movement.quantityDelta)
                                .trimEnd('0').trimEnd('.'))
                        }
                    )
                }

            }
        }
        OutlinedButton(onClick = { run { } }, enabled = !busy) { Text("Refresh") }
    }
}
