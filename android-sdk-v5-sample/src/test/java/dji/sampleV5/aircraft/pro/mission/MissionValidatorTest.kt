package dji.sampleV5.aircraft.pro.mission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionValidatorTest {
    private fun request() = MissionRequest(
        name = "validator-test",
        template = MissionTemplate.FIELD,
        centerLatitude = 40.0,
        centerLongitude = -3.0,
        lengthMeters = 100.0,
        widthMeters = 50.0,
        heightMeters = 20.0,
        bearingDegrees = 0.0,
        altitudeMeters = 30.0,
        standoffMeters = 10.0,
        lineSpacingMeters = 10.0,
        photoSpacingMeters = 8.0,
        overlapPercent = 70,
        speedMps = 4.0,
        routePattern = RoutePattern.PARALLEL,
        finishAction = FinishAction.RETURN_HOME
    )

    @Test
    fun rejectsNonFiniteBearing() {
        val validation = MissionValidator.validate(request().copy(bearingDegrees = Double.NaN))
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("Bearing") })
    }

    @Test
    fun rejectsNonFiniteCameraAndStandoffValues() {
        val validation = MissionValidator.validate(
            request().copy(standoffMeters = Double.NaN, gimbalPitchDegrees = Double.NaN)
        )
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("stand-off") })
        assertTrue(validation.errors.any { it.contains("Gimbal") })
    }

    @Test
    fun rejectsWaypointOutsideWgs84() {
        val plan = MissionGeometry.plan(request()).copy(
            waypoints = listOf(
                PlannedWaypoint(0, 91.0, -3.0, 30.0, 4.0)
            ),
            totalDistanceMeters = 0.0
        )
        val validation = MissionValidator.validate(plan.request, plan)
        assertFalse(validation.isValid)
        assertTrue(validation.errors.any { it.contains("coordinate") })
    }
}
