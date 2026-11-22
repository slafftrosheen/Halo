package com.halo.app.nativeactions

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.design.HaloShapes
import com.halo.app.ui.instruments.HaloInstrumentAction
import com.halo.app.ui.instruments.HaloInstrumentBar
import com.halo.app.language.uiText
import com.halo.app.ui.theme.HaloSunriseApricot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.*

private data class VaultApproval(val ticket: Int, val crypto: VaultUnlock?, val elapsed: Long = SystemClock.elapsedRealtime())
private data class VaultEdit(val paper: VaultPaper, val bytes: ByteArray? = null, val evidence: Map<String, String> = emptyMap())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HaloVaultScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember(context.applicationContext) { HaloVaultStore(context.applicationContext) }
    val privateStore = remember { Graph.privateLibraryStore }
    val privateRequest = remember { PrivateLibraryHandoff.pending() }
    val scope = rememberCoroutineScope()
    val family by Graph.kidsPreferences.enabled.collectAsStateWithLifecycle()
    val ready by Graph.kidsPreferences.loadedState.collectAsStateWithLifecycle()
    var session by remember { mutableStateOf<VaultSession?>(null) }
    var papers by remember { mutableStateOf(emptyList<VaultPaper>()) }
    var draft by remember { mutableStateOf<VaultEdit?>(null) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var delete by remember { mutableStateOf<VaultPaper?>(null) }
    var pendingImage by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var choosePaper by remember { mutableStateOf<String?>(null) }
    var chooseFolder by remember { mutableStateOf(false) }
    var folderFilter by remember { mutableStateOf<String?>(null) }
    var sharingPicker by remember { mutableStateOf(false) }
    var sharePaper by remember { mutableStateOf<VaultPaper?>(null) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var alert by remember { mutableStateOf<Int?>(null) }
    var resumed by remember { mutableStateOf(false) }
    var ticket by remember { mutableIntStateOf(0) }
    var legacyTicket by remember { mutableStateOf<Int?>(null) }
    var approval by remember { mutableStateOf<VaultApproval?>(null) }
    var signal by remember { mutableStateOf<CancellationSignal?>(null) }
    val privateAccess = session?.takeIf { resumed && ready && !family }
    val privateState = rememberPrivateLibraryVaultState(privateStore, privateAccess, privateRequest)

    fun clearDraft() { draft?.bytes?.fill(0); draft = null }
    fun lock() {
        session?.close()
        session = null
        papers = emptyList()
        searchQuery = ""
        searchOpen = false
        choosePaper = null
        chooseFolder = false
        folderFilter = null
        sharingPicker = false
        sharePaper = null
        clearDraft()
        photo = null
        delete = null
        alert = null
        if (signal == null && legacyTicket == null && approval == null) busy = false
    }

    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false; lock() }
    }
    DisposableEffect(context) {
        val window = context.vaultActivity()?.window
        val secure = (window?.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_SECURE != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            ticket++
            signal?.cancel()
            PrivateLibraryHandoff.discard(privateRequest)
            lock()
            if (!secure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
    LaunchedEffect(family, ready) {
        if (family || !ready) {
            ticket++
            signal?.cancel()
            signal = null
            legacyTicket = null
            approval = null
            pendingImage = null
            lock()
        }
    }
    val credential = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val reviewed = legacyTicket
        legacyTicket = null
        if (reviewed == ticket && result.resultCode == Activity.RESULT_OK) approval = VaultApproval(ticket, null)
        else busy = false
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        pendingImage = uri
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImage = uri
    }

    LaunchedEffect(approval, resumed, family) {
        val verified = approval ?: return@LaunchedEffect
        if (!resumed || family || !ready) return@LaunchedEffect
        busy = true
        var opened: VaultSession? = null
        var adopted = false
        try {
            check(verified.ticket == ticket && SystemClock.elapsedRealtime() - verified.elapsed in 0..10_000L)
            val data = withContext(Dispatchers.IO) {
                val familyRevision = Graph.kidsPreferences.adultActionRevision()
                opened = store.finishUnlock(verified.crypto ?: store.prepareUnlock()) {
                    Graph.kidsPreferences.requireAdultAction(familyRevision)
                    check(context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false)
                }
                store.list(requireNotNull(opened))
            }
            if (verified.ticket == ticket && resumed && !family) {
                session = opened
                papers = data
                adopted = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (verified.ticket == ticket) alert = R.string.halo_vault_operation_failed
        } finally {
            if (!adopted) opened?.close()
            if (approval == verified && (resumed || adopted)) approval = null
            if (verified.ticket == ticket) busy = false
        }
    }

    LaunchedEffect(session, pendingImage) {
        val token = session ?: return@LaunchedEffect
        val uri = pendingImage ?: return@LaunchedEffect
        var result: VaultPhotoDraft? = null
        var adopted = false
        busy = true
        try {
            withContext(Dispatchers.IO) { token.requireOpen(); result = readVaultPhoto(context, uri) }
            if (session == token && resumed) {
                requireNotNull(result).let { draft = VaultEdit(it.paper, it.bytes, it.evidence) }
                pendingImage = null
                adopted = true
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (session == token) {
                pendingImage = null
                alert = R.string.halo_vault_operation_failed
            }
        } finally {
            if (!adopted) result?.bytes?.fill(0)
            if (session == token) busy = false
        }
    }

    fun unlock() {
        if (busy || family || !ready) return
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) {
            alert = R.string.halo_vault_auth_unavailable
            return
        }
        alert = null
        val attempt = ++ticket
        busy = true
        if (Build.VERSION.SDK_INT < 30) {
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent(resources.getString(R.string.halo_vault_unlock),
                resources.getString(R.string.halo_vault_unlock_detail))
            legacyTicket = attempt
            try { credential.launch(requireNotNull(intent)) }
            catch (_: Exception) { legacyTicket = null; busy = false; alert = R.string.halo_vault_auth_unavailable }
            return
        }
        scope.launch {
            try {
                val request = withContext(Dispatchers.IO) { store.prepareUnlock() }
                if (attempt != ticket || !resumed) {
                    busy = false
                    return@launch
                }
                val show = BiometricPrompt.Builder(context)
                    .setTitle(resources.getString(R.string.halo_vault_unlock))
                    .setSubtitle(resources.getString(R.string.halo_vault_unlock_detail))
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()
                val cancel = CancellationSignal()
                signal = cancel
                show.authenticate(BiometricPrompt.CryptoObject(request.cipher), cancel, ContextCompat.getMainExecutor(context),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (attempt != ticket || signal !== cancel) return
                            signal = null
                            if (result.cryptoObject?.cipher === request.cipher) approval = VaultApproval(attempt, request)
                            else { busy = false; alert = R.string.halo_vault_auth_unavailable }
                        }

                        override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence) {
                            if (attempt != ticket || signal !== cancel) return
                            signal = null
                            busy = false
                            if (errorCode != BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.BIOMETRIC_ERROR_CANCELED) alert = R.string.halo_vault_auth_unavailable
                        }
                    })
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                signal = null
                busy = false
                alert = R.string.halo_vault_operation_failed
            }
        }
    }

    fun work(operation: suspend (VaultSession) -> Unit) {
        val access = session ?: return
        if (busy) return
        busy = true
        alert = null
        scope.launch {
            try { operation(access) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (session == access) alert = R.string.halo_vault_operation_failed }
            finally { if (session == access) busy = false }
        }
    }

    val shareable = papers.filter { it.kind != VaultPaperKind.PRIVATE_NOTE }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo_vault_back))
            }
            if (session != null) TextButton(onClick = { ticket++; lock() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.halo_vault_lock))
            }
        }
        Text(stringResource(R.string.halo11_private_title), style = MaterialTheme.typography.headlineMedium)
        if (!family && ready) HaloInstrumentBar("vault", mapOf(
            "vault.unlock" to HaloInstrumentAction(::unlock, !busy && session == null, selected = session != null),
            "vault.scan_a_paper" to HaloInstrumentAction({
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, !busy && session != null),
            "vault.import_file" to HaloInstrumentAction({ documentPicker.launch(arrayOf("image/*")) }, !busy && session != null),
            "vault.folders" to HaloInstrumentAction({ chooseFolder = true }, !busy && session != null, selected = folderFilter != null),
            "vault.list_grid" to HaloInstrumentAction({ grid = !grid }, !busy && session != null, selected = grid),
            "vault.preview_details" to HaloInstrumentAction({ choosePaper = "Preview paper" }, !busy && session != null && papers.isNotEmpty()),
            "vault.reminder" to HaloInstrumentAction({ choosePaper = "Edit paper reminder" }, !busy && session != null && papers.isNotEmpty()),
            "vault.search" to HaloInstrumentAction({ searchOpen = !searchOpen }, !busy && session != null, selected = searchOpen),
            "vault.share_a_copy" to HaloInstrumentAction({ sharingPicker = true }, !busy && session != null && shareable.isNotEmpty()),
        ), primary = "vault.unlock")
        Text(stringResource(R.string.halo_vault_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        alert?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (session == null || family || !ready) {
            Surface(shape = HaloShapes.hero, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.halo_vault_locked), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(if (privateRequest != null && PrivateLibraryHandoff.pending() === privateRequest)
                        R.string.halo11_private_request_unlock else R.string.halo_vault_unlock_detail))
                    Button(onClick = ::unlock, enabled = ready && !family && !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.halo_vault_unlock))
                    }
                }
            }
        } else {
            if (searchOpen) OutlinedTextField(searchQuery, { searchQuery = it.take(150) },
                label = { Text(uiText("Search papers")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            folderFilter?.let { folder -> TextButton(onClick = { folderFilter = null }) {
                Text(if (folder.isBlank()) uiText("Without a folder") else folder)
            } }
            val visiblePapers = papers.filter { (folderFilter == null || it.folder == folderFilter) &&
                (it.name.contains(searchQuery.trim(), ignoreCase = true) || it.kind.name.contains(searchQuery.trim(), ignoreCase = true) ||
                    it.text.contains(searchQuery.trim(), ignoreCase = true)) }
                .sortedWith(compareBy<VaultPaper> { it.ends ?: LocalDate.MAX }.thenBy { it.name })
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                privateAccess?.let { access -> privateLibraryVaultItems(privateStore, access, privateState) }
                if (papers.isEmpty()) item { Text(stringResource(R.string.halo_vault_empty)) }
                if (papers.isNotEmpty() && visiblePapers.isEmpty()) item { Text(uiText("No matching papers")) }
                if (grid) items(visiblePapers.chunked(2)) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { paper ->
                            Surface(Modifier.weight(1f).heightIn(min = 92.dp).clickable(enabled = !busy) { draft = VaultEdit(paper) },
                                shape = HaloShapes.hero, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(paper.name, style = MaterialTheme.typography.titleMedium)
                                    paper.ends?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                } else items(visiblePapers, key = VaultPaper::id) {
                    VaultPaperRow(it, enabled = !busy) { draft = VaultEdit(it) }
                }
            }
            Text(stringResource(R.string.halo_vault_privacy), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy) {
                    Text(stringResource(R.string.halo_vault_scan))
                }
                Button(onClick = { draft = VaultEdit(VaultPaper()) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp), enabled = !busy) {
                    Text(stringResource(R.string.halo_vault_add))
                }
            }
        }
    }

    privateAccess?.let { PrivateLibraryVaultDialogs(privateStore, it, privateState) }

    if (sharingPicker && session != null && !family) VaultSharePaperPicker(shareable, onChoose = {
        sharingPicker = false
        sharePaper = it
    }, onClose = { sharingPicker = false })
    val reviewedShare = sharePaper
    val shareTitle = uiText("Share a copy")
    if (reviewedShare != null && session != null && !family) {
        VaultShareReview(reviewedShare, busy, alert == R.string.halo_vault_operation_failed, onShare = { format, days ->
            work { access ->
                shareVaultCopy(context, store, access, reviewedShare, format, days, shareTitle) {
                    resumed && session == access && sharePaper == reviewedShare
                }
                sharePaper = null
            }
        }, onClose = { sharePaper = null })
    }

    if (chooseFolder && session != null && !family) AlertDialog(onDismissRequest = { chooseFolder = false },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn), title = { Text(uiText("Folders")) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { folderFilter = null; chooseFolder = false }) { Text(uiText("All papers")) }
            papers.map(VaultPaper::folder).distinct().sorted().forEach { name ->
                TextButton(onClick = { folderFilter = name; chooseFolder = false }) { Text(name.ifBlank { uiText("Without a folder") }) }
            }
            Text(uiText("Assign a folder when editing a paper."), style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = { TextButton(onClick = { chooseFolder = false }) { Text(uiText("Done")) } })
    choosePaper?.takeIf { session != null && !family }?.let { title ->
        AlertDialog(onDismissRequest = { choosePaper = null }, title = { Text(uiText(title)) },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn), text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    papers.sortedBy { it.name }.forEach { paper ->
                        TextButton(onClick = { choosePaper = null; draft = VaultEdit(paper) }) { Text(paper.name) }
                    }
                }
            }, confirmButton = { TextButton(onClick = { choosePaper = null }) { Text(uiText("Cancel")) } })
    }
    val currentDraft = draft
    if (session != null && !family && currentDraft != null) {
        ModalBottomSheet(onDismissRequest = { if (!busy) clearDraft() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), sheetGesturesEnabled = !busy,
            properties = ModalBottomSheetProperties(securePolicy = SecureFlagPolicy.SecureOn)) {
            VaultPaperEditor(currentDraft, busy, onCancel = ::clearDraft, onDelete = { delete = currentDraft.paper },
                onPhoto = {
                    work { access ->
                        val bitmap = withContext(Dispatchers.IO) {
                            val bytes = store.viewPhoto(access, currentDraft.paper)
                            try { requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) }
                            finally { bytes.fill(0) }
                        }
                        if (session == access) photo = bitmap else bitmap.recycle()
                    }
                }, onKeep = { next ->
                    work { access ->
                        val (saved, reminder) = withContext(Dispatchers.IO) {
                            val saved = store.save(access, next, currentDraft.bytes)
                            saved to runCatching { HaloVaultReminder.apply(context, saved) }
                        }
                        if (session == access) {
                            papers = papers.filterNot { it.id == saved.id } + saved
                            clearDraft()
                            if (reminder.isFailure) alert = R.string.halo_vault_reminder_failed
                            else if (saved.remind != null && !HaloVaultReminder.authorized(context))
                                alert = R.string.halo_vault_reminder_permission
                        }
                    }
                })
        }
    }
    delete?.let { paper ->
        AlertDialog(onDismissRequest = { delete = null }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn), title = { Text(stringResource(R.string.halo_vault_remove)) },
            text = { Text(stringResource(R.string.halo_vault_remove_body)) }, confirmButton = {
                Button(onClick = {
                    delete = null
                    work { access ->
                        withContext(Dispatchers.IO) { store.delete(access, paper); HaloVaultReminder.cancel(context, paper.id) }
                        if (session == access) { papers = papers.filterNot { it.id == paper.id }; clearDraft() }
                    }
                }) { Text(stringResource(R.string.halo_vault_remove)) }
            }, dismissButton = { TextButton(onClick = { delete = null }) { Text(stringResource(R.string.halo_vault_cancel)) } })
    }
    photo?.let { bitmap ->
        AlertDialog(onDismissRequest = { photo = null }, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn), title = { Text(stringResource(R.string.halo_vault_photo_description)) },
            text = { Image(bitmap.asImageBitmap(), stringResource(R.string.halo_vault_photo_description),
                Modifier.fillMaxWidth().heightIn(max = 420.dp), contentScale = ContentScale.Fit) },
            confirmButton = { TextButton(onClick = { photo = null }) { Text(stringResource(R.string.halo_vault_back)) } })
    }
}

@Composable
private fun VaultPaperRow(paper: VaultPaper, enabled: Boolean, onClick: () -> Unit) {
    val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(LocalResources.current.configuration.locales[0])
    val now = LocalDate.now()
    val needsAttention = paper.ends?.let { it <= now.plusDays(30) } == true || paper.remind?.let { it <= now } == true
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(paper.name, style = MaterialTheme.typography.titleMedium)
                Text(paper.bought?.let { stringResource(R.string.halo_vault_bought_on, it.format(format)) }
                    ?: stringResource(paper.kind.label), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            paper.ends?.let {
                val end = stringResource(if (it < now) R.string.halo_vault_ended_on else R.string.halo_vault_end_on, it.format(format))
                if (needsAttention) Surface(color = HaloSunriseApricot.copy(alpha = .24f), shape = MaterialTheme.shapes.small) {
                    Text(end, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = if (MaterialTheme.colorScheme.surface.luminance() < .5f) HaloSunriseApricot else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelMedium)
                } else Text(end, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultPaperEditor(edit: VaultEdit, busy: Boolean, onCancel: () -> Unit, onDelete: () -> Unit,
    onPhoto: () -> Unit, onKeep: (VaultPaper) -> Unit) {
    val paper = edit.paper
    var name by remember(paper.id) { mutableStateOf(paper.name) }
    var kind by remember(paper.id) { mutableStateOf(paper.kind) }
    var bought by remember(paper.id) { mutableStateOf(paper.bought?.toString().orEmpty()) }
    var ends by remember(paper.id) { mutableStateOf(paper.ends?.toString().orEmpty()) }
    var remind by remember(paper.id) { mutableStateOf(paper.remind?.toString().orEmpty()) }
    var folder by remember(paper.id) { mutableStateOf(paper.folder) }
    var text by remember(paper.id) { mutableStateOf(paper.text) }
    fun date(raw: String): LocalDate? = raw.takeIf(String::isNotBlank)?.let(LocalDate::parse)
    val privateNote = kind == VaultPaperKind.PRIVATE_NOTE
    val reviewed = runCatching {
        paper.copy(name = name.trim(), kind = kind, bought = if (privateNote) null else date(bought),
            ends = if (privateNote) null else date(ends), remind = if (privateNote) null else date(remind),
            folder = folder.trim(), text = if (privateNote) text.trim() else "").also(VaultPaper::validate)
    }.getOrNull()
    Column(Modifier.fillMaxWidth().heightIn(max = 640.dp).verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(when { edit.bytes != null -> R.string.halo_vault_review_photo
            paper.revision > 0 -> R.string.halo_vault_edit
            else -> R.string.halo_vault_add }), style = MaterialTheme.typography.headlineSmall)
        if (edit.bytes != null) Text(stringResource(R.string.halo_vault_review))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VaultPaperKind.entries.filter { option ->
                when {
                    paper.kind == VaultPaperKind.PRIVATE_NOTE -> option == VaultPaperKind.PRIVATE_NOTE
                    option != VaultPaperKind.PRIVATE_NOTE -> true
                    else -> edit.bytes == null && !paper.photo && paper.revision == 0L
                }
            }.forEach { option ->
                FilterChip(kind == option, { kind = option }, enabled = !busy,
                    label = { Text(stringResource(option.label)) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        OutlinedTextField(name, { name = it.take(180) }, enabled = !busy, singleLine = true,
            label = { Text(stringResource(R.string.halo_vault_what)) }, modifier = Modifier.fillMaxWidth())
        edit.evidence["what"]?.let { Text(stringResource(R.string.halo_vault_evidence, it), style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(folder, { folder = it.take(80) }, enabled = !busy, singleLine = true,
            label = { Text(uiText("Folder")) }, modifier = Modifier.fillMaxWidth())
        if (privateNote) OutlinedTextField(text, { value ->
            text = value.filterNot { it.isISOControl() && it != '\n' && it != '\t' }.take(2400)
        }, enabled = !busy, minLines = 4, maxLines = 9,
            label = { Text(stringResource(R.string.halo11_private_note_text)) }, modifier = Modifier.fillMaxWidth())
        else {
            VaultDateField(R.string.halo_vault_bought, bought, { bought = it }, !busy, edit.evidence["bought"])
            VaultDateField(R.string.halo_vault_ends, ends, { ends = it }, !busy, edit.evidence["ends"])
            VaultDateField(R.string.halo_vault_remind, remind, { remind = it }, !busy, null)
        }
        if (edit.bytes != null && paper.remind != null) Text(stringResource(R.string.halo_vault_remind_evidence),
            style = MaterialTheme.typography.bodySmall)
        if (reviewed == null && name.isNotBlank() && !privateNote) Text(stringResource(R.string.halo_vault_check_dates),
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (paper.photo) TextButton(onClick = onPhoto, enabled = !busy) { Text(stringResource(R.string.halo_vault_photo)) }
        Button(onClick = { reviewed?.let(onKeep) }, enabled = reviewed != null && !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.halo_vault_keep)) }
        if (paper.revision > 0) TextButton(onClick = onDelete, enabled = !busy) { Text(stringResource(R.string.halo_vault_remove)) }
        TextButton(onClick = onCancel, enabled = !busy) { Text(stringResource(R.string.halo_vault_cancel)) }
    }
}

@Composable
private fun VaultDateField(label: Int, value: String, onValue: (String) -> Unit, enabled: Boolean, evidence: String?) {
    OutlinedTextField(value, { onValue(it.take(10)) }, enabled = enabled, singleLine = true,
        label = { Text(stringResource(label)) }, placeholder = { Text(stringResource(R.string.halo_vault_date_hint)) },
        modifier = Modifier.fillMaxWidth())
    evidence?.let { Text(stringResource(R.string.halo_vault_evidence, it), style = MaterialTheme.typography.bodySmall) }
}

internal val VaultPaperKind.label: Int get() = when (this) {
    VaultPaperKind.WARRANTY -> R.string.halo_vault_kind_warranty
    VaultPaperKind.RECEIPT -> R.string.halo_vault_kind_receipt
    VaultPaperKind.POLICY -> R.string.halo_vault_kind_policy
    VaultPaperKind.DOCUMENT -> R.string.halo_vault_kind_document
    VaultPaperKind.PRIVATE_NOTE -> R.string.halo11_private_note
}

private tailrec fun Context.vaultActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.vaultActivity()
    else -> null
}
