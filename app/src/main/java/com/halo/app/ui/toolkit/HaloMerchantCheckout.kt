package com.halo.app.ui.toolkit

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.shell.HaloDeepLinks
import java.math.BigDecimal
import java.net.URI
import java.util.Currency
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

internal data class MerchantQuote(val destination: URI, val amount: BigDecimal, val currency: Currency) {
    companion object {
        fun parse(url: String, total: String, currency: String): MerchantQuote {
            require(url.length in 1..1000 && url.none(Char::isISOControl))
            val destination = URI(url.trim())
            val host = destination.host?.trimEnd('.').orEmpty()
            require(destination.scheme.equals("https", true) && '.' in host && host.lastOrNull()?.isLetter() == true &&
                !host.endsWith(".localhost", true) &&
                destination.rawUserInfo == null && destination.rawQuery == null && destination.rawFragment == null &&
                destination.port in setOf(-1, 443)) { "Use a public HTTPS storefront link without sign-in or cart tokens." }
            val unit = Currency.getInstance(currency.trim().uppercase(Locale.ROOT))
            require(unit.defaultFractionDigits in 0..4)
            require(total.trim().matches(Regex("""\d{1,7}(?:[.,]\d{1,4})?""")))
            val amount = total.trim().replace(',', '.').toBigDecimal()
            require(amount.scale() <= unit.defaultFractionDigits && amount.signum() > 0)
            return MerchantQuote(destination, amount, unit)
        }
    }
}

internal object MerchantCheckoutRequests {
    private data class Pending(val id: String, val quote: MerchantQuote, val adultRevision: Long, val expiresAt: Long)
    private var pending: Pending? = null

    @Synchronized
    fun dispatch(quote: MerchantQuote, adultRevision: Long, routes: MutableStateFlow<String?>): String? {
        val now = SystemClock.elapsedRealtime()
        if (pending?.let { now < it.expiresAt } == true) return null
        val request = Pending(UUID.randomUUID().toString(), quote, adultRevision, now + 180_000L)
        pending = request
        if (!routes.compareAndSet(null, HaloDeepLinks.PREFIX + "merchant/checkout/" + request.id)) {
            pending = null
            return null
        }
        return request.id
    }

    @Synchronized
    fun consume(id: String, adultRevision: Long): MerchantQuote? {
        val request = pending?.takeIf { it.id == id } ?: return null
        pending = null
        return request.quote.takeIf {
            request.adultRevision == adultRevision && SystemClock.elapsedRealtime() < request.expiresAt
        }
    }
}

@Composable
internal fun HaloMerchantCheckoutScreen(requestId: String?, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val family = Graph.kidsPreferences
    var url by rememberSaveable { mutableStateOf("") }
    var total by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf("EUR") }
    var requestRead by rememberSaveable(requestId) { mutableStateOf(false) }
    var review by remember { mutableStateOf<MerchantQuote?>(null) }
    var pending by remember { mutableStateOf<Pair<MerchantQuote, Long>?>(null) }
    var approved by remember { mutableStateOf<Pair<MerchantQuote, Long>?>(null) }
    var signal by remember { mutableStateOf<CancellationSignal?>(null) }
    var error by remember { mutableIntStateOf(0) }
    var dispatched by rememberSaveable { mutableStateOf(false) }
    val heading = stringResource(R.string.halo11_checkout_title)
    val detail = stringResource(R.string.halo11_checkout_confirm)

    LaunchedEffect(requestId) {
        if (requestId != null && !requestRead) {
            requestRead = true
            val revision = runCatching { family.adultActionRevision() }.getOrNull()
            val draft = revision?.let { MerchantCheckoutRequests.consume(requestId, it) }
            if (draft != null && !Graph.isHaloPaused()) {
                url = draft.destination.toASCIIString()
                total = draft.amount.toPlainString()
                currency = draft.currency.currencyCode
                review = draft
            } else {
                error = R.string.halo11_checkout_expired
            }
        }
    }
    DisposableEffect(Unit) { onDispose { signal?.cancel() } }
    fun finishAuthentication(accepted: Boolean) {
        val attempt = pending
        pending = null
        signal = null
        if (accepted) approved = attempt
    }
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        finishAuthentication(result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(approved) {
        val (quote, revision) = approved ?: return@LaunchedEffect
        lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
        approved = null
        try {
            family.requireAdultAction(revision)
            Graph.haloPause.requireActive()
            requireAdultToolkit(context)
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(quote.destination.toASCIIString())).addCategory(Intent.CATEGORY_BROWSABLE))
            dispatched = true
        } catch (_: Exception) { error = R.string.halo11_checkout_open_failed }
    }
    fun authenticate(quote: MerchantQuote) {
        if (pending != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        try {
            val revision = family.adultActionRevision()
            Graph.haloPause.requireActive()
            val gate = requireNotNull(context.getSystemService(KeyguardManager::class.java))
            check(gate.isDeviceSecure)
            pending = quote to revision
            if (Build.VERSION.SDK_INT >= 30) {
                val attempt = CancellationSignal()
                signal = attempt
                BiometricPrompt.Builder(context).setTitle(heading).setSubtitle(detail)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build().authenticate(attempt, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            if (signal === attempt) finishAuthentication(true)
                        }

                        override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence) {
                            if (signal === attempt) {
                                finishAuthentication(false)
                                if (errorCode !in setOf(BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED, BiometricPrompt.BIOMETRIC_ERROR_CANCELED))
                                    error = R.string.halo11_checkout_auth_failed
                            }
                        }
                    })
            } else {
                @Suppress("DEPRECATION")
                legacy.launch(requireNotNull(gate.createConfirmDeviceCredentialIntent(heading, detail)))
            }
        } catch (_: Exception) {
            finishAuthentication(false)
            error = R.string.halo11_checkout_auth_failed
        }
    }

    Column(Modifier.fillMaxSize()) {
        com.halo.app.ui.screens.notebook.ToolZoneHeader(heading, onBack) {}
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.halo11_checkout_how))
            OutlinedTextField(url, { url = it.take(1000); dispatched = false }, Modifier.fillMaxWidth(),
                enabled = pending == null && approved == null, singleLine = true,
                label = { Text(stringResource(R.string.halo11_checkout_link)) })
            OutlinedTextField(total, { total = it.take(16); dispatched = false }, Modifier.fillMaxWidth(),
                enabled = pending == null && approved == null, singleLine = true,
                label = { Text(stringResource(R.string.halo11_checkout_total)) })
            OutlinedTextField(currency, { currency = it.take(3); dispatched = false }, Modifier.fillMaxWidth(),
                enabled = pending == null && approved == null, singleLine = true,
                label = { Text(stringResource(R.string.halo11_checkout_currency)) })
            Button(onClick = {
                error = 0
                review = runCatching { MerchantQuote.parse(url, total, currency) }.getOrNull()
                if (review == null) error = R.string.halo11_checkout_invalid
            }, enabled = pending == null && approved == null && url.isNotBlank() && total.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.halo11_checkout_review)) }
            if (error != 0) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
            if (dispatched) Text(stringResource(R.string.halo11_checkout_opened))
        }
    }
    review?.let { quote ->
        AlertDialog(onDismissRequest = { review = null }, title = { Text(stringResource(R.string.halo11_checkout_review)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(quote.destination.host, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.halo11_checkout_quote, quote.amount.toPlainString(), quote.currency.currencyCode))
                    Text(stringResource(R.string.halo11_checkout_how))
                }
            },
            confirmButton = { TextButton(onClick = { review = null; authenticate(quote) }) { Text(detail) } },
            dismissButton = { TextButton(onClick = { review = null }) { Text(stringResource(android.R.string.cancel)) } })
    }
}
