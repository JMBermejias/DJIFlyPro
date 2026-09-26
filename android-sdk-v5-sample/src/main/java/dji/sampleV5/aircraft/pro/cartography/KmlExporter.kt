package dji.sampleV5.aircraft.pro.cartography

import dji.sampleV5.aircraft.pro.mission.MissionPlan

/**
 * KML export of a survey plan.
 *
 * KML is how a block gets reviewed before it is flown and shown to the client
 * afterwards: Google Earth, DJI FlightHub and every desktop GIS read it, and
 * it carries per-vertex altitude, which GeoJSON does not. A cartographic plan
 * is not agreed until someone has looked at it on a globe, so this is the
 * review format, not just another export.
 */
object KmlExporter {

    fun export(
        plan: MissionPlan,
        profile: CartographyProfile?,
        controlPoints: List<GroundControlPoint> = emptyList()
    ): String {
        val request = plan.request
        val summary = CartographicSummaryBuilder.build(plan, profile, controlPoints)
        val camera = profile?.camera ?: SurveyCamera.require(null)
        val reference = profile?.altitudeReference ?: AltitudeReference.ABOVE_GROUND

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("  <Document>")
            appendLine("    <name>${xml(plan.request.name)}</name>")
            appendLine("    <description>${xml(summary.format())}</description>")
            appendLine("""    <Style id="${xml("footprint")}">""")
            appendLine("""      <LineStyle><color>ff2f6fed</color><width>2</width></LineStyle>""")
            appendLine("""      <PolyStyle><color>2d2f6fed</color></PolyStyle>""")
            appendLine("    </Style>")
            appendLine("""    <Style id="${xml("flightline")}">""")
            appendLine("""      <LineStyle><color>ff00c853</color><width>3</width></LineStyle>""")
            appendLine("    </Style>")
            appendLine("""    <Style id="${xml("control")}">""")
            appendLine("""      <IconStyle><color>ffff0000</color><scale>1.0</scale></IconStyle>""")
            appendLine("    </Style>")
            appendLine("""    <Style id="${xml("check")}">""")
            appendLine("""      <IconStyle><color>ffff9800</color><scale>1.0</scale></IconStyle>""")
            appendLine("    </Style>")

            appendLine("    <Folder>")
            appendLine("      <name>${xml("Bloque de vuelo")}</name>")
            appendLine(footprintPlacemark(summary, plan))
            GeoJsonExporter.splitIntoFlightLines(plan).forEachIndexed { index, line ->
                appendLine(flightLinePlacemark(index + 1, line.map { Triple(it.latitude, it.longitude, it.heightMeters) }, reference))
            }
            if (controlPoints.isNotEmpty()) {
                appendLine("    </Folder>")
                appendLine("    <Folder>")
                appendLine("      <name>${xml("Puntos de control")}</name>")
                controlPoints.forEach { appendLine(controlPlacemark(it)) }
            }
            appendLine("    </Folder>")

            appendLine("    <Folder>")
            appendLine("      <name>${xml("Metadatos cartográficos")}</name>")
            appendLine(metadata(plan, summary, camera, reference))
            appendLine("    </Folder>")
            appendLine("  </Document>")
            append("</kml>")
        }
    }

    private fun footprintPlacemark(summary: CartographicSummary, plan: MissionPlan): String {
        val ring = summary.footprintPolygon + summary.footprintPolygon.first()
        return buildString {
            appendLine("      <Placemark>")
            appendLine("        <name>${xml("Zona de vuelo")}</name>")
            appendLine("        <styleUrl>#footprint</styleUrl>")
            appendLine("        <description>${xml("${summary.areaHectares} ha, ${plan.waypoints.size} puntos")}</description>")
            appendLine("        <Polygon>")
            appendLine("          <altitudeMode>clampToGround</altitudeMode>")
            appendLine("          <tessellate>1</tessellate>")
            appendLine("          <outerBoundaryIs>")
            appendLine("            <LinearRing>")
            appendLine("              <coordinates>")
            ring.forEach { appendLine("                ${coordinates(it.first, it.second, 0.0)}") }
            appendLine("              </coordinates>")
            appendLine("            </LinearRing>")
            appendLine("          </outerBoundaryIs>")
            appendLine("        </Polygon>")
            append("      </Placemark>")
        }
    }

    private fun flightLinePlacemark(
        index: Int,
        points: List<Triple<Double, Double, Double>>,
        reference: AltitudeReference
    ): String = buildString {
        appendLine("      <Placemark>")
        appendLine("        <name>${xml("Línea $index")}</name>")
        appendLine("        <styleUrl>#flightline</styleUrl>")
        appendLine("        <description>${xml("${points.size} puntos")}</description>")
        appendLine("        <LineString>")
        appendLine("          <altitudeMode>${altitudeMode(reference)}</altitudeMode>")
        appendLine("          <tessellate>1</tessellate>")
        appendLine("          <coordinates>")
        points.forEach { appendLine("            ${coordinates(it.first, it.second, it.third)}") }
        appendLine("          </coordinates>")
        appendLine("        </LineString>")
        append("      </Placemark>")
    }

    private fun controlPlacemark(point: GroundControlPoint): String = buildString {
        val utm = point.utm()
        appendLine("      <Placemark>")
        appendLine("        <name>${xml(point.code)}</name>")
        appendLine("        <styleUrl>#${if (point.isCheckPoint) "check" else "control"}</styleUrl>")
        appendLine(
            "        <description>${xml(
                buildString {
                    append(point.role.displayName)
                    append(" · ")
                    append(point.target.displayName)
                    append(" (±")
                    append(point.target.typicalAccuracyMillimeters.toInt())
                    append(" mm)")
                    point.horizontalAccuracyMillimeters?.let { append(" · medida ±${it.toInt()} mm") }
                    append(" · ")
                    append(CoordinateFormat.utm(utm))
                    if (point.source.isNotBlank()) append(" · ${point.source}")
                }
            )}</description>"
        )
        appendLine("      <Point>")
        appendLine("        <altitudeMode>${altitudeMode(if (point.heightMeters != null) AltitudeReference.ABOVE_GROUND else null)}</altitudeMode>")
        appendLine("        <coordinates>${coordinates(point.latitude, point.longitude, point.heightMeters ?: 0.0)}</coordinates>")
        appendLine("      </Point>")
        append("      </Placemark>")
    }

    private fun metadata(
        plan: MissionPlan,
        summary: CartographicSummary,
        camera: SurveyCamera,
        reference: AltitudeReference
    ): String = buildString {
        appendLine("      <Placemark>")
        appendLine("        <name>${xml("Ficha del vuelo")}</name>")
        appendLine("        <description>${xml(facts(plan, summary, camera, reference))}</description>")
        append("      </Placemark>")
    }

    private fun facts(
        plan: MissionPlan,
        summary: CartographicSummary,
        camera: SurveyCamera,
        reference: AltitudeReference
    ): String = buildString {
        val request = plan.request
        appendLine("Misión: ${plan.request.name}")
        appendLine("ID: ${plan.id}")
        appendLine("Sistema de referencia: ${summary.crsLabel}")
        summary.utm?.let { appendLine("UTM zona ${it.zone}${it.hemisphere}: E ${GeoJsonExporter.number(it.eastingMeters, 3)} N ${GeoJsonExporter.number(it.northingMeters, 3)}") }
        appendLine("Superficie: ${GeoJsonExporter.number(summary.areaHectares, 4)} ha")
        appendLine("Resolución: ${GeoJsonExporter.number(summary.achievedGsdCentimetersPerPixel, 2)} cm/px")
        appendLine("Cámara: ${camera.displayName} (${camera.sensorWidthMillimeters} × ${camera.sensorHeightMillimeters} mm, ${camera.focalLengthMillimeters} mm, ${camera.imageWidthPixels} × ${camera.imageHeightPixels} px)")
        appendLine("Altura de vuelo: ${GeoJsonExporter.number(request.altitudeMeters, 2)} m (${reference.displayName})")
        appendLine("Separación entre líneas: ${GeoJsonExporter.number(request.lineSpacingMeters, 3)} m")
        appendLine("Separación entre fotos: ${GeoJsonExporter.number(request.photoSpacingMeters, 3)} m")
        appendLine("Líneas de vuelo: ${summary.flightLineCount}")
        appendLine("Fotos: ${summary.photoCount}")
        appendLine("Solape efectivo: ${GeoJsonExporter.number(summary.redundancyFactor, 2)}×")
        appendLine("Longitud de ruta: ${GeoJsonExporter.number(plan.totalDistanceMeters / 1000.0, 3)} km")
        appendLine("Duración estimada: ${GeoJsonExporter.number(plan.estimatedDurationSeconds / 60.0, 1)} min")
        append("Esta ficha describe una planificación. No certifica vuelo seguro ni calidad del producto final.")
    }

    private fun altitudeMode(reference: AltitudeReference?): String = when (reference) {
        null -> "clampToGround"
        AltitudeReference.ABOVE_GROUND -> "relativeToGround"
        AltitudeReference.RELATIVE_TO_TAKEOFF -> "relativeToSeaLevel"
        AltitudeReference.AMSL -> "absolute"
    }

    private fun coordinates(latitude: Double, longitude: Double, altitude: Double): String =
        "${GeoJsonExporter.number(longitude, 7)},${GeoJsonExporter.number(latitude, 7)},${GeoJsonExporter.number(altitude, 2)}"

    internal fun xml(value: String): String {
        val out = StringBuilder(value.length + 16)
        value.forEach { c ->
            when (c) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&apos;")
                else -> if (c < ' ' && c != '\n' && c != '\r' && c != '\t') {
                    out.append(' ')
                } else {
                    out.append(c)
                }
            }
        }
        return out.toString()
    }
}
