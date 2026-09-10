package com.telemetryoverlay.app

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import java.util.UUID

/**
 * Connects to a standard BLE Heart Rate Service (0x180D) peripheral —
 * any chest strap, arm band, or watch that broadcasts the standard
 * Heart Rate Measurement characteristic (0x2A37) — and streams live BPM.
 */
class HeartRateManager(private val context: Context) {

    companion object {
        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_CHAR: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCC_DESCRIPTOR: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var gatt: BluetoothGatt? = null
    var onHeartRate: ((Int) -> Unit)? = null

    fun connect(deviceAddress: String) {
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter: BluetoothAdapter = btManager.adapter ?: return
            val device: BluetoothDevice = adapter.getRemoteDevice(deviceAddress)
            gatt = device.connectGatt(context, true, gattCallback)
        } catch (e: SecurityException) {
            // Bluetooth permission not granted; caller should re-prompt.
        } catch (e: IllegalArgumentException) {
            // Invalid/unsaved MAC address — no device paired yet.
        }
    }

    fun disconnect() {
        try {
            gatt?.disconnect()
            gatt?.close()
        } catch (e: SecurityException) {
            // Ignore — permission may have been revoked while tearing down.
        }
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                try {
                    g.discoverServices()
                } catch (e: SecurityException) { }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT_CHAR) ?: return
            try {
                g.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR)
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    g.writeDescriptor(descriptor)
                }
            } catch (e: SecurityException) { }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HR_MEASUREMENT_CHAR) {
                val bpm = parseHeartRate(characteristic)
                onHeartRate?.invoke(bpm)
            }
        }
    }

    /** Per Bluetooth GATT spec: flags byte bit0 selects uint8 vs uint16 BPM value. */
    private fun parseHeartRate(characteristic: BluetoothGattCharacteristic): Int {
        val flags = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
        val hrFormat = if (flags and 0x01 != 0) {
            BluetoothGattCharacteristic.FORMAT_UINT16
        } else {
            BluetoothGattCharacteristic.FORMAT_UINT8
        }
        return characteristic.getIntValue(hrFormat, 1)
    }
}
