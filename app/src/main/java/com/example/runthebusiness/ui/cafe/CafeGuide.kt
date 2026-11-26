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

internal enum class CafeGuideChapter(
    @field:StringRes val title: Int,
    @field:StringRes val summary: Int,
    val lessons: List<Pair<Int, Int>>
) {
    CONTROLS(R.string.learning_controls, R.string.learning_controls_summary, listOf(
        R.string.cafe to R.string.learning_cafe,
        R.string.operations to R.string.learning_operations,
        R.string.finance to R.string.learning_finance,
        R.string.learning_appearance to R.string.learning_appearance_body
    )),
    STRATEGY(R.string.learning_strategy, R.string.learning_strategy_summary, listOf(
        R.string.learning_trial to R.string.learning_trial_body,
        R.string.learning_price_test to R.string.learning_price_test_body,
        R.string.learning_capacity to R.string.learning_capacity_body
    )),
    ACCOUNTING(R.string.learning_accounting, R.string.learning_accounting_summary, listOf(
        R.string.learning_management to R.string.learning_management_body,
        R.string.learning_reports to R.string.learning_reports_body,
        R.string.learning_postings to R.string.learning_postings_body
    )),
    CASH(R.string.learning_cash, R.string.learning_cash_summary, listOf(
        R.string.learning_gap to R.string.learning_gap_body,
        R.string.learning_credit to R.string.learning_credit_body,
        R.string.learning_loan to R.string.learning_loan_body
    ))
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
internal fun CafeLearningGuide(chapter: CafeGuideChapter?, onChapter: (CafeGuideChapter?) -> Unit, onClose: () -> Unit) {
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
                            items(chapter.lessons) { (title, body) ->
                                CafeLedgerPanel {
                                    Text(
                                        stringResource(title),
                                        Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
