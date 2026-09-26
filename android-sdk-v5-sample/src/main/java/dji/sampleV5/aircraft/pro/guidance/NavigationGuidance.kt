package dji.sampleV5.aircraft.pro.guidance

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A position in WGS84. */
data class GuidancePosition(val latitude: Double, val longitude: Double)

/**
 * A waypoint the operator is being guided toward, reduced to what guidance
 * needs. Kept separate from the planner's model so the geometry below has no
 * dependency on the plan format.
 */
data class GuidanceTarget(
    val index: Int,
    val latitude: Double,
    val longitude: Double,
    val heightMeters: Double
)

/**
 * What the operator needs to see to fly one leg by hand.
 *
 * `relativeBearingDegrees` is the only field that changes what the operator does
 * next: it is the turn still to fly, positive to the right. A sign error here
 * would point the aircraft away from the target, so it is computed once, in one
 * place, and covered by tests in both hemispheres.
 */
data class GuidanceLeg(
    val target: GuidanceTarget,
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val relativeBearingDegrees: Double,
    val altitudeDeltaMeters: Double,
    val reached: Boolean
)

/**
 * Spherical navigation for manual waypoint following.
 *
 * The aircraft is never commanded from here. This only turns telemetry and a
 * plan into numbers an operator can fly by, on the remote controller, the way a
 * pilot would follow a plan on an aircraft whose firmware has no waypoint
 * missions.
 */
object NavigationGuidance {

    /** IUGG mean Earth radius. */
    const val EARTH_RADIUS_METERS = 6371008.8

    /** How close counts as "at the waypoint", horizontally. */
    const val DEFAULT_CAPTURE_RADIUS_METERS = 8.0

    /**
     * How close the aircraft altitude must be to the planned height. Wider than
     * the radius on purpose: a rooftop inspection is rarely flown to the
     * decimetre, and a strict vertical gate would stall the operator.
     */
    const val DEFAULT_CAPTURE_ALTITUDE_METERS = 3.0

    fun distanceMeters(from: GuidancePosition, to: GuidancePosition): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLat = Math.toRadians(to.latitude - from.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Initial great-circle bearing from [from] to [to], in degrees clockwise from true north. */
    fun bearingDegrees(from: GuidancePosition, to: GuidancePosition): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val deltaLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        return normalizeDegrees(Math.toDegrees(atan2(y, x)))
    }

    /** Folds any angle into [0, 360). */
    fun normalizeDegrees(degrees: Double): Double {
        val folded = degrees % 360.0
        return if (folded < 0) folded + 360.0 else folded
    }

    /**
     * Signed turn still to fly: `bearing - heading`, folded into (-180, 180].
     * Positive means turn right. The half-turn case resolves to +180 so the
     * operator always gets a definite direction.
     */
    fun relativeBearingDegrees(bearing: Double, heading: Double): Double {
        val delta = normalizeDegrees(bearing - heading)
        return if (delta > 180.0) delta - 360.0 else delta
    }

    /**
     * Builds the guidance for one leg.
     *
     * A target that is not a usable coordinate yields a leg that reports itself
     * as unreached and a distance of [Double.NaN], so a caller formatting the
     * distance for display shows "N/A" instead of a number that would send the
     * operator somewhere.
     */
    fun leg(
        from: GuidancePosition?,
        currentAltitudeMeters: Double?,
        headingDegrees: Double?,
        target: GuidanceTarget,
        captureRadiusMeters: Double = DEFAULT_CAPTURE_RADIUS_METERS,
        captureAltitudeMeters: Double = DEFAULT_CAPTURE_ALTITUDE_METERS
    ): GuidanceLeg {
        val targetPosition = GuidancePosition(target.latitude, target.longitude)
        val usable = from != null && isUsableCoordinate(target.latitude, target.longitude)
        if (!usable) {
            return GuidanceLeg(
                target = target,
                distanceMeters = Double.NaN,
                bearingDegrees = Double.NaN,
                relativeBearingDegrees = Double.NaN,
                altitudeDeltaMeters = target.heightMeters - (currentAltitudeMeters ?: Double.NaN),
                reached = false
            )
        }
        val distance = distanceMeters(from, targetPosition)
        val bearing = bearingDegrees(from, targetPosition)
        val relative = if (headingDegrees == null) Double.NaN
        else relativeBearingDegrees(bearing, headingDegrees)
        val altitudeDelta = target.heightMeters - (currentAltitudeMeters ?: Double.NaN)
        val reached = isReached(
            distanceMeters = distance,
            altitudeDeltaMeters = altitudeDelta,
            captureRadiusMeters = captureRadiusMeters,
            captureAltitudeMeters = captureAltitudeMeters
        )
        return GuidanceLeg(
            target = target,
            distanceMeters = distance,
            bearingDegrees = bearing,
            relativeBearingDegrees = relative,
            altitudeDeltaMeters = altitudeDelta,
            reached = reached
        )
    }

    fun isReached(
        distanceMeters: Double,
        altitudeDeltaMeters: Double,
        captureRadiusMeters: Double = DEFAULT_CAPTURE_RADIUS_METERS,
        captureAltitudeMeters: Double = DEFAULT_CAPTURE_ALTITUDE_METERS
    ): Boolean = distanceMeters.isFinite() && altitudeDeltaMeters.isFinite() &&
        distanceMeters <= captureRadiusMeters && abs(altitudeDeltaMeters) <= captureAltitudeMeters

    /** Rejects out-of-range coordinates and the exact null island sentinel. */
    fun isUsableCoordinate(latitude: Double, longitude: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite()) return false
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return false
        return abs(latitude) > 1e-9 || abs(longitude) > 1e-9
    }
}
