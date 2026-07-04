package com.dogtag.ble

import kotlin.math.ln
import kotlin.math.pow

/**
 * Log-distance path loss model: RSSI(d) = rssiAt1m - 10 * n * log10(d)
 * => d = 10 ^ ((rssiAt1m - RSSI) / (10 * n))
 *
 * Both parameters are environment-dependent (terrain, obstacles, tag
 * orientation/antenna, even weather) and should come from on-site calibration
 * (see [PathLossCalibrator]) rather than assumed defaults. The fallback values
 * below are rough literature defaults for a BLE beacon in open outdoor space,
 * useful only until you calibrate.
 */
object RangeEstimator {
    const val DEFAULT_RSSI_AT_1M = -59.0
    const val DEFAULT_PATH_LOSS_EXPONENT = 2.2

    fun estimateDistanceM(
        rssi: Int,
        rssiAt1m: Double = DEFAULT_RSSI_AT_1M,
        pathLossExponent: Double = DEFAULT_PATH_LOSS_EXPONENT,
    ): Double {
        val exponent = (rssiAt1m - rssi) / (10.0 * pathLossExponent)
        return 10.0.pow(exponent)
    }
}

/**
 * Fits rssiAt1m and pathLossExponent from calibration samples of (distanceM, rssi)
 * pairs, via simple linear regression on RSSI = rssiAt1m - 10*n*log10(d).
 *
 * Let x = log10(d), y = rssi. Then y = rssiAt1m - 10*n*x, a linear fit of y on x
 * with slope = -10*n and intercept = rssiAt1m.
 */
object PathLossCalibrator {
    data class CalibrationSample(val distanceM: Double, val rssi: Int)

    data class CalibrationResult(val rssiAt1m: Double, val pathLossExponent: Double)

    fun fit(samples: List<CalibrationSample>): CalibrationResult? {
        val points = samples.filter { it.distanceM > 0 }
        if (points.size < 2) return null

        val xs = points.map { ln(it.distanceM) / ln(10.0) }
        val ys = points.map { it.rssi.toDouble() }
        val n = points.size
        val xMean = xs.average()
        val yMean = ys.average()

        var numerator = 0.0
        var denominator = 0.0
        for (i in 0 until n) {
            numerator += (xs[i] - xMean) * (ys[i] - yMean)
            denominator += (xs[i] - xMean).pow(2)
        }
        if (denominator == 0.0) return null

        val slope = numerator / denominator
        val intercept = yMean - slope * xMean
        val pathLossExponent = -slope / 10.0
        if (pathLossExponent <= 0) return null

        return CalibrationResult(rssiAt1m = intercept, pathLossExponent = pathLossExponent)
    }
}
