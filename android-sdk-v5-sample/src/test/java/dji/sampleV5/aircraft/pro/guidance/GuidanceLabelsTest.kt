package dji.sampleV5.aircraft.pro.guidance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceLabelsTest {

    private fun leg(relative: Double, reached: Boolean = false, altitudeDelta: Double = 0.0) = GuidanceLeg(
        target = GuidanceTarget(0, 40.0, -3.0, 30.0),
        distanceMeters = 120.0,
        bearingDegrees = 45.0,
        relativeBearingDegrees = relative,
        altitudeDeltaMeters = altitudeDelta,
        reached = reached
    )

    /**
     * The turn direction is the one string that changes what the operator does
     * with the aircraft. Right must be right, in both hemispheres and across the
     * 0/360 wrap.
     */
    @Test
    fun aPositiveRelativeBearingSaysTurnRight() {
        val text = GuidanceLabels.turn(leg(40.0), aircraftConnected = true)

        assertTrue(text, text.contains("DERECHA"))
        assertTrue(text, text.contains("40"))
    }

    @Test
    fun aNegativeRelativeBearingSaysTurnLeft() {
        val text = GuidanceLabels.turn(leg(-40.0), aircraftConnected = true)

        assertTrue(text, text.contains("IZQUIERDA"))
        assertTrue("magnitude must be positive", text.contains("40"))
    }

    @Test
    fun aTargetJustRightOfTheNoseStillSaysRight() {
        assertTrue(GuidanceLabels.turn(leg(6.0), true).contains("DERECHA"))
    }

    @Test
    fun aTargetJustLeftOfTheNoseStillSaysLeft() {
        assertTrue(GuidanceLabels.turn(leg(-6.0), true).contains("IZQUIERDA"))
    }

    @Test
    fun aTargetWithinFiveDegreesReadsAsAhead() {
        assertEquals("TODO AL FRENTE", GuidanceLabels.turn(leg(3.0), true))
        assertEquals("TODO AL FRENTE", GuidanceLabels.turn(leg(-3.0), true))
        assertEquals("TODO AL FRENTE", GuidanceLabels.turn(leg(0.0), true))
    }

    @Test
    fun anArrivedTargetSaysSoInsteadOfATurn() {
        assertEquals("ALCANZADO", GuidanceLabels.turn(leg(90.0, reached = true), true))
    }

    /**
     * No link means no guidance. Saying "turn right" from stale numbers would be
     * worse than saying nothing.
     */
    @Test
    fun noLinkMeansNoDirection() {
        assertEquals("SIN ENLACE", GuidanceLabels.turn(leg(90.0), aircraftConnected = false))
    }

    @Test
    fun noLegMeansNoDirection() {
        assertEquals("SIN PUNTO", GuidanceLabels.turn(null, aircraftConnected = true))
    }

    @Test
    fun anUnknownTurnIsNeverRenderedAsADirection() {
        val text = GuidanceLabels.turn(leg(Double.NaN), aircraftConnected = true)

        assertEquals("GIRO DESCONOCIDO", text)
        assertTrue("must not name a side", !text.contains("DERECHA") && !text.contains("IZQUIERDA"))
    }

    @Test
    fun metersAndDegreesRefuseToInventAValue() {
        assertEquals("N/A", GuidanceLabels.meters(null))
        assertEquals("N/A", GuidanceLabels.meters(Double.NaN))
        assertEquals("N/A", GuidanceLabels.degrees(null))
        assertEquals("N/A", GuidanceLabels.degrees(Double.POSITIVE_INFINITY))
        assertEquals("0 m", GuidanceLabels.meters(0.0))
        assertEquals("120 m", GuidanceLabels.meters(120.4))
        assertEquals("45°", GuidanceLabels.degrees(45.0))
    }

    @Test
    fun batteryRefusesToShowANegativeCharge() {
        assertEquals("N/A", GuidanceLabels.battery(-1))
        assertEquals("0%", GuidanceLabels.battery(0))
        assertEquals("57%", GuidanceLabels.battery(57))
    }

    @Test
    fun altitudeAdviceOnlyWhenItIsWorthActing() {
        assertNull(GuidanceLabels.altitudeAdvice(leg(0.0, altitudeDelta = 0.5)))
        assertNull(GuidanceLabels.altitudeAdvice(leg(0.0, altitudeDelta = -0.5)))
        assertNull(GuidanceLabels.altitudeAdvice(null))
    }

    @Test
    fun altitudeAdviceNamesTheDirection() {
        assertEquals("Sube 12.0 m", GuidanceLabels.altitudeAdvice(leg(0.0, altitudeDelta = 12.0)))
        assertEquals("Baja 12.0 m", GuidanceLabels.altitudeAdvice(leg(0.0, altitudeDelta = -12.0)))
    }
}
