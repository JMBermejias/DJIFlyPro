package dji.sampleV5.aircraft.pro.cartography

import dji.sampleV5.aircraft.pro.mission.MissionPlan
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What the operator needs to see after generating a plan, in the units a
 * survey is quoted in: hectares, ground sample distance, number of
 * photographs, redundancy, flight time and the projected coordinates of the
 * block. A planner that only reports waypoints is not a cartographic tool.
 */
data class CartographicSummary(
    val areaSquareMeters: Double,
    val areaHectares: Double,
    val waypointCount: Int,
    val photoCount: Int,
    val flightLineCount: Int,
    val photoSpacingMeters: Double,
    val lineSpacingMeters: Double,
    val flightAltitudeMeters: Double,
    val achievedGsdCentimetersPerPixel: Double,
    val footprintWidthMeters: Double,
    val footprintDepthMeters: Double,
    val redundancyFactor: Double,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Double,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val utm: UtmCoordinate?,
    val footprintPolygon: List<Pair<Double, Double>>,
    val flightLines: List<List<Pair<Double, Double>>>,
    val startLatitude: Double,
    val startLongitude: Double,
    val notes: List<String>
) {
    val crsLabel: String
        get() = utm?.crsLabel ?: "EPSG:4326 (WGS 84 geográfico)"

    /** Expected image data volume, in gigabytes, for RAW or JPEG. */
    fun estimatedStorageGigabytes(megabytesPerPhoto: Double): Double {
        require(megabytesPerPhoto.isFinite() && megabytesPerPhoto > 0.0) {
            "The photo size must be greater than 0 MB"
        }
        return photoCount * megabytesPerPhoto / 1024.0
    }

    /** Flight time including a battery reserve, in minutes. */
    fun estimatedDurationMinutes(reserveFraction: Double): Double {
        require(reserveFraction.isFinite() && reserveFraction in 0.0..0.9) {
            "The reserve must be between 0 and 0.9"
        }
        return estimatedDurationSeconds * (1.0 + reserveFraction) / 60.0
    }

    fun format(): String = buildString {
        appendLine("Superficie: ${round1(areaHectares)} ha (${round0(areaSquareMeters)} m²)")
        appendLine("Resolución prevista: ${round2(achievedGsdCentimetersPerPixel)} cm/px")
        appendLine("Altura de vuelo: ${round1(flightAltitudeMeters)} m")
        appendLine("Huella de la foto: ${round1(footprintWidthMeters)} × ${round1(footprintDepthMeters)} m")
        appendLine("Separación entre líneas: ${round2(lineSpacingMeters)} m (${flightLineCount} líneas)")
        appendLine("Separación entre fotos: ${round2(photoSpacingMeters)} m")
        appendLine("Fotos: $photoCount de $waypointCount puntos")
        appendLine("Solape efectivo: ${round2(redundancyFactor)}×")
        appendLine("Distancia: ${round2(totalDistanceMeters / 1000.0)} km")
        appendLine("Duración estimada: ${round1(estimatedDurationSeconds / 60.0)} min")
        appendLine("Centro: ${CoordinateFormat.latitude(centerLatitude)}, ${CoordinateFormat.longitude(centerLongitude)}")
        appendLine("Sistema de referencia: $crsLabel")
        utm?.let { appendLine("Coordenadas UTM: ${CoordinateFormat.utm(it)}") }
    }

    private fun round0(value: Double) = String.format(java.util.Locale.US, "%.0f", value)
    private fun round1(value: Double) = String.format(java.util.Locale.US, "%.1f", value)
    private fun round2(value: Double) = String.format(java.util.Locale.US, "%.2f", value)
}

object CartographicSummaryBuilder {

    /**
     * Derives the cartographic summary from the plan that was actually
     * generated, not from the parameters that were requested. When the two
     * disagree the discrepancy is reported, because it means the plan on the
     * aircraft is not the plan the operator asked for.
     */
    fun build(plan: MissionPlan, profile: CartographyProfile?, controlPoints: List<GroundControlPoint> = emptyList()): CartographicSummary {
        val request = plan.request
        val camera = profile?.camera ?: SurveyCamera.require(null)
        val altitude = max(request.altitudeMeters, 0.1)
        val footprintWidth = GroundSampleDistance.footprintWidthMeters(altitude, camera)
        val footprintDepth = GroundSampleDistance.footprintDepthMeters(altitude, camera)
        val gsd = GroundSampleDistance.gsdCentimetersPerPixel(altitude, camera)
        val photoCount = plan.waypoints.count { it.takePhoto }
        val forwardOverlap = profile?.forwardOverlapPercent ?: 100 - request.overlapPercent
        val sideOverlap = profile?.sideOverlapPercent ?: 100 - request.overlapPercent
        val area = CoordinateReferenceSystem.localAreaSquareMeters(
            centerLatitudeDegrees = request.centerLatitude,
            centerLongitudeDegrees = request.centerLongitude,
            points = CoordinateReferenceSystem.blockPolygon(
                centerLatitudeDegrees = request.centerLatitude,
                centerLongitudeDegrees = request.centerLongitude,
                lengthMeters = request.lengthMeters,
                widthMeters = request.widthMeters,
                bearingDegrees = request.bearingDegrees
            )
        )
        val lineCount = countFlightLines(plan)
        val flightLines = GeoJsonExporter.splitIntoFlightLines(plan).map { line ->
            line.map { it.latitude to it.longitude }
        }
        val redundancy = if (area > 0.0) {
            photoCount * footprintWidth * footprintDepth / area
        } else {
            0.0
        }

        val notes = buildList {
            if (profile != null) {
                val expectedGsd = profile.targetGsdCentimetersPerPixel
                if (kotlin.math.abs(expectedGsd - gsd) > 0.05) {
                    add("The route altitude gives ${round2(gsd)} cm/px but the profile asked for ${round2(expectedGsd)} cm/px. " +
                        "The route was built from the spacing fields, not from the GSD.")
                }
                GroundSampleDistance.assess(gsd, altitude, camera, forwardOverlap, sideOverlap)
                    .notes
                    .forEach { add(it) }
            } else {
                add("This plan has no cartography profile. The resolution figures use the default payload " +
                    "(${camera.displayName}); the route was built from the spacing fields.")
            }
            if (photoCount == 0) add("The plan takes no photographs, so it produces no cartographic product.")
            if (lineCount < 2) add("A single flight line cannot satisfy a side overlap. Widen the block or lower the overlap.")
            if (plan.waypoints.isNotEmpty()) {
                val minimumSpacing = request.photoSpacingMeters
                if (minimumSpacing > footprintWidth) {
                    add("The photo spacing of ${round1(minimumSpacing)} m exceeds the ${round1(footprintWidth)} m " +
                        "footprint; the block will have gaps along the flight lines.")
                }
            }
            if (controlPoints.isNotEmpty()) {
                val network = ControlNetwork.assess(
                    points = controlPoints,
                    blockCenterLatitude = request.centerLatitude,
                    blockCenterLongitude = request.centerLongitude,
                    blockRadiusMeters = ControlNetwork.blockRadiusMeters(request.lengthMeters, request.widthMeters)
                )
                addAll(network.errors.map { "Control: $it" })
                addAll(network.warnings.map { "Control: $it" })
            }
            if (area > 0.0 && photoCount > 0) {
                val averageFootprint = area * redundancy / photoCount
                if (averageFootprint > 100_000_000.0) {
                    add("This block needs more than ${photoCount} photographs. Check aircraft storage and flight time.")
                }
            }
        }

        return CartographicSummary(
            areaSquareMeters = area,
            areaHectares = area / 10_000.0,
            waypointCount = plan.waypoints.size,
            photoCount = photoCount,
            flightLineCount = lineCount,
            photoSpacingMeters = request.photoSpacingMeters,
            lineSpacingMeters = request.lineSpacingMeters,
            flightAltitudeMeters = altitude,
            achievedGsdCentimetersPerPixel = gsd,
            footprintWidthMeters = footprintWidth,
            footprintDepthMeters = footprintDepth,
            redundancyFactor = redundancy,
            totalDistanceMeters = plan.totalDistanceMeters,
            estimatedDurationSeconds = plan.estimatedDurationSeconds,
            centerLatitude = request.centerLatitude,
            centerLongitude = request.centerLongitude,
            utm = runCatching {
                CoordinateReferenceSystem.toUtm(request.centerLatitude, request.centerLongitude)
            }.getOrNull(),
            footprintPolygon = CoordinateReferenceSystem.blockPolygon(
                centerLatitudeDegrees = request.centerLatitude,
                centerLongitudeDegrees = request.centerLongitude,
                lengthMeters = request.lengthMeters,
                widthMeters = request.widthMeters,
                bearingDegrees = request.bearingDegrees
            ),
            flightLines = flightLines,
            startLatitude = plan.waypoints.firstOrNull()?.latitude ?: request.centerLatitude,
            startLongitude = plan.waypoints.firstOrNull()?.longitude ?: request.centerLongitude,
            notes = notes
        )
    }

    /**
     * Counts flight lines by clustering the waypoints on their cross-track
     * offset from the block centre. Half a photo spacing is the tolerance:
     * closer than that and two waypoints belong to the same line.
     */
    fun countFlightLines(plan: MissionPlan): Int {
        if (plan.waypoints.isEmpty()) return 0
        val request = plan.request
        val bearing = Math.toRadians(request.bearingDegrees)
        val sinB = kotlin.math.sin(bearing)
        val cosB = kotlin.math.cos(bearing)
        val tolerance = max(request.photoSpacingMeters, 0.5) * 0.5
        val offsets = plan.waypoints.map { waypoint ->
            val offset = CoordinateReferenceSystem.localOffsetMeters(
                originLatitudeDegrees = request.centerLatitude,
                originLongitudeDegrees = request.centerLongitude,
                targetLatitudeDegrees = waypoint.latitude,
                targetLongitudeDegrees = waypoint.longitude
            )
            // Project onto the block's cross axis.
            offset.northMeters * -sinB + offset.eastMeters * cosB
        }
        return max(1, CoordinateReferenceSystem.clusterByTolerance(offsets, tolerance).size)
    }

    /** Photographs per flight line, the long way round, for a progress display. */
    fun photosPerLine(plan: MissionPlan): Int {
        val lines = countFlightLines(plan)
        if (lines == 0) return 0
        return ceil(plan.waypoints.size.toDouble() / lines).roundToInt()
    }

    private fun round1(value: Double) = String.format(java.util.Locale.US, "%.1f", value)
    private fun round2(value: Double) = String.format(java.util.Locale.US, "%.2f", value)
}
