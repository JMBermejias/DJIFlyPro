package dji.sampleV5.aircraft.pro.guidance

/**
 * Walks a plan one target at a time for manual flying.
 *
 * This is the part that replaces what the aircraft firmware would have done on
 * a model with waypoint support. The operator flies the legs on the remote
 * controller; the session only decides which target is current and when it has
 * been satisfied.
 *
 * Every method is pure with respect to the aircraft: nothing here can move it.
 */
class GuidanceSession(
    targets: List<GuidanceTarget>,
    val captureRadiusMeters: Double = NavigationGuidance.DEFAULT_CAPTURE_RADIUS_METERS,
    val captureAltitudeMeters: Double = NavigationGuidance.DEFAULT_CAPTURE_ALTITUDE_METERS
) {

    val targets: List<GuidanceTarget> = targets.filter {
        NavigationGuidance.isUsableCoordinate(it.latitude, it.longitude)
    }

    /** Which targets were dropped as unusable, for the operator to see. */
    val skippedCount: Int = targets.size - this.targets.size

    var index: Int = 0
        private set

    val total: Int get() = targets.size

    val isEmpty: Boolean get() = targets.isEmpty()

    val isFinished: Boolean get() = isEmpty || index >= total

    /** 1-based position for display, so the operator never sees "target 0". */
    val position: Int get() = if (isEmpty) 0 else index + 1

    val current: GuidanceTarget? get() = targets.getOrNull(index)

    val previous: GuidanceTarget? get() = targets.getOrNull(index - 1)

    val progressFraction: Double
        get() = if (total == 0) 0.0 else (index.toDouble() / total).coerceIn(0.0, 1.0)

    fun leg(
        from: GuidancePosition?,
        currentAltitudeMeters: Double?,
        headingDegrees: Double?
    ): GuidanceLeg? = current?.let {
        NavigationGuidance.leg(
            from = from,
            currentAltitudeMeters = currentAltitudeMeters,
            headingDegrees = headingDegrees,
            target = it,
            captureRadiusMeters = captureRadiusMeters,
            captureAltitudeMeters = captureAltitudeMeters
        )
    }

    /**
     * Moves on when the operator has actually arrived. Returns true when the
     * session advanced, so a caller can log it once instead of on every
     * telemetry frame.
     */
    fun advanceIfReached(
        from: GuidancePosition?,
        currentAltitudeMeters: Double?,
        headingDegrees: Double?
    ): Boolean {
        val leg = leg(from, currentAltitudeMeters, headingDegrees) ?: return false
        if (!leg.reached) return false
        index += 1
        return true
    }

    /** Operator-confirmed arrival, for when the capture gate is too tight. */
    fun markReached(): Boolean {
        if (isFinished) return false
        index += 1
        return true
    }

    fun skip(): Boolean {
        if (isFinished) return false
        index += 1
        return true
    }

    /** Never moves backwards past the first target. */
    fun back(): Boolean {
        if (isEmpty || index == 0) return false
        index -= 1
        return true
    }

    fun restart() {
        index = 0
    }
}
