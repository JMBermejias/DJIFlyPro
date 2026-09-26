package dji.sampleV5.aircraft.pro.cartography

import kotlin.math.ceil
import kotlin.math.max

/**
 * The photogrammetric core of the cartography mode: it converts between the
 * three quantities an operator has to trade against each other.
 *
 * - Ground Sample Distance (GSD), the size on the ground of one pixel.
 * - Flight height above the ground, `H`.
 * - Footprint width on the ground, `W`.
 *
 * For a nadir camera the classical relation is
 *
 * ```
 * GSD = H * sensorWidth / (focalLength * imageWidthPx)
 * W   = H * sensorWidth / focalLength = imageWidthPx * GSD
 * ```
 *
 * Everything here is plain arithmetic on those relations, with no Android or
 * DJI dependency so it can be unit tested offline and reasoned about before
 * a mission is ever generated.
 */
object GroundSampleDistance {
    const val MIN_GSD_CM_PER_PIXEL = 0.2
    const val MAX_GSD_CM_PER_PIXEL = 50.0
    const val MIN_OVERLAP_PERCENT = 10
    const val MAX_OVERLAP_PERCENT = 90

    /** GSD in centimetres per pixel for a nadir capture at [heightMeters]. */
    fun gsdCentimetersPerPixel(heightMeters: Double, camera: SurveyCamera): Double {
        requireUsableCamera(camera)
        require(heightMeters.isFinite() && heightMeters > 0.0) { "Flight height must be greater than 0 m" }
        return heightMeters * camera.sensorWidthMillimeters /
            (camera.focalLengthMillimeters * camera.imageWidthPixels) * 100.0
    }

    /** Flight height above ground that produces [gsdCentimetersPerPixel]. */
    fun heightForGsd(gsdCentimetersPerPixel: Double, camera: SurveyCamera): Double {
        requireUsableCamera(camera)
        require(gsdCentimetersPerPixel.isFinite() && gsdCentimetersPerPixel > 0.0) {
            "GSD must be greater than 0 cm/px"
        }
        return gsdCentimetersPerPixel / 100.0 * camera.focalLengthMillimeters *
            camera.imageWidthPixels / camera.sensorWidthMillimeters
    }

    /** Ground footprint width in metres, measured across the flight direction. */
    fun footprintWidthMeters(heightMeters: Double, camera: SurveyCamera): Double {
        requireUsableCamera(camera)
        require(heightMeters.isFinite() && heightMeters > 0.0) { "Flight height must be greater than 0 m" }
        return heightMeters * camera.sensorWidthMillimeters / camera.focalLengthMillimeters
    }

    /** Ground footprint height in metres, measured along the flight direction. */
    fun footprintDepthMeters(heightMeters: Double, camera: SurveyCamera): Double {
        requireUsableCamera(camera)
        require(heightMeters.isFinite() && heightMeters > 0.0) { "Flight height must be greater than 0 m" }
        return heightMeters * camera.sensorHeightMillimeters / camera.focalLengthMillimeters
    }

    /**
     * Distance between two consecutive photographs along the flight line for
     * a requested [forwardOverlapPercent].
     */
    fun photoSpacingMeters(
        heightMeters: Double,
        camera: SurveyCamera,
        forwardOverlapPercent: Int
    ): Double {
        val overlap = requireOverlapFraction(forwardOverlapPercent, "forward")
        return footprintWidthMeters(heightMeters, camera) * (1.0 - overlap)
    }

    /**
     * Distance between two consecutive flight lines for a requested
     * [sideOverlapPercent].
     */
    fun lineSpacingMeters(
        heightMeters: Double,
        camera: SurveyCamera,
        sideOverlapPercent: Int
    ): Double {
        val overlap = requireOverlapFraction(sideOverlapPercent, "side")
        return footprintWidthMeters(heightMeters, camera) * (1.0 - overlap)
    }

    /**
     * Ground area covered by every single photograph, in square metres. Each
     * photo is a `footprintWidth x footprintDepth` rectangle; the sum over the
     * plan divided by the surveyed area is the redundancy factor that shows
     * how much of the flight is spent on overlap.
     */
    fun photoCoverageSquareMeters(heightMeters: Double, camera: SurveyCamera): Double =
        footprintWidthMeters(heightMeters, camera) * footprintDepthMeters(heightMeters, camera)

    /**
     * Number of photographs needed to cover [areaSquareMeters] at the given
     * height and overlaps. Rounded up, because a partial extra photograph is
     * still a photograph the aircraft has to take and the operator has to
     * store and process.
     */
    fun requiredPhotoCount(
        areaSquareMeters: Double,
        heightMeters: Double,
        camera: SurveyCamera,
        forwardOverlapPercent: Int,
        sideOverlapPercent: Int
    ): Int {
        require(areaSquareMeters.isFinite() && areaSquareMeters > 0.0) { "Area must be greater than 0 m2" }
        val coverage = photoCoverageSquareMeters(heightMeters, camera)
        require(coverage > 0.0) { "The camera covers no ground at this height" }
        val advance = (1.0 - forwardOverlapPercent / 100.0) * (1.0 - sideOverlapPercent / 100.0)
        require(advance > 0.0) { "The overlaps leave no usable ground advance" }
        return max(1, ceil(areaSquareMeters / (coverage * advance)).toInt())
    }

    /**
     * Redundancy factor: total photographed area divided by surveyed area.
     * `1.8` means the flight images the ground one and a half times over on
     * average, which is the cost of the requested overlap.
     */
    fun redundancyFactor(
        areaSquareMeters: Double,
        heightMeters: Double,
        camera: SurveyCamera,
        forwardOverlapPercent: Int,
        sideOverlapPercent: Int
    ): Double {
        require(areaSquareMeters.isFinite() && areaSquareMeters > 0.0) { "Area must be greater than 0 m2" }
        val photos = requiredPhotoCount(
            areaSquareMeters = areaSquareMeters,
            heightMeters = heightMeters,
            camera = camera,
            forwardOverlapPercent = forwardOverlapPercent,
            sideOverlapPercent = sideOverlapPercent
        )
        return photos * photoCoverageSquareMeters(heightMeters, camera) / areaSquareMeters
    }

    /**
     * Practical checks on a cartographic solution. These are advisory, never
     * blocking: the operator decides whether a GSD suits the deliverable.
     */
    fun assess(
        gsdCentimetersPerPixel: Double,
        heightMeters: Double,
        camera: SurveyCamera,
        forwardOverlapPercent: Int,
        sideOverlapPercent: Int
    ): GsdAssessment {
        val notes = mutableListOf<String>()
        var reliable = true
        if (gsdCentimetersPerPixel < 1.0) {
            notes += "GSD is finer than 1 cm/px. Expect large files, long processing times and a real need for RTK/PPK."
            reliable = false
        }
        if (gsdCentimetersPerPixel > 5.0) {
            notes += "GSD is coarser than 5 cm/px. Fine detail, vegetation and thin structures will not resolve."
            reliable = false
        }
        if (forwardOverlapPercent < 60) {
            notes += "Forward overlap below 60% rarely reconstructs well on complex terrain. 75-85% is the common range."
        }
        if (sideOverlapPercent < 50) {
            notes += "Side overlap below 50% leaves gaps between flight lines. 60-70% is the common range."
        }
        if (sideOverlapPercent < forwardOverlapPercent) {
            notes += "Side overlap is lower than forward overlap; for a nadir block the usual case is the opposite."
        }
        if (heightMeters > 120.0) {
            notes += "Flight height above 120 m increases the effect of terrain relief, wind and lens distortion."
        }
        if (!camera.isMechanicalOrFixedLens) {
            notes += "This camera has a variable lens. Confirm the focal length is the one actually in use before flying."
        }
        // The reassuring line is about the resolution and the overlap, so it
        // is stated whenever those are inside the usual range, even if another
        // advisory note came first.
        if (reliable) {
            notes += "Resolution and overlap are inside the usual mapping range. Validate the result on a test flight."
        }
        if (notes.isEmpty()) notes += "Review this configuration before flying."
        return GsdAssessment(reliable, notes)
    }

    /** Returns the overlap as a fraction, after checking the percentage range. */
    private fun requireOverlapFraction(percent: Int, kind: String): Double {
        require(percent in MIN_OVERLAP_PERCENT..MAX_OVERLAP_PERCENT) {
            "The $kind overlap must be between $MIN_OVERLAP_PERCENT% and $MAX_OVERLAP_PERCENT%"
        }
        return percent / 100.0
    }

    private fun requireUsableCamera(camera: SurveyCamera) {
        val validation = camera.validate()
        require(validation.isValid) { "The camera is not usable: ${validation.errors.joinToString("; ")}" }
    }
}

data class GsdAssessment(
    val isReliable: Boolean,
    val notes: List<String>
)
