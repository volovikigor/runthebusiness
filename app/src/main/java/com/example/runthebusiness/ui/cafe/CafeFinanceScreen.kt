package com.example.runthebusiness.ui.cafe

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.example.runthebusiness.data.local.CafeFinancialStatementsRepository
import com.example.runthebusiness.data.local.CafeAuditExportRepository
import com.example.runthebusiness.data.local.CafeCashManagementRepository
import com.example.runthebusiness.data.local.CashPosition
import com.example.runthebusiness.data.local.DebugDatabaseRepository
import com.example.runthebusiness.data.local.DebugJournal
import com.example.runthebusiness.data.local.FinancialLine
import com.example.runthebusiness.data.local.FinancialStatements
import com.example.runthebusiness.data.local.DailyProfitLoss
import com.example.runthebusiness.data.local.CafeTransactionDetailRepository
import com.example.runthebusiness.data.local.SaleDetail
import com.example.runthebusiness.data.local.entity.StockMovementEntity
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
    val auditRepo = remember(context) { CafeAuditExportRepository(context) }
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
    var daily by remember { mutableStateOf<List<DailyProfitLoss>>(emptyList()) }
    var showDaily by remember { mutableStateOf(false) }
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
    var exportData by remember { mutableStateOf<String?>(null) }
    val saveAudit = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val data = exportData
        if (uri != null && data != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(data.toByteArray(Charsets.UTF_8))
                } ?: error("Cannot open selected file")
                settlementMessage = "Audit CSV saved. Share the file for review."
            } catch (e: Exception) {
                error = "Export failed: ${e.message}"
            }
        }
        exportData = null
    }

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
                daily = financeRepo.dailyProfitLoss()
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

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Finance", style = MaterialTheme.typography.headlineMedium)
        OutlinedButton(enabled = !busy, onClick = {
            scope.launch {
                busy = true
                try {
                    exportData = auditRepo.exportCsv()
                    saveAudit.launch("cafe_audit.csv")
                } catch (e: Exception) {
                    error = "Export failed: ${e.message}"
                } finally { busy = false }
            }
        }) { Text("Export full audit CSV (journal + inventory + visitors)") }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Forecast", "Bills", "P&L", "BS", "Cash", "Journal").forEach { tab ->
                FilterChip(selected = section == tab, onClick = {
                    if (tab == "Journal" && section != "Journal") previousSection = section
                    section = tab; accountFilter = null; expandedId = null
                }, label = { Text(tab, maxLines = 1) })
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (section == "Forecast") {
            forecast?.let { report ->
                cashPosition?.let { position ->
                    Text("Available now · Cash ${money(position.cashCents)} · Bank ${money(position.bankCents)}")
                    OutlinedButton(enabled = !busy && position.cashCents > 0L, onClick = ::openDeposit) {
                        Text("Deposit cash into bank")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Automatic deposits for bills", Modifier.weight(1f))
                    Switch(checked = autoDeposit, enabled = !busy, onCheckedChange = { enabled ->
                        billsRepo.setAutomaticDeposits(enabled)
                        autoDeposit = enabled
                    })
                }
                Text("On day change, use cash to cover the bank shortfall for bills with automatic payment enabled.",
                    style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { section = "Bills" }) { Text("Review bills") }
                settlementMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                CashForecastSection(report)
            }
            if (forecast == null && !busy && error == null) Text("Start the café to see its cash forecast.")
        }
        if (section == "Bills") {
            val today = forecast?.days?.firstOrNull()?.day
            cashPosition?.let { position ->
                if (supplierBills.isNotEmpty()) {
                    Text("Supplier invoices", style = MaterialTheme.typography.titleMedium)
                    supplierBills.forEach { invoice ->
                        val overdue = today != null && invoice.dueDay < today
                        Text(invoice.label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${money(invoice.amountCents)} · due Day ${invoice.dueDay}" +
                                if (overdue) " · overdue" else "",
                            color = if (overdue) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            enabled = !busy && position.bankCents >= invoice.amountCents,
                            onClick = { paymentAction { cashRepo.paySupplier(invoice.id) } }
                        ) { Text("Pay supplier") }
                    }
                }
            }

            cashPosition?.let { position ->
                val payment = paymentStatus
                val overdue = payment?.rentDaysLate ?: 0
                val payrollLate = payment?.payrollDaysLate ?: 0
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Payment register", style = MaterialTheme.typography.titleMedium)
                        bills.forEach { bill ->
                            HorizontalDivider()
                            Text(bill.label, style = MaterialTheme.typography.titleSmall)
                            Text("Due now: ${money(bill.due)} · Accrued: ${money(bill.accrued)}")
                            Text("Next cycle: Day ${bill.nextDueDay}", style = MaterialTheme.typography.bodySmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Automatic payment", Modifier.weight(1f))
                                Switch(checked = bill.automatic, enabled = !busy,
                                    onCheckedChange = { enabled -> paymentAction { billsRepo.setAutomatic(bill.account, enabled) } })
                            }
                            if (bill.due > 0L) {
                                OutlinedButton(enabled = !busy && position.bankCents >= bill.due,
                                    onClick = { paymentAction { cashRepo.pay(bill.account, amountCents = bill.due) } }) {
                                    Text("Pay due · ${money(bill.due)}")
                                }
                            }
                        }
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
                            Text("Bank funds are insufficient for all bills. Deposit cash before paying.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (position.cashCents > 0L && position.totalDueCents > position.bankCents) {
                            OutlinedButton(enabled = !busy, onClick = { paymentAction {
                                val transfer = minOf(position.cashCents,
                                    (position.totalDueCents - position.bankCents).coerceAtLeast(0L))
                                if (transfer > 0L) cashRepo.transferCashToBank(transfer)
                            } }) { Text("Deposit cash needed for bills") }
                        }
                        if (position.rentPayableCents > 0L) {
                            Button(enabled = !busy && position.bankCents >= position.rentPayableCents,
                                onClick = { paymentAction { cashRepo.pay("2000") } }) {
                                Text("Pay rent · €%.2f".format(Locale.ROOT, position.rentPayableCents / 100.0))
                            }
                        }
                        if (position.salariesPayableCents > 0L) {
                            Button(enabled = !busy && position.bankCents >= position.salariesPayableCents,
                                onClick = { paymentAction { cashRepo.pay("2300") } }) {
                                Text("Pay payroll · €%.2f".format(Locale.ROOT, position.salariesPayableCents / 100.0))
                            }
                        }
                        Text("Bills are accrued in P&L already; settlement only reduces bank and payables.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        val report = statements
        if (report != null && section == "P&L") {
            Text("Profit & Loss", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !showDaily, onClick = { showDaily = false },
                    label = { Text("Cumulative") })
                FilterChip(selected = showDaily, onClick = { showDaily = true },
                    label = { Text("By day") })
            }
            if (showDaily) {
                Text("Rent, gross salaries and depreciation are accrued when a day ends. The current day is provisional. Accruals do not reduce cash until paid.",
                    style = MaterialTheme.typography.bodySmall)
                if (daily.isEmpty()) Text("No journal activity yet")
                daily.forEach { day ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Day ${day.day}", style = MaterialTheme.typography.titleSmall)
                            Text("Revenue: ${money(day.revenueCents)}")
                            Text("Expenses: ${money(day.expenseCents)}")
                            Text("Net profit: ${money(day.profitCents)}",
                                style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            } else {
            StatementSection("Revenue", report.revenue, onAccount = {
                openJournal(it)
            })
            StatementSection("Expenses", report.expenses, onAccount = {
                accountFilter = it; section = "Journal"
            })
            StatementTotal("Net profit / loss", report.netIncomeCents)
            }
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
            HorizontalDivider()
            Text("Payables & liquidity", style = MaterialTheme.typography.titleMedium)
            cashPosition?.let { position ->
                Text("Cash: ${money(position.cashCents)} · Bank: ${money(position.bankCents)}")
                Text("Accrued rent payable: ${money(position.rentPayableCents)}")
                Text("Accrued salaries payable: ${money(position.salariesPayableCents)}")
                Text("Available after these payables: ${money(position.liquidityGapCents)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (position.liquidityGapCents < 0L) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface)
                Text("Paying a liability reduces Bank and the payable; it does not create a second P&L expense.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(enabled = !busy && position.cashCents > 0L, onClick = ::openDeposit) {
                    Text("Deposit cash into bank")
                }
                listOf(
                    Triple("2000", "Pay accrued rent", position.rentPayableCents),
                    Triple("2300", "Pay accrued salaries", position.salariesPayableCents)
                ).forEach { (code, label, due) ->
                    Button(
                        enabled = !busy && due > 0L && position.bankCents >= due,
                        onClick = { paymentAction {
                            val paid = cashRepo.pay(code)
                            settlementMessage = "Paid ${money(paid)} by bank"
                        } }
                    ) { Text("$label · ${money(due)}") }
                }
            }
            if (settlementMessage != null) Text(settlementMessage!!,
                color = MaterialTheme.colorScheme.primary)
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

@Composable
private fun CashForecastSection(forecast: CafeCashForecast) {
    val today = forecast.days.first().day
    val firstShortfall = forecast.days.firstOrNull { it.bankShortfallCents > 0L }
    Text("7-day cash forecast", style = MaterialTheme.typography.titleMedium)
    if (firstShortfall != null) {
        Text(
            "Bank shortfall on Day ${firstShortfall.day} · ${money(firstShortfall.bankShortfallCents)}",
            color = MaterialTheme.colorScheme.error
        )
    }
    val firstCashGap = forecast.days.firstOrNull { it.cashCents < 0L }
    if (firstCashGap != null) {
        Text("Cash shortfall on Day ${firstCashGap.day} · ${money(-firstCashGap.cashCents)}",
            color = MaterialTheme.colorScheme.error)
    }
    Text(
        if (forecast.historyDays > 0) {
            "Sales and stock purchases use the last ${forecast.historyDays} completed days. Bills are assumed paid before trading. Corporate tax uses profit booked so far. Balances exclude deposits."
        } else {
            "Sales and stock purchases will be estimated after the first completed day."
        },
        style = MaterialTheme.typography.bodySmall
    )
    forecast.days.forEach { day ->
        HorizontalDivider()
        Text(
            if (day.day == today) "Today after due payments" else "Day ${day.day} · closing balances",
            style = MaterialTheme.typography.titleSmall
        )
        Text("Cash ${money(day.cashCents)} · Bank ${money(day.bankCents)}")
        day.payments.forEach { payment ->
            val overdue = if (payment.dueDay < today) " · due Day ${payment.dueDay}" else ""
            Text("${payment.label} ${money(payment.amountCents)}$overdue", style = MaterialTheme.typography.bodySmall)
        }
    }
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

private fun readableJournalTitle(description: String): String = when {
    description.startsWith("Cost of sale ") -> "Inventory consumed · product not recorded"
    description.startsWith("Sale ") -> "Customer sale · product not recorded"
    else -> description.replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}"), "transaction")
}
