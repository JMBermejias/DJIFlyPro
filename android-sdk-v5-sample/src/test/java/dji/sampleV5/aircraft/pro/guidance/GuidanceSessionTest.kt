package dji.sampleV5.aircraft.pro.guidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceSessionTest {

    private fun target(index: Int, lat: Double, lon: Double, height: Double = 30.0) =
        GuidanceTarget(index, lat, lon, height)

    private val line = listOf(
        target(0, 40.4168, -3.7038),
        target(1, 40.4169, -3.7038),
        target(2, 40.4170, -3.7038)
    )

    @Test
    fun startsOnTheFirstTarget() {
        val session = GuidanceSession(line)

        assertEquals(1, session.position)
        assertEquals(0, session.index)
        assertEquals(3, session.total)
        assertFalse(session.isFinished)
        assertEquals(0.0, session.progressFraction, 0.001)
    }

    @Test
    fun positionIsOneBasedSoTheOperatorNeverSeesTargetZero() {
        val session = GuidanceSession(line)

        session.skip()

        assertEquals(2, session.position)
        assertEquals(1, session.index)
    }

    @Test
    fun anEmptyPlanIsFinishedRatherThanBroken() {
        val session = GuidanceSession(emptyList())

        assertTrue(session.isEmpty)
        assertTrue(session.isFinished)
        assertNull(session.current)
        assertNull(session.leg(GuidancePosition(40.0, -3.0), 30.0, 0.0))
        assertFalse(session.skip())
        assertFalse(session.markReached())
        assertFalse(session.back())
    }

    @Test
    fun unusableTargetsAreDroppedAndCounted() {
        val session = GuidanceSession(
            listOf(target(0, 40.0, -3.0), target(1, 0.0, 0.0), target(2, Double.NaN, -3.0))
        )

        assertEquals(1, session.total)
        assertEquals(2, session.skippedCount)
    }

    @Test
    fun advanceRequiresActuallyArriving() {
        val session = GuidanceSession(line)
        val far = GuidancePosition(40.5000, -3.7038)

        assertFalse(session.advanceIfReached(far, 30.0, 0.0))
        assertEquals(0, session.index)
    }

    @Test
    fun advanceMovesOnWhenTheOperatorArrives() {
        val session = GuidanceSession(line)
        val atFirst = GuidancePosition(40.4168, -3.7038)

        assertTrue(session.advanceIfReached(atFirst, 30.0, 0.0))
        assertEquals(1, session.index)
    }

    @Test
    fun advanceNeedsBothPositionAndAltitude() {
        val session = GuidanceSession(line)
        val atFirst = GuidancePosition(40.4168, -3.7038)

        assertFalse("no telemetry position", session.advanceIfReached(null, 30.0, 0.0))
        assertFalse("altitude unknown", session.advanceIfReached(atFirst, null, 0.0))
        assertEquals(0, session.index)
    }

    /**
     * Auto-advance runs on every telemetry frame, so it must fire once per
     * arrival, not keep ticking while the aircraft sits on the point.
     */
    @Test
    fun autoAdvanceDoesNotRunAwayOnASettledAircraft() {
        val session = GuidanceSession(line)
        val atFirst = GuidancePosition(40.4168, -3.7038)

        assertTrue(session.advanceIfReached(atFirst, 30.0, 0.0))
        assertFalse("still at target 0's position, target 1 is 11 m away",
            session.advanceIfReached(atFirst, 30.0, 0.0))
        assertEquals(1, session.index)
    }

    @Test
    fun autoAdvanceStopsAtTheEndOfThePlan() {
        val session = GuidanceSession(listOf(target(0, 40.4168, -3.7038)))
        val there = GuidancePosition(40.4168, -3.7038)

        assertTrue(session.advanceIfReached(there, 30.0, 0.0))
        assertTrue(session.isFinished)
        assertFalse(session.advanceIfReached(there, 30.0, 0.0))
        assertFalse(session.skip())
        assertFalse(session.markReached())
    }

    @Test
    fun theOperatorCanConfirmArrivalWhenTheGateIsTooTight() {
        val session = GuidanceSession(line)
        val far = GuidancePosition(40.5000, -3.7038)

        assertFalse(session.advanceIfReached(far, 30.0, 0.0))
        assertTrue(session.markReached())
        assertEquals(1, session.index)
    }

    @Test
    fun backNeverRunsBeforeTheFirstTarget() {
        val session = GuidanceSession(line)

        assertFalse(session.back())
        assertEquals(0, session.index)

        session.skip()
        assertTrue(session.back())
        assertEquals(0, session.index)
    }

    @Test
    fun restartReturnsToTheFirstTarget() {
        val session = GuidanceSession(line)
        session.skip()
        session.skip()

        session.restart()

        assertEquals(0, session.index)
        assertEquals(0.0, session.progressFraction, 0.001)
    }

    @Test
    fun progressIsMonotonicAndBounded() {
        val session = GuidanceSession(line)

        session.skip()
        val half = session.progressFraction
        session.skip()
        val done = session.progressFraction

        assertTrue(half in 0.0..1.0)
        assertTrue(done in 0.0..1.0)
        assertTrue("$half -> $done", done > half)
    }

    @Test
    fun aWiderCaptureGateLetsTheOperatorSkipTheAltitudeMatch() {
        val strict = GuidanceSession(line, captureAltitudeMeters = 1.0)
        val lenient = GuidanceSession(line, captureAltitudeMeters = 10.0)
        val there = GuidancePosition(40.4168, -3.7038)

        assertFalse(strict.advanceIfReached(there, 35.0, 0.0))
        assertTrue(lenient.advanceIfReached(there, 35.0, 0.0))
    }

    @Test
    fun theLegDescribesTheCurrentTargetOnly() {
        val session = GuidanceSession(line)
        session.skip()

        val leg = session.leg(GuidancePosition(40.4168, -3.7038), 30.0, 0.0)

        assertEquals(1, leg?.target?.index)
    }
}
