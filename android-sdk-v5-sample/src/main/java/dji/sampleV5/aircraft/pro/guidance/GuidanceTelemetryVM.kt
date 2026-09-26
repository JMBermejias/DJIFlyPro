package dji.sampleV5.aircraft.pro.guidance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dji.sdk.keyvalue.key.BatteryKey
import dji.sdk.keyvalue.key.FlightControllerKey
import dji.sdk.keyvalue.key.KeyTools
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.sdk.keyvalue.value.common.LocationCoordinate2D
import dji.sdk.keyvalue.value.common.LocationCoordinate3D
import dji.v5.et.create
import dji.v5.et.listen
import dji.v5.manager.KeyManager

/**
 * Telemetry for manual waypoint guidance.
 *
 * Read-only by construction: this class registers no action keys, so nothing
 * here can move the aircraft. That is the point. On a model whose firmware has
 * no waypoint missions, the app guides and the operator flies.
 */
class GuidanceTelemetryVM : ViewModel() {

    private val keyManager = KeyManager.getInstance()

    private val _position = MutableLiveData<GuidancePosition?>(null)
    val position: LiveData<GuidancePosition?> = _position

    private val _homePosition = MutableLiveData<GuidancePosition?>(null)
    val homePosition: LiveData<GuidancePosition?> = _homePosition

    private val _altitudeMeters = MutableLiveData<Double?>(null)
    val altitudeMeters: LiveData<Double?> = _altitudeMeters

    /** Compass heading in degrees, 0 = true north, clockwise. */
    private val _compassHeadingDegrees = MutableLiveData<Double?>(null)
    val compassHeadingDegrees: LiveData<Double?> = _compassHeadingDegrees

    private val _batteryPercent = MutableLiveData<Int>(-1)
    val batteryPercent: LiveData<Int> = _batteryPercent

    private val _gpsSignalLevel = MutableLiveData<Int>(0)
    val gpsSignalLevel: LiveData<Int> = _gpsSignalLevel

    private val _aircraftConnected = MutableLiveData(false)
    val aircraftConnected: LiveData<Boolean> = _aircraftConnected

    private val _listening = MutableLiveData(false)
    val listening: LiveData<Boolean> = _listening

    fun start() {
        if (_listening.value == true) return
        // A failure to attach one listener must not silently look like a
        // working guidance display, so the result is reported as a flag.
        val attached = runCatching {
            FlightControllerKey.KeyConnection.create()
                .listen(this) { connected -> _aircraftConnected.postValue(connected == true) }
            FlightControllerKey.KeyAircraftLocation3D.create()
                .listen(this) { location -> _position.postValue(location.toPosition()) }
            FlightControllerKey.KeyHomeLocation.create()
                .listen(this) { home -> _homePosition.postValue(home.toPosition()) }
            FlightControllerKey.KeyAltitude.create()
                .listen(this) { altitude -> _altitudeMeters.postValue(altitude) }
            FlightControllerKey.KeyCompassHeading.create()
                .listen(this) { heading -> _compassHeadingDegrees.postValue(sanitizeHeading(heading)) }
            FlightControllerKey.KeyGPSSignalLevel.create()
                .listen(this) { signal ->
                    _gpsSignalLevel.postValue(
                        signal?.name?.removePrefix("LEVEL_")?.toIntOrNull() ?: 0
                    )
                }
            KeyTools.createKey(BatteryKey.KeyChargeRemainingInPercent, ComponentIndexType.LEFT_OR_MAIN)
                .listen(this) { battery -> _batteryPercent.postValue(battery ?: -1) }
        }.isSuccess
        _listening.postValue(attached)
    }

    /**
     * Distance from the aircraft to the home point, or null when either end is
     * unknown. Computed here with the same geometry as the guidance legs rather
     * than read from a key, because the SDK's own distance-to-home key belongs
     * to the RTK mobile-station component and does not apply to this hardware.
     */
    fun distanceToHomeMeters(): Double? {
        val here = _position.value ?: return null
        val home = _homePosition.value ?: return null
        return NavigationGuidance.distanceMeters(here, home)
    }

    override fun onCleared() {
        runCatching { keyManager.cancelListen(this) }
        super.onCleared()
    }

    private fun LocationCoordinate3D?.toPosition(): GuidancePosition? {
        val latitude = this?.latitude
        val longitude = this?.longitude
        if (latitude == null || longitude == null) return null
        if (!NavigationGuidance.isUsableCoordinate(latitude, longitude)) return null
        return GuidancePosition(latitude, longitude)
    }

    private fun LocationCoordinate2D?.toPosition(): GuidancePosition? {
        val latitude = this?.latitude
        val longitude = this?.longitude
        if (latitude == null || longitude == null) return null
        if (!NavigationGuidance.isUsableCoordinate(latitude, longitude)) return null
        return GuidancePosition(latitude, longitude)
    }

    /**
     * A compass heading can arrive out of range or as a sentinel. Folded into
     * 0..360 and rejected when it is not a real number, so the guidance arrow
     * is never driven by a garbage value.
     */
    private fun sanitizeHeading(heading: Double?): Double? {
        if (heading == null || !heading.isFinite()) return null
        return NavigationGuidance.normalizeDegrees(heading)
    }
}
