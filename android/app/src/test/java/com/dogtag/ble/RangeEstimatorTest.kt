package com.dogtag.ble

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.log10

class RangeEstimatorTest {
    @Test
    fun `distance round-trips through the path loss model`() {
        val rssiAt1m = -60.0
        val n = 2.0
        for (trueDistance in listOf(1.0, 3.0, 5.0, 10.0, 20.0)) {
            val rssi = (rssiAt1m - 10 * n * log10(trueDistance)).toInt()
            val estimated = RangeEstimator.estimateDistanceM(rssi, rssiAt1m, n)
            assertEquals(trueDistance, estimated, 0.5)
        }
    }

    @Test
    fun `calibration fit recovers known parameters`() {
        val trueRssiAt1m = -58.0
        val trueN = 2.3
        val samples = listOf(1.0, 2.0, 5.0, 10.0, 15.0).map { d ->
            PathLossCalibrator.CalibrationSample(d, (trueRssiAt1m - 10 * trueN * log10(d)).toInt())
        }
        val fit = PathLossCalibrator.fit(samples)!!
        assertEquals(trueRssiAt1m, fit.rssiAt1m, 0.5)
        assertEquals(trueN, fit.pathLossExponent, 0.05)
    }

    @Test
    fun `fit refuses fewer than two samples`() {
        assertEquals(null, PathLossCalibrator.fit(listOf(PathLossCalibrator.CalibrationSample(1.0, -60))))
    }
}
