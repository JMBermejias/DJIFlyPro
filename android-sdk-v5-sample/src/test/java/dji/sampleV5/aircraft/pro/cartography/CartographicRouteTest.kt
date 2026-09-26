package dji.sampleV5.aircraft.pro.cartography

import dji.sampleV5.aircraft.pro.mission.MissionGeometry
import dji.sampleV5.aircraft.pro.mission.MissionRequest
import dji.sampleV5.aircraft.pro.mission.MissionTemplate
import dji.sampleV5.aircraft.pro.mission.RoutePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil

/**
 * The route generator has to behave the way a photogrammetric block is
 * actually flown: lines spread evenly across the whole block, the outermost
 * line on the boundary, and every line running past both ends so the border is
 * covered by whole photographs.
 */
class CartographicRouteTest {

    private fun request(
        length: Double = 200.0,
        width: Double = 120.0,
        lineSpacing: Double = 12.0,
        photoSpacing: Double = 8.0,
        bearing: Double = 0.0,
        routePattern: RoutePattern = RoutePattern.PARALLEL,
        template: MissionTemplate = MissionTemplate.FIELD
    ) = MissionRequest(
        name = "test",
        template = template,
        centerLatitude = 40.4168,
        centerLongitude = -3.7038,
        lengthMeters = length,
        widthMeters = width,
        heightMeters = 30.0,
        bearingDegrees = bearing,
        altitudeMeters = 160.0,
        standoffMeters = 12.0,
        lineSpacingMeters = lineSpacing,
        photoSpacingMeters = photoSpacing,
        overlapPercent = 80,
        speedMps = 4.0,
        routePattern = routePattern,
        finishAction = dji.sampleV5.aircraft.pro.mission.FinishAction.RETURN_HOME
    )

    private fun rawCrossOffsets(plan: dji.sampleV5.aircraft.pro.mission.MissionPlan): List<Double> {
        val request = plan.request
        val bearing = Math.toRadians(request.bearingDegrees)
        val sin = kotlin.math.sin(bearing)
        val cos = kotlin.math.cos(bearing)
        return plan.waypoints.map { waypoint ->
            val offset = CoordinateReferenceSystem.localOffsetMeters(
                originLatitudeDegrees = request.centerLatitude,
                originLongitudeDegrees = request.centerLongitude,
                targetLatitudeDegrees = waypoint.latitude,
                targetLongitudeDegrees = waypoint.longitude
            )
            offset.northMeters * -sin + offset.eastMeters * cos
        }
    }

    private fun rawAlongOffsets(plan: dji.sampleV5.aircraft.pro.mission.MissionPlan): List<Double> {
        val request = plan.request
        val bearing = Math.toRadians(request.bearingDegrees)
        val sin = kotlin.math.sin(bearing)
        val cos = kotlin.math.cos(bearing)
        return plan.waypoints.map { waypoint ->
            val offset = CoordinateReferenceSystem.localOffsetMeters(
                originLatitudeDegrees = request.centerLatitude,
                originLongitudeDegrees = request.centerLongitude,
                targetLatitudeDegrees = waypoint.latitude,
                targetLongitudeDegrees = waypoint.longitude
            )
            offset.northMeters * cos + offset.eastMeters * sin
        }
    }

    /**
     * Distinct flight lines, found by bucketing the cross-track offset. The
     * projection leaves last-decimal noise along a line, so an exact `distinct`
     * would report one line per photograph.
     */
    private fun crossOffsets(plan: dji.sampleV5.aircraft.pro.mission.MissionPlan): List<Double> {
        val tolerance = maxOf(plan.request.photoSpacingMeters, 0.5) * 0.5
        return CoordinateReferenceSystem.clusterByTolerance(rawCrossOffsets(plan), tolerance)
    }

    private fun alongOffsets(plan: dji.sampleV5.aircraft.pro.mission.MissionPlan): List<Double> =
        rawAlongOffsets(plan)

    @Test
    fun theOutermostLinesLandOnTheBlockBoundary() {
        val plan = MissionGeometry.plan(request(length = 200.0, width = 120.0, lineSpacing = 12.0))
        val offsets = crossOffsets(plan)
        assertEquals(-60.0, offsets.min(), 0.5)
        assertEquals(60.0, offsets.max(), 0.5)
    }

    @Test
    fun theLinesAreSpreadEvenlyAndNeverCloserThanAsked() {
        val plan = MissionGeometry.plan(request(length = 200.0, width = 120.0, lineSpacing = 12.0))
        val lines = crossOffsets(plan)
        assertEquals(10, lines.size)
        val steps = lines.zipWithNext().map { (a, b) -> b - a }
        val average = steps.average()
        assertEquals(average, steps[0], 0.05)
        // Spreading evenly may only reduce the number of lines, never tighten
        // them past what the operator asked for.
        assertTrue("average spacing was $average", average >= 12.0 - 0.01)
    }

    @Test
    fun everyLineRunsPastBothEndsByOnePhotoAdvance() {
        val plan = MissionGeometry.plan(request(length = 200.0, width = 120.0, photoSpacing = 8.0))
        val offsets = alongOffsets(plan)
        assertEquals(-108.0, offsets.min(), 0.5)
        assertEquals(108.0, offsets.max(), 0.5)
    }

    @Test
    fun thePhotoAdvanceAlongALineIsExactlyWhatWasAsked() {
        val plan = MissionGeometry.plan(request(length = 200.0, width = 120.0, photoSpacing = 8.0))
        val raw = rawAlongOffsets(plan)
        val firstLine = CoordinateReferenceSystem.clusterByTolerance(
            rawCrossOffsets(plan),
            maxOf(plan.request.photoSpacingMeters, 0.5) * 0.5
        ).first()
        val along = raw.zip(rawCrossOffsets(plan))
            .filter { (along, cross) -> abs(cross - firstLine) < 0.5 }
            .map { (along, _) -> along }
            .sorted()
        assertTrue(along.size > 2)
        val steps = along.zipWithNext().map { (a, b) -> b - a }
        assertTrue("steps were $steps", steps.all { it in 7.9..8.1 })
    }

    @Test
    fun theNumberOfLinesIsTheCeilingOfTheBlockOverTheSpacing() {
        listOf(12.0, 13.0, 20.0, 25.0, 40.0).forEach { spacing ->
            val plan = MissionGeometry.plan(request(width = 120.0, lineSpacing = spacing))
            val expected = maxOf(1, ceil(120.0 / spacing).toInt())
            assertEquals("spacing $spacing", expected, crossOffsets(plan).size)
        }
    }

    @Test
    fun aSingleLineBlockStillCoversTheWholeLength() {
        val plan = MissionGeometry.plan(request(length = 100.0, width = 20.0, lineSpacing = 40.0))
        assertEquals(1, crossOffsets(plan).size)
        val along = alongOffsets(plan)
        assertEquals(-58.0, along.min(), 0.5)
        assertEquals(58.0, along.max(), 0.5)
    }

    @Test
    fun aRotatedBlockKeepsItsShape() {
        val plan = MissionGeometry.plan(request(length = 200.0, width = 120.0, bearing = 37.0))
        val cross = crossOffsets(plan)
        val along = alongOffsets(plan)
        assertEquals(120.0, cross.max() - cross.min(), 1.0)
        assertEquals(216.0, along.max() - along.min(), 1.0)
    }

    @Test
    fun theGridPatternFliesTheBlockTwiceInPerpendicularDirections() {
        val single = MissionGeometry.plan(request(routePattern = RoutePattern.PARALLEL))
        val grid = MissionGeometry.plan(request(routePattern = RoutePattern.GRID))
        assertTrue(grid.waypoints.size > single.waypoints.size)
        // The second pass covers the block across its short axis, so it reaches
        // the corners of the first pass.
        val gridCross = crossOffsets(grid)
        assertTrue(gridCross.min() < -60.0)
        assertTrue(gridCross.max() > 60.0)
    }

    @Test
    fun theCrossPatternFliesTheBlockOnItsDiagonals() {
        val cross = MissionGeometry.plan(request(photoSpacing = 20.0, lineSpacing = 20.0, routePattern = RoutePattern.CROSS))
        val parallel = MissionGeometry.plan(request(photoSpacing = 20.0, lineSpacing = 20.0))
        assertTrue(cross.waypoints.size > parallel.waypoints.size)
        // A diagonal pass of a 200 x 120 block is 226 m long, so it reaches
        // further than the 120 m the single pass does.
        val along = alongOffsets(cross)
        assertTrue("span was ${along.max() - along.min()}", along.max() - along.min() > 220.0)
        assertTrue(cross.warnings.any { it.contains("pasada en cruz") })
    }

    @Test
    fun aCrossBlockThatCannotFitIsRefused() {
        val error = runCatching {
            MissionGeometry.plan(request(photoSpacing = 8.0, lineSpacing = 12.0, routePattern = RoutePattern.CROSS))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("puntos de vuelo") == true)
    }

    @Test
    fun theFacadeRouteAlsoRunsPastTheEnds() {
        val plan = MissionGeometry.plan(
            request(
                length = 40.0,
                width = 40.0,
                photoSpacing = 5.0,
                lineSpacing = 8.0,
                template = MissionTemplate.FACADE
            ).copy(heightMeters = 30.0, altitudeMeters = 8.0, standoffMeters = 10.0)
        )
        val along = alongOffsets(plan)
        assertEquals(-25.0, along.min(), 0.5)
        assertEquals(25.0, along.max(), 0.5)
    }

    @Test
    fun aLargeButFeasibleBlockStaysInsideTheWaypointBudget() {
        val plan = MissionGeometry.plan(request(length = 300.0, width = 150.0, lineSpacing = 12.0, photoSpacing = 8.0))
        assertTrue(plan.waypoints.size <= dji.sampleV5.aircraft.pro.mission.MissionValidator.MAX_WAYPOINTS)
        assertTrue(plan.waypoints.size > 400)
    }

    @Test
    fun anImpossibleBlockIsRefusedBeforeTheWaypointsAreBuilt() {
        val error = runCatching {
            MissionGeometry.plan(request(length = 5000.0, width = 5000.0, lineSpacing = 2.0, photoSpacing = 2.0))
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("puntos de vuelo") == true)
    }

    @Test
    fun theDerivedSpacingAgreesWithTheRouteItProduces() {
        val profile = CartographyProfile(
            cameraId = "zenmuse-p1",
            targetGsdCentimetersPerPixel = 2.0,
            forwardOverlapPercent = 80,
            sideOverlapPercent = 70
        )
        val solution = CartographySolution.solve(profile, 200.0, 120.0)
        val plan = MissionGeometry.plan(
            request(
                length = 200.0,
                width = 120.0,
                lineSpacing = solution.lineSpacingMeters,
                photoSpacing = solution.photoSpacingMeters
            ).copy(altitudeMeters = solution.altitudeMeters)
        )
        // The solution's line count is what the route actually flies.
        assertEquals(solution.lineCount, crossOffsets(plan).size)
        // And the actual line spacing is at least the requested one.
        val lines = crossOffsets(plan)
        val actual = (lines.last() - lines.first()) / (lines.size - 1)
        assertTrue(actual >= solution.lineSpacingMeters)
    }
}
