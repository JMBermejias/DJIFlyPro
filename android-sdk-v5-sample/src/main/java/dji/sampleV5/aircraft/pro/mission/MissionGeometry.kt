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
        require(points.isNotEmpty()) { "El planificador no ha generado ningún punto de vuelo" }
        require(points.size <= MissionValidator.MAX_WAYPOINTS) {
            "La ruta contiene ${points.size} puntos de vuelo; el máximo es ${MissionValidator.MAX_WAYPOINTS}"
        }

        val distance = points.zipWithNext().sumOf { (a, b) -> distanceMeters(a.latitude, a.longitude, b.latitude, b.longitude) }
        val photoCount = points.count { it.takePhoto }
        val hoverSeconds = points.sumOf { it.hoverSeconds }
        val duration = distance / max(request.speedMps, 0.1) + photoCount * 1.5 + hoverSeconds
        val warnings = buildList {
            add("Verifica el espacio aéreo, los obstáculos, las personas, los permisos y la normativa local antes de volar.")
            if (request.template == MissionTemplate.FACADE) {
                add("Las rutas de fachada usan el rumbo indicado como dirección del muro y se separan hacia su derecha; verifica de qué lado queda el aircraft antes de subir.")
            }
            if (request.overlapPercent < 35) {
                add("Un solape bajo puede reducir la calidad fotogramétrica; valida el resultado en un vuelo de prueba.")
            }
            if (request.photoSpacingMeters > 10.0) {
                add("La separación entre fotos es mayor de 10 m; comprueba que encaja con la cámara y la resolución de suelo requerida.")
            }
            if (points.size > 600) {
                add("Esta ruta es extensa. Verifica la memoria del aircraft, la reserva de batería y los límites de espacio aéreo.")
            }
            if (request.template != MissionTemplate.FACADE) {
                add("El bloque se cubre con una ruta en área, no con una sola pasada de levantamiento. " +
                    "Las líneas solapadas solo reconstruyen si el aircraft mantiene una altura constante sobre el terreno.")
                if (request.routePattern == RoutePattern.CROSS) {
                    add("La pasada en cruz cubre las esquinas del bloque; cuesta un segundo recorrido completo del área.")
                }
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
        require(request.centerLatitude.isFinite() && request.centerLatitude in -90.0..90.0) { "La latitud está fuera del rango WGS84" }
        require(request.centerLongitude.isFinite() && request.centerLongitude in -180.0..180.0) { "La longitud está fuera del rango WGS84" }
        require(request.bearingDegrees.isFinite()) { "El rumbo debe ser un valor finito" }
        require(request.lengthMeters in 1.0..5_000.0 && request.widthMeters in 1.0..5_000.0) {
            "La longitud y el ancho deben estar entre 1 m y 5 km"
        }
        val maxHeight = if (request.template == MissionTemplate.FACADE) 300.0 else 5_000.0
        require(request.heightMeters in 1.0..maxHeight) { "La altura está fuera del rango seguro" }
        require(request.altitudeMeters in 2.0..MissionValidator.MAX_GUIDED_ALTITUDE_METERS) {
            "La altura de vuelo está fuera del rango seguro"
        }
        require(request.standoffMeters in 0.5..500.0) { "La distancia de seguridad está fuera del rango seguro" }
        require(request.speedMps in 1.0..MissionValidator.MAX_SPEED_MPS) { "La velocidad está fuera del rango seguro" }
        require(request.overlapPercent in 10..90) { "El solape está fuera del rango seguro" }
        require(request.gimbalPitchDegrees.isFinite() && request.gimbalPitchDegrees in -90.0..30.0) {
            "La inclinación del estabilizador está fuera del rango seguro"
        }
        require(request.photoSpacingMeters in 0.5..100.0 && request.lineSpacingMeters in 0.5..500.0) {
            "La separación entre fotos y entre líneas está fuera del rango seguro"
        }
    }

    private fun areaPoints(request: MissionRequest): List<PlannedWaypoint> {
        val center = GeoPoint(request.centerLatitude, request.centerLongitude)
        val passes = when (request.routePattern) {
            RoutePattern.PARALLEL -> listOf(0.0)
            RoutePattern.GRID -> listOf(0.0, 90.0)
            // Diagonals of the block, not its edges. Two diagonal passes give
            // the block a different shadow and viewing geometry, which is what
            // oblique and facade capture needs.
            RoutePattern.CROSS -> listOf(45.0, -45.0)
        }
        val extents = passes.map { blockExtents(request.lengthMeters, request.widthMeters, it) }
        ensureWaypointBudget(extents.sumOf { (along, across) ->
            lineCount(across, request.lineSpacingMeters).toLong() * linePoints(along, request.photoSpacingMeters)
        })

        val points = mutableListOf<PlannedWaypoint>()
        passes.forEachIndexed { index, delta ->
            val (along, across) = extents[index]
            appendPass(
                into = points,
                center = center,
                bearingDegrees = normalizeBearing(request.bearingDegrees + delta),
                alongMeters = along,
                acrossMeters = across,
                request = request
            )
        }
        return points
    }

    /**
     * Extent of a rectangle seen from a direction [deltaDegrees] away from its
     * own bearing: how far the block reaches along that direction, and how far
     * across it. Needed because a second pass at 90 or 45 degrees has to cover
     * the corners of the first one, not the same width.
     */
    private fun blockExtents(lengthMeters: Double, widthMeters: Double, deltaDegrees: Double): Pair<Double, Double> {
        val delta = Math.toRadians(deltaDegrees)
        val cos = kotlin.math.abs(kotlin.math.cos(delta))
        val sin = kotlin.math.abs(kotlin.math.sin(delta))
        return (lengthMeters * cos + widthMeters * sin) to (lengthMeters * sin + widthMeters * cos)
    }

    /**
     * Appends one boustrophedon pass over the block. The pass is extended by
     * one photo spacing past each end of the block so the boundary is covered
     * by whole photographs instead of by the edge of the first and last one,
     * and the lines are spread evenly so the outermost line lands on the
     * boundary rather than leaving a sliver uncovered.
     */
    private fun appendPass(
        into: MutableList<PlannedWaypoint>,
        center: GeoPoint,
        bearingDegrees: Double,
        alongMeters: Double,
        acrossMeters: Double,
        request: MissionRequest
    ) {
        val halfAlong = alongMeters / 2.0
        val halfAcross = acrossMeters / 2.0
        val lineCount = lineCount(acrossMeters, request.lineSpacingMeters)
        val actualLineSpacing = if (lineCount > 1) acrossMeters / (lineCount - 1.0) else request.lineSpacingMeters
        val pointsPerLine = linePoints(alongMeters, request.photoSpacingMeters)
        ensureWaypointBudget(lineCount.toLong() * pointsPerLine)
        val margin = request.photoSpacingMeters
        var reverse = false

        for (line in 0 until lineCount) {
            val crossOffset = -halfAcross + line * actualLineSpacing
            val boundedCross = crossOffset.coerceIn(-halfAcross, halfAcross)
            val start = offsetAlong(center, -halfAlong - margin, boundedCross, bearingDegrees)
            val end = offsetAlong(center, halfAlong + margin, boundedCross, bearingDegrees)
            val samples = sampleLine(start, end, request.photoSpacingMeters)
            val ordered = if (reverse) samples.asReversed() else samples
            ordered.forEach { point -> into += waypoint(into.size, point, request) }
            reverse = !reverse
        }
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
                error("La ruta de fachada tiene demasiados niveles verticales")
            }
            val next = level + request.lineSpacingMeters
            require(next.isFinite() && next > level) { "La separación entre alturas de fachada no avanza de forma segura" }
            level = min(wallHeight, next)
        }
        if (levels.isEmpty() || abs(levels.last() - wallHeight) > 0.05) levels += wallHeight
        ensureWaypointBudget(levels.size.toLong() * linePoints(request.lengthMeters, request.photoSpacingMeters))

        val halfLength = request.lengthMeters / 2.0
        val standoff = request.standoffMeters
        val margin = request.photoSpacingMeters
        val normalBearing = normalizeBearing(request.bearingDegrees + 90.0)
        val points = mutableListOf<PlannedWaypoint>()
        levels.forEachIndexed { levelIndex, height ->
            val wallStart = offsetAlong(center, -halfLength - margin, 0.0, request.bearingDegrees)
            val wallEnd = offsetAlong(center, halfLength + margin, 0.0, request.bearingDegrees)
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

    /**
     * Number of flight lines across [widthMeters]. `ceil` keeps the side
     * overlap at or above the requested value; the lines are then spread
     * evenly so the outermost one lands exactly on the boundary.
     */
    private fun lineCount(widthMeters: Double, spacingMeters: Double): Int =
        max(1, ceil(widthMeters / spacingMeters).toInt())

    /**
     * Photographs on one line, including the margin that extends the line one
     * photo spacing past each end of the block.
     */
    private fun linePoints(alongMeters: Double, photoSpacingMeters: Double): Int {
        val extended = alongMeters + 2.0 * photoSpacingMeters
        val segments = max(1, ceil(extended / photoSpacingMeters).toInt())
        return segments + 1
    }

    private fun ensureWaypointBudget(estimated: Long) {
        require(estimated <= MissionValidator.MAX_WAYPOINTS.toLong()) {
            "La ruta solicitada tendría unos $estimated puntos de vuelo; el máximo es ${MissionValidator.MAX_WAYPOINTS}"
        }
    }

    private fun normalizeBearing(value: Double): Double = (value % 360.0 + 360.0) % 360.0
}
