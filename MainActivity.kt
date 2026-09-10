package com.telemetryoverlay.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val denied = result.filterValues { !it }.keys
            if (denied.isEmpty()) {
                tvStatus.text = "Permissions granted. Pair a heart-rate monitor, then start the overlay."
            } else {
                tvStatus.text = "Missing: ${denied.joinToString()}\nSome stats may not work without them."
            }
        }

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("telemetry_overlay", MODE_PRIVATE)
        tvStatus = findViewById(R.id.tvStatus)

        findViewById<Button>(R.id.btnGrantPermissions).setOnClickListener { requestAllPermissions() }
        findViewById<Button>(R.id.btnPairHr).setOnClickListener { HrPairingDialog(this).show() }
        findViewById<Button>(R.id.btnStartOverlay).setOnClickListener { startOverlay() }
        findViewById<Button>(R.id.btnStopOverlay).setOnClickListener { stopOverlay() }

        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        tvStatus.text = if (!Settings.canDrawOverlays(this)) {
            "Overlay permission not yet granted."
        } else if (!hasRuntimePermissions()) {
            "Location / Bluetooth permissions not yet granted."
        } else {
            "Ready. Tap Start Overlay before your run."
        }
    }

    private fun requestAllPermissions() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }

        val runtimePermissions = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACTIVITY_RECOGNITION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runtimePermissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            runtimePermissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimePermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(runtimePermissions.toTypedArray())
    }

    private fun hasRuntimePermissions(): Boolean {
        val required = mutableListOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            required.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        return required.all {
            ContextCompat.checkSelfPermission(this, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant the overlay permission first.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasRuntimePermissions()) {
            Toast.makeText(this, "Grant location/Bluetooth permissions first.", Toast.LENGTH_SHORT).show()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Overlay started", Toast.LENGTH_SHORT).show()
    }

    private fun stopOverlay() {
        stopService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show()
    }
}
