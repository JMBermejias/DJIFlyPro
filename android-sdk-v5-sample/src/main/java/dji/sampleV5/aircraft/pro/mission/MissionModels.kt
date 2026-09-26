package dji.sampleV5.aircraft.pro.mission

/** The route families supported by the first DJIFlyPro planner release. */
enum class MissionTemplate(val key: String, val displayName: String) {
    FACADE("facade", "Fachada"),
    ROOF("roof", "Cubierta"),
    SOLAR("solar", "Planta solar"),
    FIELD("field", "Campo"),
    GRID("grid", "Malla genérica");

    companion object {
        fun fromKey(value: String): MissionTemplate =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: GRID
    }
}

enum class RoutePattern(val key: String, val displayName: String) {
    PARALLEL("parallel", "Líneas paralelas"),
    GRID("grid", "Malla doble");

    companion object {
        fun fromKey(value: String): RoutePattern =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: PARALLEL
    }
}

enum class FinishAction(val key: String, val displayName: String) {
    RETURN_HOME("return_home", "Regreso a casa"),
    HOVER("hover", "Mantener posición"),
    LAND("land", "Aterrizaje");

    companion object {
        fun fromKey(value: String): FinishAction =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: RETURN_HOME
    }
}

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class PlannedWaypoint(
    val index: Int,
    val latitude: Double,
    val longitude: Double,
    val heightMeters: Double,
    val speedMps: Double,
    val takePhoto: Boolean = true,
    val hoverSeconds: Int = 0,
    val pitchDegrees: Double = -90.0
)

data class MissionRequest(
    val name: String,
    val template: MissionTemplate,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val lengthMeters: Double,
    val widthMeters: Double,
    val heightMeters: Double,
    val bearingDegrees: Double,
    val altitudeMeters: Double,
    val standoffMeters: Double,
    val lineSpacingMeters: Double,
    val photoSpacingMeters: Double,
    val overlapPercent: Int,
    val speedMps: Double,
    val routePattern: RoutePattern,
    val finishAction: FinishAction,
    val gimbalPitchDegrees: Double = -90.0
)

data class MissionPlan(
    val schemaVersion: Int = 1,
    val id: String,
    val createdAtEpochMs: Long,
    val request: MissionRequest,
    val waypoints: List<PlannedWaypoint>,
    val totalDistanceMeters: Double,
    val estimatedDurationSeconds: Double,
    val warnings: List<String>,
    val sourceAlgorithmId: String? = null
)

data class MissionValidation(
    val errors: List<String>,
    val warnings: List<String>
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

data class MissionSummary(
    val waypointCount: Int,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val photoCount: Int
)
