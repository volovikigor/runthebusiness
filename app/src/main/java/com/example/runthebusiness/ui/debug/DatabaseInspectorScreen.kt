package com.example.runthebusiness.ui.debug

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.runthebusiness.R
import com.example.runthebusiness.data.local.CafeSaleRepository
import com.example.runthebusiness.data.local.DebugDatabaseRepository
import com.example.runthebusiness.data.local.DebugJournal
import com.example.runthebusiness.data.local.DebugTable
import kotlinx.coroutines.launch

@Composable
fun DatabaseInspectorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { DebugDatabaseRepository(context.applicationContext) }
    val saleRepository = remember(context) { CafeSaleRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var tables by remember { mutableStateOf<List<DebugTable>>(emptyList()) }
    var journal by remember { mutableStateOf<List<DebugJournal>>(emptyList()) }
    var lastSale by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh(seed: Boolean = false) {
        scope.launch {
            loading = true
            error = null
            try {
                if (seed) repository.seedOpeningJournal()
                tables = repository.inspect()
                journal = repository.journal()
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.debug_database_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.debug_database_notice), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { refresh() }, enabled = !loading) {
                Text(stringResource(R.string.debug_refresh))
            }
            OutlinedButton(onClick = { refresh(seed = true) }, enabled = !loading) {
                Text(stringResource(R.string.debug_seed))
            }
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    loading = true
                    error = null
                    try {
                        val sale = saleRepository.sellDemoItem()
                        lastSale = "Sale: €%.2f · Net: €%.2f · IVA: €%.2f · COGS: €%.2f".format(
                            sale.grossCents / 100.0, sale.netCents / 100.0,
                            sale.vatCents / 100.0, sale.cogsCents / 100.0
                        )
                        tables = repository.inspect()
                        journal = repository.journal()
                    } catch (e: Exception) {
                        error = e.message ?: e.javaClass.simpleName
                    } finally {
                        loading = false
                    }
                }
            },
            enabled = !loading
        ) { Text("Sell diagnostic item (€50)") }
        Text("Test only: uses 1% of placeholder opening inventory. Not a real café product.",
            style = MaterialTheme.typography.bodySmall)
        if (lastSale != null) Text(lastSale!!, style = MaterialTheme.typography.titleSmall)
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        JournalSection(journal)
        tables.forEach { table ->
            HorizontalDivider()
            Text("${table.name} (${table.rows.size})", style = MaterialTheme.typography.titleMedium)
            if (table.rows.isEmpty()) {
                Text(stringResource(R.string.debug_empty))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    table.rows.forEachIndexed { index, record ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("#${index + 1}", style = MaterialTheme.typography.labelMedium)
                                table.columns.forEachIndexed { columnIndex, column ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(column, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                        Text(record[columnIndex], modifier = Modifier.weight(1.4f), style = MaterialTheme.typography.bodySmall)
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
