package com.example.runthebusiness.ui.cafe

import com.example.runthebusiness.ui.components.cafeLabel
import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.runthebusiness.R

@Composable
internal fun CafeGuideButton(title: String, @StringRes message: Int) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text(stringResource(R.string.guide_button)) }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(cafeLabel(title)) },
            text = { Text(stringResource(message), Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.guide_close)) }
            }
        )
    }
}
