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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect

/** User-started launcher only: no microphone, content capture, boot restart or tool execution. */
class FloatingHaloService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windows: WindowManager
    private var bubble: ImageView? = null
    private var dismiss: TextView? = null
    private val parameters = WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT }
    private val preferences by lazy { getSharedPreferences("halo_bubble", MODE_PRIVATE) }
    private val paletteStores by lazy { listOf(getSharedPreferences("halo_palettes", MODE_PRIVATE), getSharedPreferences("halo_appearance", MODE_PRIVATE)) }
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
        paletteStores.forEach { it.registerOnSharedPreferenceChangeListener(changed) }
        ContextCompat.registerReceiver(this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
        getSystemService(android.app.AppOpsManager::class.java).startWatchingMode(android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageName, overlayAccess)
        scope.launch { HaloHostVisibility.active.collect { active -> bubble?.visibility = if (active) View.GONE else View.VISIBLE } }
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == HIDE || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        try {
            foreground()
            if (bubble == null) showBubble()
        } catch (_: Exception) { failure.value = "Could not show floating halo. Try again."; stopSelf() }
        return START_NOT_STICKY
    }
    private fun foreground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, nativeUi("Floating halo"), NotificationManager.IMPORTANCE_LOW))
        val hide = PendingIntent.getService(this, 8101, Intent(this, FloatingHaloService::class.java).setAction(HIDE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_halo_talk)
            .setContentTitle(nativeUi("Floating halo")).setContentText(nativeUi("Tap the orb to open Mini halo."))
            .setContentIntent(MiniHaloEntry.pending(this, 8100)).setOngoing(true)
            .addAction(Notification.Action.Builder(null, nativeUi("Hide"), hide).build()).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(8100, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(8100, notification)
    }
    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= 30) windows.currentWindowMetrics.bounds.let { it.width() to it.height() }
        else resources.displayMetrics.let { it.widthPixels to it.heightPixels }
    private fun showBubble() {
        val size = (56 * resources.displayMetrics.density).toInt()
        parameters.width = size; parameters.height = size
        val (width, height) = screenSize()
        parameters.x = (preferences.getFloat("x", 1f).coerceIn(0f,1f) * (width-size).coerceAtLeast(0)).toInt()
        parameters.y = (preferences.getFloat("y", .3f).coerceIn(0f,1f) * (height-size).coerceAtLeast(0)).toInt()
        val image = ImageView(this).apply {
            visibility = if (HaloHostVisibility.active.value) View.GONE else View.VISIBLE
            contentDescription = nativeUi("Open Mini halo. Hold to hide.")
            setOnClickListener { runCatching { startActivity(MiniHaloEntry.intent(this@FloatingHaloService)) }
                .onFailure { failure.value = "Could not open Mini halo. Open halo from your launcher." } }
            setOnLongClickListener { stopSelf(); true }
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
                        if (event.actionMasked == MotionEvent.ACTION_UP && event.rawY > bounds.second-120*resources.displayMetrics.density &&
                            kotlin.math.abs(event.rawX-bounds.first/2f) < 100*resources.displayMetrics.density) stopSelf()
                        else {
                            val position = BubblePosition.snap(parameters.x, parameters.y, bounds.first, bounds.second, size)
                            parameters.x = position.first; parameters.y = position.second
                            windows.updateViewLayout(image, parameters)
                            scope.launch(Dispatchers.IO) { preferences.edit().putFloat("x", if (position.first == 0) 0f else 1f)
                                .putFloat("y", position.second.toFloat()/(bounds.second-size).coerceAtLeast(1)).commit() }
                        }
                    }
                    moved
                }
                else -> false
            }
        }
        windows.addView(image, parameters); bubble = image; running.value = true; failure.value = null
        paintOrb()
    }
    private fun paintOrb() { scope.launch {
        val bitmap = withContext(Dispatchers.IO) { nearbyOrb(this@FloatingHaloService, (56*resources.displayMetrics.density).toInt(), floating = true) }
        bubble?.setImageBitmap(bitmap)
    } }
    private fun showDismiss() {
        if (dismiss != null) return
        val view = TextView(this).apply { text = nativeUi("Drop here to hide"); gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE); setBackgroundColor(0xCC20242A.toInt()) }
        val params = WindowManager.LayoutParams((160*resources.displayMetrics.density).toInt(), (56*resources.displayMetrics.density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = (24*resources.displayMetrics.density).toInt(); alpha = .8f }
        windows.addView(view, params); dismiss = view
    }
    private fun removeDismiss() { dismiss?.let { runCatching { windows.removeView(it) } }; dismiss = null }
    override fun onConfigurationChanged(configuration: Configuration) {
        super.onConfigurationChanged(configuration)
        val bounds = screenSize()
        val snapped = BubblePosition.snap(parameters.x, parameters.y, bounds.first, bounds.second, parameters.width)
        parameters.x = snapped.first; parameters.y = snapped.second
        bubble?.let { windows.updateViewLayout(it, parameters) }; paintOrb()
    }
    override fun onDestroy() {
        running.value = false; removeDismiss()
        bubble?.let { runCatching { windows.removeView(it) } }; bubble = null
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
