package dji.sampleV5.aircraft.models

enum class FlightCommand {
    TAKEOFF,
    LAND,
    RETURN_HOME,
    MANUAL_CONTROL,
    EMERGENCY_LAND
}

enum class FlightSafetyReason {
    READY,
    NOT_REGISTERED,
    AIRCRAFT_DISCONNECTED,
    REMOTE_CONTROLLER_DISCONNECTED,
    HOME_NOT_SET,
    GPS_TOO_WEAK,
    BATTERY_UNKNOWN,
    BATTERY_TOO_LOW,
    LOW_BATTERY_WARNING,
    MOTORS_ALREADY_ON,
    NOT_FLYING
}

data class FlightTelemetry(
    val registered: Boolean = false,
    val aircraftConnected: Boolean = false,
    val remoteControllerConnected: Boolean = false,
    val homeLocationSet: Boolean = false,
    val gpsSignalLevel: Int = 0,
    val batteryPercent: Int = -1,
    val lowBatteryWarning: Boolean = false,
    val seriousLowBatteryWarning: Boolean = false,
    val isFlying: Boolean = false,
    val motorsOn: Boolean = false,
    val altitudeMeters: Double = 0.0,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class FlightSafetyDecision(
    val allowed: Boolean,
    val reason: FlightSafetyReason
)

class FlightSafetyPolicy(
    private val minimumTakeoffBatteryPercent: Int = 30,
    private val minimumControlBatteryPercent: Int = 10,
    private val minimumTakeoffGpsLevel: Int = 3,
    private val minimumReturnHomeGpsLevel: Int = 2
) {
    fun evaluate(command: FlightCommand, telemetry: FlightTelemetry): FlightSafetyDecision {
        if (!telemetry.registered) {
            return denied(FlightSafetyReason.NOT_REGISTERED)
        }
        if (!telemetry.aircraftConnected) {
            return denied(FlightSafetyReason.AIRCRAFT_DISCONNECTED)
        }
        if (command == FlightCommand.EMERGENCY_LAND) {
            return if (telemetry.isFlying) {
                allowed()
            } else {
                denied(FlightSafetyReason.NOT_FLYING)
            }
        }
        if (!telemetry.remoteControllerConnected) {
            return denied(FlightSafetyReason.REMOTE_CONTROLLER_DISCONNECTED)
        }
        return when (command) {
            FlightCommand.TAKEOFF -> evaluateTakeoff(telemetry)
            FlightCommand.LAND -> evaluateLanding(telemetry)
            FlightCommand.RETURN_HOME -> evaluateReturnHome(telemetry)
            FlightCommand.MANUAL_CONTROL -> evaluateManualControl(telemetry)
            FlightCommand.EMERGENCY_LAND -> allowed()
        }
    }

    private fun evaluateTakeoff(telemetry: FlightTelemetry): FlightSafetyDecision {
        if (!telemetry.homeLocationSet) {
            return denied(FlightSafetyReason.HOME_NOT_SET)
        }
        if (telemetry.gpsSignalLevel < minimumTakeoffGpsLevel) {
            return denied(FlightSafetyReason.GPS_TOO_WEAK)
        }
        if (telemetry.batteryPercent < 0) {
            return denied(FlightSafetyReason.BATTERY_UNKNOWN)
        }
        if (telemetry.batteryPercent < minimumTakeoffBatteryPercent) {
            return denied(FlightSafetyReason.BATTERY_TOO_LOW)
        }
        if (telemetry.lowBatteryWarning || telemetry.seriousLowBatteryWarning) {
            return denied(FlightSafetyReason.LOW_BATTERY_WARNING)
        }
        if (telemetry.isFlying || telemetry.motorsOn) {
            return denied(FlightSafetyReason.MOTORS_ALREADY_ON)
        }
        return allowed()
    }

    private fun evaluateLanding(telemetry: FlightTelemetry): FlightSafetyDecision {
        if (!telemetry.isFlying) {
            return denied(FlightSafetyReason.NOT_FLYING)
        }
        if (telemetry.batteryPercent < 0) {
            return denied(FlightSafetyReason.BATTERY_UNKNOWN)
        }
        return allowed()
    }

    private fun evaluateReturnHome(telemetry: FlightTelemetry): FlightSafetyDecision {
        if (!telemetry.isFlying) {
            return denied(FlightSafetyReason.NOT_FLYING)
        }
        if (!telemetry.homeLocationSet) {
            return denied(FlightSafetyReason.HOME_NOT_SET)
        }
        if (telemetry.gpsSignalLevel < minimumReturnHomeGpsLevel) {
            return denied(FlightSafetyReason.GPS_TOO_WEAK)
        }
        if (telemetry.batteryPercent < 0) {
            return denied(FlightSafetyReason.BATTERY_UNKNOWN)
        }
        return allowed()
    }

    private fun evaluateManualControl(telemetry: FlightTelemetry): FlightSafetyDecision {
        if (!telemetry.isFlying) {
            return denied(FlightSafetyReason.NOT_FLYING)
        }
        if (telemetry.batteryPercent < 0) {
            return denied(FlightSafetyReason.BATTERY_UNKNOWN)
        }
        if (telemetry.batteryPercent < minimumControlBatteryPercent) {
            return denied(FlightSafetyReason.BATTERY_TOO_LOW)
        }
        return allowed()
    }

    private fun allowed() = FlightSafetyDecision(true, FlightSafetyReason.READY)

    private fun denied(reason: FlightSafetyReason) = FlightSafetyDecision(false, reason)
}
