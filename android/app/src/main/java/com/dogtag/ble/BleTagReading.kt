package com.dogtag.ble

data class BleTagReading(
    val macAddress: String,
    val rssi: Int,
    val distanceM: Double,
    val timestampMs: Long,
)
