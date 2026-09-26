package dji.sampleV5.aircraft.pro.algorithm

import dji.sampleV5.aircraft.pro.mission.MissionRequest
import dji.sampleV5.aircraft.pro.mission.MissionTemplate
import dji.sampleV5.aircraft.pro.mission.RoutePattern

/**
 * A recipe is data, not executable code. It is intentionally restricted to a
 * small, auditable set of route and capture operations.
 */
data class AlgorithmRecipe(
    val schema: String = SCHEMA,
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val jobType: String,
    val description: String,
    val template: MissionTemplate,
    val routePattern: RoutePattern = RoutePattern.PARALLEL,
    val defaults: AlgorithmDefaults = AlgorithmDefaults(),
    val outputs: List<String> = listOf("KMZ", "mission.json", "audit.jsonl")
) {
    companion object {
        const val SCHEMA = "djiflypro.algorithm/v1"
    }
}

data class AlgorithmDefaults(
    val lengthMeters: Double = 80.0,
    val widthMeters: Double = 50.0,
    val heightMeters: Double = 40.0,
    val bearingDegrees: Double = 0.0,
    val altitudeMeters: Double = 40.0,
    val standoffMeters: Double = 12.0,
    val lineSpacingMeters: Double = 12.0,
    val photoSpacingMeters: Double = 8.0,
    val overlapPercent: Int = 70,
    val speedMps: Double = 4.0,
    val gimbalPitchDegrees: Double = -90.0
) {
    fun applyTo(
        request: MissionRequest,
        template: MissionTemplate,
        routePattern: RoutePattern
    ): MissionRequest = request.copy(
        lengthMeters = lengthMeters,
        widthMeters = widthMeters,
        heightMeters = heightMeters,
        bearingDegrees = bearingDegrees,
        altitudeMeters = altitudeMeters,
        standoffMeters = standoffMeters,
        lineSpacingMeters = lineSpacingMeters,
        photoSpacingMeters = photoSpacingMeters,
        overlapPercent = overlapPercent,
        speedMps = speedMps,
        gimbalPitchDegrees = gimbalPitchDegrees,
        template = template,
        routePattern = routePattern
    )
}
