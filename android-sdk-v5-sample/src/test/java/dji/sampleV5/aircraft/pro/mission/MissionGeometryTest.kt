package dji.sampleV5.aircraft.pro.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MissionGeometryTest {
    private fun request(
        template: MissionTemplate = MissionTemplate.FIELD,
        routePattern: RoutePattern = RoutePattern.PARALLEL
    ) = MissionRequest(
        name = "test",
        template = template,
        centerLatitude = 40.0,
        centerLongitude = -3.0,
        lengthMeters = 100.0,
        widthMeters = 40.0,
        heightMeters = 20.0,
        bearingDegrees = 0.0,
        altitudeMeters = 30.0,
        standoffMeters = 10.0,
        lineSpacingMeters = 10.0,
        photoSpacingMeters = 10.0,
        overlapPercent = 70,
        speedMps = 4.0,
        routePattern = routePattern,
        finishAction = FinishAction.RETURN_HOME
    )

    @Test
    fun areaRouteHasConsecutiveIndexedWaypoints() {
        val plan = MissionGeometry.plan(request())
        assertTrue(plan.waypoints.isNotEmpty())
        assertEquals((0 until plan.waypoints.size).toList(), plan.waypoints.map { it.index })
        assertTrue(plan.totalDistanceMeters > 0.0)
        assertTrue(MissionValidator.validate(plan.request, plan).isValid)
    }

    @Test
    fun facadeRouteCreatesMultipleHeightLevels() {
        val plan = MissionGeometry.plan(
            request(template = MissionTemplate.FACADE).copy(
                lengthMeters = 40.0,
                heightMeters = 30.0,
                altitudeMeters = 8.0,
                lineSpacingMeters = 8.0,
                photoSpacingMeters = 10.0
            )
        )
        val heights = plan.waypoints.map { it.heightMeters }.distinct()
        assertTrue(heights.size >= 3)
        assertEquals(8.0, heights.first(), 0.001)
        assertTrue(heights.last() <= 30.0)
    }

    @Test
    fun aRouteIsRefusedBeyondTheGuidedCeiling() {
        val unsafe = request().copy(altitudeMeters = MissionValidator.MAX_GUIDED_ALTITUDE_METERS + 1.0)
        val error = runCatching { MissionGeometry.plan(unsafe) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(MissionValidator.validate(unsafe).errors.any { it.contains("Altitude") })
    }

    @Test
    fun aboveTheAutomaticCeilingTheRouteIsPlannedButNotAutomated() {
        // Mapping payloads need real height. The route may be planned and flown
        // by hand; it may never be uploaded as an automatic waypoint mission.
        val high = request().copy(altitudeMeters = 160.0)
        val plan = MissionGeometry.plan(high)
        assertTrue(MissionValidator.validate(high, plan).isValid)
        assertTrue(MissionValidator.validate(high, plan).warnings.any { it.contains("manual guidance") })
        val automatic = MissionValidator.validateAutomaticMission(high, plan)
        assertTrue(!automatic.isValid)
        assertTrue(automatic.errors.any { it.contains("120 m") })
        assertTrue(automatic.errors.any { it.contains("manual guidance") })
    }

    @Test
    fun insideTheAutomaticCeilingTheRouteMayBeAutomated() {
        val low = request().copy(altitudeMeters = 100.0)
        val plan = MissionGeometry.plan(low)
        assertTrue(MissionValidator.validateAutomaticMission(low, plan).isValid)
    }

    @Test
    fun oversizedRouteIsRejectedBeforeBuildingWaypoints() {
        val error = runCatching {
            MissionGeometry.plan(
                request().copy(
                    lengthMeters = 5_000.0,
                    widthMeters = 5_000.0,
                    lineSpacingMeters = 0.5,
                    photoSpacingMeters = 0.5
                )
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message?.contains("waypoints") == true)
    }

    @Test
    fun distanceUsesExpectedScale() {
        val a = GeoPoint(0.0, 0.0)
        val b = MissionGeometry.destination(a, 100.0, 90.0)
        assertTrue(abs(MissionGeometry.distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) - 100.0) < 1.0)
    }
}
