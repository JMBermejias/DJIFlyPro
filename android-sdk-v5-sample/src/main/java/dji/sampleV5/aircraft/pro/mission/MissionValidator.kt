package dji.sampleV5.aircraft.pro.mission

import kotlin.math.abs

object MissionValidator {
    const val MAX_WAYPOINTS = 900
    const val MAX_ALTITUDE_METERS = 120.0
    const val MAX_SPEED_MPS = 15.0

    fun validate(request: MissionRequest, plan: MissionPlan? = null): MissionValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (request.name.isBlank() || request.name.length > 80) {
            errors += "Mission name must contain between 1 and 80 characters"
        }
        if (!request.centerLatitude.isFinite() || request.centerLatitude !in -90.0..90.0) {
            errors += "Latitude must be a finite WGS84 value between -90 and 90"
        }
        if (!request.centerLongitude.isFinite() || request.centerLongitude !in -180.0..180.0) {
            errors += "Longitude must be a finite WGS84 value between -180 and 180"
        }
        if (!request.bearingDegrees.isFinite()) {
            errors += "Bearing must be a finite value"
        }
        checkPositive(request.lengthMeters, "Length", errors)
        checkPositive(request.widthMeters, "Width", errors)
        checkPositive(request.heightMeters, "Height", errors)
        checkPositive(request.lineSpacingMeters, "Line spacing", errors)
        checkPositive(request.photoSpacingMeters, "Photo spacing", errors)
        if (request.lengthMeters > 5_000.0 || request.widthMeters > 5_000.0) {
            errors += "Length and width are limited to 5 km per side"
        }
        if (request.lineSpacingMeters < 0.5 || request.lineSpacingMeters > 500.0) {
            errors += "Line spacing must be between 0.5 m and 500 m"
        }
        if (request.photoSpacingMeters < 0.5 || request.photoSpacingMeters > 100.0) {
            errors += "Photo spacing must be between 0.5 m and 100 m"
        }
        if (request.altitudeMeters !in 2.0..MAX_ALTITUDE_METERS) {
            errors += "Altitude must be between 2 m and ${MAX_ALTITUDE_METERS.toInt()} m"
        }
        if (request.speedMps !in 1.0..MAX_SPEED_MPS) {
            errors += "Speed must be between 1 and ${MAX_SPEED_MPS.toInt()} m/s"
        }
        if (request.overlapPercent !in 10..90) {
            errors += "Overlap must be between 10% and 90%"
        }
        if (!request.standoffMeters.isFinite() || request.standoffMeters < 0.5 || request.standoffMeters > 500.0) {
            errors += "Facade stand-off must be between 0.5 m and 500 m"
        }
        if (!request.gimbalPitchDegrees.isFinite() || request.gimbalPitchDegrees !in -90.0..30.0) {
            errors += "Gimbal pitch must be between -90° and 30°"
        }
        if (request.template == MissionTemplate.FACADE && request.heightMeters > 300.0) {
            errors += "Facade height is limited to 300 m in this release"
        }

        if (plan != null) {
            if (plan.schemaVersion != 1) errors += "Unsupported mission schema version"
            if (plan.id.isBlank() || plan.id.length > 120) errors += "Mission id is invalid"
            if (plan.createdAtEpochMs < 0L) errors += "Mission timestamp is invalid"
            if (plan.waypoints.isEmpty()) errors += "Mission has no waypoints"
            if (plan.waypoints.size > MAX_WAYPOINTS) errors += "Mission has too many waypoints"
            if (!plan.totalDistanceMeters.isFinite() || plan.totalDistanceMeters < 0.0) {
                errors += "Mission distance is invalid"
            } else if (plan.totalDistanceMeters > 200_000.0) {
                errors += "Mission route exceeds the 200 km safety limit"
            }
            if (!plan.estimatedDurationSeconds.isFinite() || plan.estimatedDurationSeconds < 0.0) {
                errors += "Mission duration is invalid"
            }
            if (plan.waypoints.any {
                    !it.latitude.isFinite() || it.latitude !in -90.0..90.0 ||
                        !it.longitude.isFinite() || it.longitude !in -180.0..180.0
                }) {
                errors += "Mission contains an invalid coordinate"
            }
            if (plan.waypoints.any {
                    !it.heightMeters.isFinite() || it.heightMeters !in 2.0..MAX_ALTITUDE_METERS ||
                        !it.speedMps.isFinite() || it.speedMps !in 1.0..MAX_SPEED_MPS ||
                        !it.pitchDegrees.isFinite() || it.pitchDegrees !in -90.0..30.0 ||
                        it.hoverSeconds !in 0..3_600
                }) {
                errors += "Mission contains an invalid waypoint property"
            }
            if (plan.waypoints.firstOrNull()?.index != 0 ||
                plan.waypoints.zipWithNext().any { (a, b) -> a.index + 1 != b.index }
            ) {
                errors += "Waypoint indexes are not consecutive"
            }
        }
        return MissionValidation(errors, warnings + (plan?.warnings ?: emptyList()))
    }

    private fun checkPositive(value: Double, label: String, errors: MutableList<String>) {
        if (!value.isFinite() || value <= 0.0) errors += "$label must be greater than zero"
    }
}
