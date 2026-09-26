package dji.sampleV5.aircraft.pro.cartography

import java.io.Serializable

/**
 * How the height in a mission is interpreted. This matters for cartography:
 * a GSD is only true over a known ground height, so the datum the flight
 * altitude refers to has to be stated rather than assumed.
 */
enum class AltitudeReference(val key: String, val displayName: String) {
    /** Height above the terrain, so the planned GSD holds where the ground is. */
    ABOVE_GROUND("above_ground", "Sobre el terreno (AGL)"),

    /** Height over the ellipsoid. Needs a geoid model to become a real height. */
    AMSL("amsl", "Nivel del mar (AMSL)"),

    /** Height above the point where the aircraft took off. */
    RELATIVE_TO_TAKEOFF("takeoff", "Desde el despegue");

    companion object {
        fun fromKey(value: String?): AltitudeReference =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: ABOVE_GROUND
    }
}

/**
 * The cartographic intent behind a plan. It is kept next to the route rather
 * than inside it because it is the part an operator has to justify in a
 * delivery: which payload, which resolution, which overlap, and over which
 * datum.
 */
data class CartographyProfile(
    val schema: String = SCHEMA,
    val cameraId: String = SurveyCamera.DEFAULT.id,
    val targetGsdCentimetersPerPixel: Double = 2.0,
    val forwardOverlapPercent: Int = 80,
    val sideOverlapPercent: Int = 70,
    val altitudeReference: AltitudeReference = AltitudeReference.ABOVE_GROUND,
    val terrainFollowing: Boolean = true,
    val crossTrack: Boolean = true,
    val obliqueDegrees: Int = 0
) : Serializable {
    val camera: SurveyCamera
        get() = SurveyCamera.require(cameraId)

    fun validate(): CartographyValidation {
        val errors = mutableListOf<String>()
        if (schema != SCHEMA) errors += "Unknown cartography schema: $schema"
        if (targetGsdCentimetersPerPixel.isFinite().not() ||
            targetGsdCentimetersPerPixel < GroundSampleDistance.MIN_GSD_CM_PER_PIXEL ||
            targetGsdCentimetersPerPixel > GroundSampleDistance.MAX_GSD_CM_PER_PIXEL
        ) {
            errors += "GSD must be between ${GroundSampleDistance.MIN_GSD_CM_PER_PIXEL} and " +
                "${GroundSampleDistance.MAX_GSD_CM_PER_PIXEL} cm/px"
        }
        if (forwardOverlapPercent !in GroundSampleDistance.MIN_OVERLAP_PERCENT..GroundSampleDistance.MAX_OVERLAP_PERCENT) {
            errors += "Forward overlap must be between ${GroundSampleDistance.MIN_OVERLAP_PERCENT}% and " +
                "${GroundSampleDistance.MAX_OVERLAP_PERCENT}%"
        }
        if (sideOverlapPercent !in GroundSampleDistance.MIN_OVERLAP_PERCENT..GroundSampleDistance.MAX_OVERLAP_PERCENT) {
            errors += "Side overlap must be between ${GroundSampleDistance.MIN_OVERLAP_PERCENT}% and " +
                "${GroundSampleDistance.MAX_OVERLAP_PERCENT}%"
        }
        if (obliqueDegrees !in 0..45) errors += "The oblique angle must be between 0 and 45 degrees"
        val cameraValidation = camera.validate()
        if (!cameraValidation.isValid) {
            errors += cameraValidation.errors
        }
        return CartographyValidation(errors)
    }

    companion object {
        const val SCHEMA = "djiflypro.cartography/v1"

        val DEFAULT = CartographyProfile()
    }
}

data class CartographyValidation(val errors: List<String>) {
    val isValid: Boolean
        get() = errors.isEmpty()
}
