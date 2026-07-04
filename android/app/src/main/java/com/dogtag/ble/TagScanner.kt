package com.dogtag.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Nearby BLE advertisement, before we know which (if any) owned tag it is.
 * Used by the pairing/discovery screen.
 */
data class NearbyDevice(
    val macAddress: String,
    val name: String?,
    val rssi: Int,
)

/**
 * Direct BLE scanning of the 4 tags for near-field proximity.
 *
 * Caveat: FMDN-network tags (like OTAG) rotate their advertised BLE address
 * periodically as an anti-stalking privacy measure, similar to Apple's Find My
 * network. A plain MAC allow-list (what this class does) will silently stop
 * matching a tag after it rotates. Workarounds, in increasing effort:
 *   1. Re-run the discovery scan ([scanNearby]) periodically to notice when a
 *      tag's address has changed and re-pair by proximity/RSSI.
 *   2. Connect via GATT once a candidate is found and read a stable
 *      manufacturer characteristic (serial number etc.) to confirm identity
 *      regardless of the advertised address - requires knowing OTAG's GATT
 *      layout, which isn't publicly documented; sniff it with nRF Connect
 *      against the real hardware and fill in [OtagGatt].
 * This scaffold ships the simple MAC-based approach so the app is usable
 * immediately; treat address rotation as a known follow-up.
 */
class TagScanner(context: Context) {
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    @SuppressLint("MissingPermission")
    fun scanNearby(): Flow<NearbyDevice> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) {
            close(IllegalStateException("Bluetooth LE scanner unavailable (Bluetooth off or unsupported)"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(
                    NearbyDevice(
                        macAddress = result.device.address,
                        name = result.scanRecord?.deviceName,
                        rssi = result.rssi,
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed, error code $errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(null, settings, callback)

        awaitClose { scanner.stopScan(callback) }
    }

    @SuppressLint("MissingPermission")
    fun scanKnown(macAddresses: Set<String>): Flow<BleTagReading> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null || macAddresses.isEmpty()) {
            close(IllegalStateException("Bluetooth LE scanner unavailable or no known tags configured"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                if (address !in macAddresses) return
                trySend(
                    BleTagReading(
                        macAddress = address,
                        rssi = result.rssi,
                        distanceM = RangeEstimator.estimateDistanceM(result.rssi),
                        timestampMs = System.currentTimeMillis(),
                    )
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed, error code $errorCode"))
            }
        }

        val filters = macAddresses.map { ScanFilter.Builder().setDeviceAddress(it).build() }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        scanner.startScan(filters, settings, callback)

        awaitClose { scanner.stopScan(callback) }
    }
}
