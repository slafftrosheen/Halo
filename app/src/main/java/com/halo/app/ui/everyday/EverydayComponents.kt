package com.halo.app.ui.everyday

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloAlertDialog
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EverydaySheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val requestDismiss by rememberUpdatedState(onDismiss)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { target ->
        if (target == SheetValue.Hidden) { requestDismiss(); false } else true
    })
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        CompositionLocalProvider(LocalDensity provides density, LocalMinimumInteractiveComponentSize provides 56.dp) {
            Column(Modifier.fillMaxWidth().imePadding(), content = content)
        }
    }
}

@Composable
internal fun EverydayMessage(message: String, error: Boolean = false) {
    Text(uiText(message), color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
internal fun EverydayToggle(text: String, checked: Boolean, enabled: Boolean = true, description: String = text, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked, onChange, enabled = enabled, modifier = Modifier.sizeIn(minWidth = 56.dp, minHeight = 56.dp).semantics { contentDescription = description })
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EverydayDateField(label: String, value: String, enabled: Boolean = true, child: Boolean = false, onChange: (String) -> Unit) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(value, { onChange(it.take(10)) }, modifier = Modifier.fillMaxWidth(), enabled = enabled,
        label = { Text(label) }, supportingText = { Text(uiText("YYYY-MM-DD")) }, singleLine = true)
    TextButton({ choosing = true }, enabled = enabled, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Choose date")) }
    if (choosing) {
        val selected = runCatching { LocalDate.parse(value) }.getOrNull()?.takeIf { it.year in 1970..2199 } ?: LocalDate.now()
        val inputOnly = child || LocalDensity.current.fontScale >= 1.5f
        if(inputOnly) {
            EverydayDateInputDialog(label,value,{choosing=false}) {chosen -> onChange(chosen);choosing=false}
            return
        }
        val state = rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1970..2199,
            initialDisplayMode = DisplayMode.Picker)
        HaloAlertDialog(onDismissRequest = { choosing = false }, title = { Text(label) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { DatePicker(state, title = null, headline = null, showModeToggle = !inputOnly, focusRequester = null) } },
            confirmButton = { TextButton({ state.selectedDateMillis?.let {
                onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()); choosing = false
            } }, enabled = state.selectedDateMillis != null, modifier = Modifier.heightIn(min = 56.dp)) { Text(uiText("Apply")) } },
            dismissButton = { TextButton({ choosing = false }, Modifier.heightIn(min = 56.dp)) { Text(uiText("Cancel")) } })
    }
}

/** Narrow and large-text dialogs reuse the editor's stable ISO input instead of a fixed calendar input layout. */
@Composable
private fun EverydayDateInputDialog(label: String,initial: String,onDismiss: ()->Unit,onChoose: (String)->Unit) {
    var draft by rememberSaveable {mutableStateOf(initial.take(10))}
    val parsed=runCatching {LocalDate.parse(draft)}.getOrNull()?.takeIf {it.year in 1970..2199}
    EverydaySheet(onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(label,style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(draft,{draft=it.take(10)},label={Text(label)},supportingText={Text(uiText("YYYY-MM-DD"))},
                singleLine=true,isError=draft.isNotBlank() && parsed==null,modifier=Modifier.fillMaxWidth())
            if(draft.isNotBlank() && parsed==null) Text(uiText("Choose a valid date between 1970 and 2199."),color=MaterialTheme.colorScheme.error)
            Button({parsed?.let {onChoose(it.toString())}},enabled=parsed!=null,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(uiText("Apply"))}
            TextButton(onDismiss,Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(uiText("Cancel"))}
        }
    }
}
