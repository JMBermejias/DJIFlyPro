package dji.sampleV5.aircraft.pro.mission

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small WGS84 local projection used by the planner. It is deliberately kept
 * independent from Android and DJI classes so it can be unit tested offline.
 */
object MissionGeometry {
    private const val EARTH_RADIUS_METERS = 6_371_008.8
    private const val METERS_PER_DEGREE = 111_320.0

    fun plan(request: MissionRequest, sourceAlgorithmId: String? = null): MissionPlan {
        validateGeometryInput(request)
        val points = when (request.template) {
            MissionTemplate.FACADE -> facadePoints(request)
            MissionTemplate.ROOF,
            MissionTemplate.SOLAR,
            MissionTemplate.FIELD,
            MissionTemplate.GRID -> areaPoints(request)
        }
        require(points.isNotEmpty()) { "The planner did not generate any waypoints" }
        require(points.size <= MissionValidator.MAX_WAYPOINTS) {
            "The route contains ${points.size} waypoints; maximum is ${MissionValidator.MAX_WAYPOINTS}"
        }

        val distance = points.zipWithNext().sumOf { (a, b) -> distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) }
        val photoCount = points.count { it.takePhoto }
        val hoverSeconds = points.sumOf { it.hoverSeconds }
        val duration = distance / max(request.speedMps, 0.1) + photoCount * 1.5 + hoverSeconds
        val warnings = buildList {
            add("Verify airspace, obstacles, people, property permissions and local regulations before flight.")
            if (request.template == MissionTemplate.FACADE) {
                add("Facade routes use the requested bearing as the wall direction and stand off to its right; verify the aircraft side before upload.")
            }
            if (request.overlapPercent < 35) {
                add("Low overlap may reduce photogrammetry quality; validate the result on a test flight.")
            }
            if (request.photoSpacingMeters > 10.0) {
                add("Photo spacing is greater than 10 m; check that it matches the camera and required ground sample distance.")
            }
            if (points.size > 600) {
                add("This is a large route. Verify aircraft memory, battery reserve and airspace limits.")
            }
        }
        return MissionPlan(
            id = "DFP-${System.currentTimeMillis()}",
            createdAtEpochMs = System.currentTimeMillis(),
            request = request,
            waypoints = points,
            totalDistanceMeters = distance,
            estimatedDurationSeconds = duration,
            warnings = warnings,
            sourceAlgorithmId = sourceAlgorithmId
        )
    }

    fun offset(origin: GeoPoint, northMeters: Double, eastMeters: Double): GeoPoint {
        val latitude = origin.latitude + northMeters / METERS_PER_DEGREE
        val longitudeScale = max(abs(cos(Math.toRadians(origin.latitude))), 0.000001)
        val longitude = origin.longitude + eastMeters / (METERS_PER_DEGREE * longitudeScale)
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Generated latitude is outside WGS84" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Generated longitude is outside WGS84" }
        return GeoPoint(latitude, longitude)
    }

    fun destination(origin: GeoPoint, distanceMeters: Double, bearingDegrees: Double): GeoPoint {
        val bearing = Math.toRadians(bearingDegrees)
        val north = cos(bearing) * distanceMeters
        val east = sin(bearing) * distanceMeters
        return offset(origin, north, east)
    }

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        require(lat1.isFinite() && lat1 in -90.0..90.0 && lon1.isFinite() && lon1 in -180.0..180.0)
        require(lat2.isFinite() && lat2 in -90.0..90.0 && lon2.isFinite() && lon2 in -180.0..180.0)
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(p1) * cos(p2) * sin(dLon / 2) * sin(dLon / 2)
        return 2.0 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        require(lat1.isFinite() && lat1 in -90.0..90.0 && lon1.isFinite() && lon1 in -180.0..180.0)
        require(lat2.isFinite() && lat2 in -90.0..90.0 && lon2.isFinite() && lon2 in -180.0..180.0)
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun validateGeometryInput(request: MissionRequest) {
        require(request.centerLatitude.isFinite() && request.centerLatitude in -90.0..90.0) { "Latitude is outside WGS84" }
        require(request.centerLongitude.isFinite() && request.centerLongitude in -180.0..180.0) { "Longitude is outside WGS84" }
        require(request.bearingDegrees.isFinite()) { "Bearing must be finite" }
        require(request.lengthMeters in 1.0..5_000.0 && request.widthMeters in 1.0..5_000.0) {
            "Length and width must be between 1 m and 5 km"
        }
        val maxHeight = if (request.template == MissionTemplate.FACADE) 300.0 else 5_000.0
        require(request.heightMeters in 1.0..maxHeight) { "Height is outside the safe range" }
        require(request.altitudeMeters in 2.0..MissionValidator.MAX_ALTITUDE_METERS) {
            "Altitude is outside the safe range"
        }
        require(request.standoffMeters in 0.5..500.0) { "Stand-off is outside the safe range" }
        require(request.speedMps in 1.0..MissionValidator.MAX_SPEED_MPS) { "Speed is outside the safe range" }
        require(request.overlapPercent in 10..90) { "Overlap is outside the safe range" }
        require(request.gimbalPitchDegrees.isFinite() && request.gimbalPitchDegrees in -90.0..30.0) {
            "Gimbal pitch is outside the safe range"
        }
        require(request.photoSpacingMeters in 0.5..100.0 && request.lineSpacingMeters in 0.5..500.0) {
            "Photo and line spacing are outside the safe range"
        }
    }

    private fun areaPoints(request: MissionRequest): List<PlannedWaypoint> {
        val center = GeoPoint(request.centerLatitude, request.centerLongitude)
        val halfLength = request.lengthMeters / 2.0
        val halfWidth = request.widthMeters / 2.0
        val lineCount = lineCount(request.widthMeters, request.lineSpacingMeters)
        val pointsPerLine = linePoints(request.lengthMeters, request.photoSpacingMeters)
        if (request.routePattern == RoutePattern.GRID) {
            val shortLineCount = lineCount(request.lengthMeters, request.lineSpacingMeters)
            val shortPointsPerLine = linePoints(request.widthMeters, request.photoSpacingMeters)
            ensureWaypointBudget(lineCount.toLong() * pointsPerLine + shortLineCount.toLong() * shortPointsPerLine)
        } else {
            ensureWaypointBudget(lineCount.toLong() * pointsPerLine)
        }
        val points = mutableListOf<PlannedWaypoint>()
        var reverse = false

        for (line in 0 until lineCount) {
            val crossOffset = -halfWidth + line * request.lineSpacingMeters
            val boundedCross = crossOffset.coerceIn(-halfWidth, halfWidth)
            val start = offsetAlong(center, -halfLength, boundedCross, request.bearingDegrees)
            val end = offsetAlong(center, halfLength, boundedCross, request.bearingDegrees)
            val samples = sampleLine(start, end, request.photoSpacingMeters)
            val ordered = if (reverse) samples.asReversed() else samples
            ordered.forEach { point -> points += waypoint(points.size, point, request) }
            reverse = !reverse
        }

        if (request.routePattern == RoutePattern.GRID && points.size > 2) {
            // A second pass across the short axis gives a useful inspection grid
            // for roofs and solar plants while preserving the same safety limits.
            val halfShort = request.widthMeters / 2.0
            val shortCount = lineCount(request.lengthMeters, request.lineSpacingMeters)
            var reverseShort = false
            for (line in 0 until shortCount) {
                val alongOffset = -halfLength + line * request.lineSpacingMeters
                val boundedAlong = alongOffset.coerceIn(-halfLength, halfLength)
                val start = offsetAlong(center, boundedAlong, -halfShort, request.bearingDegrees)
                val end = offsetAlong(center, boundedAlong, halfShort, request.bearingDegrees)
                val samples = sampleLine(start, end, request.photoSpacingMeters)
                val ordered = if (reverseShort) samples.asReversed() else samples
                ordered.forEach { point -> points += waypoint(points.size, point, request) }
                reverseShort = !reverseShort
            }
        }
        return points
    }

    private fun facadePoints(request: MissionRequest): List<PlannedWaypoint> {
        val center = GeoPoint(request.centerLatitude, request.centerLongitude)
        val wallHeight = max(request.heightMeters, request.altitudeMeters)
        val firstHeight = min(request.altitudeMeters, wallHeight).coerceAtLeast(1.0)
        val levels = mutableListOf<Double>()
        var level = firstHeight
        while (level < wallHeight - 0.05) {
            levels += level
            if (levels.size > MissionValidator.MAX_WAYPOINTS) {
                error("The facade route contains too many vertical levels")
            }
            val next = level + request.lineSpacingMeters
            require(next.isFinite() && next > level) { "Facade level spacing did not advance safely" }
            level = min(wallHeight, next)
        }
        if (levels.isEmpty() || abs(levels.last() - wallHeight) > 0.05) levels += wallHeight
        ensureWaypointBudget(levels.size.toLong() * linePoints(request.lengthMeters, request.photoSpacingMeters))

        val halfLength = request.lengthMeters / 2.0
        val standoff = request.standoffMeters
        val normalBearing = normalizeBearing(request.bearingDegrees + 90.0)
        val points = mutableListOf<PlannedWaypoint>()
        levels.forEachIndexed { levelIndex, height ->
            val wallStart = offsetAlong(center, -halfLength, 0.0, request.bearingDegrees)
            val wallEnd = offsetAlong(center, halfLength, 0.0, request.bearingDegrees)
            val flightStart = destination(wallStart, standoff, normalBearing)
            val flightEnd = destination(wallEnd, standoff, normalBearing)
            val samples = sampleLine(flightStart, flightEnd, request.photoSpacingMeters)
            val ordered = if (levelIndex % 2 == 0) samples else samples.asReversed()
            ordered.forEach { point ->
                points += waypoint(points.size, point, request, height)
            }
        }
        return points
    }

    private fun waypoint(
        index: Int,
        point: GeoPoint,
        request: MissionRequest,
        height: Double = request.altitudeMeters
    ): PlannedWaypoint = PlannedWaypoint(
        index = index,
        latitude = point.latitude,
        longitude = point.longitude,
        heightMeters = height,
        speedMps = request.speedMps,
        takePhoto = true,
        pitchDegrees = request.gimbalPitchDegrees
    )

    private fun sampleLine(start: GeoPoint, end: GeoPoint, spacingMeters: Double): List<GeoPoint> {
        val length = distanceMeters(start.latitude, start.longitude, end.latitude, end.longitude)
        val segments = max(1, ceil(length / spacingMeters).toInt())
        return (0..segments).map { fraction ->
            val t = fraction.toDouble() / segments.toDouble()
            GeoPoint(
                latitude = start.latitude + (end.latitude - start.latitude) * t,
                longitude = start.longitude + (end.longitude - start.longitude) * t
            )
        }
    }

    private fun offsetAlong(center: GeoPoint, along: Double, across: Double, bearing: Double): GeoPoint {
        val b = Math.toRadians(bearing)
        val north = cos(b) * along - sin(b) * across
        val east = sin(b) * along + cos(b) * across
        return offset(center, north, east)
    }

    private fun lineCount(width: Double, spacing: Double): Int {
        val count = floor(width / spacing).toInt() + 1
        return max(1, count)
    }

    private fun linePoints(lengthMeters: Double, spacingMeters: Double): Int {
        val segments = max(1, ceil(lengthMeters / spacingMeters).toInt())
        return segments + 1
    }

    private fun ensureWaypointBudget(estimated: Long) {
        require(estimated <= MissionValidator.MAX_WAYPOINTS.toLong()) {
            "The requested route would contain approximately $estimated waypoints; maximum is ${MissionValidator.MAX_WAYPOINTS}"
        }
    }

    private fun normalizeBearing(value: Double): Double = (value % 360.0 + 360.0) % 360.0
}
