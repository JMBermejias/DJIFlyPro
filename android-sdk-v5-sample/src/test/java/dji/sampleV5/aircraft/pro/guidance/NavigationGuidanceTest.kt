package dji.sampleV5.aircraft.pro.guidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class NavigationGuidanceTest {

    private val madrid = GuidancePosition(40.4168, -3.7038)
    private val barcelona = GuidancePosition(41.3851, 2.1734)

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.5) {
        assertTrue("expected $expected but was $actual", abs(expected - actual) <= tolerance)
    }

    @Test
    fun distanceToItselfIsZero() {
        assertEquals(0.0, NavigationGuidance.distanceMeters(madrid, madrid), 0.001)
    }

    @Test
    fun distanceMatchesAKnownCityPair() {
        // Madrid to Barcelona is about 505 km great-circle.
        assertClose(505_000.0, NavigationGuidance.distanceMeters(madrid, barcelona), 3_000.0)
    }

    @Test
    fun distanceIsSymmetric() {
        assertClose(
            NavigationGuidance.distanceMeters(madrid, barcelona),
            NavigationGuidance.distanceMeters(barcelona, madrid),
            0.001
        )
    }

    @Test
    fun distanceAcrossTheAntimeridianDoesNotWrapTheWrongWay() {
        val justWest = GuidancePosition(0.0, 179.9)
        val justEast = GuidancePosition(0.0, -179.9)

        // 0.2 degrees of longitude at the equator is about 22 km, not most of
        // the way round the planet.
        assertClose(22_239.0, NavigationGuidance.distanceMeters(justWest, justEast), 200.0)
    }

    @Test
    fun distanceHandlesThePoles() {
        val north = GuidancePosition(90.0, 0.0)
        val south = GuidancePosition(-90.0, 0.0)

        assertClose(20_015_114.0, NavigationGuidance.distanceMeters(north, south), 1_000.0)
    }

    @Test
    fun bearingDueNorthIsZero() {
        val target = GuidancePosition(madrid.latitude + 0.1, madrid.longitude)
        assertClose(0.0, NavigationGuidance.bearingDegrees(madrid, target), 0.5)
    }

    @Test
    fun bearingDueEastIsNinety() {
        val target = GuidancePosition(madrid.latitude, madrid.longitude + 0.1)
        assertClose(90.0, NavigationGuidance.bearingDegrees(madrid, target), 0.5)
    }

    @Test
    fun bearingAndDistanceAgreeOnBarcelona() {
        // Barcelona sits east-north-east of Madrid: about 505 km at 75.8 degrees.
        assertClose(75.8, NavigationGuidance.bearingDegrees(madrid, barcelona), 1.0)
    }

    @Test
    fun bearingInTheSouthernHemisphereIsStillClockwiseFromNorth() {
        val origin = GuidancePosition(-33.8688, 151.2093) // Sydney
        val dueNorth = GuidancePosition(-33.7, 151.2093)

        assertClose(0.0, NavigationGuidance.bearingDegrees(origin, dueNorth), 0.5)
    }

    @Test
    fun normalizeFoldsBothDirections() {
        assertEquals(0.0, NavigationGuidance.normalizeDegrees(360.0), 0.001)
        assertEquals(350.0, NavigationGuidance.normalizeDegrees(-10.0), 0.001)
        assertEquals(10.0, NavigationGuidance.normalizeDegrees(370.0), 0.001)
        assertEquals(180.0, NavigationGuidance.normalizeDegrees(-180.0), 0.001)
    }

    /**
     * The sign of the relative bearing decides which way the operator turns the
     * aircraft. A target to the right of the nose must be a positive, right
     * turn, everywhere.
     */
    @Test
    fun aTargetToTheRightIsAPositiveTurn() {
        val target = GuidancePosition(madrid.latitude, madrid.longitude + 0.1) // due east
        val bearing = NavigationGuidance.bearingDegrees(madrid, target)

        // Facing north, the target is 90 degrees to the right.
        assertClose(90.0, NavigationGuidance.relativeBearingDegrees(bearing, 0.0), 0.5)
    }

    @Test
    fun aTargetToTheLeftIsANegativeTurn() {
        val target = GuidancePosition(madrid.latitude, madrid.longitude - 0.1) // due west
        val bearing = NavigationGuidance.bearingDegrees(madrid, target)

        // Facing north, the target is 90 degrees to the left.
        assertClose(-90.0, NavigationGuidance.relativeBearingDegrees(bearing, 0.0), 0.5)
    }

    @Test
    fun relativeBearingStaysSignedAcrossTheWrap() {
        // Bearing 10, heading 350: 20 degrees to the right, not -340.
        assertClose(20.0, NavigationGuidance.relativeBearingDegrees(10.0, 350.0), 0.001)
    }

    @Test
    fun aTargetDirectlyBehindIsAHalfTurn() {
        assertEquals(180.0, NavigationGuidance.relativeBearingDegrees(180.0, 0.0), 0.001)
    }

    @Test
    fun uselessCoordinatesAreRejected() {
        assertFalse(NavigationGuidance.isUsableCoordinate(0.0, 0.0))
        assertFalse(NavigationGuidance.isUsableCoordinate(Double.NaN, 1.0))
        assertFalse(NavigationGuidance.isUsableCoordinate(91.0, 0.0))
        assertFalse(NavigationGuidance.isUsableCoordinate(0.0, 181.0))
        assertTrue(NavigationGuidance.isUsableCoordinate(40.4168, -3.7038))
    }

    /**
     * Without a position there is nothing to guide to. Reporting a distance of
     * NaN keeps the display honest instead of showing 0 m, which would read as
     * "you have arrived".
     */
    @Test
    fun withoutAPositionTheLegIsUnusableRatherThanZeroDistance() {
        val target = GuidanceTarget(0, madrid.latitude, madrid.longitude, 30.0)

        val leg = NavigationGuidance.leg(
            from = null,
            currentAltitudeMeters = 30.0,
            headingDegrees = 0.0,
            target = target
        )

        assertFalse(leg.distanceMeters.isFinite())
        assertFalse(leg.reached)
    }

    @Test
    fun aTargetWithNoUsableCoordinateYieldsAnUnusableLeg() {
        val leg = NavigationGuidance.leg(
            from = madrid,
            currentAltitudeMeters = 30.0,
            headingDegrees = 0.0,
            target = GuidanceTarget(0, 0.0, 0.0, 30.0)
        )

        assertFalse(leg.distanceMeters.isFinite())
        assertFalse(leg.reached)
    }

    @Test
    fun withoutAHeadingTheTurnIsUnknownButTheDistanceIsNot() {
        val leg = NavigationGuidance.leg(
            from = madrid,
            currentAltitudeMeters = 30.0,
            headingDegrees = null,
            target = GuidanceTarget(0, barcelona.latitude, barcelona.longitude, 30.0)
        )

        assertTrue(leg.distanceMeters.isFinite())
        assertFalse("an unknown heading must not be shown as a turn", leg.relativeBearingDegrees.isFinite())
    }

    @Test
    fun altitudeDeltaIsTargetMinusCurrent() {
        val leg = NavigationGuidance.leg(
            from = madrid,
            currentAltitudeMeters = 10.0,
            headingDegrees = 0.0,
            target = GuidanceTarget(0, madrid.latitude, madrid.longitude, 25.0)
        )

        assertEquals(15.0, leg.altitudeDeltaMeters, 0.001)
    }

    @Test
    fun arrivingMeansCloseEnoughInBothAxes() {
        val near = GuidancePosition(40.4168, -3.7038)

        assertTrue(NavigationGuidance.isReached(5.0, 1.0))
        assertTrue("on the gate counts as arrived", NavigationGuidance.isReached(8.0, 3.0))
        assertFalse("too far", NavigationGuidance.isReached(9.0, 1.0))
        assertFalse("wrong height", NavigationGuidance.isReached(5.0, 4.0))
    }

    @Test
    fun beingFarBelowOrAboveThePlannedHeightIsNotArrival() {
        val position = GuidancePosition(40.4168, -3.7038)

        listOf(-30.0, 30.0).forEach { delta ->
            val leg = NavigationGuidance.leg(
                from = position,
                currentAltitudeMeters = 50.0,
                headingDegrees = 0.0,
                target = GuidanceTarget(0, position.latitude, position.longitude, 50.0 + delta)
            )
            assertFalse("delta $delta", leg.reached)
        }
    }
}
