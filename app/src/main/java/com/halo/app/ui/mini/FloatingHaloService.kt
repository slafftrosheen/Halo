package com.halo.app.ui.mini

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.nativeactions.nativeUi
import com.halo.app.ui.state.toSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import com.halo.app.ui.workspace.ToolPageAppearance
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect

/** User-started launcher only: no microphone, content capture, boot restart or tool execution. */
class FloatingHaloService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windows: WindowManager
    private var bubble: ImageView? = null
    private var dismiss: TextView? = null
    private var paintedScheme: androidx.compose.material3.ColorScheme? = null
    private var bubblePhase = BubblePhase.IDLE
    private var paintJob: Job? = null
    private var bubbleMotion: android.animation.ObjectAnimator? = null
    private val parameters = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }
    private val preferences by lazy { getSharedPreferences("halo_bubble", MODE_PRIVATE) }
    private val appearanceStore by lazy { getSharedPreferences(ToolPageAppearance.STORE, MODE_PRIVATE) }
    private val appearanceOwner by lazy { miniAppearanceOwner(this) }
    private var appearance = MiniAppearance()
    private var fullScreenBehind = false
    private var appearanceJob: Job? = null
    private val appearanceChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == MINI_APPEARANCE_KEY) readAppearance()
    }
    private val paletteStores by lazy {
        listOf("halo_palettes", "halo_appearance", "halo_visual_options").map { getSharedPreferences(it, MODE_PRIVATE) }
    }
    private val changed = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> paintOrb() }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { stopSelf() }
    }
    private var receiverRegistered = false
    private val overlayAccess = android.app.AppOpsManager.OnOpChangedListener { _, _ ->
        scope.launch { if (!Settings.canDrawOverlays(this@FloatingHaloService)) stopSelf() }
    }

    override fun onCreate() {
        super.onCreate()
        ManualDependencyGraph.init(this)
        windows = getSystemService(WindowManager::class.java)
        appearanceStore.registerOnSharedPreferenceChangeListener(appearanceChanged)
        readAppearance()
        paletteStores.forEach { it.registerOnSharedPreferenceChangeListener(changed) }
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        getSystemService(android.app.AppOpsManager::class.java).startWatchingMode(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, overlayAccess)
        scope.launch {
            HaloHostVisibility.active.collect { refreshVisibility() }
        }
        scope.launch {
            ManualDependencyGraph.homeViewModel.uiState.collect { ui ->
                val phase = when {
                    ui.runtimeWaitingState in setOf(com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_APPROVAL,
                        com.halo.runtime.cognition.agent.AgentRunState.WAITING_FOR_CLARIFICATION) -> BubblePhase.NEEDS_YOU
                    ui.inputStatus.capturing || ui.speechActive -> BubblePhase.LISTENING
                    ui.speechProcessing || ui.sessionState.toSignalPresentation(ui.runtimeState).state in
                        setOf(HaloSignalState.THINKING, HaloSignalState.ACTING) -> BubblePhase.WORKING
                    else -> BubblePhase.IDLE
                }
                if (phase != bubblePhase) { bubblePhase = phase; paintOrb() }
            }
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == HIDE || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            foreground()
            if (bubble == null) showBubble()
        } catch (_: Exception) { failure.value = "Could not show floating Halo. Try again."; stopSelf() }
        return START_NOT_STICKY
    }
    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, nativeUi("Floating Halo"), NotificationManager.IMPORTANCE_LOW))
        val hide = PendingIntent.getService(this, 8101, Intent(this, FloatingHaloService::class.java).setAction(HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_halo_talk)
            .setContentTitle(nativeUi("Floating Halo")).setContentText(nativeUi("Tap the orb to open Mini Halo."))
            .setContentIntent(MiniHaloEntry.pending(this, 8100)).setOngoing(true)
            .addAction(Notification.Action.Builder(null, nativeUi("Hide"), hide).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(8100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(8100, notification)
    }
    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else resources.displayMetrics.let { it.widthPixels to it.heightPixels }

    private fun readAppearance() {
        appearanceJob?.cancel()
        appearanceJob = scope.launch {
            appearance = appearanceOwner.load().current
            placeBubble()
            bubble?.let { windows.updateViewLayout(it, parameters) }
            refreshVisibility()
            paintOrb()
        }
    }

    private fun placeBubble() {
        val size = (appearance.size.diameter * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        parameters.width = size
        parameters.height = size
        val (width, height) = screenSize()
        parameters.x = if (appearance.edge == BubbleEdge.LEFT) 0 else (width - size).coerceAtLeast(0)
        parameters.y = (preferences.getFloat("y", .3f).coerceIn(0f, 1f) * (height - size).coerceAtLeast(0)).toInt()
    }

    private fun refreshVisibility() {
        val image = bubble ?: return
        val visible = !HaloHostVisibility.active.value && !(appearance.hideFullscreen && fullScreenBehind)
        val nextVisibility = if (visible) View.VISIBLE else View.INVISIBLE
        if (image.visibility != nextVisibility) {
            image.visibility = nextVisibility
            if (visible) paintOrb() else stopBubbleMotion()
        }
    }

    private fun showBubble() {
        placeBubble()
        val image = ImageView(this).apply {
            visibility = if (HaloHostVisibility.active.value) View.INVISIBLE else View.VISIBLE
            contentDescription = nativeUi("Open Mini Halo. Hold to hide.")
            setOnClickListener { runCatching { startActivity(MiniHaloEntry.intent(this@FloatingHaloService)) }
                .onFailure { failure.value = "Could not open Mini Halo. Open Halo from your launcher." } }
            setOnLongClickListener { stopSelf(); true }
            if (Build.VERSION.SDK_INT >= 30) setOnApplyWindowInsetsListener { _, insets ->
                fullScreenBehind = !insets.isVisible(WindowInsets.Type.statusBars()) &&
                    !insets.isVisible(WindowInsets.Type.navigationBars())
                refreshVisibility()
                insets
            }
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        image.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = parameters.x; startY = parameters.y; moved = false; false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX-downX; val dy = event.rawY-downY
                    if (kotlin.math.abs(dx) > slop || kotlin.math.abs(dy) > slop) moved = true
                    if (moved) {
                        image.cancelLongPress(); image.isPressed = false
                        showDismiss()
                        val bounds = screenSize()
                        val size = parameters.width
                        parameters.x = (startX+dx.toInt()).coerceIn(0, (bounds.first-size).coerceAtLeast(0))
                        parameters.y = (startY+dy.toInt()).coerceIn(0, (bounds.second-size).coerceAtLeast(0))
                        windows.updateViewLayout(image, parameters)
                    }
                    moved
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) { image.cancelLongPress(); image.isPressed = false }
                    removeDismiss()
                    if (moved) {
                        val bounds = screenSize()
                        val size = parameters.width
                        if (event.actionMasked == MotionEvent.ACTION_UP && event.rawY > bounds.second-120*resources.displayMetrics.density &&
                            kotlin.math.abs(event.rawX-bounds.first/2f) < 100*resources.displayMetrics.density) stopSelf()
                        else {
                            val position = BubblePosition.snap(parameters.x, parameters.y, bounds.first, bounds.second, size)
                            parameters.x = position.first; parameters.y = position.second
                            windows.updateViewLayout(image, parameters)
                            val edge = if (position.first == 0) BubbleEdge.LEFT else BubbleEdge.RIGHT
                            scope.launch {
                                // A drag and the settings picker share the same persisted edge.
                                val saved = appearanceOwner.load()
                                val next = saved.current.copy(edge = edge)
                                val accepted = try {
                                    appearanceOwner.apply(saved, next)
                                    true
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    false
                                }
                                if (accepted) withContext(Dispatchers.IO) {
                                    preferences.edit().putFloat("x", if (edge == BubbleEdge.LEFT) 0f else 1f)
                                        .putFloat("y", position.second.toFloat() / (bounds.second - size).coerceAtLeast(1)).commit()
                                }
                                readAppearance()
                            }
                        }
                    }
                    moved
                }
                else -> false
            }
        }
        windows.addView(image, parameters); bubble = image; running.value = true; failure.value = null
        refreshVisibility()
        paintOrb()
    }
    private fun stopBubbleMotion() {
        bubbleMotion?.cancel()
        bubbleMotion = null
        bubble?.apply { alpha = 1f; rotation = 0f }
    }

    private fun paintOrb() {
        val image = bubble ?: return
        paintJob?.cancel()
        paintJob = scope.launch {
            val phase = bubblePhase
            val density = resources.displayMetrics.density
            val size = parameters.width
            val paint = withContext(Dispatchers.IO) {
                val scheme = nearbyScheme(this@FloatingHaloService)
                val orb = nearbyOrb(this@FloatingHaloService, size, floating = true, scheme = scheme)
                if (phase == BubblePhase.NEEDS_YOU) android.graphics.Canvas(orb).drawCircle(
                    orb.width * .81f, orb.height * .21f, 5f * density,
                    android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = scheme.tertiary.toArgb() },
                )
                orb to scheme
            }
            if (bubble !== image || phase != bubblePhase || size != parameters.width) return@launch
            paintedScheme = paint.second
            image.setImageBitmap(paint.first)
            val description = when (phase) {
                BubblePhase.IDLE -> nativeUi("Open mini halo. Hold to hide.")
                BubblePhase.LISTENING -> getString(R.string.bubble_listening)
                BubblePhase.WORKING -> getString(R.string.bubble_working)
                BubblePhase.NEEDS_YOU -> getString(R.string.bubble_needs_you)
            }
            if (image.contentDescription != description) image.contentDescription = description
            stopBubbleMotion()
            val budget = getSystemService(PowerManager::class.java)
            val motion = getSharedPreferences("halo_visual_options", MODE_PRIVATE)
            val fullMotion = motion.getString("motionLevel", null) == "FULL"
            if (image.visibility == View.VISIBLE && android.animation.ValueAnimator.areAnimatorsEnabled() &&
                !budget.isPowerSaveMode && !motion.getBoolean("motion", false) &&
                phase != BubblePhase.NEEDS_YOU && (phase != BubblePhase.IDLE || fullMotion)) {
                bubbleMotion = if (phase == BubblePhase.WORKING && fullMotion) {
                    android.animation.ObjectAnimator.ofFloat(image, View.ROTATION, 0f, 360f).apply {
                        duration = 1700
                        interpolator = android.view.animation.LinearInterpolator()
                    }
                } else {
                    android.animation.ObjectAnimator.ofFloat(image, View.ALPHA, 1f, if (fullMotion) .80f else .91f, 1f).apply {
                        duration = if (phase == BubblePhase.LISTENING) 1100 else 4800
                    }
                }.apply { repeatCount = android.animation.ValueAnimator.INFINITE; start() }
            }
            dismiss?.apply { setTextColor(paint.second.onSurface.toArgb()); setBackgroundColor(paint.second.surfaceContainerHigh.toArgb()) }
        }
    }

    private fun showDismiss() {
        if (dismiss != null) return
        val colors = paintedScheme ?: return
        val view = TextView(this).apply { text = nativeUi("Drop here to hide"); gravity = Gravity.CENTER
            setTextColor(colors.onSurface.toArgb()); setBackgroundColor(colors.surfaceContainerHigh.toArgb()) }
        val params = WindowManager.LayoutParams((160*resources.displayMetrics.density).toInt(), (56*resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = (24*resources.displayMetrics.density).toInt(); alpha = .8f }
        windows.addView(view, params); dismiss = view
    }
    private fun removeDismiss() { dismiss?.let { runCatching { windows.removeView(it) } }; dismiss = null }
    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        placeBubble()
        bubble?.let { windows.updateViewLayout(it, parameters) }; paintOrb()
    }
    override fun onDestroy() {
        running.value = false; removeDismiss(); stopBubbleMotion()
        bubble?.let { runCatching { windows.removeView(it) } }; bubble = null
        appearanceStore.unregisterOnSharedPreferenceChangeListener(appearanceChanged)
        paletteStores.forEach { it.unregisterOnSharedPreferenceChangeListener(changed) }
        if (receiverRegistered) unregisterReceiver(screenOff)
        getSystemService(android.app.AppOpsManager::class.java).stopWatchingMode(overlayAccess)
        scope.cancel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    companion object {
        const val HIDE = "com.halo.app.HIDE_BUBBLE"
        private const val CHANNEL = "halo_floating"
        val running = MutableStateFlow(false)
        val failure = MutableStateFlow<String?>(null)
    }
}

internal object BubblePosition {
    fun snap(x: Int, y: Int, width: Int, height: Int, size: Int): Pair<Int, Int> =
        (if (x+size/2 < width/2) 0 else (width-size).coerceAtLeast(0)) to
            y.coerceIn(0, (height-size).coerceAtLeast(0))
}

private enum class BubblePhase { IDLE, LISTENING, WORKING, NEEDS_YOU }
