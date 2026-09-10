package com.telemetryoverlay.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        private const val CHANNEL_ID = "telemetry_overlay_channel"
        private const val NOTIFICATION_ID = 1
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var layoutParams: WindowManager.LayoutParams

    private lateinit var locationTracker: LocationTracker
    private lateinit var heartRateManager: HeartRateManager
    private lateinit var cadenceTracker: CadenceTracker

    private var activityStartElapsedMs = 0L
    private var latestDistanceM = 0.0
    private var latestPaceSecPerKm = 0.0
    private var latestElevationM = 0.0
    private var latestHeartRate = 0
    private var latestCadence = 0

    private val tickHandler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())

    private val tickRunnable = object : Runnable {
        override fun run() {
            refreshClockAndDuration()
            tickHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlayView()

        activityStartElapsedMs = SystemClock.elapsedRealtime()

        locationTracker = LocationTracker(this).apply {
            onUpdate = { stats ->
                latestDistanceM = stats.distanceMeters
                latestPaceSecPerKm = stats.paceSecPerKm
                latestElevationM = stats.elevationMeters
                refreshDistancePaceElevation()
            }
            start()
        }

        cadenceTracker = CadenceTracker(this).apply {
            onCadenceUpdate = { spm ->
                latestCadence = spm
                overlayView.findViewById<TextView>(R.id.tvCadence).text = "$spm spm"
            }
            start()
        }

        heartRateManager = HeartRateManager(this).apply {
            onHeartRate = { bpm ->
                latestHeartRate = bpm
                overlayView.findViewById<TextView>(R.id.tvHeartRate).text = "$bpm bpm"
            }
        }
        getSharedPreferences("telemetry_overlay", MODE_PRIVATE)
            .getString("hr_device_address", null)
            ?.let { heartRateManager.connect(it) }

        tickHandler.post(tickRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        tickHandler.removeCallbacks(tickRunnable)
        if (::locationTracker.isInitialized) locationTracker.stop()
        if (::cadenceTracker.isInitialized) cadenceTracker.stop()
        if (::heartRateManager.isInitialized) heartRateManager.disconnect()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (e: IllegalArgumentException) {
                // View was already detached.
            }
        }
    }

    // ---- Overlay window setup & drag handling ----------------------------------

    private fun addOverlayView() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_stats, null)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 120
        }

        windowManager.addView(overlayView, layoutParams)

        overlayView.findViewById<View>(R.id.btnCloseOverlay).setOnClickListener {
            stopSelf()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = initialX + (event.rawX - initialTouchX).roundToInt()
                    layoutParams.y = initialY + (event.rawY - initialTouchY).roundToInt()
                    windowManager.updateViewLayout(overlayView, layoutParams)
                    true
                }
                else -> false
            }
        }
    }

    // ---- Per-second display refresh ---------------------------------------------

    private fun refreshClockAndDuration() {
        val now = Date()
        overlayView.findViewById<TextView>(R.id.tvTimeOfDay).text = timeFormat.format(now)
        overlayView.findViewById<TextView>(R.id.tvDate).text = dateFormat.format(now)

        val elapsedMs = SystemClock.elapsedRealtime() - activityStartElapsedMs
        overlayView.findViewById<TextView>(R.id.tvActivityTime).text = formatDuration(elapsedMs)
    }

    private fun refreshDistancePaceElevation() {
        val km = latestDistanceM / 1000.0
        overlayView.findViewById<TextView>(R.id.tvDistance).text =
            String.format(Locale.getDefault(), "%.2f km", km)

        overlayView.findViewById<TextView>(R.id.tvPace).text =
            if (latestPaceSecPerKm > 0) formatPace(latestPaceSecPerKm) else "--:-- /km"

        overlayView.findViewById<TextView>(R.id.tvElevation).text =
            String.format(Locale.getDefault(), "%.0f m", latestElevationM)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    private fun formatPace(secPerKm: Double): String {
        val totalSeconds = secPerKm.roundToInt()
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.getDefault(), "%d:%02d /km", m, s)
    }

    // ---- Foreground service notification -----------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Telemetry Overlay",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the running stats overlay active" }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val tapIntent = Intent(this, MainActivity::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else 0
        val pendingIntent = PendingIntent.getActivity(this, 0, tapIntent, flags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telemetry Overlay running")
            .setContentText("Tracking your run in the background")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
