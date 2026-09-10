package com.telemetryoverlay.app

import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.widget.ArrayAdapter
import android.widget.Toast
import java.util.UUID

/**
 * Scans for nearby BLE devices advertising the standard Heart Rate Service
 * (0x180D) and lets the user pick one. The chosen MAC address is stored in
 * SharedPreferences and read back by HeartRateManager when the overlay starts.
 */
class HrPairingDialog(private val context: Context) {

    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val foundDevices = LinkedHashMap<String, BluetoothDevice>()
    private lateinit var adapter: ArrayAdapter<String>
    private var scanning = false

    fun show() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bleScanner = bluetoothManager.adapter?.bluetoothLeScanner
        if (bluetoothManager.adapter == null || !bluetoothManager.adapter.isEnabled) {
            Toast.makeText(context, "Turn on Bluetooth first.", Toast.LENGTH_SHORT).show()
            return
        }

        adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, mutableListOf())
        val dialog = AlertDialog.Builder(context)
            .setTitle("Scanning for HR monitor…")
            .setAdapter(adapter) { _, which ->
                val name = adapter.getItem(which) ?: return@setAdapter
                val device = foundDevices[name] ?: return@setAdapter
                context.getSharedPreferences("telemetry_overlay", Context.MODE_PRIVATE)
                    .edit()
                    .putString("hr_device_address", device.address)
                    .apply()
                Toast.makeText(context, "Paired: $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener { stopScan(bleScanner) }
            .create()
        dialog.show()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanning = true
            bleScanner?.startScan(listOf(filter), settings, scanCallback)
        } catch (e: SecurityException) {
            Toast.makeText(context, "Bluetooth permission missing.", Toast.LENGTH_SHORT).show()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val label = try {
                (device.name ?: "Unknown device") + " (${device.address})"
            } catch (e: SecurityException) {
                device.address
            }
            if (!foundDevices.containsKey(label)) {
                foundDevices[label] = device
                adapter.add(label)
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun stopScan(bleScanner: android.bluetooth.le.BluetoothLeScanner?) {
        if (scanning) {
            try {
                bleScanner?.stopScan(scanCallback)
            } catch (e: SecurityException) {
                // Permission revoked mid-scan; nothing to clean up.
            }
            scanning = false
        }
    }
}
