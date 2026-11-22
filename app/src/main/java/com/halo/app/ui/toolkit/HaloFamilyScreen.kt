package com.halo.app.ui.toolkit

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.zone6.Zone6ButtonSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
internal fun HaloFamilyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = remember(context) { HaloLifeOwner.get(context) }
    val posts by owner.family.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(false) }
    var selectedKind by remember { mutableStateOf(FamilyKind.LIST) }
    var selected by remember { mutableStateOf<FamilyPost?>(null) }
    var imported by remember { mutableStateOf<FamilyPost?>(null) }
    var removing by remember { mutableStateOf<FamilyPost?>(null) }
    var export by remember { mutableStateOf<Pair<FamilyPost, Long>?>(null) }
    var about by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    fun write(change: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { change(); error = false }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) write {
            val family = requireAdultToolkit(context)
            imported = withContext(Dispatchers.IO) {
                requireAdultToolkit(context, family)
                requireNotNull(context.contentResolver.openInputStream(uri)).use { stream ->
                    val data = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(2048)
                    while (true) {
                        val read = stream.read(chunk)
                        if (read < 0) break
                        require(data.size() + read <= HaloFamilyTransfer.MAX_BYTES)
                        data.write(chunk, 0, read)
                    }
                    HaloFamilyTransfer.decode(data.toByteArray())
                }
            }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val request = export
        export = null
        if (uri != null && request != null) write {
            withContext(Dispatchers.IO) {
                requireAdultToolkit(context, request.second)
                check(owner.family.value.any { it == request.first })
                requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { stream ->
                    stream.write(HaloFamilyTransfer.encode(request.first))
                }
            }
        }
    }
    val title = stringResource(R.string.halo_family_title)
    val description = stringResource(R.string.halo_family_description)
    LifePage("family", title, description, stringResource(R.string.halo_family_add),
        !busy && posts.size < 100, { edit = true }, onBack, guidanceBlocked = edit || selected != null || imported != null || removing != null,
        extra = listOf(ShellPageAction("page.family.appearance", stringResource(R.string.coach_edit_page), HaloGlyph.SETTINGS) {
            about = true
        })) {
        Text(description)
        HaloFamilySafePlaces()
        TextButton({ importPicker.launch(arrayOf("application/json", "text/plain")) }, enabled = !busy && posts.size < 100) {
            Text(stringResource(R.string.halo_family_import))
        }
        TabRow(selectedKind.ordinal) {
            FamilyKind.entries.forEach { kind ->
                Tab(selectedKind == kind, { selectedKind = kind }, text = { Text(stringResource(kind.label)) })
            }
        }
        val visible = posts.filter { it.kind == selectedKind }
        if (visible.isEmpty()) Text(stringResource(R.string.halo_family_empty))
        visible.takeLast(100).asReversed().forEach { post ->
            key(post.id) {
                val initials = post.author.split(' ').filter(String::isNotBlank).take(2)
                    .mapNotNull { it.firstOrNull()?.uppercase() }.joinToString("")
                val palette = listOf(MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.primaryContainer)
                ListItem(leadingContent = {
                    Surface(shape = CircleShape, color = lerp(MaterialTheme.colorScheme.surfaceContainer,
                        palette[(post.author.hashCode() % palette.size).absoluteValue], .48f)) {
                        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { Text(initials) }
                    }
                }, headlineContent = { Text(post.text) }, supportingContent = { Text(post.author) },
                    trailingContent = {
                        if (post.kind == FamilyKind.LIST) Checkbox(post.done, { write { owner.toggleFamily(post) } }, enabled = !busy)
                        else TextButton({ selected = post }) { Text(stringResource(R.string.halo_family_prepare_share)) }
                    })
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (post.kind == FamilyKind.LIST) TextButton({ selected = post }) {
                        Text(stringResource(R.string.halo_family_prepare_share))
                    }
                    TextButton({ removing = post }, enabled = !busy) { Text(stringResource(R.string.halo_family_remove)) }
                }
            }
        }
        if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
    }
    if (edit) {
        var author by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { if (!busy) edit = false },
            title = { Text(stringResource(R.string.halo_family_add)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(selectedKind.label), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(author, { author = it.take(36) }, singleLine = true,
                        label = { Text(stringResource(R.string.halo_family_author)) })
                    OutlinedTextField(text, { text = it.take(1200) }, minLines = 2, maxLines = 5,
                        label = { Text(stringResource(R.string.halo_family_item)) })
                    if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { Button({
                write { owner.addFamily(selectedKind, author.trim(), text.trim()); edit = false }
            }, enabled = !busy && author.isNotBlank() && text.isNotBlank()) { Text(stringResource(R.string.halo_life_save)) } },
            dismissButton = { TextButton({ edit = false }, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } })
    }
    selected?.let { snapshot ->
        AlertDialog(onDismissRequest = { selected = null },
            title = { Text(stringResource(R.string.halo_family_prepare_share)) },
            text = {
                Column {
                    Text(snapshot.text)
                    Text(snapshot.author)
                    TextButton({
                        runCatching {
                            val family = requireAdultToolkit(context)
                            check(owner.family.value.any { it == snapshot })
                            export = snapshot to family
                            selected = null
                            exportPicker.launch("halo-family-entry.json")
                        }.onFailure { error = true; export = null }
                    }) { Text(stringResource(R.string.halo_family_transfer)) }
                }
            },
            confirmButton = { Button({
                val stillCurrent = posts.any { it == snapshot }
                if (stillCurrent) runCatching {
                    requireAdultToolkit(context)
                    check(owner.family.value.any { it == snapshot })
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, snapshot.text), null))
                }.onFailure { error = true }
                else error = true
                selected = null
            }) { Text(stringResource(R.string.halo_family_share)) } },
            dismissButton = { TextButton({ selected = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
    imported?.let { post ->
        AlertDialog(onDismissRequest = { if (!busy) imported = null },
            title = { Text(stringResource(R.string.halo_family_import)) },
            text = {
                Column {
                    Text(stringResource(post.kind.label))
                    Text(post.author)
                    Text(post.text)
                    if (error) Text(stringResource(R.string.page_layout_error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { Button({
                write { owner.importFamily(post); imported = null }
            }, enabled = !busy) { Text(stringResource(R.string.halo_life_save)) } },
            dismissButton = { TextButton({ imported = null }, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } })
    }
    removing?.let { post ->
        AlertDialog(onDismissRequest = { if (!busy) removing = null },
            title = { Text(stringResource(R.string.halo_family_remove)) }, text = { Text(post.text) },
            confirmButton = { Button({
                write { owner.removeFamily(post); removing = null }
            }, enabled = !busy) { Text(stringResource(R.string.halo_family_remove)) } },
            dismissButton = { TextButton({ removing = null }, enabled = !busy) { Text(stringResource(android.R.string.cancel)) } })
    }
    if (about) Zone6ButtonSheet(title, { about = false }) {
        Text(description, Modifier.padding(16.dp))
        Text(stringResource(R.string.halo_family_scope), Modifier.padding(horizontal = 16.dp))
    }
}

private val FamilyKind.label get() = when (this) {
    FamilyKind.LIST -> R.string.halo_family_list
    FamilyKind.PLAN -> R.string.halo_family_plan
    FamilyKind.NOTE -> R.string.halo_family_note
}
