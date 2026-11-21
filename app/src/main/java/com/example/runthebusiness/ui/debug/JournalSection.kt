package com.example.runthebusiness.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.data.local.DebugJournal
import java.text.NumberFormat
import java.util.Locale

private fun money(cents: Long): String =
    NumberFormat.getCurrencyInstance(Locale.GERMANY).format(cents / 100.0)

@Composable
fun JournalSection(journal: List<DebugJournal>) {
    Text("Journal entries (${journal.size})", style = MaterialTheme.typography.titleLarge)
    journal.forEach { entry ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Day ${entry.day} · ${entry.description}", style = MaterialTheme.typography.titleMedium)
                Text(entry.id, style = MaterialTheme.typography.labelSmall)
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Account", modifier = Modifier.weight(1.7f), style = MaterialTheme.typography.labelSmall)
                    Text("Dr", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    Text("Cr", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                }
                entry.lines.forEach { line ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(line.account, modifier = Modifier.weight(1.7f), style = MaterialTheme.typography.bodySmall)
                        Text(if (line.debitCents > 0) money(line.debitCents) else "—",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(if (line.creditCents > 0) money(line.creditCents) else "—",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
                val debit = entry.lines.sumOf { it.debitCents }
                val credit = entry.lines.sumOf { it.creditCents }
                Text("Total Dr ${money(debit)}  ·  Cr ${money(credit)}",
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
