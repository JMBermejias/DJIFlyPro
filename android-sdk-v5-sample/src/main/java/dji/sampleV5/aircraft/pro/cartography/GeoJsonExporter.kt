package dji.sampleV5.aircraft.pro.cartography

import dji.sampleV5.aircraft.pro.mission.MissionPlan
import dji.sampleV5.aircraft.pro.mission.PlannedWaypoint
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * GeoJSON export of a survey plan.
 *
 * GeoJSON is the hand-over format of the cartographic world: QGIS, Pix4D,
 * Agisoft, cloud services and national mapping portals all read it, and none
 * of them read KMZ/WPML. An operator who has to load the block into a
 * processing suite needs this file, so the app can produce it without leaving
 * the phone.
 *
 * The output follows RFC 7946: WGS 84 longitude/latitude pairs, no custom
 * CRS member, and a foreign `crs`-free feature set. Properties carry the
 * cartography metadata that the receiving software cannot infer.
 */
object GeoJsonExporter {

    const val SCHEMA = "djiflypro.cartography.geojson/v1"

    fun export(
        plan: MissionPlan,
        profile: CartographyProfile?,
        controlPoints: List<GroundControlPoint> = emptyList()
    ): String {
        val request = plan.request
        val summary = CartographicSummaryBuilder.build(plan, profile, controlPoints)
        val camera = profile?.camera ?: SurveyCamera.require(null)

        return buildString {
            appendLine("{")
            appendLine("""  "type": "FeatureCollection",""")
            appendLine("""  "name": ${json(plan.request.name)},""")
            appendLine("""  "djiflypro_schema": ${json(SCHEMA)},""")
            append("""  "features": [""")

            val features = mutableListOf<String>()

            features += footprintFeature(summary, plan)
            features += flightLineFeatures(plan)
            if (controlPoints.isNotEmpty()) {
                features += controlPointFeatures(controlPoints)
            }
            features += takeoffFeature(plan)

            appendLine(joinToIndented(features))
            appendLine("  ],")
            appendLine("""  "properties": {""")
            appendLine("""    "missionId": ${json(plan.id)},""")
            appendLine("""    "createdAtEpochMs": ${plan.createdAtEpochMs},""")
            appendLine("""    "template": ${json(request.template.key)},""")
            appendLine("""    "routePattern": ${json(request.routePattern.key)},""")
            appendLine("""    "coordinateReferenceSystem": ${json(summary.crsLabel)},""")
            appendLine("""    "groundSampleDistanceCmPerPx": ${number(summary.achievedGsdCentimetersPerPixel)},""")
            appendLine("""    "targetGroundSampleDistanceCmPerPx": ${number(profile?.targetGsdCentimetersPerPixel ?: 0.0)},""")
            appendLine("""    "forwardOverlapPercent": ${profile?.forwardOverlapPercent ?: (100 - request.overlapPercent)},""")
            appendLine("""    "sideOverlapPercent": ${profile?.sideOverlapPercent ?: (100 - request.overlapPercent)},""")
            appendLine("""    "declaredOverlapPercent": ${request.overlapPercent},""")
            appendLine("""    "altitudeReference": ${json((profile?.altitudeReference ?: AltitudeReference.ABOVE_GROUND).key)},""")
            appendLine("""    "terrainFollowing": ${profile?.terrainFollowing ?: false},""")
            appendLine("""    "flightAltitudeMeters": ${number(request.altitudeMeters)},""")
            appendLine("""    "lineSpacingMeters": ${number(request.lineSpacingMeters)},""")
            appendLine("""    "photoSpacingMeters": ${number(request.photoSpacingMeters)},""")
            appendLine("""    "areaHectares": ${number(summary.areaHectares)},""")
            appendLine("""    "waypointCount": ${plan.waypoints.size},""")
            appendLine("""    "photoCount": ${summary.photoCount},""")
            appendLine("""    "flightLineCount": ${summary.flightLineCount},""")
            appendLine("""    "redundancyFactor": ${number(summary.redundancyFactor)},""")
            appendLine("""    "totalDistanceMeters": ${number(plan.totalDistanceMeters)},""")
            appendLine("""    "estimatedDurationSeconds": ${number(plan.estimatedDurationSeconds)},""")
            appendLine("""    "camera": {""")
            appendLine("""      "id": ${json(camera.id)},""")
            appendLine("""      "name": ${json(camera.displayName)},""")
            appendLine("""      "sensorWidthMm": ${number(camera.sensorWidthMillimeters)},""")
            appendLine("""      "sensorHeightMm": ${number(camera.sensorHeightMillimeters)},""")
            appendLine("""      "focalLengthMm": ${number(camera.focalLengthMillimeters)},""")
            appendLine("""      "imageWidthPx": ${camera.imageWidthPixels},""")
            appendLine("""      "imageHeightPx": ${camera.imageHeightPixels},""")
            appendLine("""      "horizontalFovDegrees": ${number(camera.horizontalFovDegrees)}""")
            appendLine("    },")
            appendLine("""    "utm": ${summary.utm?.let { utmJson(it) } ?: "null"},""")
            appendLine("""    "sourceAlgorithmId": ${json(plan.sourceAlgorithmId)}""")
            append("""  }""")
            appendLine()
            append("}")
        }
    }

    private fun footprintFeature(summary: CartographicSummary, plan: MissionPlan): String {
        val ring = summary.footprintPolygon + summary.footprintPolygon.first()
        val coordinates = ring.joinToString(",") { position(it.first, it.second) }
        return feature(
            type = "Polygon",
            geometry = "{\"type\":\"Polygon\",\"coordinates\":[[$coordinates]]}",
            properties = """
                {"role":"footprint","name":${json(plan.request.name)},"areaSquareMeters":${number(summary.areaSquareMeters)},"areaHectares":${number(summary.areaHectares)},"centerLatitude":${number(summary.centerLatitude)},"centerLongitude":${number(summary.centerLongitude)},"utmEastingMeters":${number(summary.utm?.eastingMeters ?: 0.0)},"utmNorthingMeters":${number(summary.utm?.northingMeters ?: 0.0)},"utmZone":${summary.utm?.zone ?: 0},"utmHemisphere":${json(summary.utm?.hemisphere?.toString() ?: "")}}
            """.trimIndent()
        )
    }

    private fun flightLineFeatures(plan: MissionPlan): List<String> {
        val lines = splitIntoFlightLines(plan)
        return lines.mapIndexed { index, line ->
            val distance = line.zipWithNext().sumOf { (a, b) ->
                GroundControlPoint.geodesicDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            }
            feature(
                type = "LineString",
                geometry = "{\"type\":\"LineString\",\"coordinates\":[${line.joinToString(",") { position(it.latitude, it.longitude) }}]}",
                properties = """
                    {"role":"flightline","index":${index + 1},"waypointCount":${line.size},"photoCount":${line.count { it.takePhoto }},"lengthMeters":${number(distance)},"altitudeMeters":${number(line.firstOrNull()?.heightMeters ?: 0.0)},"gimbalPitchDegrees":${number(line.firstOrNull()?.pitchDegrees ?: -90.0)}}
                """.trimIndent()
            )
        }
    }

    private fun controlPointFeatures(controlPoints: List<GroundControlPoint>): List<String> =
        controlPoints.map { point ->
            val utm = point.utm()
            feature(
                type = "Point",
                geometry = "{\"type\":\"Point\",\"coordinates\":${position(point.latitude, point.longitude)}}",
                properties = """
                    {"role":"ground_control","id":${json(point.id)},"code":${json(point.code)},"kind":${json(point.role.key)},"target":${json(point.target.key)},"targetAccuracyMm":${number(point.target.typicalAccuracyMillimeters)},"heightMeters":${point.heightMeters?.let { number(it) } ?: "null"},"horizontalAccuracyMm":${point.horizontalAccuracyMillimeters?.let { number(it) } ?: "null"},"source":${json(point.source)},"utmEastingMeters":${number(utm.eastingMeters)},"utmNorthingMeters":${number(utm.northingMeters)},"utmZone":${utm.zone},"epsg":${utm.epsgCode}}
                """.trimIndent()
            )
        }

    private fun takeoffFeature(plan: MissionPlan): String {
        val first = plan.waypoints.first()
        return feature(
            type = "Point",
            geometry = "{\"type\":\"Point\",\"coordinates\":${position(first.latitude, first.longitude)}}",
            properties = """{"role":"start","name":${json(plan.request.name)}}"""
        )
    }

    private fun feature(type: String, geometry: String, properties: String): String =
        "    {\"type\":\"Feature\",\"geometry\":$geometry,\"properties\":$properties}"

    private fun position(latitude: Double, longitude: Double): String =
        "[${number(longitude, 7)},${number(latitude, 7)}]"

    private fun utmJson(utm: UtmCoordinate): String = """
        {"zone":${utm.zone},"hemisphere":${json(utm.hemisphere.toString())},"eastingMeters":${number(utm.eastingMeters)},"northingMeters":${number(utm.northingMeters)},"centralMeridianDegrees":${number(utm.centralMeridianDegrees)},"scaleFactor":${number(utm.scaleFactor)},"epsg":${utm.epsgCode},"crs":${json(utm.crsLabel)}}
    """.trimIndent()

    private fun joinToIndented(features: List<String>): String =
        features.joinToString(",\n")

    internal fun json(value: String?): String {
        if (value == null) return "null"
        val out = StringBuilder(value.length + 2)
        out.append('"')
        value.forEach { c ->
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (c < ' ' || c == '\u2028' || c == '\u2029') {
                    out.append(String.format(Locale.US, "\\u%04x", c.code))
                } else {
                    out.append(c)
                }
            }
        }
        out.append('"')
        return out.toString()
    }

    internal fun number(value: Double, decimals: Int = 4): String {
        if (!value.isFinite()) return "null"
        val formatted = String.format(Locale.US, "%.${decimals}f", value)
        return if (formatted == "-0.0000" || formatted == "-0.0") formatted.removePrefix("-") else formatted
    }

    /**
     * Groups a plan's waypoints into flight lines. A new line starts when the
     * cross-track offset from the block centre changes by more than half a
     * photo spacing, which is how a boustrophedon route actually looks.
     */
    fun splitIntoFlightLines(plan: MissionPlan): List<List<PlannedWaypoint>> {
        if (plan.waypoints.isEmpty()) return emptyList()
        val request = plan.request
        val bearing = Math.toRadians(request.bearingDegrees)
        val sinB = sin(bearing)
        val cosB = cos(bearing)
        val tolerance = maxOf(request.photoSpacingMeters, 0.5) * 0.5

        val lines = mutableListOf<MutableList<PlannedWaypoint>>()
        var current = mutableListOf<PlannedWaypoint>()
        var previousCross: Double? = null

        plan.waypoints.forEach { waypoint ->
            val offset = CoordinateReferenceSystem.localOffsetMeters(
                originLatitudeDegrees = request.centerLatitude,
                originLongitudeDegrees = request.centerLongitude,
                targetLatitudeDegrees = waypoint.latitude,
                targetLongitudeDegrees = waypoint.longitude
            )
            val cross = offset.northMeters * -sinB + offset.eastMeters * cosB
            if (previousCross != null && abs(cross - previousCross) > tolerance) {
                lines += current
                current = mutableListOf()
            }
            current += waypoint
            previousCross = cross
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }
}
