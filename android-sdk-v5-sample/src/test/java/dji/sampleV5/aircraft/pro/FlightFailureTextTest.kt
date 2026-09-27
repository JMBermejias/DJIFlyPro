package dji.sampleV5.aircraft.pro

import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.CameraAutomationState
import dji.sampleV5.aircraft.models.FlightSafetyReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El mapeo de enums a textos en español.
 *
 * These tests pin each enum value to its resource id. A copy-and-paste slip in
 * the `when` maps two different reasons to the same string, and the symptom is
 * that the app says "battery too low" when the remote controller disconnected:
 * plausible enough to go unnoticed and wrong enough to mislead the pilot.
 * Asserting the exact id per value is what catches that.
 *
 * The resource *text* is not asserted here. That needs a Context, and these run
 * without one. What matters at this level is that each value points somewhere
 * deliberate, and the strings themselves are already reviewed in
 * `strings_pro.xml`.
 */
class FlightFailureTextTest {

    @Test
    fun cadaMotivoDeBloqueoApuntaASuPropiaCadena() {
        val expected = mapOf(
            FlightSafetyReason.READY to R.string.safety_reason_ready,
            FlightSafetyReason.NOT_REGISTERED to R.string.safety_reason_not_registered,
            FlightSafetyReason.AIRCRAFT_DISCONNECTED to R.string.safety_reason_aircraft_disconnected,
            FlightSafetyReason.REMOTE_CONTROLLER_DISCONNECTED to R.string.safety_reason_remote_disconnected,
            FlightSafetyReason.HOME_NOT_SET to R.string.safety_reason_home_not_set,
            FlightSafetyReason.GPS_TOO_WEAK to R.string.safety_reason_gps_too_weak,
            FlightSafetyReason.BATTERY_UNKNOWN to R.string.safety_reason_battery_unknown,
            FlightSafetyReason.BATTERY_TOO_LOW to R.string.safety_reason_battery_too_low,
            FlightSafetyReason.LOW_BATTERY_WARNING to R.string.safety_reason_low_battery_warning,
            FlightSafetyReason.MOTORS_ALREADY_ON to R.string.safety_reason_motors_already_on,
            FlightSafetyReason.NOT_FLYING to R.string.safety_reason_not_flying
        )
        assertEquals(
            "el mapa de pruebas no cubre todos los motivos del enum",
            FlightSafetyReason.entries.toSet(),
            expected.keys
        )
        expected.forEach { (reason, resource) ->
            assertEquals("mapeo equivocado para $reason", resource, FlightFailureText.label(reason))
        }
    }

    @Test
    fun losMotivosNoSeRepitenLaCadena() {
        val labels = FlightSafetyReason.entries.map { FlightFailureText.label(it) }
        assertEquals("dos motivos comparten la misma cadena", labels.size, labels.toSet().size)
        assertTrue("alguna cadena resulto ser el id 0", labels.none { it == 0 })
    }

    @Test
    fun cadaEstadoDeCamaraApuntaASuPropiaCadena() {
        val expected = mapOf(
            CameraAutomationState.IDLE to R.string.camera_state_idle,
            CameraAutomationState.CAPTURING to R.string.camera_state_capturing,
            CameraAutomationState.RECORDING to R.string.camera_state_recording,
            CameraAutomationState.ERROR to R.string.camera_state_error
        )
        assertEquals(
            "el mapa de pruebas no cubre todos los estados del enum",
            CameraAutomationState.entries.toSet(),
            expected.keys
        )
        expected.forEach { (state, resource) ->
            assertEquals("mapeo equivocado para $state", resource, FlightFailureText.label(state))
        }
    }
}
