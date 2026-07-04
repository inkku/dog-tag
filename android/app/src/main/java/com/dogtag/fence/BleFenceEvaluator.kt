package com.dogtag.fence

import com.dogtag.data.model.Fence

enum class FenceStatus { INSIDE, APPROACHING_EDGE, UNCERTAIN, OUTSIDE }

data class FenceCheckResult(val fenceId: Int?, val status: FenceStatus)

/**
 * BLE RSSI gives a distance estimate from the phone to the tag, but no bearing.
 * So "is the dog inside this fence" can only be bounded, not known exactly,
 * unless the phone happens to be sitting at the fence's center: the dog could
 * be anywhere on a circle of radius `tagDistanceM` around the phone.
 *
 * We use interval arithmetic: given the phone's own distance to the fence
 * center, the dog's distance to that same center is between
 * |phoneToCenter - tagDistance| and (phoneToCenter + tagDistance). That lets us
 * say INSIDE / OUTSIDE only when we're sure, and UNCERTAIN otherwise rather
 * than guessing. In practice this is most useful (and most certain) when the
 * phone/gateway is left near the fence's center - e.g. mounted at the yard
 * boundary's midpoint, or you set the fence's center to your own position
 * before walking away.
 */
object BleFenceEvaluator {
    fun evaluate(phoneLat: Double, phoneLon: Double, tagDistanceM: Double, fence: Fence): FenceCheckResult {
        val phoneToCenter = haversineDistanceM(phoneLat, phoneLon, fence.centerLat, fence.centerLon)
        val worstCaseFromCenter = phoneToCenter + tagDistanceM
        val bestCaseFromCenter = kotlin.math.abs(phoneToCenter - tagDistanceM)

        val status = when {
            worstCaseFromCenter <= fence.radiusM - fence.warnMarginM -> FenceStatus.INSIDE
            worstCaseFromCenter <= fence.radiusM -> FenceStatus.APPROACHING_EDGE
            bestCaseFromCenter > fence.radiusM -> FenceStatus.OUTSIDE
            else -> FenceStatus.UNCERTAIN
        }
        return FenceCheckResult(fence.id, status)
    }
}
