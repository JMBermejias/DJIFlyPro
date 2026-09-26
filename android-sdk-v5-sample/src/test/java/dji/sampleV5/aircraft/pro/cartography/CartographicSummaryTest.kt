package dji.sampleV5.aircraft.pro.cartography

import dji.sampleV5.aircraft.pro.mission.FinishAction
import dji.sampleV5.aircraft.pro.mission.MissionGeometry
import dji.sampleV5.aircraft.pro.mission.MissionRequest
import dji.sampleV5.aircraft.pro.mission.MissionTemplate
import dji.sampleV5.aircraft.pro.mission.RoutePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CartographicSummaryTest {

    private val profile = CartographyProfile(
        cameraId = "zenmuse-p1",
        targetGsdCentimetersPerPixel = 2.0,
        forwardOverlapPercent = 80,
        sideOverlapPercent = 70,
        terrainFollowing = true
    )

    private fun plan(
        altitude: Double = profile.camera.let { GroundSampleDistance.heightForGsd(2.0, it) },
        lineSpacing: Double = 12.0,
        photoSpacing: Double = 8.0,
        routePattern: RoutePattern = RoutePattern.PARALLEL
    ): dji.sampleV5.aircraft.pro.mission.MissionPlan = MissionGeometry.plan(
        MissionRequest(
            name = "Bloque centro",
            template = MissionTemplate.FIELD,
            centerLatitude = 40.4168,
            centerLongitude = -3.7038,
            lengthMeters = 200.0,
            widthMeters = 120.0,
            heightMeters = 30.0,
            bearingDegrees = 0.0,
            altitudeMeters = altitude,
            standoffMeters = 12.0,
            lineSpacingMeters = lineSpacing,
            photoSpacingMeters = photoSpacing,
            overlapPercent = 80,
            speedMps = 4.0,
            routePattern = routePattern,
            finishAction = FinishAction.RETURN_HOME
        )
    )

    @Test
    fun theSummaryReportsTheAreaInSurveyUnits() {
        val summary = CartographicSummaryBuilder.build(plan(), profile)
        assertEquals(2.4, summary.areaHectares, 0.02)
        assertEquals(24_000.0, summary.areaSquareMeters, 30.0)
    }

    @Test
    fun theSummaryReportsTheAchievedResolution() {
        val summary = CartographicSummaryBuilder.build(plan(), profile)
        assertEquals(2.0, summary.achievedGsdCentimetersPerPixel, 0.01)
        assertTrue(summary.footprintWidthMeters > summary.footprintDepthMeters)
    }

    @Test
    fun theSummaryCountsFlightLinesFromTheWaypoints() {
        val summary = CartographicSummaryBuilder.build(plan(), profile)
        // 120 m at 12 m spacing: ceil(120 / 12) = 10 lines, spread evenly at
        // 120 / 9 = 13.33 m so the outermost line lands on the boundary.
        assertEquals(10, summary.flightLineCount)
        assertEquals(10, CartographicSummaryBuilder.countFlightLines(plan()))
    }

    @Test
    fun aTwoPassRouteDoublesTheLineCount() {
        val single = plan()
        val grid = plan(routePattern = RoutePattern.GRID)
        assertTrue(CartographicSummaryBuilder.countFlightLines(grid) > CartographicSummaryBuilder.countFlightLines(single))
    }

    @Test
    fun photosPerLineIsTheLongDivisionOfTheRoute() {
        val p = plan()
        val lines = CartographicSummaryBuilder.countFlightLines(p)
        assertEquals(kotlin.math.ceil(p.waypoints.size.toDouble() / lines).toInt(), CartographicSummaryBuilder.photosPerLine(p))
        assertTrue(lines * CartographicSummaryBuilder.photosPerLine(p) >= p.waypoints.size)
    }

    @Test
    fun theSummaryProjectsTheBlockCentre() {
        val summary = CartographicSummaryBuilder.build(plan(), profile)
        assertNotNull(summary.utm)
        assertEquals(30, summary.utm!!.zone)
        assertTrue(summary.crsLabel.contains("32630"))
    }

    @Test
    fun theFootprintPolygonHasFourCorners() {
        val summary = CartographicSummaryBuilder.build(plan(), profile)
        assertEquals(4, summary.footprintPolygon.size)
        val box = CoordinateReferenceSystem.boundingBox(summary.footprintPolygon)!!
        assertTrue(box.northLatitude - box.southLatitude > 0.001)
    }

    @Test
    fun storageAndDurationEstimatesAreDerived() {
        val summary = CartographicSummaryBuilder.build(plan(), profile)
        assertTrue(summary.estimatedStorageGigabytes(8.0) > 0.0)
        assertTrue(summary.estimatedStorageGigabytes(45.0) > summary.estimatedStorageGigabytes(8.0))
        assertEquals(summary.estimatedDurationSeconds / 60.0 * 1.3, summary.estimatedDurationMinutes(0.3), 1e-9)
    }

    @Test
    fun aNonPositiveReserveIsRejected() {
        val summary = CartographicSummaryBuilder.build(plan(), profile)
        listOf(-0.1, 0.95, Double.NaN).forEach { reserve ->
            val error = runCatching { summary.estimatedDurationMinutes(reserve) }.exceptionOrNull()
            assertTrue("reserve $reserve must be rejected", error is IllegalArgumentException)
        }
    }

    @Test
    fun aMismatchBetweenProfileAndRouteIsReported() {
        // The profile asks for 2 cm/px but the route was built for 60 m, which
        // is 0.75 cm/px. The discrepancy has to be visible, not averaged away.
        val summary = CartographicSummaryBuilder.build(plan(altitude = 60.0), profile)
        assertTrue(summary.notes.toString(), summary.notes.any { it.contains("cm/px") && it.contains("se construyó") })
        assertEquals(0.75, summary.achievedGsdCentimetersPerPixel, 0.01)
    }

    @Test
    fun aPlanWithoutAProfileSaysSoRatherThanGuessing() {
        val summary = CartographicSummaryBuilder.build(plan(), null)
        assertTrue(summary.notes.any { it.contains("no tiene perfil cartográfico") })
        assertTrue(summary.notes.any { it.contains(SurveyCamera.DEFAULT.displayName) })
    }

    @Test
    fun photoSpacingWiderThanTheFootprintIsCalledOut() {
        // At 90 m a P1 covers 92.3 m, so a 95 m photo advance leaves gaps along
        // every flight line.
        val summary = CartographicSummaryBuilder.build(plan(altitude = 90.0, photoSpacing = 95.0), profile)
        assertTrue(summary.notes.any { it.contains("supera la huella") })
    }

    @Test
    fun aSingleLineBlockIsCalledOut() {
        val narrow = MissionGeometry.plan(
            MissionRequest(
                name = "n",
                template = MissionTemplate.FIELD,
                centerLatitude = 40.0,
                centerLongitude = -3.0,
                lengthMeters = 200.0,
                widthMeters = 20.0,
                heightMeters = 10.0,
                bearingDegrees = 0.0,
                altitudeMeters = 160.0,
                standoffMeters = 10.0,
                lineSpacingMeters = 50.0,
                photoSpacingMeters = 8.0,
                overlapPercent = 80,
                speedMps = 4.0,
                routePattern = RoutePattern.PARALLEL,
                finishAction = FinishAction.RETURN_HOME
            )
        )
        val summary = CartographicSummaryBuilder.build(narrow, profile)
        assertTrue(summary.notes.any { it.contains("Una sola línea de vuelo") })
    }

    @Test
    fun controlProblemsSurfaceInTheSummary() {
        val summary = CartographicSummaryBuilder.build(plan(), profile, listOf(
            GroundControlPoint(id = "1", code = "A", latitude = 40.4168, longitude = -3.7038)
        ))
        assertTrue(summary.notes.any { it.startsWith("Control:") && it.contains("Hacen falta al menos 4") })
    }

    @Test
    fun theRedundancyMatchesThePhotographCount() {
        val p = plan()
        val summary = CartographicSummaryBuilder.build(p, profile)
        val expected = summary.photoCount * summary.footprintWidthMeters * summary.footprintDepthMeters /
            summary.areaSquareMeters
        assertEquals(expected, summary.redundancyFactor, 1e-9)
    }

    @Test
    fun theFormattedSummaryCarriesTheEssentials() {
        val text = CartographicSummaryBuilder.build(plan(), profile).format()
        assertTrue(text.contains("ha"))
        assertTrue(text.contains("cm/px"))
        assertTrue(text.contains("líneas"))
        assertTrue(text.contains("EPSG"))
    }

    @Test
    fun anEmptyPlanProducesAnEmptySummaryRatherThanACrash() {
        val empty = plan().copy(waypoints = emptyList())
        val summary = CartographicSummaryBuilder.build(empty, profile)
        assertEquals(0, summary.waypointCount)
        assertEquals(0, summary.photoCount)
        assertTrue(summary.notes.any { it.contains("no hace ninguna foto") })
    }

    @Test
    fun theFootprintPolygonIsPresentEvenWhenTheUtmFails() {
        // Nothing in a valid plan should leave the block without a polygon.
        val summary = CartographicSummaryBuilder.build(plan(), profile)
        assertTrue(summary.footprintPolygon.isNotEmpty())
        assertNull(CoordinateReferenceSystem.boundingBox(emptyList()))
    }
}
