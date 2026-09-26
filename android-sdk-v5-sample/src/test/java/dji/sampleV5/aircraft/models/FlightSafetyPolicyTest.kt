package dji.sampleV5.aircraft.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlightSafetyPolicyTest {
    private val policy = FlightSafetyPolicy()
    private val readyTelemetry = FlightTelemetry(
        registered = true,
        aircraftConnected = true,
        remoteControllerConnected = true,
        homeLocationSet = true,
        gpsSignalLevel = 4,
        batteryPercent = 80,
        isFlying = false
    )

    @Test
    fun takeoffRequiresAValidSafetyChain() {
        val decision = policy.evaluate(FlightCommand.TAKEOFF, readyTelemetry)

        assertTrue(decision.allowed)
        assertEquals(FlightSafetyReason.READY, decision.reason)
    }

    @Test
    fun takeoffIsBlockedWithoutAHomePoint() {
        val decision = policy.evaluate(
            FlightCommand.TAKEOFF,
            readyTelemetry.copy(homeLocationSet = false)
        )

        assertFalse(decision.allowed)
        assertEquals(FlightSafetyReason.HOME_NOT_SET, decision.reason)
    }

    @Test
    fun takeoffIsBlockedWhenGpsIsTooWeak() {
        val decision = policy.evaluate(
            FlightCommand.TAKEOFF,
            readyTelemetry.copy(gpsSignalLevel = 2)
        )

        assertFalse(decision.allowed)
        assertEquals(FlightSafetyReason.GPS_TOO_WEAK, decision.reason)
    }

    @Test
    fun emergencyLandDoesNotRequireGpsOrRemoteConnection() {
        val decision = policy.evaluate(
            FlightCommand.EMERGENCY_LAND,
            readyTelemetry.copy(
                remoteControllerConnected = false,
                homeLocationSet = false,
                gpsSignalLevel = 0,
                batteryPercent = 5,
                isFlying = true
            )
        )

        assertTrue(decision.allowed)
    }
}
