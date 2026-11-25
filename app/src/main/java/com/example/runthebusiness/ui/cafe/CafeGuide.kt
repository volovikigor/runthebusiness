package com.example.runthebusiness.ui.cafe

import com.example.runthebusiness.ui.components.cafeLabel
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.runthebusiness.R
import com.example.runthebusiness.ui.components.CafeLedgerOptions

@Composable
internal fun CafeGuideButton(title: String, @StringRes message: Int) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.guide_button)) }
    if (open) {
        var active by remember(message) { mutableIntStateOf(0) }
        val topics = when (message) {
            R.string.guide_cafe -> listOf(message, R.string.guide_pricing, R.string.guide_forecast, R.string.guide_accounting)
            R.string.guide_pl, R.string.guide_bs, R.string.guide_cash, R.string.guide_journal ->
                listOf(message, R.string.guide_accounting)
            else -> listOf(message)
        }
        val labels = if (topics.size == 4) listOf(R.string.guide_controls_topic, R.string.pricing,
            R.string.guide_cash_topic, R.string.guide_accounting_title).map { stringResource(it) }
            else listOf(title, stringResource(R.string.guide_accounting_title))
        val scroll = rememberScrollState()
        LaunchedEffect(active) { scroll.scrollTo(0) }
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(cafeLabel(title)) },
            text = {
                Column {
                    if (topics.size > 1) CafeLedgerOptions(labels, labels[active]) { active = labels.indexOf(it) }
                    val body = stringResource(topics[active])
                    val text = if (topics[active] == R.string.guide_cafe)
                        body + "\n\n" + stringResource(R.string.guide_appearance) else body
                    Text(text, Modifier.weight(1f, fill = false).verticalScroll(scroll))
                }
            },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.guide_close)) }
            }
        )
    }
}
