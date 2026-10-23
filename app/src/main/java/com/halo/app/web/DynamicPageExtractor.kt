package com.halo.app.web

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * MINING-P2-001 web reading hardening.
 *
 * Halo already has `halo_fetch_url` with public-network rules and untrusted-content treatment. This
 * is an OPTIONAL host capability used only when the static extraction path reports
 * [DYNAMIC_CONTENT_REQUIRED]: a page whose readable text only exists after scripts run.
 *
 * It is deliberately **not** browser automation:
 *
 * - one isolated, ephemeral `WebView` per extraction, destroyed immediately afterwards;
 * - JavaScript on (a rendered page needs it) but no model-supplied script — Halo owns the single
 *   extraction script;
 * - file access, content access, DOM storage, cookies, autofill and password saving all disabled;
 * - no Halo credentials are ever attached, and no cookie manager session is reused;
 * - private/loopback/link-local navigation is refused before the WebView ever loads it;
 * - the DOM must go quiet before extraction (a MutationObserver quiet window), with hard ceilings on
 *   wait and navigation, because `delay(3000)` and hoping is not a strategy;
 * - only Halo's own bounded extraction script produces the result, which stays untrusted external
 *   content.
 *
 * Harvested conceptually from Brownie's `BrowserService` (wait for page stability, quiet window,
 * bounded extraction). No general browser surface was adopted.
 */
interface DynamicPageExtractor {
    /** True when this host can run a dynamic extraction at all. */
    val available: Boolean

    suspend fun extract(url: String, deadlineMs: Long): DynamicExtractionOutcome
}

sealed interface DynamicExtractionOutcome {
    data class Extracted(
        val finalUrl: String,
        val title: String,
        val readableText: String,
        val linkCount: Int,
        /** How many mutation waves the quiet-window observer saw; diagnostics only. */
        val quietWaves: Int,
        val elapsedMs: Long,
    ) : DynamicExtractionOutcome

    data class Unavailable(val reason: String) : DynamicExtractionOutcome

    data class Failed(val reason: String) : DynamicExtractionOutcome
}

/** Refusal before loading, shared by the static path and this one. */
object PublicPagePolicy {
    fun isAllowed(url: String): Boolean {
        val parsed = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = parsed.host?.lowercase()?.trim('.') ?: return false
        if (host.isEmpty() || host == "localhost" || host.endsWith(".local")) return false
        if (host.endsWith(".internal") || host.endsWith(".home.arpa")) return false
        return isPublicAddress(host)
    }

    /** Literal address check. DNS-resolved re-validation stays in the fetch tool path. */
    private fun isPublicAddress(host: String): Boolean {
        val ipv4 = host.split(".").takeIf { it.size == 4 }?.mapNotNull { it.toIntOrNull() }
        if (ipv4 != null && ipv4.size == 4 && ipv4.all { it in 0..255 }) {
            val (a, b) = ipv4[0] to ipv4[1]
            return when {
                a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) -> false
                a == 127 || a == 0 -> false
                a == 169 && b == 254 -> false
                a == 100 && b in 64..127 -> false
                else -> true
            }
        }
        if (host.contains(':')) {
            // IPv6 literal: refuse loopback, link-local, unique-local and mapped ranges.
            val lowered = host.removePrefix("[").removeSuffix("]")
            return !(lowered == "::1" || lowered.startsWith("fe80") || lowered.startsWith("fc") ||
                lowered.startsWith("fd") || lowered.startsWith("::ffff:"))
        }
        return true
    }

    /** Called from WebView's interception worker before every public-network resource request. */
    fun resolvesPublicly(url: String): Boolean {
        if (!isAllowed(url)) return false
        val host = Uri.parse(url).host?.removePrefix("[")?.removeSuffix("]") ?: return false
        return runCatching {
            val addresses = java.net.InetAddress.getAllByName(host)
            addresses.isNotEmpty() && addresses.all { address ->
                !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress &&
                    !address.isSiteLocalAddress && !address.isMulticastAddress &&
                    isPublicAddress(address.hostAddress.orEmpty())
            }
        }.getOrDefault(false)
    }
}

/** Android implementation. Created lazily by the host; never at startup. */
class AndroidDynamicPageExtractor(
    private val context: Context,
    private val enabled: () -> Boolean = { false },
    private val dispatchPolicy: com.halo.sdk.provider.RemoteDispatchPolicy? = null,
    /** Injectable public-network predicates for isolated loopback instrumentation. */
    private val urlAllowed: (String) -> Boolean = PublicPagePolicy::isAllowed,
    private val resolutionAllowed: (String) -> Boolean = PublicPagePolicy::resolvesPublicly,
) : DynamicPageExtractor {

    override val available: Boolean get() = enabled()

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun extract(url: String, deadlineMs: Long): DynamicExtractionOutcome {
        if (!available) return DynamicExtractionOutcome.Unavailable("Rendered page extraction is disabled")
        if (!urlAllowed(url)) {
            return DynamicExtractionOutcome.Failed("Refused: not a public http or https page")
        }
        val startedAt = System.currentTimeMillis()
        val provenance = com.halo.sdk.provider.RemoteDispatchContext.provenance.get()
        val dispatchRevision = provenance?.revision ?: dispatchPolicy?.revision
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val handler = Handler(Looper.getMainLooper())
                val finished = AtomicBoolean(false)
                var webView: WebView? = null
                var cookiePolicyHeld = false
                var lease: com.halo.sdk.provider.RemoteDispatchLease? = null

                fun finish(outcome: DynamicExtractionOutcome) {
                    if (!finished.compareAndSet(false, true)) return
                    lease?.finish(if (outcome is DynamicExtractionOutcome.Extracted)
                        com.halo.sdk.provider.RemoteDispatchOutcome.COMPLETED else com.halo.sdk.provider.RemoteDispatchOutcome.FAILED)
                    handler.removeCallbacksAndMessages(null)
                    webView?.let { view ->
                        runCatching {
                            view.stopLoading()
                            view.loadUrl("about:blank")
                            view.destroy()
                        }
                    }
                    webView = null
                    if (cookiePolicyHeld) {
                        cookiePolicyHeld = false
                        activeExtractions--
                        if (activeExtractions == 0) CookieManager.getInstance().setAcceptCookie(previousCookiePolicy)
                    }
                    if (continuation.isActive) continuation.resume(outcome)
                }

                if (dispatchPolicy != null) {
                    lease = try {
                        dispatchPolicy.acquire(com.halo.sdk.provider.RemoteDispatchRequest("web.public",
                            com.halo.sdk.provider.RemoteOperation.WEB,
                            setOf(com.halo.sdk.provider.RemoteDataCategory.TEXT) + provenance?.categories.orEmpty()),
                            requireNotNull(dispatchRevision)) {
                            continuation.cancel(CancellationException("Mode profile changed"))
                            handler.post { finish(DynamicExtractionOutcome.Failed("Mode profile changed")) }
                        }
                    } catch (_: Exception) {
                        finish(DynamicExtractionOutcome.Unavailable("Rendered web reading needs Full Power with web enabled"))
                        return@suspendCancellableCoroutine
                    }
                }

                if (!continuation.isActive) {
                    finish(DynamicExtractionOutcome.Failed("Cancelled"))
                    return@suspendCancellableCoroutine
                }

                val view = WebView(context)
                webView = view
                view.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = false
                    databaseEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(false)
                    builtInZoomControls = false
                    loadsImagesAutomatically = false
                    mediaPlaybackRequiresUserGesture = true
                    // No Halo credentials, no autofill, no password manager participation.
                    saveFormData = false
                    userAgentString = userAgentString?.replace(" wv", "")
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
                val cookies = CookieManager.getInstance()
                if (activeExtractions == 0) previousCookiePolicy = cookies.acceptCookie()
                cookies.setAcceptCookie(false)
                activeExtractions++
                cookiePolicyHeld = true
                view.isSaveEnabled = false
                view.isHapticFeedbackEnabled = false

                // Hard navigation ceiling: a page that never settles still terminates.
                handler.postDelayed({ finish(DynamicExtractionOutcome.Failed("Page did not settle in time")) }, deadlineMs)

                view.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val target = request?.url?.toString() ?: return true
                        if (runCatching { lease?.requireCurrent() }.isFailure) return true
                        // Every navigation, including in-page redirects, re-passes the public rule.
                        if (!urlAllowed(target)) {
                            finish(DynamicExtractionOutcome.Failed("Refused: navigation left the public network"))
                        }
                        return !urlAllowed(target)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): WebResourceResponse? {
                        val target = request?.url?.toString()
                        if (runCatching { lease?.requireCurrent() }.isFailure) {
                            return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                        }
                        if (target != null && !resolutionAllowed(target)) {
                            return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                        // First settle attempt; the observer below may extend it.
                        handler.postDelayed({ extractAfterQuiet(view, startedAt, ::finish) { !finished.get() } }, QUIET_PERIOD_MS)
                    }
                }

                continuation.invokeOnCancellation {
                    handler.post { finish(DynamicExtractionOutcome.Failed("Cancelled")) }
                }

                runCatching { lease?.requireCurrent(); view.loadUrl(url) }
                    .onFailure { finish(DynamicExtractionOutcome.Failed("Could not load the page")) }
            }
        }
    }

    /** Runs Halo's own bounded extraction script and interprets its single JSON result. */
    private fun extractAfterQuiet(
        view: WebView?,
        startedAt: Long,
        finish: (DynamicExtractionOutcome) -> Unit,
        isActive: () -> Boolean,
    ) {
        if (!isActive()) return
        val target = view ?: return finish(DynamicExtractionOutcome.Failed("Page view was released"))
        target.evaluateJavascript(EXTRACTION_SCRIPT) { raw ->
            val payload = raw?.let { text ->
                runCatching {
                    val decoded = JSONTokener(text).nextValue() as? String ?: return@runCatching null
                    JSONObject(decoded).optJSONObject("halo")
                }.getOrNull()
            }
            if (payload == null) {
                finish(DynamicExtractionOutcome.Failed("Extraction returned nothing usable"))
                return@evaluateJavascript
            }
            if (!payload.optBoolean("settled")) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isActive()) extractAfterQuiet(target, startedAt, finish, isActive)
                }, QUIET_PERIOD_MS)
                return@evaluateJavascript
            }
            val text = payload.optString("text").take(MAX_TEXT_CHARS)
            if (text.isBlank()) {
                finish(DynamicExtractionOutcome.Failed("No readable text after scripts settled"))
                return@evaluateJavascript
            }
            finish(
                DynamicExtractionOutcome.Extracted(
                    finalUrl = target.url.orEmpty(),
                    title = payload.optString("title").take(MAX_TITLE_CHARS),
                    readableText = text,
                    linkCount = payload.optInt("links").coerceAtLeast(0),
                    quietWaves = payload.optInt("waves").coerceAtLeast(0),
                    elapsedMs = System.currentTimeMillis() - startedAt,
                )
            )
        }
    }

    private companion object {
        // WebView cookie policy is process-wide; all accesses occur on the main thread.
        var activeExtractions = 0
        var previousCookiePolicy = true
        /**
         * Quiet-window concept: observe DOM mutations and extract once the document has been still for
         * [QUIET_PERIOD_MS]. The hard deadline above guarantees termination regardless.
         */
        const val QUIET_PERIOD_MS = 550L
        const val MAX_TEXT_CHARS = 20_000
        const val MAX_TITLE_CHARS = 300

        /** Halo-owned script. No model input reaches it. */
        const val EXTRACTION_SCRIPT = """
            (function () {
              var state = window.__haloQuiet;
              if (!state) {
                state = { waves: 0, lastMutation: Date.now() };
                window.__haloQuiet = state;
                var observer = new MutationObserver(function () { state.waves++; state.lastMutation = Date.now(); });
                observer.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
              }
              if (Date.now() - state.lastMutation < 550) return JSON.stringify({ halo: { settled: false } });
              var clone = document.body ? document.body.cloneNode(true) : null;
              if (clone) {
                ['script','style','noscript','template','svg','canvas','iframe'].forEach(function (tag) {
                  var found = clone.getElementsByTagName(tag);
                  for (var i = found.length - 1; i >= 0; i--) { found[i].parentNode.removeChild(found[i]); }
                });
              }
              var text = clone ? (clone.innerText || clone.textContent || '') : '';
              text = text.replace(/\s+/g, ' ').trim();
              var links = document.querySelectorAll('a[href]').length;
              return JSON.stringify({ halo: {
                title: document.title || '',
                text: text.slice(0, 20000),
                links: links,
                waves: state.waves,
                settled: true
              }});
            })();
        """
    }
}
