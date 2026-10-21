package com.halo.app.ui.screens.study

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeworkPageNavigation(page: Int, total: Int, kids: Boolean, onPage: (Int) -> Unit) {
    val target = if (kids) 56.dp else 48.dp
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(uiText("Page %1\$d of %2\$d", page, total), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onPage(page - 1) }, enabled = page > 1,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Previous page")) }
            OutlinedButton(onClick = { onPage(page + 1) }, enabled = page < total,
                modifier = Modifier.heightIn(min = target)) { Text(uiText("Next page")) }
        }
    }
}
