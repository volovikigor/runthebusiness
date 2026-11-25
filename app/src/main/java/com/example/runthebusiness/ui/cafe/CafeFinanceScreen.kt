package com.example.runthebusiness.ui.cafe

import com.example.runthebusiness.ui.components.cafeLabel
import com.example.runthebusiness.ui.components.cafeJournalAccount
import com.example.runthebusiness.ui.components.cafeProductName
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.runthebusiness.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import com.example.runthebusiness.data.local.CafeFinancialStatementsRepository
import com.example.runthebusiness.data.local.CafeCashManagementRepository
import com.example.runthebusiness.data.local.CafeLoanPaymentRepository
import com.example.runthebusiness.data.local.CafeCreditLine
import com.example.runthebusiness.data.local.CafeCreditLineRepository
import com.example.runthebusiness.data.local.CashPosition
import com.example.runthebusiness.data.local.DebugDatabaseRepository
import com.example.runthebusiness.data.local.DebugJournal
import com.example.runthebusiness.data.local.FinancialLine
import com.example.runthebusiness.data.local.FinancialStatements
import com.example.runthebusiness.data.local.financialMonthStart
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
import java.math.BigDecimal
import java.math.RoundingMode
import com.example.runthebusiness.data.local.CafeCashForecast
import com.example.runthebusiness.data.local.CafeCashForecastRepository

@Composable
fun CafeFinanceScreen(modifier: Modifier = Modifier, requestedSection: String = "Forecast") {
    val context = LocalContext.current.applicationContext
    val forecastRepo = remember(context) { CafeCashForecastRepository(context) }
    var forecast by remember { mutableStateOf<CafeCashForecast?>(null) }
    val journalRepo = remember(context) { DebugDatabaseRepository(context) }
    val financeRepo = remember(context) { CafeFinancialStatementsRepository(context) }
    val creditRepo = remember(context) { CafeCreditLineRepository(context) }
    var credit by remember { mutableStateOf<CafeCreditLine?>(null) }
    val loanRepo = remember(context) { CafeLoanPaymentRepository(context) }
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
                credit = if (forecast != null) creditRepo.position() else null
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
            title = { Text(stringResource(R.string.deposit_bank)) },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { depositInput = it },
                    label = { Text(stringResource(R.string.amount_euros)) },
                    supportingText = { Text(stringResource(R.string.cash_available, money(available))) },
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
                ) { Text(stringResource(R.string.deposit)) }
            },
            dismissButton = { TextButton(onClick = { depositInput = null }) { Text(stringResource(R.string.cancel)) } }
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
        CafeGuideButton(section, when (section) {
            "Bills" -> R.string.guide_bills
            "P&L" -> R.string.guide_pl
            "BS" -> R.string.guide_bs
            "Cash Flow" -> R.string.guide_cash
            "Journal" -> R.string.guide_journal
            else -> R.string.guide_forecast
        })
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        if (section == "Forecast") {
            forecast?.let { report ->
                cashPosition?.let { position ->
                    CafeLedgerPanel {
                        Text(stringResource(R.string.available_now), style = MaterialTheme.typography.titleMedium)
                        FinanceAmountRow("Cash on hand", position.cashCents)
                        FinanceAmountRow("Bank balance", position.bankCents)
                        ForecastWarnings(report)
                        CafeActionButton(enabled = !busy && position.cashCents > 0L, onClick = ::openDeposit) {
                            Text(stringResource(R.string.deposit_bank))
                        }
                    }
                }
                TextButton(onClick = { section = "Bills" }) { Text(stringResource(R.string.kpi_review_bills)) }
                credit?.let { line ->
                    CreditLinePanel(
                        credit = line,
                        bankCents = cashPosition?.bankCents ?: 0L,
                        forecastGap = report.days.firstOrNull { it.bankShortfallCents > 0L }?.bankShortfallCents ?: 0L,
                        busy = busy,
                        onDraw = { cents -> paymentAction { creditRepo.draw(cents) } },
                        onRepay = { cents -> paymentAction { creditRepo.repay(cents) } }
                    )
                }
                CashForecastSection(report)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.deposits), Modifier.weight(1f))
                    Switch(checked = autoDeposit, enabled = !busy, onCheckedChange = { enabled ->
                        billsRepo.setAutomaticDeposits(enabled)
                        autoDeposit = enabled
                    })
                }
                Text(stringResource(R.string.auto_deposit_help),
                    style = MaterialTheme.typography.bodySmall)
                settlementMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
            if (forecast == null && !busy && error == null) Text(stringResource(R.string.start_for_forecast))
        }
        if (section == "Bills") {
            val today = forecast?.days?.firstOrNull()?.day
            cashPosition?.let { position ->
                val loanDue = forecast?.installments?.firstOrNull { today != null && it.dueDay <= today }
                if (loanDue != null) {
                    CafeLedgerPanel {
                        Text(stringResource(R.string.loan_due, money(loanDue.totalCents)),
                            style = MaterialTheme.typography.titleMedium)
                        CafeActionButton(enabled = !busy && position.bankCents >= loanDue.totalCents,
                            onClick = { paymentAction { loanRepo.settleDue(automatic = false) } }) {
                            Text(stringResource(R.string.loan_pay))
                        }
                        if (position.bankCents < loanDue.totalCents) {
                            Text(stringResource(R.string.purchase_bank_short), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = ::openDeposit, enabled = !busy && position.cashCents > 0L) {
                                Text(stringResource(R.string.deposit_bank))
                            }
                        }
                    }
                }
                listOf(true, false).forEach { dueNow ->
                    val invoices = supplierBills.filter { (today != null && it.dueDay <= today) == dueNow }
                    val recurring = bills.filter { (it.due > 0L) == dueNow }
                        .sortedBy { it.nextDueDay - if (dueNow) it.periodDays else 0 }
                    if (invoices.isNotEmpty() || recurring.isNotEmpty()) {
                        Text(stringResource(if (dueNow) R.string.due_overdue else R.string.upcoming), style = MaterialTheme.typography.titleMedium)
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
                        ) { Text(stringResource(R.string.pay_supplier)) }
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
                            Text(stringResource(R.string.auto_pay), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Switch(checked = bill.automatic, enabled = !busy,
                                onCheckedChange = { enabled -> paymentAction { billsRepo.setAutomatic(bill.account, enabled) } })
                            if (dueNow) {
                                TextButton(enabled = !busy && position.bankCents >= bill.due,
                                    onClick = { paymentAction { cashRepo.pay(bill.account, amountCents = bill.due) } }
                                ) { Text(stringResource(R.string.pay_due)) }
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
                    Text(stringResource(R.string.early_settlement), style = MaterialTheme.typography.titleSmall)
                    Text("Bank: €%.2f · Cash: €%.2f".format(Locale.ROOT,
                        position.bankCents / 100.0, position.cashCents / 100.0))
                    Text("Rent payable: €%.2f · %d days late".format(Locale.ROOT,
                        position.rentPayableCents / 100.0, overdue))
                    Text("Payroll payable: €%.2f · %d days late".format(Locale.ROOT,
                        position.salariesPayableCents / 100.0, payrollLate))
                    if (payment?.rentShutdown == true) {
                        Text(stringResource(R.string.trade_blocked),
                            color = MaterialTheme.colorScheme.error)
                    } else if (overdue >= 5) {
                        Text(stringResource(R.string.rent_shutdown_rule),
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (payment?.staffDisruption == true) {
                        Text(stringResource(R.string.late_payroll),
                            color = MaterialTheme.colorScheme.error)
                    } else if (payrollLate >= 1) {
                        Text(stringResource(R.string.payroll_disruption_rule),
                            color = MaterialTheme.colorScheme.error)
                    }
                    if (position.bankCents < position.totalDueCents &&
                        position.cashCents > 0L) {
                        Text(stringResource(R.string.bank_short),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (position.cashCents > 0L && position.totalDueCents > position.bankCents) {
                        OutlinedButton(enabled = !busy, onClick = { paymentAction {
                            val transfer = minOf(position.cashCents,
                                (position.totalDueCents - position.bankCents).coerceAtLeast(0L))
                            if (transfer > 0L) cashRepo.transferCashToBank(transfer)
                        } }) { Text(stringResource(R.string.deposit_payroll)) }
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
            if (section == "P&L" || section == "BS") {
                Text(stringResource(R.string.finance_journal_hint), style = MaterialTheme.typography.bodySmall)
            }
            ComparativeReport(section, report, onAccount = { openJournal(it) })
        }
        if (section == "Journal") {
            Text(stringResource(R.string.ledger_newest), style = MaterialTheme.typography.titleMedium)
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
                        Text(stringResource(R.string.day_number, entry.day),
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
                                Text(stringResource(R.string.historic_sale),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (entry.id.startsWith("sale_")) expandedSale?.products?.forEach { item ->
                            Text("${item.quantity} × ${cafeProductName(item.productId, item.productName)} · ${money(item.unitPriceCents)} each",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        if (entry.id.startsWith("cogs_")) expandedSale?.stock?.let { stock ->
                            if (stock.isNotEmpty()) {
                                Text(stringResource(R.string.fifo_breakdown),
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
            if (filtered.size > 100) Text(stringResource(R.string.journal_limit))
        }
        OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text(stringResource(R.string.refresh)) }
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
    val liveLabel = "Month to date"
    val closedLabel = "Closed months"
    val closed = report.closedMonths
    var showClosed by remember(report.time.day, closed != null) {
        mutableStateOf(closed != null && report.time.day == financialMonthStart(report.time.day))
    }
    val comparison = if (showClosed && closed != null) closed else report.monthToDate
    val current = comparison.current
    val previous = comparison.previous
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
    Text(cafeLabel(heading), style = MaterialTheme.typography.titleLarge)
    if (closed != null) {
        CafeLedgerOptions(listOf(liveLabel, closedLabel), if (showClosed) closedLabel else liveLabel) {
            showClosed = it == closedLabel
        }
    }
    fun periodLabel(days: IntRange): String = when {
        !monthly || days.first == days.last -> "Day ${days.last}"
        else -> "Days ${days.first}–${days.last}"
    }
    val currentLabel = periodLabel(current.days)
    val previousLabel = previous?.days?.let(::periodLabel)
    Text(
        if (previousLabel == null) "$currentLabel · No earlier period"
        else "$currentLabel  ·  Compared with $previousLabel",
        style = MaterialTheme.typography.bodySmall
    )
    if (!showClosed) {
        val time = "%02d:%02d".format(Locale.ROOT, report.time.hour, report.time.minute)
        Text(
            if (previous == null) "Through $time"
            else "Both months through the same day and time ($time).",
            style = MaterialTheme.typography.bodySmall
        )
    }
    when (section) {
        "P&L" -> {
            ComparisonTable("Results", listOf(
                ComparisonRow("Revenue", current.revenueCents, previous?.revenueCents, "4000"),
                ComparisonRow("Cost of goods sold", current.costOfSalesCents, previous?.costOfSalesCents, "5000"),
                ComparisonRow("Gross profit", current.grossProfitCents, previous?.grossProfitCents),
                ComparisonRow("Operating expenses", current.operatingExpenseCents, previous?.operatingExpenseCents),
                ComparisonRow("Operating profit", current.operatingProfitCents, previous?.operatingProfitCents),
                ComparisonRow("Interest expense", current.interestCents, previous?.interestCents, "5700"),
                ComparisonRow("Profit before income tax", current.profitCents + current.incomeTaxCents,
                    previous?.let { it.profitCents + it.incomeTaxCents }),
                ComparisonRow("Income tax", current.incomeTaxCents, previous?.incomeTaxCents),
                ComparisonRow("Net profit", current.profitCents, previous?.profitCents)
            ), emphasis = "Net profit", onAccount = onAccount)
            ComparisonTable("Operating expense breakdown",
                accounts(current.operatingExpenses, previous?.operatingExpenses), onAccount = onAccount)
            ComparisonTable("Sales breakdown", accounts(current.revenue, previous?.revenue), onAccount = onAccount)
            val nowServed = current.performance.sumOf { it.servedGroups }.toLong()
            val beforeServed = previous?.performance?.sumOf { it.servedGroups }?.toLong()
            ComparisonTable("Customer activity", listOf(
                ComparisonRow("Guests arrived", current.performance.sumOf { it.arrivingGuests }.toLong(),
                    previous?.performance?.sumOf { it.arrivingGuests }?.toLong()),
                ComparisonRow("Groups served", nowServed, beforeServed),
                ComparisonRow("Groups lost", current.performance.sumOf { it.lostGroups }.toLong(),
                    previous?.performance?.sumOf { it.lostGroups }?.toLong())
            ), format = { it.toString() })
            val nowAverage = nowServed.takeIf { it > 0L }?.let { current.revenueCents / it }
            val beforeAverage = previous?.let { period ->
                beforeServed?.takeIf { it > 0L }?.let { period.revenueCents / it }
            }
            if (nowAverage != null || beforeAverage != null) {
                ComparisonTable("Sales per served group", listOf(
                    ComparisonRow("Excluding VAT", nowAverage, beforeAverage)
                ))
            }
            OutlinedButton(onClick = { showDaily = !showDaily }) {
                Text(stringResource(if (showDaily) R.string.hide_daily else R.string.show_daily))
            }
            if (showDaily) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                        FinanceTableRow("Day", listOf("Revenue", "Expenses", "Net"), heading = true, ruled = true)
                        current.dailyResults.forEach { day ->
                            FinanceTableRow(day.day.toString(), listOf(wholeEuros(day.revenueCents),
                                wholeEuros(day.expenseCents), wholeEuros(day.revenueCents - day.expenseCents)),
                                ruled = true)
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
                ), emphasis = "Total $label", onAccount = onAccount)
            }
            if (current.assets.sumOf { it.amountCents } !=
                current.liabilities.sumOf { it.amountCents } + current.equity.sumOf { it.amountCents }
            ) {
                Text(stringResource(R.string.bs_mismatch), color = MaterialTheme.colorScheme.error)
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
            Text(stringResource(R.string.internal_deposits),
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ComparisonTable(
    title: String,
    rows: List<ComparisonRow>,
    emphasis: String? = null,
    format: (Long) -> String = ::wholeEuros,
    onAccount: (String) -> Unit = {}
) {
    if (rows.isEmpty()) return
    FinanceReportTable(title) {
        FinanceTableRow("", listOf("Current", "Previous", "Change"), heading = true,
            modifier = Modifier.padding(horizontal = 10.dp), labelWeight = 1.6f, ruled = true)
        rows.forEach { row ->
            val change = row.current?.let { now -> row.previous?.let { now - it } }
            val interaction = if (row.account != null) Modifier.clickable(
                role = Role.Button, onClickLabel = "Open account in journal"
            ) { onAccount(row.account) } else Modifier
            FinanceTableRow(
                row.name, listOf(row.current?.let(format) ?: "—", row.previous?.let(format) ?: "—",
                    change?.let { (if (it > 0L && format(it) != format(0L)) "+" else "") + format(it) } ?: "—"),
                modifier = interaction.then(
                    if (row.name == emphasis) Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                    else Modifier
                ).padding(horizontal = 10.dp),
                labelWeight = 1.6f, ruled = true, bold = row.name == "Gross profit"
            )
        }
    }
}

@Composable
private fun FinanceReportTable(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Text(cafeLabel(title), Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.titleSmall)
            content()
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun FinanceAmountRow(label: String, cents: Long, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(cafeLabel(label), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        LedgerAmount(money(cents), color = if (cents < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LedgerAmount(
    value: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    Text(value, modifier, color = color, textAlign = TextAlign.End,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"))
}

@Composable
private fun FinanceTableRow(
    label: String,
    values: List<String>,
    heading: Boolean = false,
    modifier: Modifier = Modifier,
    labelWeight: Float = if (values.size == 3) 0.65f else 1.2f,
    ruled: Boolean = false,
    bold: Boolean = false
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(cafeJournalAccount(label), Modifier.weight(labelWeight),
                style = if (heading) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                fontWeight = if (bold) FontWeight.SemiBold else null)
            values.forEach { LedgerAmount(it, Modifier.weight(1f), bold = bold || heading) }
        }
        if (ruled) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f))
        }
    }
}

private fun wholeEuros(cents: Long): String {
    val rounded = BigDecimal.valueOf(cents, 2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    return if (rounded < 0L) "-€%,d".format(Locale.ROOT, -rounded)
        else "€%,d".format(Locale.ROOT, rounded)
}

@Composable
private fun ForecastWarnings(forecast: CafeCashForecast) {
    forecast.days.firstOrNull { it.bankShortfallCents > 0L }?.let {
        Text(stringResource(R.string.bank_short_day, it.day, money(it.bankShortfallCents)),
            color = MaterialTheme.colorScheme.error)
    }
    forecast.days.firstOrNull { it.cashCents < 0L }?.let {
        Text(stringResource(R.string.cash_short_day, it.day, money(-it.cashCents)),
            color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CashForecastSection(forecast: CafeCashForecast) {
    val today = forecast.days.first().day
    val inset = Modifier.padding(horizontal = 10.dp)
    FinanceReportTable("7-day cash forecast") {
        FinanceTableRow("Day", listOf("Cash", "Bank"), heading = true, modifier = inset, ruled = true)
        forecast.days.forEach { day ->
            FinanceTableRow(if (day.day == today) "Today" else "Day ${day.day}",
                listOf(wholeEuros(day.cashCents), wholeEuros(day.bankCents)), modifier = inset, ruled = true)
        }
    }
    Text(stringResource(R.string.forecast_explain),
        style = MaterialTheme.typography.bodySmall)
    FinanceReportTable("Upcoming payments") {
        FinanceTableRow("Payment", listOf("Due", "Amount"), heading = true, modifier = inset,
            labelWeight = 2.5f, ruled = true)
        if (forecast.days.none { it.payments.isNotEmpty() }) {
            Text(stringResource(R.string.no_forecast_payments), inset.padding(vertical = 6.dp),
                style = MaterialTheme.typography.bodySmall)
        }
        forecast.days.forEach { day ->
            day.payments.forEach { payment ->
                val due = if (payment.dueDay < today) "Day ${payment.dueDay}\nOverdue" else "Day ${payment.dueDay}"
                FinanceTableRow(payment.label, listOf(due, wholeEuros(payment.amountCents)), modifier = inset,
                    labelWeight = 2.5f, ruled = true)
            }
        }
    }
    Text(
        "Staff cost today ${wholeEuros(forecast.staffCostTodayCents)} · Day ${today + 1} ${wholeEuros(forecast.staffCostTomorrowCents)}",
        style = MaterialTheme.typography.bodySmall
    )
    Text(
        if (forecast.historyDays > 0) {
            "Sales and stock purchases use the last ${forecast.historyDays} completed days. Bills are assumed paid before trading. Corporate tax uses profit booked so far. Balances exclude deposits."
        } else {
            "Sales and stock purchases will be estimated after the first completed day."
        },
        style = MaterialTheme.typography.bodySmall
    )
    if (forecast.installments.isNotEmpty()) {
        FinanceReportTable("Next loan installments") {
            FinanceTableRow("Day", listOf("Principal", "Interest", "Total"), heading = true,
                modifier = inset, labelWeight = 1.2f, ruled = true)
            forecast.installments.take(3).forEach { installment ->
                FinanceTableRow(stringResource(R.string.day_number, installment.dueDay), listOf(
                    wholeEuros(installment.principalCents), wholeEuros(installment.interestCents),
                    wholeEuros(installment.totalCents)
                ), modifier = inset, labelWeight = 1.2f, ruled = true)
            }
        }
        Text(stringResource(R.string.loan_projection), style = MaterialTheme.typography.bodySmall)
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


@Composable
private fun CreditLinePanel(
    credit: CafeCreditLine,
    bankCents: Long,
    forecastGap: Long,
    busy: Boolean,
    onDraw: (Long) -> Unit,
    onRepay: (Long) -> Unit
) {
    var drawing by remember { mutableStateOf<Boolean?>(null) }
    var input by remember { mutableStateOf("") }
    val repayLimit = minOf(bankCents, credit.owedCents)
    CafeLedgerPanel {
        Text(stringResource(R.string.credit_line_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.credit_terms, money(CafeCreditLine.LIMIT_CENTS), CafeCreditLine.ANNUAL_RATE_PERCENT),
            style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.credit_available, money(credit.availableCents)))
        if (credit.owedCents > 0L) {
            Text(stringResource(R.string.credit_owing, money(credit.principalCents), money(credit.interestCents)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CafeActionButton(enabled = !busy && credit.availableCents > 0L, onClick = {
                val suggestion = minOf(forecastGap, credit.availableCents)
                input = if (suggestion > 0L) java.math.BigDecimal.valueOf(suggestion, 2).toPlainString() else ""
                drawing = true
            }) { Text(stringResource(R.string.credit_draw)) }
            OutlinedButton(enabled = !busy && repayLimit > 0L, onClick = {
                input = java.math.BigDecimal.valueOf(repayLimit, 2).toPlainString()
                drawing = false
            }) { Text(stringResource(R.string.credit_repay)) }
        }
    }
    drawing?.let { draw ->
        val limit = if (draw) credit.availableCents else repayLimit
        val amount = input.trim().replace(',', '.').toBigDecimalOrNull()
            ?.takeIf { it.scale() in 0..2 && it.signum() > 0 }
            ?.let { runCatching { it.movePointRight(2).longValueExact() }.getOrNull() }
        val action = if (draw) R.string.credit_draw else R.string.credit_repay
        AlertDialog(
            onDismissRequest = { drawing = null },
            title = { Text(stringResource(action)) },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it },
                    label = { Text(stringResource(R.string.amount_euros)) },
                    supportingText = { Text(stringResource(R.string.credit_max, money(limit))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = !busy && amount != null && amount <= limit, onClick = {
                    val cents = checkNotNull(amount)
                    drawing = null
                    if (draw) onDraw(cents) else onRepay(cents)
                }) { Text(stringResource(action)) }
            },
            dismissButton = { TextButton(onClick = { drawing = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
