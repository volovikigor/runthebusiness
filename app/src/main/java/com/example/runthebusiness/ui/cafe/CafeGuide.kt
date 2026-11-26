package com.example.runthebusiness.ui.cafe

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.runthebusiness.R
import com.example.runthebusiness.ui.components.CafeLedgerPanel
import com.example.runthebusiness.ui.components.cafeLabel

internal enum class CafeGuideChapter(@field:StringRes val title: Int, @field:StringRes val summary: Int) {
    CONTROLS(R.string.learning_controls, R.string.learning_controls_summary),
    STRATEGY(R.string.learning_strategy, R.string.learning_strategy_summary),
    ACCOUNTING(R.string.learning_accounting, R.string.learning_accounting_summary),
    CASH(R.string.learning_cash, R.string.learning_cash_summary)
}

internal enum class CafeGuideDestination(@field:StringRes val title: Int) {
    CAFE(R.string.cafe),
    PRICING(R.string.pricing),
    PURCHASING(R.string.purchasing),
    INVENTORY(R.string.inventory),
    STAFF(R.string.staff),
    FORECAST(R.string.forecast),
    BILLS(R.string.bills),
    PROFIT(R.string.pl),
    BALANCE(R.string.bs),
    CASH_FLOW(R.string.cash_flow),
    JOURNAL(R.string.journal)
}

private data class GuideLesson(
    @field:StringRes val title: Int,
    @field:StringRes val body: Int,
    @field:StringRes val example: Int? = null,
    val destination: CafeGuideDestination? = null,
    val related: CafeGuideChapter? = null
)

private fun guideLessons(chapter: CafeGuideChapter): List<GuideLesson> = when (chapter) {
    CafeGuideChapter.CONTROLS -> listOf(
        GuideLesson(R.string.cafe, R.string.learning_cafe, destination = CafeGuideDestination.CAFE),
        GuideLesson(R.string.learning_skip_title, R.string.learning_skip_body, R.string.learning_skip_example),
        GuideLesson(R.string.learning_navigation_title, R.string.learning_navigation_body),
        GuideLesson(R.string.learning_appearance, R.string.learning_appearance_body),
        GuideLesson(R.string.pricing, R.string.learning_pricing_page, destination = CafeGuideDestination.PRICING, related = CafeGuideChapter.STRATEGY),
        GuideLesson(R.string.purchasing, R.string.learning_purchasing_page, destination = CafeGuideDestination.PURCHASING, related = CafeGuideChapter.CASH),
        GuideLesson(R.string.inventory, R.string.learning_inventory_page, destination = CafeGuideDestination.INVENTORY, related = CafeGuideChapter.ACCOUNTING),
        GuideLesson(R.string.staff, R.string.learning_staff_page, destination = CafeGuideDestination.STAFF, related = CafeGuideChapter.STRATEGY),
        GuideLesson(R.string.forecast, R.string.learning_forecast_page, destination = CafeGuideDestination.FORECAST, related = CafeGuideChapter.CASH),
        GuideLesson(R.string.bills, R.string.learning_bills_page, destination = CafeGuideDestination.BILLS, related = CafeGuideChapter.CASH),
        GuideLesson(R.string.pl, R.string.learning_pl_page, destination = CafeGuideDestination.PROFIT, related = CafeGuideChapter.ACCOUNTING),
        GuideLesson(R.string.bs, R.string.learning_bs_page, destination = CafeGuideDestination.BALANCE, related = CafeGuideChapter.ACCOUNTING),
        GuideLesson(R.string.cash_flow, R.string.learning_cash_page, destination = CafeGuideDestination.CASH_FLOW, related = CafeGuideChapter.CASH),
        GuideLesson(R.string.journal, R.string.learning_journal_page, destination = CafeGuideDestination.JOURNAL, related = CafeGuideChapter.ACCOUNTING)
    )
    CafeGuideChapter.STRATEGY -> listOf(
        GuideLesson(R.string.learning_trial, R.string.learning_trial_body, destination = CafeGuideDestination.PRICING),
        GuideLesson(R.string.learning_price_test, R.string.learning_price_test_body, R.string.learning_price_example, CafeGuideDestination.PRICING),
        GuideLesson(R.string.learning_capacity, R.string.learning_capacity_body, R.string.learning_capacity_example, CafeGuideDestination.STAFF)
    )
    CafeGuideChapter.ACCOUNTING -> listOf(
        GuideLesson(R.string.learning_management, R.string.learning_management_body, R.string.learning_revenue_example, CafeGuideDestination.PROFIT),
        GuideLesson(R.string.strategy_margin_drivers, R.string.learning_margin_body, R.string.learning_margin_example, CafeGuideDestination.PROFIT),
        GuideLesson(R.string.inventory, R.string.learning_inventory_decision, R.string.learning_inventory_example, CafeGuideDestination.INVENTORY),
        GuideLesson(R.string.learning_reports, R.string.learning_reports_body, R.string.learning_period_example, CafeGuideDestination.PROFIT),
        GuideLesson(R.string.learning_postings, R.string.learning_postings_body, R.string.learning_postings_example, CafeGuideDestination.JOURNAL)
    )
    CafeGuideChapter.CASH -> listOf(
        GuideLesson(R.string.learning_gap, R.string.learning_gap_body, R.string.learning_gap_example, CafeGuideDestination.FORECAST),
        GuideLesson(R.string.learning_credit, R.string.learning_credit_body, R.string.learning_credit_example, CafeGuideDestination.PURCHASING),
        GuideLesson(R.string.credit_line_title, R.string.learning_line_body, R.string.learning_line_example, CafeGuideDestination.FORECAST),
        GuideLesson(R.string.learning_loan, R.string.learning_loan_body, R.string.learning_loan_example, CafeGuideDestination.BILLS)
    )
}

@Composable
internal fun CafeGuideButton(title: String, @StringRes message: Int, onOpenGuide: (CafeGuideChapter) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.guide_button)) }
    if (open) {
        val chapter = when (message) {
            R.string.guide_bills, R.string.guide_forecast, R.string.guide_purchasing -> CafeGuideChapter.CASH
            R.string.guide_pl, R.string.guide_bs, R.string.guide_cash, R.string.guide_journal -> CafeGuideChapter.ACCOUNTING
            else -> CafeGuideChapter.STRATEGY
        }
        val body = when (message) {
            R.string.guide_forecast -> R.string.learning_forecast_hint
            R.string.guide_pricing -> R.string.learning_pricing_hint
            else -> message
        }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(cafeLabel(title)) },
            text = {
                Text(
                    stringResource(body),
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge
                )
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.guide_close)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    open = false
                    onOpenGuide(chapter)
                }) { Text(stringResource(R.string.learning_read_more)) }
            }
        )
    }
}

@Composable
internal fun CafeLearningGuide(
    chapter: CafeGuideChapter?,
    onChapter: (CafeGuideChapter?) -> Unit,
    onClose: () -> Unit,
    onDestination: ((CafeGuideDestination) -> Unit)?
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.learning_title),
                        Modifier.weight(1f).semantics { heading() },
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = onClose) { Text(stringResource(R.string.learning_close)) }
                }
                key(chapter) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (chapter == null) {
                            items(CafeGuideChapter.entries) { topic ->
                                Surface(
                                    onClick = { onChapter(topic) },
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(stringResource(topic.title), style = MaterialTheme.typography.titleLarge)
                                        Text(stringResource(topic.summary), style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        } else {
                            item {
                                TextButton(onClick = { onChapter(null) }) {
                                    Text(stringResource(R.string.learning_contents))
                                }
                                Text(
                                    stringResource(chapter.title),
                                    Modifier.semantics { heading() },
                                    style = MaterialTheme.typography.headlineSmall
                                )
                            }
                            items(guideLessons(chapter)) { lesson ->
                                CafeLedgerPanel {
                                    Text(
                                        stringResource(lesson.title),
                                        Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(stringResource(lesson.body), style = MaterialTheme.typography.bodyLarge)
                                    lesson.example?.let { example ->
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.small,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest
                                        ) {
                                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(stringResource(R.string.learning_example), style = MaterialTheme.typography.titleSmall)
                                                Text(stringResource(example), style = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                    }
                                    if (lesson.destination != null && onDestination != null) {
                                        TextButton(onClick = { onDestination(lesson.destination) }) {
                                            Text(stringResource(R.string.learning_open_page, stringResource(lesson.destination.title)))
                                        }
                                    }
                                    lesson.related?.let { related ->
                                        TextButton(onClick = { onChapter(related) }) { Text(stringResource(related.title)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
