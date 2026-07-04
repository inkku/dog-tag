package com.dogtag.fence

import com.dogtag.data.model.Fence
import org.junit.Assert.assertEquals
import org.junit.Test

class BleFenceEvaluatorTest {
    private val fence = Fence(id = 1, name = "yard", centerLat = 52.0, centerLon = 4.0, radiusM = 50.0, warnMarginM = 10.0)

    @Test
    fun `phone at fence center is fully determinate`() {
        assertEquals(FenceStatus.INSIDE, BleFenceEvaluator.evaluate(52.0, 4.0, 10.0, fence).status)
        assertEquals(FenceStatus.APPROACHING_EDGE, BleFenceEvaluator.evaluate(52.0, 4.0, 45.0, fence).status)
        assertEquals(FenceStatus.OUTSIDE, BleFenceEvaluator.evaluate(52.0, 4.0, 60.0, fence).status)
    }

    @Test
    fun `phone far from center with a nearby tag is still definitely outside`() {
        // Phone ~111m from center, tag only 30m from phone: even in the best
        // case (tag on the side closest to center) it's 81m out, past the fence.
        val result = BleFenceEvaluator.evaluate(52.001, 4.0, 30.0, fence)
        assertEquals(FenceStatus.OUTSIDE, result.status)
    }

    @Test
    fun `ambiguous bearing yields uncertain rather than a guess`() {
        // Phone ~55m from center, tag 20m from phone: could be anywhere from
        // 35m to 75m from center - straddles the fence radius, so we must not
        // claim to know either way.
        val fenceAt55 = fence.copy(radiusM = 55.0, warnMarginM = 5.0)
        val result = BleFenceEvaluator.evaluate(52.000494, 4.0, 20.0, fenceAt55) // ~55m north of center
        assertEquals(FenceStatus.UNCERTAIN, result.status)
    }
}
