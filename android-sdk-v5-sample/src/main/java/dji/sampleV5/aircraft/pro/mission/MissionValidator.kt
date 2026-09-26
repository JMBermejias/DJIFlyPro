package dji.sampleV5.aircraft.pro.mission

import kotlin.math.abs

object MissionValidator {
    const val MAX_WAYPOINTS = 900

    /**
     * DJI's default ceiling for an automatic waypoint mission. Nothing above
     * this can be uploaded or started automatically, whatever else is allowed.
     */
    const val MAX_ALTITUDE_METERS = 120.0

    /**
     * Ceiling for a route a person flies by hand, guided by the app. Mapping
     * payloads need real height to reach a useful ground sample distance, and
     * a human flying the aircraft is what makes that safe, so the route may be
     * planned higher than it may ever be automated.
     */
    const val MAX_GUIDED_ALTITUDE_METERS = 500.0

    const val MAX_SPEED_MPS = 15.0

    /**
     * Everything that has to hold for the route to exist, be saved, or be
     * offered for manual guidance.
     */
    @JvmOverloads
    fun validate(
        request: MissionRequest,
        plan: MissionPlan? = null,
        altitudeCeiling: Double = MAX_GUIDED_ALTITUDE_METERS
    ): MissionValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (request.name.isBlank() || request.name.length > 80) {
            errors += "El nombre de la misión debe tener entre 1 y 80 caracteres"
        }
        if (!request.centerLatitude.isFinite() || request.centerLatitude !in -90.0..90.0) {
            errors += "La latitud debe ser un valor WGS84 finito entre -90 y 90"
        }
        if (!request.centerLongitude.isFinite() || request.centerLongitude !in -180.0..180.0) {
            errors += "La longitud debe ser un valor WGS84 finito entre -180 y 180"
        }
        if (!request.bearingDegrees.isFinite()) {
            errors += "El rumbo debe ser un valor finito"
        }
        checkPositive(request.lengthMeters, "La longitud", errors)
        checkPositive(request.widthMeters, "El ancho", errors)
        checkPositive(request.heightMeters, "La altura", errors)
        checkPositive(request.lineSpacingMeters, "La separación entre líneas", errors)
        checkPositive(request.photoSpacingMeters, "La separación entre fotos", errors)
        if (request.lengthMeters > 5_000.0 || request.widthMeters > 5_000.0) {
            errors += "La longitud y el ancho están limitados a 5 km por lado"
        }
        if (request.lineSpacingMeters < 0.5 || request.lineSpacingMeters > 500.0) {
            errors += "La separación entre líneas debe estar entre 0,5 m y 500 m"
        }
        if (request.photoSpacingMeters < 0.5 || request.photoSpacingMeters > 100.0) {
            errors += "La separación entre fotos debe estar entre 0,5 m y 100 m"
        }
        if (request.altitudeMeters !in 2.0..altitudeCeiling) {
            errors += "La altura de vuelo debe estar entre 2 m y ${altitudeCeiling.toInt()} m"
        }
        if (request.altitudeMeters > MAX_ALTITUDE_METERS) {
            warnings += "Por encima de ${MAX_ALTITUDE_METERS.toInt()} m este bloque no puede ejecutarse como misión automática; " +
                "hay que volarlo con guía manual."
        }
        if (request.speedMps !in 1.0..MAX_SPEED_MPS) {
            errors += "La velocidad debe estar entre 1 y ${MAX_SPEED_MPS.toInt()} m/s"
        }
        if (request.overlapPercent !in 10..90) {
            errors += "El solape debe estar entre 10 % y 90 %"
        }
        if (!request.standoffMeters.isFinite() || request.standoffMeters < 0.5 || request.standoffMeters > 500.0) {
            errors += "La distancia de seguridad a la fachada debe estar entre 0,5 m y 500 m"
        }
        if (!request.gimbalPitchDegrees.isFinite() || request.gimbalPitchDegrees !in -90.0..30.0) {
            errors += "La inclinación del estabilizador debe estar entre -90° y 30°"
        }
        if (request.template == MissionTemplate.FACADE && request.heightMeters > 300.0) {
            errors += "La altura de fachada está limitada a 300 m en esta versión"
        }

        if (plan != null) {
            if (plan.schemaVersion != 1) errors += "Versión del esquema de misión no admitida"
            if (plan.id.isBlank() || plan.id.length > 120) errors += "El identificador de la misión no es válido"
            if (plan.createdAtEpochMs < 0L) errors += "La fecha de la misión no es válida"
            if (plan.waypoints.isEmpty()) errors += "La misión no tiene puntos de vuelo"
            if (plan.waypoints.size > MAX_WAYPOINTS) errors += "La misión tiene demasiados puntos de vuelo"
            if (!plan.totalDistanceMeters.isFinite() || plan.totalDistanceMeters < 0.0) {
                errors += "La longitud de la ruta no es válida"
            } else if (plan.totalDistanceMeters > 200_000.0) {
                errors += "La ruta supera el límite de seguridad de 200 km"
            }
            if (!plan.estimatedDurationSeconds.isFinite() || plan.estimatedDurationSeconds < 0.0) {
                errors += "La duración estimada no es válida"
            }
            if (plan.waypoints.any {
                    !it.latitude.isFinite() || it.latitude !in -90.0..90.0 ||
                        !it.longitude.isFinite() || it.longitude !in -180.0..180.0
                }) {
                errors += "La misión contiene una coordenada no válida"
            }
            if (plan.waypoints.any {
                    !it.heightMeters.isFinite() || it.heightMeters !in 2.0..altitudeCeiling ||
                        !it.speedMps.isFinite() || it.speedMps !in 1.0..MAX_SPEED_MPS ||
                        !it.pitchDegrees.isFinite() || it.pitchDegrees !in -90.0..30.0 ||
                        it.hoverSeconds !in 0..3_600
                }) {
                errors += "La misión contiene un punto de vuelo con propiedades no válidas"
            }
            if (plan.waypoints.firstOrNull()?.index != 0 ||
                plan.waypoints.zipWithNext().any { (a, b) -> a.index + 1 != b.index }
            ) {
                errors += "Los índices de los puntos de vuelo no son consecutivos"
            }
        }
        return MissionValidation(errors, warnings + (plan?.warnings ?: emptyList()))
    }

    /**
     * The extra check an automatic waypoint mission has to pass on top of
     * [validate]. Called again immediately before an upload and immediately
     * before a start, never trusted from an earlier moment.
     */
    fun validateAutomaticMission(request: MissionRequest, plan: MissionPlan? = null): MissionValidation {
        val base = validate(request, plan)
        if (request.altitudeMeters > MAX_ALTITUDE_METERS) {
            return MissionValidation(
                errors = base.errors + "Las misiones waypoint automáticas están limitadas a ${MAX_ALTITUDE_METERS.toInt()} m " +
                    "en esta versión. Vuela este bloque con guía manual o elige una resolución más gruesa.",
                warnings = base.warnings
            )
        }
        return base
    }

    private fun checkPositive(value: Double, label: String, errors: MutableList<String>) {
        if (!value.isFinite() || value <= 0.0) errors += "$label debe ser mayor que cero"
    }
}
