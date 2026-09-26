package dji.sampleV5.aircraft.pro.cartography

import kotlin.math.sqrt

/**
 * How a ground control point is observed. This is not a formality: the
 * accuracy a photogrammetric block can reach is decided by the quality of
 * its control, and the type of target decides how precisely that coordinate
 * can be measured on the ground.
 */
enum class ControlPointTarget(val key: String, val displayName: String, val typicalAccuracyMillimeters: Double) {
    /** Painted or adhesive cross on pavement or a flat surface. */
    CROSS("cross", "Cruz pintada", 5.0),

    /** A rigid target of known size, checked for scale and centring. */
    CHECKERBOARD("checkerboard", "Patrón de damero", 3.0),

    /** An existing permanent mark, used for check rather than control. */
    PERMANENT_MARK("permanent_mark", "Marca permanente", 15.0),

    /** A recognisable feature corner. The weakest option; only for checks. */
    FEATURE("feature", "Elemento del terreno", 100.0);

    val isSuitableAsControl: Boolean
        get() = this != FEATURE

    companion object {
        fun fromKey(value: String?): ControlPointTarget =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: CROSS
    }
}

/** What the point is for. Check points are never used to adjust the block. */
enum class ControlPointRole(val key: String, val displayName: String) {
    CONTROL("control", "Control"),
    CHECK("check", "Verificación");

    companion object {
        fun fromKey(value: String?): ControlPointRole =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: CONTROL
    }
}

/**
 * A ground control point for a survey block.
 *
 * Coordinates may come from a survey, a cadastral or topographic database, or
 * from a GNSS receiver. The recorded [horizontalAccuracyMillimeters] and
 * [source] travel with the point all the way into the exported report,
 * because a block adjusted against control of unknown quality produces a
 * product of unknown quality.
 */
data class GroundControlPoint(
    val id: String,
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val heightMeters: Double? = null,
    val role: ControlPointRole = ControlPointRole.CONTROL,
    val target: ControlPointTarget = ControlPointTarget.CROSS,
    val horizontalAccuracyMillimeters: Double? = null,
    val source: String = ""
) : java.io.Serializable {
    val isCheckPoint: Boolean
        get() = role == ControlPointRole.CHECK

    fun validate(): ControlPointValidation {
        val errors = mutableListOf<String>()
        if (id.isBlank()) errors += "The control point id is required"
        if (code.isBlank()) errors += "The control point code is required"
        if (!latitude.isFinite() || latitude !in -90.0..90.0) errors += "Latitude is outside WGS 84"
        if (!longitude.isFinite() || longitude !in -180.0..180.0) errors += "Longitude is outside WGS 84"
        if (heightMeters != null && !heightMeters.isFinite()) errors += "The height must be a number"
        if (horizontalAccuracyMillimeters != null &&
            (!horizontalAccuracyMillimeters.isFinite() || horizontalAccuracyMillimeters <= 0.0)
        ) {
            errors += "The horizontal accuracy must be greater than 0 mm"
        }
        if (isCheckPoint && target == ControlPointTarget.FEATURE) {
            errors += "A check point on a terrain feature cannot be measured precisely; use it only as a rough check"
        }
        if (!isCheckPoint && !target.isSuitableAsControl) {
            errors += "${target.displayName} is not a valid control target; use it as a check point instead"
        }
        return ControlPointValidation(errors)
    }

    fun utm(zone: Int = CoordinateReferenceSystem.zoneFor(longitude, latitude)): UtmCoordinate =
        CoordinateReferenceSystem.toUtm(latitude, longitude, zone)

    fun localOffsetMeters(centerLatitude: Double, centerLongitude: Double): LocalOffset =
        CoordinateReferenceSystem.localOffsetMeters(centerLatitude, centerLongitude, latitude, longitude)

    /** Distance from this point to another, on the ground, in metres. */
    fun distanceTo(other: GroundControlPoint): Double = geodesicDistanceMeters(latitude, longitude, other.latitude, other.longitude)

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_008.8

        fun geodesicDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(p1) * kotlin.math.cos(p2) * kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
            return 2.0 * EARTH_RADIUS_METERS * kotlin.math.asin(minOf(1.0, sqrt(a)))
        }
    }
}

data class ControlPointValidation(val errors: List<String>) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

/**
 * Checks the control network of a block: enough control, spread around the
 * block, not clustered on one side, and not too close to each other.
 *
 * These are the conditions that decide whether a reconstruction can be
 * georeferenced at all, and they are cheap to state and impossible to see on
 * a map at a glance, so the app states them.
 */
object ControlNetwork {
    const val MIN_CONTROL_POINTS = 4
    const val MIN_CHECK_POINTS = 1

    /** Control should reach at least this fraction of the block radius. */
    const val MIN_BLOCK_COVERAGE = 0.5

    /** Two axes of the control cloud should be at least this similar in extent. */
    const val MAX_ELONGATION_RATIO = 4.0

    /**
     * @param blockRadiusMeters distance from the block centre to its corner.
     * Pass 0 when the block size is not known; the spread check is then skipped
     * rather than guessed at.
     */
    @JvmOverloads
    fun assess(
        points: List<GroundControlPoint>,
        blockCenterLatitude: Double,
        blockCenterLongitude: Double,
        blockRadiusMeters: Double = 0.0
    ): ControlNetworkAssessment {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val valid = points.filter { it.validate().isValid }
        if (valid.size < points.size) {
            errors += "${points.size - valid.size} control point(s) are not usable and were ignored"
        }
        if (valid.isEmpty()) {
            errors += "The block has no usable ground control"
            return ControlNetworkAssessment(errors, warnings, ControlNetworkMetrics())
        }

        val control = valid.filter { !it.isCheckPoint }
        val check = valid.filter { it.isCheckPoint }
        if (control.size < MIN_CONTROL_POINTS) {
            errors += "At least $MIN_CONTROL_POINTS control points are needed to georeference a block, found ${control.size}"
        }
        if (check.size < MIN_CHECK_POINTS && control.size >= MIN_CONTROL_POINTS) {
            warnings += "Without an independent check point the block cannot be validated, only adjusted"
        }
        valid.filter { it.target == ControlPointTarget.FEATURE && !it.isCheckPoint }.forEach { point ->
            errors += "${point.code} is a terrain feature used as control; its measured accuracy is not controllable"
        }

        if (valid.size >= 2) {
            var minSeparation = Double.MAX_VALUE
            for (i in valid.indices) {
                for (j in i + 1 until valid.size) {
                    val distance = valid[i].distanceTo(valid[j])
                    if (distance < minSeparation) minSeparation = distance
                }
            }
            if (minSeparation < 5.0) {
                warnings += "Two control points are less than 5 m apart. Points that close do not constrain the block."
            }
        }

        val controlOffsets = control.map { it.localOffsetMeters(blockCenterLatitude, blockCenterLongitude) }
        val maxControlRadius = controlOffsets.maxOfOrNull { it.radiusMeters } ?: 0.0
        val allOffsets = valid.map { it.localOffsetMeters(blockCenterLatitude, blockCenterLongitude) }
        val maxRadius = allOffsets.maxOfOrNull { it.radiusMeters } ?: 0.0

        val blockCoverage = if (blockRadiusMeters > 0.0) (maxControlRadius / blockRadiusMeters).coerceAtMost(1.0) else 0.0
        if (control.size >= 2 && blockRadiusMeters > 0.0 && blockCoverage < MIN_BLOCK_COVERAGE) {
            warnings += "Control only reaches ${(blockCoverage * 100).toInt()}% of the block radius. " +
                "A block needs control near its edges, not only in the middle."
        }

        if (controlOffsets.size >= 3) {
            val northExtent = controlOffsets.maxOf { it.northMeters } - controlOffsets.minOf { it.northMeters }
            val eastExtent = controlOffsets.maxOf { it.eastMeters } - controlOffsets.minOf { it.eastMeters }
            val shorter = minOf(northExtent, eastExtent)
            val longer = maxOf(northExtent, eastExtent)
            if (shorter < 0.001) {
                warnings += "All control lies on one line. A collinear network cannot resolve rotation and will " +
                    "leave the block tilted."
            } else if (longer / shorter > MAX_ELONGATION_RATIO) {
                warnings += "Control is ${(longer / shorter).toInt()} times longer in one direction than the other. " +
                    "Spread it across the short axis of the block too."
            }
        }

        val worstAccuracy = control.mapNotNull { it.horizontalAccuracyMillimeters }.maxOrNull()
        if (worstAccuracy != null && worstAccuracy > 50.0) {
            warnings += "The worst control accuracy is ${worstAccuracy.toInt()} mm. " +
                "The block cannot be more accurate than its control."
        }
        if (control.isNotEmpty() && control.all { it.heightMeters == null }) {
            warnings += "No control has a height. The block can only be georeferenced in 2D."
        }

        return ControlNetworkAssessment(
            errors = errors,
            warnings = warnings,
            metrics = ControlNetworkMetrics(
                totalCount = valid.size,
                controlCount = control.size,
                checkCount = check.size,
                blockCoverageRatio = blockCoverage,
                maxControlRadiusMeters = maxControlRadius,
                maxRadiusMeters = maxRadius
            )
        )
    }

    /** True when no other point sits closer than [minimumMeters] to [point]. */
    fun isDistinctEnough(point: GroundControlPoint, others: List<GroundControlPoint>, minimumMeters: Double = 5.0): Boolean =
        others.none { it !== point && it.distanceTo(point) < minimumMeters }

    /** Half the diagonal of the block: the radius control should reach. */
    fun blockRadiusMeters(lengthMeters: Double, widthMeters: Double): Double =
        sqrt(lengthMeters * lengthMeters + widthMeters * widthMeters) / 2.0
}

data class ControlNetworkMetrics(
    val totalCount: Int = 0,
    val controlCount: Int = 0,
    val checkCount: Int = 0,
    val blockCoverageRatio: Double = 0.0,
    val maxControlRadiusMeters: Double = 0.0,
    val maxRadiusMeters: Double = 0.0
)

data class ControlNetworkAssessment(
    val errors: List<String>,
    val warnings: List<String>,
    val metrics: ControlNetworkMetrics
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}
