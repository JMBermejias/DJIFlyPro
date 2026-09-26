package dji.sampleV5.aircraft.pro.cartography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CartographySolutionTest {

    private fun profile(
        cameraId: String = "zenmuse-p1",
        gsd: Double = 2.0,
        forward: Int = 80,
        side: Int = 70,
        crossTrack: Boolean = false,
        reference: AltitudeReference = AltitudeReference.ABOVE_GROUND
    ) = CartographyProfile(
        cameraId = cameraId,
        targetGsdCentimetersPerPixel = gsd,
        forwardOverlapPercent = forward,
        sideOverlapPercent = side,
        crossTrack = crossTrack,
        altitudeReference = reference,
        terrainFollowing = true
    )

    @Test
    fun theSolutionReproducesTheRequestedGsd() {
        val result = CartographySolution.solve(profile(), 500.0, 300.0)
        assertEquals(2.0, result.achievedGsdCentimetersPerPixel, 1e-9)
        assertEquals(159.73, result.altitudeMeters, 0.01)
    }

    @Test
    fun theSpacingFollowsTheRequestedOverlap() {
        val result = CartographySolution.solve(profile(), 500.0, 300.0)
        val footprint = GroundSampleDistance.footprintWidthMeters(result.altitudeMeters, SurveyCamera.ZENMUSE_P1)
        assertEquals(footprint * 0.2, result.photoSpacingMeters, 1e-9)
        assertEquals(footprint * 0.3, result.lineSpacingMeters, 1e-9)
    }

    @Test
    fun aLargerBlockNeedsMoreLinesAndMorePhotographs() {
        val small = CartographySolution.solve(profile(), 200.0, 100.0)
        val large = CartographySolution.solve(profile(), 800.0, 400.0)
        assertTrue(large.lineCount > small.lineCount)
        assertTrue(large.photosPerLine > small.photosPerLine)
        assertTrue(large.estimatedPhotoCount > small.estimatedPhotoCount)
        // The height and the resolution do not depend on the size of the block.
        assertEquals(small.altitudeMeters, large.altitudeMeters, 1e-9)
    }

    @Test
    fun theEdgeMarginAddsOneSpacingPastEachEnd() {
        // Half a photo advance of margin at each end is one full advance in
        // total, so the line is photosPerLine = ceil(length / advance) + 2.
        val length = 200.0
        val result = CartographySolution.solve(profile(forward = 80), length, 40.0)
        val advance = result.photoSpacingMeters
        assertEquals(kotlin.math.ceil(length / advance).toInt() + 2, result.photosPerLine)
    }

    @Test
    fun theCrossTrackPassDoublesThePhotographs() {
        val single = CartographySolution.solve(profile(crossTrack = false), 300.0, 200.0)
        val double = CartographySolution.solve(profile(crossTrack = true), 300.0, 200.0)
        assertEquals(single.estimatedPhotoCount * 2, double.estimatedPhotoCount)
        assertEquals(single.altitudeMeters, double.altitudeMeters, 1e-9)
        assertTrue(double.notes.any { it.contains("Pasada cruzada") })
    }

    @Test
    fun theAreaIsReportedInHectaresAsWellAsSquareMetres() {
        val result = CartographySolution.solve(profile(), 400.0, 250.0)
        assertEquals(100_000.0, result.areaSquareMeters, 1e-6)
        assertEquals(10.0, result.areaHectares, 1e-9)
    }

    @Test
    fun theRedundancyReflectsTheOverlap() {
        val light = CartographySolution.solve(profile(forward = 60, side = 50), 200.0, 200.0)
        val heavy = CartographySolution.solve(profile(forward = 85, side = 80), 200.0, 200.0)
        assertTrue(heavy.redundancyFactor > light.redundancyFactor)
        assertTrue(light.redundancyFactor >= 1.0)
    }

    @Test
    fun aNarrowBlockIsToldItCannotMeetTheSideOverlap() {
        val result = CartographySolution.solve(profile(side = 70), 400.0, 20.0)
        assertEquals(1, result.lineCount)
        assertTrue(result.notes.any { it.contains("Una sola línea de vuelo") })
    }

    @Test
    fun anAltitudeReferenceIsAlwaysStated() {
        assertTrue(
            CartographySolution.solve(profile(reference = AltitudeReference.RELATIVE_TO_TAKEOFF), 200.0, 200.0)
                .notes.any { it.contains("despegue") }
        )
        assertTrue(
            CartographySolution.solve(profile(reference = AltitudeReference.AMSL), 200.0, 200.0)
                .notes.any { it.contains("elipsoide") }
        )
        assertTrue(
            CartographySolution.solve(profile(reference = AltitudeReference.ABOVE_GROUND), 200.0, 200.0)
                .notes.none { it.contains("despegue") || it.contains("elipsoide") }
        )
    }

    @Test
    fun aCoarseGsdThatWouldNeedTooMuchHeightIsCalledOut() {
        // 8 cm/px on a P1 needs about 640 m, past the 500 m the planner builds.
        val result = CartographySolution.solve(profile(gsd = 8.0), 100.0, 100.0)
        assertTrue(result.altitudeMeters > CartographyLimits.MAX_SURVEY_HEIGHT_METERS)
        assertTrue(result.notes.any { it.contains("500") })
        assertTrue(!result.isReliable)
    }

    @Test
    fun aFineGsdIsReachedAtALowHeight() {
        // 1 cm/px on a P1 is about 80 m, which a P1 flies easily.
        val result = CartographySolution.solve(profile(gsd = 1.0), 100.0, 100.0)
        assertEquals(79.87, result.altitudeMeters, 0.1)
        assertTrue(result.isFlyableAutomatically)
    }

    @Test
    fun aResolutionAboveTheAutomaticCeilingIsFlaggedNotBlocked() {
        // 2 cm/px on a P1 needs 160 m: plannable, but not automatic.
        val result = CartographySolution.solve(profile(gsd = 2.0), 200.0, 200.0)
        assertTrue(!result.isFlyableAutomatically)
        assertTrue(result.notes.any { it.contains("120 m de una misión") })
        assertTrue(result.notes.any { it.contains("guía manual") })

        // 1.5 cm/px on a P1 is 120 m, right at the automatic ceiling.
        val coarse = CartographySolution.solve(profile(gsd = 1.5), 200.0, 200.0)
        assertTrue(coarse.isFlyableAutomatically)
        assertTrue(!coarse.notes.any { it.contains("120 m de una misión") })
    }

    @Test
    fun theCameraChoiceChangesTheHeight() {
        val p1 = CartographySolution.solve(profile(cameraId = "zenmuse-p1"), 200.0, 200.0)
        val mavic = CartographySolution.solve(profile(cameraId = "mavic-3e"), 200.0, 200.0)
        assertTrue(mavic.altitudeMeters < p1.altitudeMeters)
        assertEquals(2.0, mavic.achievedGsdCentimetersPerPixel, 1e-9)
    }

    @Test
    fun anInvalidProfileIsRejectedBeforeAnythingIsGenerated() {
        val error = runCatching {
            CartographySolution.solve(profile(gsd = 0.0), 200.0, 200.0)
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("cm/px") == true)
    }

    @Test
    fun aNonPositiveBlockIsRejected() {
        listOf(0.0, -5.0, Double.NaN).forEach { size ->
            val error = runCatching {
                CartographySolution.solve(profile(), size, 200.0)
            }.exceptionOrNull()
            assertTrue("size $size must be rejected", error is IllegalArgumentException)
        }
    }

    @Test
    fun theSolutionIsReproducible() {
        val a = CartographySolution.solve(profile(), 337.0, 211.0)
        val b = CartographySolution.solve(profile(), 337.0, 211.0)
        assertEquals(a, b)
    }

    @Test
    fun theProfileValidatesItsOwnFields() {
        assertTrue(CartographyProfile.DEFAULT.validate().isValid)
        assertTrue(!profile(forward = 5).validate().isValid)
        assertTrue(!profile(gsd = 200.0).validate().isValid)
        assertTrue(!CartographyProfile(obliqueDegrees = 90).validate().isValid)
        assertTrue(CartographyProfile(schema = "nope").validate().errors.any { it.contains("Esquema") })
    }

    @Test
    fun anObliqueAngleOutsideTheRangeIsRejected() {
        assertTrue(CartographyProfile(obliqueDegrees = 0).validate().isValid)
        assertTrue(CartographyProfile(obliqueDegrees = 45).validate().isValid)
        assertTrue(!CartographyProfile(obliqueDegrees = 60).validate().isValid)
        assertTrue(!CartographyProfile(obliqueDegrees = -10).validate().isValid)
    }

    @Test
    fun altitudeReferencesParseFromTheirKeys() {
        assertEquals(AltitudeReference.AMSL, AltitudeReference.fromKey("amsl"))
        assertEquals(AltitudeReference.AMSL, AltitudeReference.fromKey("AMSL"))
        assertEquals(AltitudeReference.ABOVE_GROUND, AltitudeReference.fromKey(null))
        assertEquals(AltitudeReference.ABOVE_GROUND, AltitudeReference.fromKey("nonsense"))
        assertEquals(AltitudeReference.RELATIVE_TO_TAKEOFF, AltitudeReference.fromKey("takeoff"))
    }
}
