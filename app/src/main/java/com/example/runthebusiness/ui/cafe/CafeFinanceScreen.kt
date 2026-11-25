package com.example.runthebusiness.ui.cafe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.example.runthebusiness.data.local.CafeFinancialStatementsRepository
import com.example.runthebusiness.data.local.CafeCashManagementRepository
import com.example.runthebusiness.data.local.CashPosition
import com.example.runthebusiness.data.local.DebugDatabaseRepository
import com.example.runthebusiness.data.local.DebugJournal
import com.example.runthebusiness.data.local.FinancialLine
import com.example.runthebusiness.data.local.FinancialStatements
import com.example.runthebusiness.data.local.CashFlowLine
import com.example.runthebusiness.data.local.CafeTransactionDetailRepository
import com.example.runthebusiness.data.local.SaleDetail
import com.example.runthebusiness.data.local.entity.StockMovementEntity
import com.example.runthebusiness.ui.components.CafeSectionScreen
import com.example.runthebusiness.ui.components.CafeActionButton
import com.example.runthebusiness.ui.components.CafeLedgerPanel
import com.example.runthebusiness.ui.components.CafeLedgerOptions
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.runthebusiness.data.local.CafeCashForecast
import com.example.runthebusiness.data.local.CafeCashForecastRepository

@Composable
fun CafeFinanceScreen(modifier: Modifier = Modifier, requestedSection: String = "Forecast") {
    val context = LocalContext.current.applicationContext
    val forecastRepo = remember(context) { CafeCashForecastRepository(context) }
    var forecast by remember { mutableStateOf<CafeCashForecast?>(null) }
    val journalRepo = remember(context) { DebugDatabaseRepository(context) }
    val financeRepo = remember(context) { CafeFinancialStatementsRepository(context) }
    val cashRepo = remember(context) { CafeCashManagementRepository(context) }
    val billsRepo = remember(context) { com.example.runthebusiness.data.local.CafePaymentRegisterRepository(context) }
    val consequencesRepo = remember(context) { com.example.runthebusiness.data.local.CafePaymentConsequencesRepository(context) }
    var supplierBills by remember {
        mutableStateOf<List<com.example.runthebusiness.data.local.SupplierBill>>(emptyList())
    }
    var bills by remember { mutableStateOf<List<com.example.runthebusiness.data.local.ScheduledBill>>(emptyList()) }
    var paymentStatus by remember { mutableStateOf<com.example.runthebusiness.data.local.PaymentConsequences?>(null) }
    val detailsRepo = remember(context) { CafeTransactionDetailRepository(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DebugJournal>>(emptyList()) }
    var statements by remember { mutableStateOf<FinancialStatements?>(null) }
    var cashPosition by remember { mutableStateOf<CashPosition?>(null) }
    var autoDeposit by remember { mutableStateOf(billsRepo.automaticDepositsEnabled()) }
    var depositInput by remember { mutableStateOf<String?>(null) }
    fun openDeposit() {
        val cash = cashPosition?.cashCents ?: return
        val gap = forecast?.days?.firstOrNull { it.bankShortfallCents > 0L }?.bankShortfallCents ?: 0L
        val suggestion = minOf(cash, gap)
        depositInput = if (suggestion > 0L) java.math.BigDecimal.valueOf(suggestion, 2).toPlainString() else ""
    }
    var settlementMessage by remember { mutableStateOf<String?>(null) }
    var section by remember { mutableStateOf(requestedSection) }
    LaunchedEffect(requestedSection) { section = requestedSection }
    var previousSection by remember { mutableStateOf("Forecast") }
    var accountFilter by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var expandedSale by remember { mutableStateOf<SaleDetail?>(null) }
    var expandedPurchase by remember { mutableStateOf<List<StockMovementEntity>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = expandedId != null || section != "Forecast") {
        when {
            expandedId != null -> expandedId = null
            section == "Journal" -> {
                section = previousSection
                accountFilter = null
                selectedDay = null
            }
            else -> section = "Forecast"
        }
    }

    fun openJournal(code: String?) {
        previousSection = section
        accountFilter = code
        selectedDay = null
        section = "Journal"
        expandedId = null
    }

    fun refresh() {
        scope.launch {
            busy = true
            error = null
            try {
                forecast = forecastRepo.load()
                statements = financeRepo.load()
                cashPosition = cashRepo.position()
                bills = billsRepo.snapshot()
                supplierBills = billsRepo.supplierBills()
                paymentStatus = consequencesRepo.load()
                entries = journalRepo.journal().asReversed()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally { busy = false }
        }
    }
    fun paymentAction(action: suspend () -> Unit) {
        scope.launch {
            busy = true
            error = null
            try { action(); refresh() }
            catch (e: Exception) { error = e.message; busy = false }
        }
    }
    depositInput?.let { input ->
        val amount = input.trim().replace(',', '.').toBigDecimalOrNull()
            ?.takeIf { it.scale() in 0..2 && it.signum() > 0 }
            ?.let { runCatching { it.movePointRight(2).longValueExact() }.getOrNull() }
        val available = cashPosition?.cashCents ?: 0L
        AlertDialog(
            onDismissRequest = { depositInput = null },
            title = { Text("Deposit cash into bank") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { depositInput = it },
                    label = { Text("Amount in euros") },
                    supportingText = { Text("Available cash ${money(available)}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && amount != null && amount <= available,
                    onClick = {
                        val cents = checkNotNull(amount)
                        depositInput = null
                        paymentAction {
                            cashRepo.transferCashToBank(cents)
                            settlementMessage = "Deposited ${money(cents)} into bank"
                        }
                    }
                ) { Text("Deposit") }
            },
            dismissButton = { TextButton(onClick = { depositInput = null }) { Text("Cancel") } }
        )
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

    CafeSectionScreen(
        modifier, "Finance", listOf("Forecast", "Bills", "P&L", "BS", "Cash Flow", "Journal"), section,
        onSelect = { tab ->
            if (tab == "Journal" && section != "Journal") previousSection = section
            section = tab
            accountFilter = null
            expandedId = null
        }
    ) {
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (section == "Forecast") {
            forecast?.let { report ->
                cashPosition?.let { position ->
                    CafeLedgerPanel {
                        Text("Available now", style = MaterialTheme.typography.titleMedium)
                        FinanceAmountRow("Cash on hand", position.cashCents)
                        FinanceAmountRow("Bank balance", position.bankCents)
                        ForecastWarnings(report)
                        CafeActionButton(enabled = !busy && position.cashCents > 0L, onClick = ::openDeposit) {
                            Text("Deposit cash into bank")
                        }
                    }
                }
                TextButton(onClick = { section = "Bills" }) { Text("Review bills") }
                CashForecastSection(report)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatic deposits for bills", Modifier.weight(1f))
                    Switch(checked = autoDeposit, enabled = !busy, onCheckedChange = { enabled ->
                        billsRepo.setAutomaticDeposits(enabled)
                        autoDeposit = enabled
                    })
                }
                Text("On day change, use cash to cover the bank shortfall for bills with automatic payment enabled.",
                    style = MaterialTheme.typography.bodySmall)
                settlementMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            if (forecast == null && !busy && error == null) Text("Start the café to see its cash forecast.")
        }
        if (section == "Bills") {
            val today = forecast?.days?.firstOrNull()?.day
            cashPosition?.let { position ->
                listOf(true, false).forEach { dueNow ->
                    val invoices = supplierBills.filter { (today != null && it.dueDay <= today) == dueNow }
                    val recurring = bills.filter { (it.due > 0L) == dueNow }
                        .sortedBy { it.nextDueDay - if (dueNow) it.periodDays else 0 }
                    if (invoices.isNotEmpty() || recurring.isNotEmpty()) {
                        Text(if (dueNow) "Due and overdue" else "Upcoming", style = MaterialTheme.typography.titleMedium)
                    }
                    invoices.forEach { invoice ->
                        val overdue = today != null && invoice.dueDay < today
                        CafeLedgerPanel {
                        FinanceAmountRow(invoice.label.removePrefix("Supplier invoice · "), invoice.amountCents)
                        Text("Due Day ${invoice.dueDay}" + if (overdue) " · overdue" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(enabled = !busy && position.bankCents >= invoice.amountCents,
                            onClick = { paymentAction { cashRepo.paySupplier(invoice.id) } }
                        ) { Text("Pay supplier") }
                        }
                    }
                    recurring.forEach { bill ->
                        val dueDay = if (dueNow) bill.nextDueDay - bill.periodDays else bill.nextDueDay
                        val overdue = dueNow && today != null && dueDay < today
                        CafeLedgerPanel {
                        FinanceAmountRow(bill.label, if (dueNow) bill.due else bill.accrued)
                        Text(
                            if (dueNow) "Due since Day $dueDay · next cycle Day ${bill.nextDueDay}"
                            else "Accrued so far · next cycle Day $dueDay",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-pay", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = bill.automatic, enabled = !busy,
                                onCheckedChange = { enabled -> paymentAction { billsRepo.setAutomatic(bill.account, enabled) } })
                            if (dueNow) {
                                TextButton(enabled = !busy && position.bankCents >= bill.due,
                                    onClick = { paymentAction { cashRepo.pay(bill.account, amountCents = bill.due) } }
                                ) { Text("Pay due") }
                            }
                        }
                        }
                    }
                }
                val payment = paymentStatus
                val overdue = payment?.rentDaysLate ?: 0
                val payrollLate = payment?.payrollDaysLate ?: 0
                CafeLedgerPanel {
                    HorizontalDivider()
                    Text("Early settlement & cash deposits", style = MaterialTheme.typography.titleSmall)
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
                        Text("Bank funds are short for accrued rent and payroll.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (position.cashCents > 0L && position.totalDueCents > position.bankCents) {
                        OutlinedButton(enabled = !busy, onClick = { paymentAction {
                            val transfer = minOf(position.cashCents,
                                (position.totalDueCents - position.bankCents).coerceAtLeast(0L))
                            if (transfer > 0L) cashRepo.transferCashToBank(transfer)
                        } }) { Text("Deposit for rent and payroll") }
                    }
                    if (position.rentPayableCents > 0L) {
                        CafeActionButton(enabled = !busy && position.bankCents >= position.rentPayableCents,
                            onClick = { paymentAction { cashRepo.pay("2000") } }) {
                            Text("Pay rent · €%.2f".format(Locale.ROOT, position.rentPayableCents / 100.0))
                        }
                    }
                    if (position.salariesPayableCents > 0L) {
                        CafeActionButton(enabled = !busy && position.bankCents >= position.salariesPayableCents,
                            onClick = { paymentAction { cashRepo.pay("2300") } }) {
                            Text("Pay payroll · €%.2f".format(Locale.ROOT, position.salariesPayableCents / 100.0))
                        }
                    }
                }
            }
        }
        val report = statements
        if (report != null && section in setOf("P&L", "BS", "Cash Flow")) {
            ComparativeReport(section, report, onAccount = { openJournal(it) })
        }
        if (section == "Journal") {
            Text("General ledger · newest first", style = MaterialTheme.typography.titleMedium)
            if (accountFilter != null) {
                TextButton(onClick = { accountFilter = null }) { Text("Clear account $accountFilter") }
            }
            CafeLedgerOptions(listOf("All days") + days.take(3).map { "Day $it" },
                selectedDay?.let { "Day $it" } ?: "All days") { label ->
                selectedDay = label.removePrefix("Day ").toIntOrNull()
            }
            Text("${filtered.size} entries", style = MaterialTheme.typography.labelMedium)
            filtered.take(100).forEach { entry ->
                HorizontalDivider()
                val expanded = expandedId == entry.id
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel =
                        if (expanded) "Collapse entry" else "Expand entry"
                    ) { expandedId = if (expanded) null else entry.id }
                        .heightIn(min = 48.dp).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(journalHeading(entry.id, entry.description), style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("Day ${entry.day}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(if (expanded) "▴" else "▾", Modifier.padding(start = 8.dp))
                }
                if (expanded) {
                    CafeLedgerPanel {
                        HorizontalDivider()
                        FinanceTableRow("Account", listOf("Debit", "Credit"), heading = true)
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
                        if (entry.id.startsWith("cogs_")) expandedSale?.stock?.let { stock ->
                            if (stock.isNotEmpty()) {
                                Text("Recipe cost breakdown · actual FIFO",
                                    style = MaterialTheme.typography.titleSmall)
                                stock.filter { it.quantityDelta < 0.0 }.forEach { movement ->
                                    val quantity = -movement.quantityDelta
                                    val formattedQuantity = if (quantity % 1.0 == 0.0)
                                        "%.0f".format(Locale.ROOT, quantity)
                                    else "%.2f".format(Locale.ROOT, quantity).trimEnd('0').trimEnd('.')
                                    val amountCents = kotlin.math.round(quantity * movement.unitCostCents).toLong()
                                    val unit = when {
                                        movement.inventoryItemId.startsWith("kitchen_") ->
                                            when (movement.inventoryItemId) {
                                                "kitchen_bread" -> "pcs"
                                                "kitchen_dressing" -> "ml"
                                                else -> "g"
                                            }
                                        else -> "units"
                                    }
                                    Text("${movement.inventoryItemName}: $formattedQuantity $unit × " +
                                        "${money(movement.unitCostCents)}/$unit = ${money(amountCents)}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                val totalCents = kotlin.math.round(
                                    stock.filter { it.quantityDelta < 0.0 }
                                        .sumOf { -it.quantityDelta * it.unitCostCents }
                                ).toLong()
                                Text("Total ingredient / finished-goods cost: ${money(totalCents)}",
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        expandedPurchase.forEach { movement ->
                            Text("${movement.inventoryItemName}: %.0f received".format(
                                Locale.ROOT, movement.quantityDelta),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        entry.lines.forEach { line ->
                            FinanceTableRow(line.account, listOf(
                                if (line.debitCents > 0) money(line.debitCents) else "—",
                                if (line.creditCents > 0) money(line.creditCents) else "—"
                            ))
                        }
                        HorizontalDivider()
                        FinanceTableRow("Total", listOf(
                            money(entry.lines.sumOf { it.debitCents }),
                            money(entry.lines.sumOf { it.creditCents })
                        ), heading = true)
                    }
                }
            }
            if (filtered.size > 100) Text("Showing 100 newest entries; narrow by day or account.")
        }
        OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh") }
    }
}

private data class ComparisonRow(
    val name: String,
    val current: Long?,
    val previous: Long?,
    val account: String? = null
)

@Composable
private fun ComparativeReport(
    section: String,
    report: FinancialStatements,
    onAccount: (String) -> Unit
) {
    var showDaily by remember { mutableStateOf(false) }
    val current = report.current
    val previous = report.previous
    fun accounts(now: List<FinancialLine>, before: List<FinancialLine>?): List<ComparisonRow> =
        (now + before.orEmpty()).distinctBy { it.code }.map { line ->
            ComparisonRow(line.name, now.firstOrNull { it.code == line.code }?.amountCents ?: 0L,
                before?.let { rows -> rows.firstOrNull { it.code == line.code }?.amountCents ?: 0L },
                line.code.takeUnless { it == "RESULT" })
        }
    val monthly = section != "BS"
    val heading = when (section) {
        "P&L" -> "Profit & Loss"
        "BS" -> "Balance Sheet"
        else -> "Cash Flow"
    }
    Text(heading, style = MaterialTheme.typography.titleLarge)
    val currentLabel = if (monthly) "Day ${current.days.first}–${current.days.last}" else "Day ${current.days.last}"
    val previousLabel = if (monthly) previous?.days?.let { "Day ${it.first}–${it.last}" }
        else previous?.days?.last?.let { "Day $it" }
    Text(
        if (previousLabel == null) "Current: $currentLabel · comparison starts on Day 31"
        else "Current: $currentLabel  ·  Previous: $previousLabel",
        style = MaterialTheme.typography.bodySmall
    )
    if (monthly && previous != null && current.days.count() < 30) {
        Text("Current month to date vs. the full previous month.", style = MaterialTheme.typography.bodySmall)
    }
    when (section) {
        "P&L" -> {
            ComparisonTable("Results", listOf(
                ComparisonRow("Revenue", current.revenueCents, previous?.revenueCents),
                ComparisonRow("Expenses", current.expenseCents, previous?.expenseCents),
                ComparisonRow("Net profit", current.profitCents, previous?.profitCents)
            ), emphasis = "Net profit", grid = true)
            ComparisonTable("Expense breakdown", accounts(current.expenses, previous?.expenses), onAccount = onAccount, grid = true)
            ComparisonTable("Sales breakdown", accounts(current.revenue, previous?.revenue), onAccount = onAccount, grid = true)
            val nowServed = current.performance.sumOf { it.servedGroups }.toLong()
            val beforeServed = previous?.performance?.sumOf { it.servedGroups }?.toLong()
            ComparisonTable("Customer activity", listOf(
                ComparisonRow("Guests arrived", current.performance.sumOf { it.arrivingGuests }.toLong(),
                    previous?.performance?.sumOf { it.arrivingGuests }?.toLong()),
                ComparisonRow("Groups served", nowServed, beforeServed),
                ComparisonRow("Groups lost", current.performance.sumOf { it.lostGroups }.toLong(),
                    previous?.performance?.sumOf { it.lostGroups }?.toLong())
            ), format = { it.toString() }, grid = true)
            val nowAverage = nowServed.takeIf { it > 0L }?.let { current.revenueCents / it }
            val beforeAverage = previous?.let { period ->
                beforeServed?.takeIf { it > 0L }?.let { period.revenueCents / it }
            }
            if (nowAverage != null || beforeAverage != null) {
                ComparisonTable("Sales per served group", listOf(
                    ComparisonRow("Excluding VAT", nowAverage, beforeAverage)
                ), grid = true)
            }
            OutlinedButton(onClick = { showDaily = !showDaily }) {
                Text(if (showDaily) "Hide daily results" else "Show daily results")
            }
            if (showDaily) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        FinanceTableRow("Day", listOf("Revenue", "Expenses", "Net"), heading = true)
                        current.dailyResults.forEach { day ->
                            FinanceTableRow(day.day.toString(), listOf(money(day.revenueCents),
                                money(day.expenseCents), money(day.revenueCents - day.expenseCents)))
                        }
                    }
                }
            }
        }
        "BS" -> {
            val sections = listOf(
                Triple("Assets", current.assets, previous?.assets),
                Triple("Liabilities", current.liabilities, previous?.liabilities),
                Triple("Equity", current.equity, previous?.equity)
            )
            sections.forEach { (label, now, before) ->
                ComparisonTable(label, accounts(now, before) + ComparisonRow(
                    "Total $label", now.sumOf { it.amountCents }, before?.sumOf { it.amountCents }
                ), emphasis = "Total $label", onAccount = onAccount, grid = true)
            }
            if (current.assets.sumOf { it.amountCents } !=
                current.liabilities.sumOf { it.amountCents } + current.equity.sumOf { it.amountCents }
            ) {
                Text("Balance sheet does not balance. Inspect the journal.", color = MaterialTheme.colorScheme.error)
            }
        }
        else -> {
            ComparisonTable("Cash and bank combined", listOf(
                ComparisonRow("Opening balance", current.openingCashCents, previous?.openingCashCents),
                ComparisonRow("Net cash flow", current.closingCashCents - current.openingCashCents,
                    previous?.let { it.closingCashCents - it.openingCashCents }),
                ComparisonRow("Closing balance", current.closingCashCents, previous?.closingCashCents)
            ), emphasis = "Net cash flow")
            CashFlowLine.entries.groupBy { it.section }.forEach { (label, lines) ->
                val movements = lines.mapNotNull { line ->
                    val now = current.cashFlows[line] ?: 0L
                    val before = previous?.let { it.cashFlows[line] ?: 0L }
                    if (now == 0L && (before ?: 0L) == 0L) null else ComparisonRow(line.label, now, before)
                }
                if (movements.isNotEmpty()) {
                    ComparisonTable(label, movements + ComparisonRow("Net $label", movements.sumOf { it.current ?: 0L },
                        previous?.let { movements.sumOf { it.previous ?: 0L } }),
                        emphasis = "Net $label")
                }
            }
            Text("Cash deposits and other transfers between cash and bank are internal.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ComparisonTable(
    title: String,
    rows: List<ComparisonRow>,
    emphasis: String? = null,
    format: (Long) -> String = ::money,
    grid: Boolean = false,
    onAccount: (String) -> Unit = {}
) {
    if (rows.isEmpty()) return
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Text(title, Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall)
            FinanceTableRow("", listOf("Current", "Previous", "Change"), heading = true,
                modifier = Modifier.padding(horizontal = 10.dp), labelWeight = 1.6f, grid = grid)
            rows.forEach { row ->
                val change = row.current?.let { now -> row.previous?.let { now - it } }
                val interaction = if (row.account != null) Modifier.clickable(
                    role = Role.Button, onClickLabel = "Open account in journal"
                ) { onAccount(row.account) }.heightIn(min = 40.dp) else Modifier
                FinanceTableRow(
                    row.name, listOf(row.current?.let(format) ?: "—", row.previous?.let(format) ?: "—",
                        change?.let { (if (it > 0L) "+" else "") + format(it) } ?: "—"),
                    modifier = interaction.then(
                        if (row.name == emphasis) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                        else Modifier
                    ).padding(horizontal = 10.dp),
                    labelWeight = 1.6f, grid = grid
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FinanceAmountRow(label: String, cents: Long, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        LedgerAmount(money(cents), color = if (cents < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LedgerAmount(
    value: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Text(value, modifier, color = color, textAlign = TextAlign.End,
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
}

@Composable
private fun FinanceTableRow(
    label: String,
    values: List<String>,
    heading: Boolean = false,
    modifier: Modifier = Modifier,
    labelWeight: Float = if (values.size == 3) 0.65f else 1.2f,
    grid: Boolean = false
) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.23f)
    val gridLines = if (grid) Modifier.drawBehind {
        val gap = 4.dp.toPx()
        val unit = (size.width - gap * values.size) / (labelWeight + values.size)
        repeat(values.size) { index ->
            val x = unit * (labelWeight + index) + gap * (index + 0.5f)
            drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        val y = size.height - 0.5.dp.toPx()
        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
    } else Modifier
    Row(modifier.fillMaxWidth().then(gridLines).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, Modifier.weight(labelWeight),
            style = if (heading) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall)
        values.forEach { LedgerAmount(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun ForecastWarnings(forecast: CafeCashForecast) {
    forecast.days.firstOrNull { it.bankShortfallCents > 0L }?.let {
        Text("Bank shortfall on Day ${it.day} · ${money(it.bankShortfallCents)}",
            color = MaterialTheme.colorScheme.error)
    }
    forecast.days.firstOrNull { it.cashCents < 0L }?.let {
        Text("Cash shortfall on Day ${it.day} · ${money(-it.cashCents)}",
            color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CashForecastSection(forecast: CafeCashForecast) {
    val today = forecast.days.first().day
    Text("7-day cash forecast", style = MaterialTheme.typography.titleMedium)
    Text(
        "Staff cost today ${money(forecast.staffCostTodayCents)} · Day ${today + 1} ${money(forecast.staffCostTomorrowCents)}",
        style = MaterialTheme.typography.bodySmall
    )
    Text("Expected balances after payments. Cash is the money in the café. Bills are paid from the bank.",
        style = MaterialTheme.typography.bodySmall)
    FinanceTableRow("Day", listOf("Cash", "Bank"), heading = true)
    forecast.days.forEach { day ->
        HorizontalDivider()
        FinanceTableRow(if (day.day == today) "Today" else "${day.day}",
            listOf(money(day.cashCents), money(day.bankCents)))
        day.payments.forEach { payment ->
            val overdue = if (payment.dueDay < today) " · due Day ${payment.dueDay}" else ""
            Text("${payment.label} ${money(payment.amountCents)}$overdue", style = MaterialTheme.typography.bodySmall)
        }
    }
    Text(
        if (forecast.historyDays > 0) {
            "Sales and stock purchases use the last ${forecast.historyDays} completed days. Bills are assumed paid before trading. Corporate tax uses profit booked so far. Balances exclude deposits."
        } else {
            "Sales and stock purchases will be estimated after the first completed day."
        },
        style = MaterialTheme.typography.bodySmall
    )
    if (forecast.installments.isNotEmpty()) {
        HorizontalDivider()
        Text("Next loan installments", style = MaterialTheme.typography.titleMedium)
        Text("Upcoming amounts from the original schedule. Loan payments are not posted yet.",
            style = MaterialTheme.typography.bodySmall)
        forecast.installments.take(3).forEach { installment ->
            Text("Day ${installment.dueDay} · ${money(installment.totalCents)}")
            Text(
                "Principal ${money(installment.principalCents)} · Interest ${money(installment.interestCents)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun money(cents: Long) = "€%.2f".format(Locale.ROOT, cents / 100.0)

private fun journalHeading(id: String, description: String): String = when {
    id.startsWith("sale_") -> "Customer sale"
    id.startsWith("cogs_") -> "Cost of sale"
    id.startsWith("purchase_") -> description.substringBefore(",")
    else -> readableJournalTitle(description)
}

private fun readableJournalTitle(description: String): String = when {
    description.startsWith("Cost of sale ") -> "Inventory consumed · product not recorded"
    description.startsWith("Sale ") -> "Customer sale · product not recorded"
    else -> description.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "transaction")
}
