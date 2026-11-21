package com.example.runthebusiness.ui.cafe

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.data.local.DebugDatabaseRepository
import com.example.runthebusiness.data.local.DebugJournal
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun CafeFinanceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { DebugDatabaseRepository(context) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<DebugJournal>>(emptyList()) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            busy = true
            error = null
            try {
                entries = repository.journal().asReversed()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    val days = entries.map { it.day }.distinct().sortedDescending()
    val filtered = if (selectedDay == null) entries else entries.filter { it.day == selectedDay }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Finance", style = MaterialTheme.typography.headlineMedium)
        Text("Journal entries · newest first", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("Refresh journal") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = selectedDay == null,
                onClick = { selectedDay = null },
                label = { Text("All days") }
            )
            days.take(3).forEach { day ->
                FilterChip(
                    selected = selectedDay == day,
                    onClick = { selectedDay = day },
                    label = { Text("Day $day") }
                )
            }
        }
        Text("${filtered.size} entries", style = MaterialTheme.typography.labelMedium)
        filtered.take(100).forEach { entry ->
            Card(onClick = { expandedId = if (expandedId == entry.id) null else entry.id }) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(entry.description, style = MaterialTheme.typography.titleSmall)
                    val debit = entry.lines.sumOf { it.debitCents }
                    Text("Day ${entry.day}  •  ${money(debit)}  •  ${entry.lines.size} lines",
                        style = MaterialTheme.typography.bodySmall)
                    if (expandedId == entry.id) {
                        HorizontalDivider()
                        entry.lines.forEach { line ->
                            Text(line.account, style = MaterialTheme.typography.bodySmall)
                            Text("Dr ${money(line.debitCents)}    Cr ${money(line.creditCents)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        if (filtered.size > 100) {
            Text("Showing 100 newest entries. Choose a game day to narrow the list.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun money(cents: Long) = "€%.2f".format(Locale.ROOT, cents / 100.0)
