package dji.sampleV5.aircraft.pro.cartography

import dji.sampleV5.aircraft.pro.mission.MissionPlan

/**
 * The delivery document of a mapping flight.
 *
 * A reconstructed orthomosaic or DSM is only as good as the record of how it
 * was captured, and that record is normally lost. This builds a single JSON
 * file that travels with the imagery: payload, resolution, overlap, control,
 * coordinate reference, timing and every caveat the operator has to sign.
 *
 * It is a description of a plan, never a certificate: the block has not been
 * flown, and the product does not exist yet, when this file is written.
 */
object CartographyReportBuilder {

    const val SCHEMA = "djiflypro.cartography.report/v1"

    fun build(
        plan: MissionPlan,
        profile: CartographyProfile?,
        controlPoints: List<GroundControlPoint> = emptyList()
    ): String {
        val request = plan.request
        val summary = CartographicSummaryBuilder.build(plan, profile, controlPoints)
        val camera = profile?.camera ?: SurveyCamera.require(null)
        val reference = profile?.altitudeReference ?: AltitudeReference.ABOVE_GROUND
        val network = ControlNetwork.assess(
            points = controlPoints,
            blockCenterLatitude = request.centerLatitude,
            blockCenterLongitude = request.centerLongitude,
            blockRadiusMeters = ControlNetwork.blockRadiusMeters(request.lengthMeters, request.widthMeters)
        )
        val profileValidation = profile?.validate()

        return buildString {
            appendLine("{")
            appendLine("""  "schema": ${GeoJsonExporter.json(SCHEMA)},""")
            appendLine("""  "documentType": "flight_plan_report",""")
            appendLine("""  "status": "planned",""")
            appendLine("""  "generatedFrom": "DJIFlyPro",""")

            appendLine("""  "mission": {""")
            appendLine("""    "id": ${GeoJsonExporter.json(plan.id)},""")
            appendLine("""    "name": ${GeoJsonExporter.json(request.name)},""")
            appendLine("""    "createdAtEpochMs": ${plan.createdAtEpochMs},""")
            appendLine("""    "template": ${GeoJsonExporter.json(request.template.key)},""")
            appendLine("""    "routePattern": ${GeoJsonExporter.json(request.routePattern.key)},""")
            appendLine("""    "finishAction": ${GeoJsonExporter.json(request.finishAction.key)},""")
            appendLine("""    "sourceAlgorithmId": ${GeoJsonExporter.json(plan.sourceAlgorithmId)},""")
            appendLine("""    "centerLatitude": ${GeoJsonExporter.number(request.centerLatitude, 7)},""")
            appendLine("""    "centerLongitude": ${GeoJsonExporter.number(request.centerLongitude, 7)},""")
            appendLine("""    "bearingDegrees": ${GeoJsonExporter.number(request.bearingDegrees, 2)},""")
            appendLine("""    "blockLengthMeters": ${GeoJsonExporter.number(request.lengthMeters, 2)},""")
            appendLine("""    "blockWidthMeters": ${GeoJsonExporter.number(request.widthMeters, 2)},""")
            appendLine("""    "standoffMeters": ${GeoJsonExporter.number(request.standoffMeters, 2)}""")
            appendLine("  },")

            appendLine("""  "cartography": {""")
            appendLine("""    "cameraId": ${GeoJsonExporter.json(camera.id)},""")
            appendLine("""    "cameraName": ${GeoJsonExporter.json(camera.displayName)},""")
            appendLine("""    "sensorWidthMm": ${GeoJsonExporter.number(camera.sensorWidthMillimeters, 2)},""")
            appendLine("""    "sensorHeightMm": ${GeoJsonExporter.number(camera.sensorHeightMillimeters, 2)},""")
            appendLine("""    "focalLengthMm": ${GeoJsonExporter.number(camera.focalLengthMillimeters, 2)},""")
            appendLine("""    "imageWidthPx": ${camera.imageWidthPixels},""")
            appendLine("""    "imageHeightPx": ${camera.imageHeightPixels},""")
            appendLine("""    "horizontalFovDegrees": ${GeoJsonExporter.number(camera.horizontalFovDegrees, 2)},""")
            appendLine("""    "verticalFovDegrees": ${GeoJsonExporter.number(camera.verticalFovDegrees, 2)},""")
            appendLine("""    "targetGsdCmPerPx": ${GeoJsonExporter.number(profile?.targetGsdCentimetersPerPixel ?: 0.0, 2)},""")
            appendLine("""    "achievedGsdCmPerPx": ${GeoJsonExporter.number(summary.achievedGsdCentimetersPerPixel, 2)},""")
            appendLine("""    "footprintWidthMeters": ${GeoJsonExporter.number(summary.footprintWidthMeters, 2)},""")
            appendLine("""    "footprintDepthMeters": ${GeoJsonExporter.number(summary.footprintDepthMeters, 2)},""")
            appendLine("""    "forwardOverlapPercent": ${profile?.forwardOverlapPercent ?: (100 - request.overlapPercent)},""")
            appendLine("""    "sideOverlapPercent": ${profile?.sideOverlapPercent ?: (100 - request.overlapPercent)},""")
            appendLine("""    "declaredOverlapPercent": ${request.overlapPercent},""")
            appendLine("""    "lineSpacingMeters": ${GeoJsonExporter.number(request.lineSpacingMeters, 3)},""")
            appendLine("""    "photoSpacingMeters": ${GeoJsonExporter.number(request.photoSpacingMeters, 3)},""")
            appendLine("""    "achievedRedundancyFactor": ${GeoJsonExporter.number(summary.redundancyFactor, 3)},""")
            appendLine("""    "flightAltitudeMeters": ${GeoJsonExporter.number(request.altitudeMeters, 2)},""")
            appendLine("""    "altitudeReference": ${GeoJsonExporter.json(reference.key)},""")
            appendLine("""    "terrainFollowing": ${profile?.terrainFollowing ?: false},""")
            appendLine("""    "crossTrack": ${profile?.crossTrack ?: false},""")
            appendLine("""    "gimbalPitchDegrees": ${GeoJsonExporter.number(request.gimbalPitchDegrees, 1)},""")
            appendLine("""    "speedMps": ${GeoJsonExporter.number(request.speedMps, 2)}""")
            appendLine("  },")

            appendLine("""  "coverage": {""")
            appendLine("""    "areaSquareMeters": ${GeoJsonExporter.number(summary.areaSquareMeters, 2)},""")
            appendLine("""    "areaHectares": ${GeoJsonExporter.number(summary.areaHectares, 4)},""")
            appendLine("""    "waypointCount": ${plan.waypoints.size},""")
            appendLine("""    "photoCount": ${summary.photoCount},""")
            appendLine("""    "flightLineCount": ${summary.flightLineCount},""")
            appendLine("""    "totalDistanceMeters": ${GeoJsonExporter.number(plan.totalDistanceMeters, 2)},""")
            appendLine("""    "estimatedDurationSeconds": ${GeoJsonExporter.number(plan.estimatedDurationSeconds, 1)},""")
            appendLine("""    "estimatedStorageGigabytesJpeg": ${GeoJsonExporter.number(summary.estimatedStorageGigabytes(8.0), 2)},""")
            appendLine("""    "estimatedStorageGigabytesRaw": ${GeoJsonExporter.number(summary.estimatedStorageGigabytes(45.0), 2)}""")
            appendLine("  },")

            appendLine("""  "referenceSystem": {""")
            appendLine("""    "geographic": "EPSG:4326",""")
            summary.utm?.let { utm ->
                appendLine("""    "projected": ${GeoJsonExporter.json(utm.crsLabel)},""")
                appendLine("""    "utmZone": ${utm.zone},""")
                appendLine("""    "utmHemisphere": ${GeoJsonExporter.json(utm.hemisphere.toString())},""")
                appendLine("""    "utmEastingMeters": ${GeoJsonExporter.number(utm.eastingMeters, 3)},""")
                appendLine("""    "utmNorthingMeters": ${GeoJsonExporter.number(utm.northingMeters, 3)},""")
                appendLine("""    "centralMeridianDegrees": ${GeoJsonExporter.number(utm.centralMeridianDegrees, 2)},""")
                appendLine("""    "scaleFactor": ${GeoJsonExporter.number(utm.scaleFactor, 6)}""")
            } ?: appendLine("""    "projected": null""")
            appendLine("  },")

            appendLine("""  "groundControl": {""")
            appendLine("""    "pointCount": ${controlPoints.size},""")
            appendLine("""    "controlCount": ${network.metrics.controlCount},""")
            appendLine("""    "checkCount": ${network.metrics.checkCount},""")
            appendLine("""    "blockCoverageRatio": ${GeoJsonExporter.number(network.metrics.blockCoverageRatio, 3)},""")
            appendLine("""    "blockRadiusMeters": ${GeoJsonExporter.number(ControlNetwork.blockRadiusMeters(request.lengthMeters, request.widthMeters), 2)},""")
            appendLine("""    "maxControlRadiusMeters": ${GeoJsonExporter.number(network.metrics.maxControlRadiusMeters, 2)},""")
            appendLine("""    "isUsable": ${network.isValid},""")
            appendLine("""    "points": [""")
            if (controlPoints.isEmpty()) {
                appendLine("""      null""")
            } else {
                controlPoints.forEachIndexed { index, point ->
                    val utm = point.utm()
                    val separator = if (index == controlPoints.lastIndex) "" else ","
                    appendLine(
                        """      {"id": ${GeoJsonExporter.json(point.id)}, "code": ${GeoJsonExporter.json(point.code)}, """ +
                            """"role": ${GeoJsonExporter.json(point.role.key)}, "target": ${GeoJsonExporter.json(point.target.key)}, """ +
                            """"latitude": ${GeoJsonExporter.number(point.latitude, 7)}, "longitude": ${GeoJsonExporter.number(point.longitude, 7)}, """ +
                            """"heightMeters": ${point.heightMeters?.let { GeoJsonExporter.number(it, 3) } ?: "null"}, """ +
                            """"horizontalAccuracyMm": ${point.horizontalAccuracyMillimeters?.let { GeoJsonExporter.number(it, 1) } ?: "null"}, """ +
                            """"targetAccuracyMm": ${GeoJsonExporter.number(point.target.typicalAccuracyMillimeters, 1)}, """ +
                            """"source": ${GeoJsonExporter.json(point.source)}, """ +
                            """"utmEastingMeters": ${GeoJsonExporter.number(utm.eastingMeters, 3)}, """ +
                            """"utmNorthingMeters": ${GeoJsonExporter.number(utm.northingMeters, 3)}, "epsg": ${utm.epsgCode}}$separator"""
                    )
                }
            }
            appendLine("    ]")
            appendLine("  },")

            appendLine("""  "checks": {""")
            appendLine("""    "profileValid": ${profileValidation?.isValid ?: false},""")
            appendLine("""    "profileErrors": ${stringList(profileValidation?.errors ?: listOf("Este plan no tiene perfil cartográfico"))},""")
            appendLine("""    "controlErrors": ${stringList(network.errors)},""")
            appendLine("""    "controlWarnings": ${stringList(network.warnings)},""")
            appendLine("""    "planWarnings": ${stringList(plan.warnings)},""")
            appendLine("""    "notes": ${stringList(summary.notes)}""")
            appendLine("  },")

            appendLine("""  "limitations": [""")
            appendLine("""    "Este documento describe una planificación. El bloque no se ha volado y todavía no existe ortofotografía ni imagen.",""")
            appendLine("""    "La resolución de suelo se cumple sobre terreno de altura conocida. El relieve desplaza la resolución efectiva.",""")
            appendLine("""    "La exactitud de un producto reconstruido la limita su control terrestre, no esta planificación.",""")
            appendLine("""    "Este documento no comprueba espacio aéreo, obstáculos, personas, permisos ni normativa local."""")
            appendLine("  ]")
            append("}")
        }
    }

    private fun stringList(values: List<String>): String =
        if (values.isEmpty()) "[]" else values.joinToString(", ", "[", "]") { GeoJsonExporter.json(it) }
}
