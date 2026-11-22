package com.example.runthebusiness.ui.cafe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.data.local.CafeFinancialStatementsRepository
import com.example.runthebusiness.data.local.DebugDatabaseRepository
import com.example.runthebusiness.data.local.DebugJournal
import com.example.runthebusiness.data.local.FinancialLine
import com.example.runthebusiness.data.local.FinancialStatements
import com.example.runthebusiness.data.local.CafeTransactionDetailRepository
import com.example.runthebusiness.data.local.SaleDetail
import com.example.runthebusiness.data.local.entity.StockMovementEntity
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CafeFinanceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val journalRepo = remember(context) { DebugDatabaseRepository(context) }
    val financeRepo = remember(context) { CafeFinancialStatementsRepository(context) }
    val detailsRepo = remember(context) { CafeTransactionDetailRepository(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DebugJournal>>(emptyList()) }
    var statements by remember { mutableStateOf<FinancialStatements?>(null) }
    var section by remember { mutableStateOf("P&L") }
    var previousSection by remember { mutableStateOf("P&L") }
    var accountFilter by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var expandedSale by remember { mutableStateOf<SaleDetail?>(null) }
    var expandedPurchase by remember { mutableStateOf<List<StockMovementEntity>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = expandedId != null || section != "P&L") {
        when {
            expandedId != null -> expandedId = null
            section == "Journal" -> {
                section = previousSection
                accountFilter = null
                selectedDay = null
            }
            else -> section = "P&L"
        }
    }

    fun openJournal(code: String?) {
        previousSection = section
        accountFilter = code
        section = "Journal"
        expandedId = null
    }

    fun refresh() {
        scope.launch {
            busy = true
            error = null
            try {
                statements = financeRepo.load()
                entries = journalRepo.journal().asReversed()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(expandedId) {
        expandedSale = null
        expandedPurchase = emptyList()
        val id = expandedId ?: return@LaunchedEffect
        when {
            id.startsWith("sale_") -> expandedSale = detailsRepo.sale(id.removePrefix("sale_"))
            id.startsWith("cogs_") -> expandedSale = detailsRepo.sale(id.removePrefix("cogs_"))
            id.startsWith("purchase_") ->
                expandedPurchase = detailsRepo.purchase("purchase_batch_" + id.removePrefix("purchase_"))
        }
    }
    val days = entries.map { it.day }.distinct().sortedDescending()
    val filtered = entries.filter { entry ->
        (selectedDay == null || entry.day == selectedDay) &&
            (accountFilter == null || entry.lines.any { it.account.substringBefore(" ") == accountFilter })
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Finance", style = MaterialTheme.typography.headlineMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("P&L", "BS", "Cash", "Journal").forEachIndexed { index, tab ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, 4),
                    selected = section == tab,
                    onClick = {
                        if (tab == "Journal" && section != "Journal") previousSection = section
                        section = tab; accountFilter = null; expandedId = null
                    },
                    label = { Text(tab) }
                )
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        val report = statements
        if (report != null && section == "P&L") {
            Text("Profit & Loss · cumulative", style = MaterialTheme.typography.titleMedium)
            StatementSection("Revenue", report.revenue, onAccount = {
                openJournal(it)
            })
            StatementSection("Expenses", report.expenses, onAccount = {
                accountFilter = it; section = "Journal"
            })
            StatementTotal("Net profit / loss", report.netIncomeCents)
            Text("Figures are cumulative for the whole game; day-specific P&L is not yet implemented.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (report != null && section == "BS") {
            Text("Balance Sheet · current balances", style = MaterialTheme.typography.titleMedium)
            StatementSection("Assets", report.assets, onAccount = {
                accountFilter = it; section = "Journal"
            })
            StatementSection("Liabilities", report.liabilities, onAccount = {
                accountFilter = it; section = "Journal"
            })
            StatementSection("Equity and current result", report.equity, onAccount = {
                openJournal(if (it == "CURRENT_RESULT") null else it)
            })
            val assets = report.assets.sumOf { it.amountCents }
            val liabilitiesAndEquity = report.liabilities.sumOf { it.amountCents } +
                report.equity.sumOf { it.amountCents }
            StatementTotal("Assets", assets)
            StatementTotal("Liabilities + equity", liabilitiesAndEquity)
            if (assets != liabilitiesAndEquity) {
                Text("Balance sheet does not balance: inspect the journal.",
                    color = MaterialTheme.colorScheme.error)
            }
        }
        if (report != null && section == "Cash") {
            Text("Cash & bank balances", style = MaterialTheme.typography.titleMedium)
            StatementSection("Accounts", report.cashMovements, onAccount = {
                accountFilter = it; section = "Journal"
            })
            StatementTotal("Cash + bank", report.cashMovements.sumOf { it.amountCents })
            Text("This is a cash/bank balance view, NOT a full cash-flow statement. Operating, investing and financing cash flows will be classified in a later milestone.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (section == "Journal") {
            Text("General ledger · newest first", style = MaterialTheme.typography.titleMedium)
            if (accountFilter != null) {
                AssistChip(onClick = { accountFilter = null }, label = { Text("Account $accountFilter ×") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = selectedDay == null, onClick = { selectedDay = null },
                    label = { Text("All days") })
                days.take(3).forEach { day ->
                    FilterChip(selected = selectedDay == day, onClick = { selectedDay = day },
                        label = { Text("Day $day") })
                }
            }
            Text("${filtered.size} entries", style = MaterialTheme.typography.labelMedium)
            filtered.take(100).forEach { entry ->
                Card(onClick = { expandedId = if (expandedId == entry.id) null else entry.id }) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(readableJournalTitle(entry.description), style = MaterialTheme.typography.titleSmall)
                        Text("Day ${entry.day} · ${entry.lines.size} accounting lines",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (expandedId == entry.id) {
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth()) {
                                Text("Account", Modifier.weight(1.6f), style = MaterialTheme.typography.labelSmall)
                                Text("Debit", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                Text("Credit", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                            }
                            if (!entry.description.startsWith("Sale ") && !entry.description.startsWith("Cost of sale ")) {
                                Text(entry.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val legacy = entry.description.startsWith("Sale ") ||
                                entry.description.startsWith("Cost of sale ")
                            if (legacy) {
                                if (expandedSale?.products?.isEmpty() == true) {
                                    Text("Historical sale: product detail was not recorded.",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (entry.id.startsWith("sale_")) expandedSale?.products?.forEach { item ->
                                Text("${item.quantity} × ${item.productName} · ${money(item.unitPriceCents)} each",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            if (entry.id.startsWith("cogs_")) expandedSale?.stock?.forEach { movement ->
                                Text("${movement.inventoryItemName}: %.3f consumed from FIFO batch".format(
                                    Locale.ROOT, -movement.quantityDelta),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            expandedPurchase.forEach { movement ->
                                Text("${movement.inventoryItemName}: %.0f received".format(
                                    Locale.ROOT, movement.quantityDelta),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            entry.lines.forEach { line ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(line.account, Modifier.weight(1.6f), style = MaterialTheme.typography.bodySmall)
                                    Text(if (line.debitCents > 0) money(line.debitCents) else "—",
                                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Text(if (line.creditCents > 0) money(line.creditCents) else "—",
                                        Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Total", Modifier.weight(1.6f),
                                    style = MaterialTheme.typography.labelSmall)
                                Text(money(entry.lines.sumOf { it.debitCents }), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall)
                                Text(money(entry.lines.sumOf { it.creditCents }), Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            if (filtered.size > 100) Text("Showing 100 newest entries; narrow by day or account.")
        }
        OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
    }
}

@Composable
private fun StatementSection(title: String, lines: List<FinancialLine>, onAccount: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    if (lines.isEmpty()) Text("No transactions", style = MaterialTheme.typography.bodySmall)
    lines.forEach { line ->
        Card(onClick = { onAccount(line.code) }) {
            Row(Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text(line.name, Modifier.weight(1f))
                Text(money(line.amountCents))
            }
        }
    }
    StatementTotal("Total $title", lines.sumOf { it.amountCents })
}

@Composable
private fun StatementTotal(label: String, cents: Long) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(money(cents), style = MaterialTheme.typography.titleSmall)
    }
}

private fun money(cents: Long) = "€%.2f".format(Locale.ROOT, cents / 100.0)

private fun readableJournalTitle(description: String): String = when {
    description.startsWith("Cost of sale ") -> "Inventory consumed · product not recorded"
    description.startsWith("Sale ") -> "Customer sale · product not recorded"
    else -> description.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "transaction")
}
