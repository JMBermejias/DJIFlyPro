package dji.sampleV5.aircraft.pro

import android.content.Context
import androidx.annotation.StringRes
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.CameraAutomationState
import dji.sampleV5.aircraft.models.FlightFailure
import dji.sampleV5.aircraft.models.FlightSafetyReason

/**
 * Traduce al español lo que los ViewModel emiten como valores de enum.
 *
 * El enum se queda en inglés a propósito: es un identificador, y los
 * identificadores no se traducen. Lo que se traduce es lo que el usuario lee, y
 * para eso el nombre del enum no sirve. Aquí se decide qué cadena Spanish
 * corresponde a cada valor.
 *
 * Las traducciones ya existían en `strings_pro.xml` desde antes y no las usaba
 * nadie: el código pintaba `reason.name`. Aquí se conectan.
 *
 * Vive fuera de los ViewModel a propósito. Un ViewModel no debería tocar
 * recursos, y si se los pasara habría que darle un `Context` y atarlo al ciclo
 * de vida de la pantalla. Mapear a un identificador de recurso y resolverlo en
 * la vista mantiene el ViewModel sin Android que probar.
 */
object FlightFailureText {

    /**
     * La traducción de cada motivo de bloqueo. Es un `when` exhaustivo sobre el
     * enum: al añadir un motivo nuevo, el compilador obliga a decidir aquí en vez
     * de dejar que se muestre el nombre en inglés por descuido.
     */
    @StringRes
    fun label(reason: FlightSafetyReason): Int = when (reason) {
        FlightSafetyReason.READY -> R.string.safety_reason_ready
        FlightSafetyReason.NOT_REGISTERED -> R.string.safety_reason_not_registered
        FlightSafetyReason.AIRCRAFT_DISCONNECTED -> R.string.safety_reason_aircraft_disconnected
        FlightSafetyReason.REMOTE_CONTROLLER_DISCONNECTED -> R.string.safety_reason_remote_disconnected
        FlightSafetyReason.HOME_NOT_SET -> R.string.safety_reason_home_not_set
        FlightSafetyReason.GPS_TOO_WEAK -> R.string.safety_reason_gps_too_weak
        FlightSafetyReason.BATTERY_UNKNOWN -> R.string.safety_reason_battery_unknown
        FlightSafetyReason.BATTERY_TOO_LOW -> R.string.safety_reason_battery_too_low
        FlightSafetyReason.LOW_BATTERY_WARNING -> R.string.safety_reason_low_battery_warning
        FlightSafetyReason.MOTORS_ALREADY_ON -> R.string.safety_reason_motors_already_on
        FlightSafetyReason.NOT_FLYING -> R.string.safety_reason_not_flying
    }

    /** Igual que [label], para el estado de la cámara del centro de control. */
    @StringRes
    fun label(state: CameraAutomationState): Int = when (state) {
        CameraAutomationState.IDLE -> R.string.camera_state_idle
        CameraAutomationState.CAPTURING -> R.string.camera_state_capturing
        CameraAutomationState.RECORDING -> R.string.camera_state_recording
        CameraAutomationState.ERROR -> R.string.camera_state_error
    }

    /**
     * El texto que se muestra al usuario. Un rechazo de DJI se muestra entre
     * paréntesis para que quede claro que esa frase concreta no la hemos escrito
     * nosotros: si mañana se traduce, se verá que el mensaje es nuestro y el
     * detalle es de DJI.
     */
    fun describe(context: Context, failure: FlightFailure): String = when (failure) {
        is FlightFailure.Blocked -> context.getString(label(failure.reason))
        is FlightFailure.Rejected -> context.getString(R.string.flight_rejected, failure.description)
    }
}
