package com.example.runthebusiness.ui.debug

import androidx.compose.foundation.horizontalScroll
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
import com.example.runthebusiness.data.local.DebugDatabaseRepository
import com.example.runthebusiness.data.local.DebugTable
import kotlinx.coroutines.launch

@Composable
fun DatabaseInspectorScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { DebugDatabaseRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var tables by remember { mutableStateOf<List<DebugTable>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh(seed: Boolean = false) {
        scope.launch {
            loading = true
            error = null
            try {
                if (seed) repository.seedOpeningJournal()
                tables = repository.inspect()
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
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        tables.forEach { table ->
            HorizontalDivider()
            Text("${table.name} (${table.rows.size})", style = MaterialTheme.typography.titleMedium)
            if (table.rows.isEmpty()) {
                Text(stringResource(R.string.debug_empty))
            } else {
                Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Text(table.columns.joinToString("  |  "), style = MaterialTheme.typography.labelMedium)
                    table.rows.forEach { row ->
                        Text(row.joinToString("  |  "), style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
