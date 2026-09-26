package dji.sampleV5.aircraft.pro.guidance

import java.util.Locale
import kotlin.math.abs

/**
 * Wording for the guidance display.
 *
 * Kept out of the Activity so the strings are unit tested. The turn label in
 * particular is safety critical: a sign error would point the operator the wrong
 * way, so every case where the input is not trustworthy reads as "unknown"
 * instead of guessing.
 */
object GuidanceLabels {

    fun turn(leg: GuidanceLeg?, aircraftConnected: Boolean): String = when {
        !aircraftConnected -> "SIN ENLACE"
        leg == null -> "SIN PUNTO"
        leg.reached -> "ALCANZADO"
        !leg.relativeBearingDegrees.isFinite() -> "GIRO DESCONOCIDO"
        abs(leg.relativeBearingDegrees) < STRAIGHT_AHEAD_DEGREES -> "TODO AL FRENTE"
        else -> String.format(
            Locale.US,
            "GIRA %s %.0f°",
            if (leg.relativeBearingDegrees > 0) "DERECHA" else "IZQUIERDA",
            abs(leg.relativeBearingDegrees)
        )
    }

    fun meters(value: Double?): String =
        if (value == null || !value.isFinite()) "N/A" else String.format(Locale.US, "%.0f m", value)

    fun degrees(value: Double?): String =
        if (value == null || !value.isFinite()) "N/A" else String.format(Locale.US, "%.0f°", value)

    fun battery(percent: Int): String = if (percent >= 0) "$percent%" else "N/A"

    fun altitudeAdvice(leg: GuidanceLeg?): String? {
        if (leg == null || !leg.altitudeDeltaMeters.isFinite()) return null
        if (abs(leg.altitudeDeltaMeters) < DEAD_BAND_METERS) return null
        return if (leg.altitudeDeltaMeters > 0) {
            String.format(Locale.US, "Sube %.1f m", leg.altitudeDeltaMeters)
        } else {
            String.format(Locale.US, "Baja %.1f m", -leg.altitudeDeltaMeters)
        }
    }

    /** Inside this the target is effectively dead ahead. */
    const val STRAIGHT_AHEAD_DEGREES = 5.0

    /** Below this altitude difference the operator is close enough; do not nag. */
    const val DEAD_BAND_METERS = 1.0
}
