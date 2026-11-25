package com.example.runthebusiness.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CafeSectionScreen(
    modifier: Modifier,
    title: String,
    sections: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val scroll = rememberScrollState()
    LaunchedEffect(selected) { scroll.scrollTo(0) }
    Column(modifier.fillMaxSize()) {
        CafeSectionHeader(title, sections, selected, onSelect)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun CafeSectionHeader(
    title: String,
    sections: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Text(cafeLabel(title), Modifier.padding(start = 16.dp, top = 10.dp),
            style = MaterialTheme.typography.headlineMedium)
        CafeLedgerOptions(sections, selected, onSelect)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun CafeLedgerOptions(sections: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .selectableGroup().padding(horizontal = 8.dp)
    ) {
        sections.forEach { label ->
            CafeTab(label, label == selected, { onSelect(label) }, Modifier.widthIn(min = 56.dp))
        }
    }
}

@Composable
fun CafeBottomNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.background(colors.surfaceContainer).navigationBarsPadding()) {
        HorizontalDivider(color = colors.outlineVariant)
        Row(
            Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("Café", "Operations", "Finance").forEachIndexed { index, label ->
                val active = index == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .selectable(selected = active, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(cafeLabel(label), style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) colors.onSurface else colors.onSurfaceVariant,
                        maxLines = 1)
                    if (active) {
                        Spacer(Modifier.align(Alignment.BottomCenter).width(18.dp).height(2.dp)
                            .background(colors.tertiaryContainer, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
    }
}

@Composable
private fun CafeTab(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) requester.bringIntoView()
    }
    Column(
        modifier.bringIntoViewRequester(requester)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            cafeLabel(label),
            Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        if (selected) HorizontalDivider(Modifier.width(24.dp), thickness = 2.dp,
            color = MaterialTheme.colorScheme.tertiaryContainer)
        else Spacer(Modifier.height(2.dp))
    }
}

@Composable
fun CafeLedgerPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun CafeActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ), content = content)
}
