package com.example.runthebusiness.ui.cafe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.data.local.*
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CafeDashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val menu = remember(context) { CafeMenuRepository(context) }
    val purchasing = remember(context) { CafePurchasingRepository(context) }
    val inventorySetup = remember(context) { CafeInventorySetupRepository(context) }
    val game = remember(context) { GameRepository(context) }
    val dashboard = remember(context) { CafeDashboardRepository(context) }
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<CafeMenuItem>>(emptyList()) }
    var stock by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var started by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        started = dashboard.snapshot() != null
        products = menu.loadMenu()
        stock = purchasing.stock()
    }
    fun run(action: suspend () -> Unit) {
        scope.launch {
            busy = true
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
            listOf("Pricing", "Purchasing", "Inventory").forEachIndexed { index, title ->
                SegmentedButton(
                    selected = section == index,
                    onClick = { section = index; message = null },
                    shape = SegmentedButtonDefaults.itemShape(index, 3),
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
            Button(onClick = { run { game.initializeOpeningGame() } }, enabled = !busy) {
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
                products.forEach { product ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
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
                if (products.isNotEmpty()) {
                    OutlinedButton(onClick = { run { inventorySetup.initializeRealStock() } },
                        enabled = !busy) { Text("Set up real ingredients") }
                }
                CafePurchasingRepository.supplies.forEach { supply ->
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
                        }
                    }
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
                Text("Manual write-offs and batch-level inventory movements are next.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedButton(onClick = { run { } }, enabled = !busy) { Text("Refresh") }
    }
}
