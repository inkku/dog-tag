package com.dogtag.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Dog(
    val id: Int? = null,
    val name: String,
    val color: String = "#4285F4",
)

@Serializable
enum class TagType {
    @SerialName("ble") BLE,
    @SerialName("fmdn") FMDN,
    @SerialName("tractive") TRACTIVE,
}

@Serializable
data class Tag(
    val id: Int? = null,
    @SerialName("dog_id") val dogId: Int? = null,
    val name: String,
    val type: TagType,
    @SerialName("mac_address") val macAddress: String? = null,
    @SerialName("rssi_at_1m") val rssiAt1m: Double? = null,
    @SerialName("path_loss_exponent") val pathLossExponent: Double? = null,
    @SerialName("fmdn_device_id") val fmdnDeviceId: String? = null,
    @SerialName("tractive_tracker_id") val tractiveTrackerId: String? = null,
)

@Serializable
data class Fence(
    val id: Int? = null,
    @SerialName("dog_id") val dogId: Int? = null,
    val name: String,
    @SerialName("center_lat") val centerLat: Double,
    @SerialName("center_lon") val centerLon: Double,
    @SerialName("radius_m") val radiusM: Double,
    @SerialName("warn_margin_m") val warnMarginM: Double = 10.0,
)

@Serializable
enum class AlertTrigger {
    @SerialName("exit") EXIT,
    @SerialName("approach") APPROACH,
}

@Serializable
enum class AlertAction {
    @SerialName("notify_owner") NOTIFY_OWNER,
    @SerialName("ring_tag") RING_TAG,
}

@Serializable
data class AlertRule(
    val id: Int? = null,
    @SerialName("fence_id") val fenceId: Int,
    val trigger: AlertTrigger,
    val action: AlertAction,
    val enabled: Boolean = true,
)

@Serializable
enum class LocationSource {
    @SerialName("ble_proximity") BLE_PROXIMITY,
    @SerialName("fmdn") FMDN,
    @SerialName("tractive") TRACTIVE,
}

@Serializable
data class LocationSample(
    val id: Int? = null,
    @SerialName("tag_id") val tagId: Int,
    val lat: Double,
    val lon: Double,
    @SerialName("accuracy_m") val accuracyM: Double? = null,
    val source: LocationSource,
)
