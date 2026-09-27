package dji.sampleV5.aircraft.models

import dji.v5.common.error.ErrorType

/**
 * Por qué no se ha podido ejecutar una orden de vuelo.
 *
 * Antes esto viajaba como un `String` y el ViewModel lo llenaba con
 * `reason.name`, que es el nombre del enum en inglés: el Toast llegaba a decir
 * `BATTERY_TOO_LOW` o `NOT_REGISTERED` dentro de una frase en español.
 *
 * El problema de fondo no era el idioma, era que se mezclaban dos cosas de
 * naturaleza distinta: un motivo nuestro, que se traduce, y un rechazo del
 * aircraft o del SDK, cuyo texto llega en el idioma que DJI quiera. Al
 * separarlas, cada una se puede mostrar como corresponde, y el texto deja de
 * fabricarse en la capa que no tiene acceso a los recursos.
 */
sealed interface FlightFailure {

    /**
     * La política local de seguridad lo impide antes de enviar la orden.
     * El motivo es nuestro y se traduce con [dji.sampleV5.aircraft.pro.FlightFailureText].
     */
    data class Blocked(val reason: FlightSafetyReason) : FlightFailure

    /**
     * El aircraft o el SDK han rechazado la orden.
     *
     * Se guardan el tipo y el código además del texto porque son lo que permite
     * traducirlo: el texto solo de DJI llega siempre en inglés, pero el par
     * (tipo, código) identifica el fallo y tiene traducción propia. Sin ellos
     * solo se podría repetir la frase de DJI.
     */
    data class Rejected(
        val type: ErrorType,
        val code: String,
        val description: String
    ) : FlightFailure
}
