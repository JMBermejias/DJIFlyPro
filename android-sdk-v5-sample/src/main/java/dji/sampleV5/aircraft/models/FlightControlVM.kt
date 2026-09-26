package dji.sampleV5.aircraft.models

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.key.RemoteControllerKey
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.EmptyMsg
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.et.action
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.manager.KeyManager

class FlightControlVM : DJIViewModel() {
    private val safetyPolicy = FlightSafetyPolicy()
    private val _telemetry = MutableLiveData(FlightTelemetry())
    private val _lastDecision = MutableLiveData(
        safetyPolicy.evaluate(FlightCommand.TAKEOFF, FlightTelemetry())
    )

    val telemetry: LiveData<FlightTelemetry> = _telemetry
    val lastDecision: LiveData<FlightSafetyDecision> = _lastDecision

    init {
        listenTelemetry()
    }

    fun setRegistered(registered: Boolean) {
        updateTelemetry { it.copy(registered = registered) }
    }

    fun evaluate(command: FlightCommand): FlightSafetyDecision {
        val decision = safetyPolicy.evaluate(command, _telemetry.value ?: FlightTelemetry())
        _lastDecision.postValue(decision)
        return decision
    }

    fun takeOff(callback: (Boolean, String) -> Unit) {
        execute(
            FlightCommand.TAKEOFF,
            callback,
            { commandCallback ->
                FlightControllerKey.KeyStartTakeoff.create().action(
                    { commandCallback.onSuccess(it) },
                    { commandCallback.onFailure(it) }
                )
            }
        )
    }

    fun land(callback: (Boolean, String) -> Unit) {
        execute(
            FlightCommand.LAND,
            callback,
            { commandCallback ->
                FlightControllerKey.KeyStartAutoLanding.create().action(
                    { commandCallback.onSuccess(it) },
                    { commandCallback.onFailure(it) }
                )
            }
        )
    }

    fun returnHome(callback: (Boolean, String) -> Unit) {
        execute(
            FlightCommand.RETURN_HOME,
            callback,
            { commandCallback ->
                FlightControllerKey.KeyStartGoHome.create().action(
                    { commandCallback.onSuccess(it) },
                    { commandCallback.onFailure(it) }
                )
            }
        )
    }

    fun emergencyLand(callback: (Boolean, String) -> Unit) {
        execute(
            FlightCommand.EMERGENCY_LAND,
            callback,
            { commandCallback ->
                FlightControllerKey.KeyStartAutoLanding.create().action(
                    { commandCallback.onSuccess(it) },
                    { commandCallback.onFailure(it) }
                )
            }
        )
    }

    fun canUseVirtualStick(): Boolean = evaluate(FlightCommand.MANUAL_CONTROL).allowed

    private fun execute(
        command: FlightCommand,
        callback: (Boolean, String) -> Unit,
        action: (CommonCallbacks.CompletionCallbackWithParam<EmptyMsg>) -> Unit
    ) {
        val decision = evaluate(command)
        if (!decision.allowed) {
            callback(false, decision.reason.name)
            return
        }
        action(object : CommonCallbacks.CompletionCallbackWithParam<EmptyMsg> {
            override fun onSuccess(t: EmptyMsg?) {
                callback(true, "Command accepted")
            }

            override fun onFailure(error: IDJIError) {
                callback(false, error.description())
            }
        })
    }

    private fun listenTelemetry() {
        FlightControllerKey.KeyConnection.create().listen(this) { connected ->
            updateTelemetry { it.copy(aircraftConnected = connected == true) }
        }
        RemoteControllerKey.KeyConnection.create().listen(this) { connected ->
            updateTelemetry { it.copy(remoteControllerConnected = connected == true) }
        }
        FlightControllerKey.KeyIsHomeLocationSet.create().listen(this) { homeSet ->
            updateTelemetry { it.copy(homeLocationSet = homeSet == true) }
        }
        FlightControllerKey.KeyGPSSignalLevel.create().listen(this) { signal ->
            val level = signal?.name?.removePrefix("LEVEL_")?.toIntOrNull() ?: 0
            updateTelemetry { it.copy(gpsSignalLevel = level) }
        }
        FlightControllerKey.KeyIsFlying.create().listen(this) { flying ->
            updateTelemetry { it.copy(isFlying = flying == true) }
        }
        FlightControllerKey.KeyAreMotorsOn.create().listen(this) { motorsOn ->
            updateTelemetry { it.copy(motorsOn = motorsOn == true) }
        }
        FlightControllerKey.KeyIsLowBatteryWarning.create().listen(this) { warning ->
            updateTelemetry { it.copy(lowBatteryWarning = warning == true) }
        }
        FlightControllerKey.KeyIsSeriousLowBatteryWarning.create().listen(this) { warning ->
            updateTelemetry { it.copy(seriousLowBatteryWarning = warning == true) }
        }
        FlightControllerKey.KeyAltitude.create().listen(this) { altitude ->
            updateTelemetry { it.copy(altitudeMeters = altitude ?: 0.0) }
        }
        FlightControllerKey.KeyAircraftLocation3D.create().listen(this) { location ->
            updateTelemetry {
                it.copy(
                    latitude = location?.latitude,
                    longitude = location?.longitude
                )
            }
        }
        KeyTools.createKey(
            BatteryKey.KeyChargeRemainingInPercent,
            ComponentIndexType.LEFT_OR_MAIN
        ).listen(this) { battery ->
            updateTelemetry { it.copy(batteryPercent = battery ?: -1) }
        }
    }

    private fun updateTelemetry(transform: (FlightTelemetry) -> FlightTelemetry) {
        _telemetry.postValue(transform(_telemetry.value ?: FlightTelemetry()))
    }

    override fun onCleared() {
        KeyManager.getInstance().cancelListen(this)
        super.onCleared()
    }
}
