package com.example.runthebusiness.ui.cafe

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.R
import com.example.runthebusiness.data.local.CREDIT_LINE_INTEREST
import com.example.runthebusiness.data.local.CREDIT_LINE_PRINCIPAL
import com.example.runthebusiness.data.local.cafeCreditAccountNames
import com.example.runthebusiness.industries.cafe.CafeStartingFactory
import com.example.runthebusiness.ui.components.CafeLedgerPanel
import com.example.runthebusiness.ui.components.cafeLabel
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency

@Composable
private fun guideEuros(cents: Int): String =
    NumberFormat.getCurrencyInstance(LocalConfiguration.current.locales[0]).apply {
        currency = Currency.getInstance("EUR")
        minimumFractionDigits = if (cents % 100 == 0) 0 else 2
    }.format(BigDecimal.valueOf(cents.toLong(), 2))

@Composable
private fun GuideTable(headers: List<String>, rows: List<List<String>>, numeric: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        GuideTableRow(headers, numeric, heading = true)
        rows.forEach { cells ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            GuideTableRow(cells, numeric)
        }
    }
}

@Composable
private fun GuideTableRow(cells: List<String>, numeric: Boolean, heading: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEachIndexed { index, value ->
            Text(
                value,
                Modifier.weight(if (index == 0 && numeric) 1.6f else 1f),
                textAlign = if (numeric && index > 0) TextAlign.End else TextAlign.Start,
                style = if (heading) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun CafeGuideExampleTable(@StringRes example: Int) {
    val beforeAfter = listOf(stringResource(R.string.strategy_before), stringResource(R.string.strategy_after))
    val measures = listOf(stringResource(R.string.guide_measure), stringResource(R.string.amount))
    when (example) {
        R.string.learning_skip_example -> GuideTable(
            listOf(stringResource(R.string.guide_where), stringResource(R.string.guide_check)),
            listOf(
                listOf(stringResource(R.string.learning_skip_title), stringResource(R.string.next_day)),
                listOf(stringResource(R.string.cafe_overview), stringResource(R.string.guide_skip_losses)),
                listOf(stringResource(R.string.inventory), stringResource(R.string.waste))
            )
        )
        R.string.learning_price_example -> GuideTable(
            listOf(stringResource(R.string.guide_measure)) + beforeAfter,
            listOf(
                listOf(stringResource(R.string.loss_price), guideEuros(400), guideEuros(350)),
                listOf(stringResource(R.string.guide_ingredient_per_dish), guideEuros(200), guideEuros(200)),
                listOf(stringResource(R.string.units_sold), "30", "40"),
                listOf(stringResource(R.string.gross_profit), guideEuros(6000), guideEuros(6000))
            ), numeric = true
        )
        R.string.learning_capacity_example -> GuideTable(measures, listOf(
            listOf(stringResource(R.string.guide_daily_hire), guideEuros(4000)),
            listOf(stringResource(R.string.guide_margin_per_dish), guideEuros(200)),
            listOf(stringResource(R.string.guide_extra_portions), "20")
        ), numeric = true)
        R.string.learning_revenue_example -> GuideTable(
            listOf(stringResource(R.string.guide_measure)) + beforeAfter,
            listOf(
                listOf(stringResource(R.string.kpi_served), "100", "120"),
                listOf(stringResource(R.string.sales_per_group), guideEuros(1000), guideEuros(1000)),
                listOf(stringResource(R.string.revenue), guideEuros(100000), guideEuros(120000))
            ), numeric = true
        )
        R.string.learning_margin_example -> GuideTable(
            listOf(stringResource(R.string.guide_check), stringResource(R.string.guide_action)),
            listOf(
                listOf(stringResource(R.string.guide_mix_changed), stringResource(R.string.strategy_sales_mix)),
                listOf(stringResource(R.string.guide_milk_cost_up), stringResource(R.string.guide_review_milk))
            )
        )
        R.string.learning_inventory_example -> GuideTable(
            listOf(stringResource(R.string.inventory), stringResource(R.string.guide_portions)),
            listOf(
                listOf(stringResource(R.string.bought), "24"),
                listOf(stringResource(R.string.used), "12"),
                listOf(stringResource(R.string.waste), "8"),
                listOf(stringResource(R.string.end_balance), "4")
            ), numeric = true
        )
        R.string.learning_period_example -> GuideTable(
            listOf(stringResource(R.string.guide_period), stringResource(R.string.current), stringResource(R.string.previous)),
            listOf(
                listOf(stringResource(R.string.month_to_date), "31–38", "1–8"),
                listOf(stringResource(R.string.closed_months), "1–30", "—")
            ), numeric = true
        )
        R.string.learning_gap_example -> GuideTable(
            listOf(stringResource(R.string.guide_measure)) + beforeAfter,
            listOf(
                listOf(stringResource(R.string.bank), guideEuros(20000), guideEuros(60000)),
                listOf(stringResource(R.string.cash), guideEuros(50000), guideEuros(10000)),
                listOf(stringResource(R.string.due), guideEuros(60000), guideEuros(60000)),
                listOf(stringResource(R.string.guide_bank_gap), guideEuros(40000), guideEuros(0))
            ), numeric = true
        )
        R.string.learning_credit_example -> GuideTable(
            listOf(stringResource(R.string.payment), stringResource(R.string.label_today), stringResource(R.string.guide_in_seven_days)),
            listOf(
                listOf(stringResource(R.string.guide_pay_now), guideEuros(30000), guideEuros(0)),
                listOf(stringResource(R.string.guide_pay_later), guideEuros(0), guideEuros(30000))
            ), numeric = true
        )
        R.string.learning_line_example -> GuideTable(measures, listOf(
            listOf(stringResource(R.string.guide_credit_available), guideEuros(100000)),
            listOf(stringResource(R.string.guide_bank_gap), guideEuros(25000)),
            listOf(stringResource(R.string.guide_entry_credit_draw), guideEuros(25000))
        ), numeric = true)
        R.string.learning_loan_example -> GuideTable(measures, listOf(
            listOf(stringResource(R.string.principal), guideEuros(60000)),
            listOf(stringResource(R.string.interest), guideEuros(10000)),
            listOf(stringResource(R.string.payment), guideEuros(70000))
        ), numeric = true)
    }
}

internal data class GuidePosting(@field:StringRes val title: Int, val lines: List<Pair<String, Int>>) {
    init {
        require(lines.sumOf { it.second } == 0)
    }
}

private fun posting(@StringRes title: Int, vararg lines: Pair<String, Int>) = GuidePosting(title, lines.toList())

internal val guidePostings = listOf(
    posting(R.string.guide_entry_sale, "1000" to 1100, "4000" to -1000, "2200" to -100),
    posting(R.string.guide_entry_cost, "5000" to 400, "1400" to -400),
    posting(R.string.guide_entry_vendor_credit, "1400" to 12000, "2010" to -12000),
    posting(R.string.guide_entry_vendor_bank, "1400" to 12000, "1100" to -12000),
    posting(R.string.guide_entry_vendor_pay, "2010" to 12000, "1100" to -12000),
    posting(R.string.guide_entry_waste, "5100" to 800, "1400" to -800),
    posting(R.string.guide_entry_deposit, "1100" to 40000, "1000" to -40000),
    posting(R.string.guide_entry_rent, "5400" to 5000, "2000" to -5000),
    posting(R.string.guide_entry_rent_pay, "2000" to 5000, "1100" to -5000),
    posting(R.string.guide_entry_wage, "5200" to 10000, "2300" to -10000),
    posting(R.string.guide_entry_employee_social, "2300" to 600, "2400" to -600),
    posting(R.string.guide_entry_employer_social, "5300" to 3000, "2400" to -3000),
    posting(R.string.guide_entry_wage_pay, "2300" to 9400, "1100" to -9400),
    posting(R.string.guide_entry_social_pay, "2400" to 3600, "1100" to -3600),
    posting(R.string.guide_entry_vat_pay, "2200" to 100, "1100" to -100),
    posting(R.string.guide_entry_tax, "5800" to 2500, "2600" to -2500),
    posting(R.string.guide_entry_tax_pay, "2600" to 2500, "1100" to -2500),
    posting(R.string.guide_entry_equipment, "1500" to 800000, "1100" to -800000),
    posting(R.string.guide_entry_depreciation, "5600" to 1000, "1590" to -1000),
    posting(R.string.guide_entry_loan_draw, "1100" to 2000000, "2100" to -2000000),
    posting(R.string.guide_entry_loan_interest, "5700" to 10000, "2110" to -10000),
    posting(R.string.guide_entry_loan_pay, "2100" to 60000, "2110" to 10000, "1100" to -70000),
    posting(R.string.guide_entry_credit_draw, "1100" to 25000, CREDIT_LINE_PRINCIPAL to -25000),
    posting(R.string.guide_entry_credit_interest, "5700" to 8, CREDIT_LINE_INTEREST to -8),
    posting(R.string.guide_entry_credit_pay, CREDIT_LINE_INTEREST to 8, CREDIT_LINE_PRINCIPAL to 25000, "1100" to -25008)
)

private val guideAccountNames = CafeStartingFactory.startingAccounts.associate { it.code to it.name } + cafeCreditAccountNames

@Composable
private fun PostingTable(entry: GuidePosting) {
    GuideTable(
        listOf(stringResource(R.string.account), stringResource(R.string.debit), stringResource(R.string.credit)),
        entry.lines.map { (code, amount) ->
            listOf(
                "$code · ${cafeLabel(guideAccountNames.getValue(code))}",
                if (amount > 0) guideEuros(amount) else "—",
                if (amount < 0) guideEuros(-amount) else "—"
            )
        },
        numeric = true
    )
}

@Composable
internal fun CafeGuidePosting(entry: GuidePosting) {
    CafeLedgerPanel {
        Text(stringResource(entry.title), style = MaterialTheme.typography.titleMedium)
        PostingTable(entry)
    }
}

@Composable
internal fun CafeGuideChart() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    CafeLedgerPanel {
        Text(stringResource(R.string.guide_chart_title), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.guide_chart_hide else R.string.guide_chart_show))
        }
        if (expanded) {
            GuideTable(
                listOf(stringResource(R.string.guide_code), stringResource(R.string.account)),
                guideAccountNames.toSortedMap().map { (code, name) -> listOf(code, cafeLabel(name)) }
            )
        }
    }
}
