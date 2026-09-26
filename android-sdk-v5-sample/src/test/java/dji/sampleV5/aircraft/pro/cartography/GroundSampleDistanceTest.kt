package dji.sampleV5.aircraft.pro.cartography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundSampleDistanceTest {

    @Test
    fun gsdFollowsTheThinLensRelation() {
        // P1 at 100 m: 100 * 35.9 / (35.0 * 8192) = 0.01252 m/px = 1.25 cm/px
        val gsd = GroundSampleDistance.gsdCentimetersPerPixel(100.0, SurveyCamera.ZENMUSE_P1)
        assertEquals(1.2522, gsd, 0.001)
    }

    @Test
    fun gsdIsLinearInHeight() {
        val low = GroundSampleDistance.gsdCentimetersPerPixel(50.0, SurveyCamera.ZENMUSE_P1)
        val high = GroundSampleDistance.gsdCentimetersPerPixel(150.0, SurveyCamera.ZENMUSE_P1)
        assertEquals(3.0, high / low, 0.0001)
    }

    @Test
    fun heightForGsdInvertsGsd() {
        listOf(0.5, 1.0, 2.0, 3.5, 10.0).forEach { target ->
            val height = GroundSampleDistance.heightForGsd(target, SurveyCamera.ZENMUSE_P1)
            val back = GroundSampleDistance.gsdCentimetersPerPixel(height, SurveyCamera.ZENMUSE_P1)
            assertEquals(target, back, 1e-9)
        }
    }

    @Test
    fun heightForGsdMatchesPublishedFigure() {
        // P1 needs about 159.7 m for 2 cm/px, the figure DJI quotes for the P1.
        assertEquals(159.7, GroundSampleDistance.heightForGsd(2.0, SurveyCamera.ZENMUSE_P1), 0.1)
    }

    @Test
    fun footprintWidthMatchesFootprintDepth() {
        val width = GroundSampleDistance.footprintWidthMeters(100.0, SurveyCamera.ZENMUSE_P1)
        val depth = GroundSampleDistance.footprintDepthMeters(100.0, SurveyCamera.ZENMUSE_P1)
        assertEquals(102.57, width, 0.05)
        assertEquals(68.57, depth, 0.05)
        assertTrue(depth < width)
    }

    @Test
    fun footprintEqualsPixelsTimesGsd() {
        val height = 120.0
        val gsdMeters = GroundSampleDistance.gsdCentimetersPerPixel(height, SurveyCamera.ZENMUSE_P1) / 100.0
        val width = GroundSampleDistance.footprintWidthMeters(height, SurveyCamera.ZENMUSE_P1)
        assertEquals(SurveyCamera.ZENMUSE_P1.imageWidthPixels * gsdMeters, width, 1e-9)
    }

    @Test
    fun photoSpacingHonoursForwardOverlap() {
        // 80% forward overlap on a 102.57 m footprint advances 20.51 m.
        assertEquals(
            20.514,
            GroundSampleDistance.photoSpacingMeters(100.0, SurveyCamera.ZENMUSE_P1, 80),
            0.01
        )
    }

    @Test
    fun lineSpacingHonoursSideOverlap() {
        assertEquals(
            30.771,
            GroundSampleDistance.lineSpacingMeters(100.0, SurveyCamera.ZENMUSE_P1, 70),
            0.01
        )
    }

    @Test
    fun higherOverlapTightensSpacing() {
        val low = GroundSampleDistance.lineSpacingMeters(100.0, SurveyCamera.ZENMUSE_P1, 50)
        val high = GroundSampleDistance.lineSpacingMeters(100.0, SurveyCamera.ZENMUSE_P1, 80)
        assertTrue(high < low)
    }

    @Test
    fun overlapsOutsideTheRangeAreRejected() {
        listOf(0, 9, 91, 100, -5).forEach { percent ->
            val error = runCatching {
                GroundSampleDistance.lineSpacingMeters(100.0, SurveyCamera.ZENMUSE_P1, percent)
            }.exceptionOrNull()
            assertTrue("overlap $percent must be rejected", error is IllegalArgumentException)
        }
    }

    @Test
    fun requiredPhotoCountRoundsUp() {
        // 1 ha at 100 m with a P1 is a lot of photographs; the count must be an
        // integer and at least the theoretical minimum.
        val count = GroundSampleDistance.requiredPhotoCount(
            areaSquareMeters = 10_000.0,
            heightMeters = 160.0,
            camera = SurveyCamera.ZENMUSE_P1,
            forwardOverlapPercent = 80,
            sideOverlapPercent = 70
        )
        assertTrue(count > 0)
        val coverage = GroundSampleDistance.photoCoverageSquareMeters(160.0, SurveyCamera.ZENMUSE_P1)
        val advance = coverage * 0.2 * 0.3
        assertTrue(count >= (10_000.0 / advance).toInt() - 1)
    }

    @Test
    fun redundancyFactorIsAtLeastTheOverlapImplies() {
        val area = 10_000.0
        val height = 160.0
        val redundancy = GroundSampleDistance.redundancyFactor(
            areaSquareMeters = area,
            heightMeters = height,
            camera = SurveyCamera.ZENMUSE_P1,
            forwardOverlapPercent = 80,
            sideOverlapPercent = 70
        )
        // 1 / (0.2 * 0.3) = 16.67 is the floor. Rounding the photo count up can
        // only push the measured redundancy above it, never below.
        assertTrue("redundancy was $redundancy", redundancy >= 1.0 / (0.2 * 0.3) - 1e-9)
        assertTrue("redundancy was $redundancy", redundancy < 1.0 / (0.2 * 0.3) + 2.0)
    }

    @Test
    fun zeroAreaIsRejectedRatherThanReturningZero() {
        val error = runCatching {
            GroundSampleDistance.requiredPhotoCount(0.0, 100.0, SurveyCamera.ZENMUSE_P1, 80, 70)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun nonPositiveHeightIsRejected() {
        listOf(0.0, -10.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { height ->
            val error = runCatching {
                GroundSampleDistance.gsdCentimetersPerPixel(height, SurveyCamera.ZENMUSE_P1)
            }.exceptionOrNull()
            assertTrue("height $height must be rejected", error is IllegalArgumentException)
        }
    }

    @Test
    fun anUnusableCameraIsRejected() {
        val broken = SurveyCamera.custom("x", "X", 0.0, 0.0, 0.0, 0, 0)
        val error = runCatching {
            GroundSampleDistance.gsdCentimetersPerPixel(100.0, broken)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun assessmentFlagsUnreliableResolutions() {
        val fine = GroundSampleDistance.assess(0.4, 60.0, SurveyCamera.ZENMUSE_P1, 80, 70)
        assertTrue(!fine.isReliable)
        assertTrue(fine.notes.any { it.contains("RTK") || it.contains("1 cm/px") })

        val coarse = GroundSampleDistance.assess(12.0, 900.0, SurveyCamera.ZENMUSE_P1, 80, 70)
        assertTrue(!coarse.isReliable)
        assertTrue(coarse.notes.any { it.contains("5 cm/px") })
    }

    @Test
    fun assessmentFlagsThinOverlap() {
        val assessment = GroundSampleDistance.assess(2.0, 160.0, SurveyCamera.ZENMUSE_P1, 50, 40)
        assertTrue(assessment.notes.any { it.contains("60%") })
        assertTrue(assessment.notes.any { it.contains("50%") })
        assertTrue(assessment.notes.any { it.contains("perpendicular") || it.contains("opposite") })
    }

    @Test
    fun assessmentOfAConformingSolutionIsReliable() {
        val assessment = GroundSampleDistance.assess(2.0, 159.7, SurveyCamera.ZENMUSE_P1, 80, 70)
        assertTrue(assessment.isReliable)
        assertTrue(assessment.notes.any { it.contains("Validate") })
        // 159.7 m is above the caution threshold, and that is a note, not a veto.
        assertTrue(assessment.notes.any { it.contains("120 m") })
    }

    @Test
    fun assessmentNotesHighFlightHeight() {
        val assessment = GroundSampleDistance.assess(4.0, 300.0, SurveyCamera.MAVIC_3E, 80, 70)
        assertTrue(assessment.notes.any { it.contains("120 m") })
    }

    @Test
    fun assessmentWarnsAboutVariableLens() {
        val assessment = GroundSampleDistance.assess(2.0, 100.0, SurveyCamera.IPHONE_15_PRO, 80, 70)
        assertTrue(assessment.notes.any { it.contains("variable lens") })
    }

    @Test
    fun aWiderLensReachesTheSameGsdAtALowerHeight() {        val p1 = GroundSampleDistance.heightForGsd(2.0, SurveyCamera.ZENMUSE_P1)
        val mavic = GroundSampleDistance.heightForGsd(2.0, SurveyCamera.MAVIC_3E)
        // The Mavic 3E covers more ground per metre of height (17.3/12 against
        // the P1's 35.9/35), so it needs fewer metres to reach 2 cm/px.
        assertTrue("P1 $p1 vs Mavic 3E $mavic", mavic < p1)
        assertEquals(73.25, mavic, 0.1)
        assertEquals(159.73, p1, 0.1)
    }
}
