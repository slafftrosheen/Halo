package com.halo.app.ui.screens.notebook

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.data.notebook.WorkspaceLifecycle
import com.halo.app.data.notebook.NoteBlock
import com.halo.app.data.notebook.NoteBlockType
import com.halo.app.data.workspace.NoteAttachment
import com.halo.app.ui.presence.HaloSignalGlyph
import com.halo.app.ui.presence.HaloSignalKind
import com.halo.app.ui.theme.HaloPastelBlue
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloPastelPink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun NoteEditorScreen(viewModel: NoteEditorViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::addAttachment)
    }

    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            viewModel.flush()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        EditorToolbar(
            lifecycle = state.lifecycle,
            onBack = {
                viewModel.flush()
                onBack()
            },
            onArchive = viewModel::archive,
            onTrash = viewModel::moveToTrash,
            onRestore = viewModel::restore,
            onRemember = viewModel::rememberNote
        )

        if (state.status == SaveStatus.MISSING) {
            MissingNoteState(onBack, Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                HaloSignalGlyph(kind = HaloSignalKind.NOTE, size = 34.dp)
                Spacer(Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = noteTitle(state.body),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = when (state.lifecycle) {
                            WorkspaceLifecycle.ACTIVE -> "Note"
                            WorkspaceLifecycle.ARCHIVED -> "Archived note"
                            WorkspaceLifecycle.TRASHED -> "Trashed note"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Spacer(Modifier.height(8.dp))

            // Markdown & Action Toolbar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val helpers = listOf("#", "✓", "•", "1.", "Link")
                helpers.forEach { symbol ->
                    Surface(
                        onClick = {
                            if (state.blocks.isEmpty()) {
                                viewModel.insertMarkdownHelper(symbol)
                            } else {
                                val type = when (symbol) {
                                    "#" -> NoteBlockType.HEADING
                                    "✓" -> NoteBlockType.CHECKLIST
                                    else -> NoteBlockType.PARAGRAPH
                                }
                                viewModel.addBlock(state.blocks.last().position, type)
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = symbol,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    onClick = { filePicker.launch(arrayOf("image/*", "application/pdf", "text/*", "audio/*", "video/*")) },
                    enabled = !state.isAddingAttachment && state.lifecycle != WorkspaceLifecycle.TRASHED,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AttachFile,
                        contentDescription = "Add image or file",
                        modifier = Modifier.padding(6.dp).size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    onClick = {
                        viewModel.requestSummary()
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                            text = if (state.isTransforming) "Summarizing…" else "Summarize",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Transformation Preview Box if active
            state.transformPreview?.let { preview ->
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "AI Transformation Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = viewModel::applyPreviewReplace) {
                                Text("Replace")
                            }
                            TextButton(onClick = viewModel::applyPreviewInsertBelow) {
                                Text("Insert below")
                            }
                            TextButton(onClick = viewModel::discardPreview) {
                                Text("Discard")
                            }
                        }
                    }
                }
            }
            state.transformError?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }

            if (state.attachments.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(state.attachments, key = NoteAttachment::id) { attachment ->
                        AttachmentCard(
                            attachment = attachment,
                            onOpen = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW)
                                            .setDataAndType(Uri.parse(attachment.pathOrUri), attachment.mimeType)
                                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    )
                                }
                            },
                            onRemove = { viewModel.removeAttachment(attachment.id) },
                        )
                    }
                }
            }
            state.attachmentError?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (state.blocks.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.blocks, key = NoteBlock::id) { block ->
                        NoteBlockEditor(
                            block = block,
                            enabled = state.status != SaveStatus.LOADING && state.lifecycle != WorkspaceLifecycle.TRASHED,
                            canMoveUp = block.position > 0,
                            canMoveDown = block.position < state.blocks.lastIndex,
                            canRemove = state.blocks.size > 1,
                            onTextChange = { viewModel.updateBlock(block.id, it) },
                            onTypeChange = { viewModel.changeBlockType(block.id, it) },
                            onCheckedChange = { viewModel.setBlockChecked(block.id, it) },
                            onMoveUp = { viewModel.moveBlock(block.id, -1) },
                            onMoveDown = { viewModel.moveBlock(block.id, 1) },
                            onRemove = { viewModel.removeBlock(block.id) },
                            onAddBelow = { viewModel.addBlock(block.position) },
                        )
                    }
                }
            } else {
                BasicTextField(
                    value = state.body,
                    onValueChange = viewModel::updateBody,
                    enabled = state.status != SaveStatus.LOADING && state.lifecycle != WorkspaceLifecycle.TRASHED,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 25.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    decorationBox = { innerTextField ->
                        if (state.body.isEmpty()) {
                            Text(
                                text = "# Untitled note\n\nStart writing…",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )
            }

            EditorStatusBar(state)
        }
    }
}

@Composable
private fun NoteBlockEditor(
    block: NoteBlock,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onTextChange: (String) -> Unit,
    onTypeChange: (NoteBlockType) -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onAddBelow: () -> Unit,
) {
    var typeMenu by remember(block.id) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = when (block.type) {
            NoteBlockType.CODE -> MaterialTheme.colorScheme.surfaceVariant
            NoteBlockType.QUOTE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f)
            else -> MaterialTheme.colorScheme.background
        },
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { typeMenu = true }, enabled = enabled) {
                        Text(block.type.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                    DropdownMenu(expanded = typeMenu, onDismissRequest = { typeMenu = false }) {
                        NoteBlockType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                onClick = { typeMenu = false; onTypeChange(type) },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onMoveUp, enabled = enabled && canMoveUp, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move block up", modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onMoveDown, enabled = enabled && canMoveDown, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move block down", modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onRemove, enabled = enabled && canRemove, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete block", modifier = Modifier.size(17.dp))
                }
            }
            Row(verticalAlignment = Alignment.Top) {
                if (block.type == NoteBlockType.CHECKLIST) {
                    Checkbox(
                        checked = block.checked,
                        onCheckedChange = onCheckedChange,
                        enabled = enabled,
                    )
                }
                BasicTextField(
                    value = block.text,
                    onValueChange = onTextChange,
                    enabled = enabled,
                    textStyle = when (block.type) {
                        NoteBlockType.HEADING -> MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onBackground)
                        NoteBlockType.CODE -> MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )
                        else -> MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                            lineHeight = 25.sp,
                        )
                    },
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        if (block.text.isEmpty()) {
                            Text(
                                if (block.type == NoteBlockType.HEADING) "Heading" else "Write something…",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    },
                )
            }
            TextButton(onClick = onAddBelow, enabled = enabled) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("Add block")
            }
        }
    }
}

@Composable
private fun AttachmentCard(
    attachment: NoteAttachment,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(if (attachment.type == "IMAGE") 248.dp else 220.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            if (attachment.type == "IMAGE") {
                AttachmentImage(attachment, onOpen)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(32.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            attachment.displayName ?: "Attached file",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                        )
                        Text(
                            attachment.mimeType.substringAfterLast('/').uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    attachment.caption ?: attachment.displayName ?: "Attachment",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove attachment", modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

@Composable
private fun AttachmentImage(attachment: NoteAttachment, onOpen: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, attachment.pathOrUri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(attachment.pathOrUri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }
    Box(
        modifier = Modifier.fillMaxWidth().height(170.dp).clickable(onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = attachment.caption ?: attachment.displayName ?: "Attached image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.BrokenImage, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(6.dp))
                Text("Preview unavailable", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    lifecycle: WorkspaceLifecycle,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onRemember: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Text(
            text = "NOTE",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            TextButton(onClick = { menuExpanded = true }) { Text("More") }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (lifecycle == WorkspaceLifecycle.ACTIVE) {
                    DropdownMenuItem(text = { Text("Remember") }, onClick = {
                        menuExpanded = false
                        onRemember()
                    })
                    DropdownMenuItem(text = { Text("Archive") }, onClick = {
                        menuExpanded = false
                        onArchive()
                    })
                } else {
                    DropdownMenuItem(text = { Text("Restore") }, onClick = {
                        menuExpanded = false
                        onRestore()
                    })
                }
                if (lifecycle != WorkspaceLifecycle.TRASHED) {
                    DropdownMenuItem(text = { Text("Move to trash") }, onClick = {
                        menuExpanded = false
                        onTrash()
                    })
                }
            }
        }
    }
}

@Composable
private fun EditorStatusBar(state: NoteEditorUiState) {
    val label = when (state.status) {
        SaveStatus.LOADING -> "Loading…"
        SaveStatus.SAVING -> "Saving…"
        SaveStatus.SAVED -> "Saved locally"
        SaveStatus.ERROR -> "Could not save"
        SaveStatus.MISSING -> "Unavailable"
    }
    val color = when (state.status) {
        SaveStatus.LOADING -> MaterialTheme.colorScheme.onSurfaceVariant
        SaveStatus.SAVING -> HaloPastelBlue
        SaveStatus.SAVED -> HaloPastelMint
        SaveStatus.ERROR, SaveStatus.MISSING -> MaterialTheme.colorScheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, RoundedCornerShape(3.dp))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
        Text(
            text = "${wordCount(state.body)} words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MissingNoteState(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            onClick = onBack,
            shape = RoundedCornerShape(18.dp),
            color = HaloPastelPink.copy(alpha = 0.16f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Note unavailable",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "This note was deleted or is no longer available. Tap to return.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun noteTitle(body: String): String = body.lineSequence()
    .map(String::trim)
    .firstOrNull(String::isNotEmpty)
    ?.removePrefix("#")
    ?.trim()
    ?.take(80)
    .orEmpty()
    .ifEmpty { "Untitled note" }

private fun wordCount(body: String): Int = body
    .trim()
    .split(Regex("\\s+"))
    .count(String::isNotBlank)
