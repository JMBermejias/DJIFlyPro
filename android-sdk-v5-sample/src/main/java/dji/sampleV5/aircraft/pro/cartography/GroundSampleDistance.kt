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
        require(heightMeters.isFinite() && heightMeters > 0.0) { "La altura de vuelo debe ser mayor que 0 m" }
        return heightMeters * camera.sensorWidthMillimeters /
            (camera.focalLengthMillimeters * camera.imageWidthPixels) * 100.0
    }

    /** Flight height above ground that produces [gsdCentimetersPerPixel]. */
    fun heightForGsd(gsdCentimetersPerPixel: Double, camera: SurveyCamera): Double {
        requireUsableCamera(camera)
        require(gsdCentimetersPerPixel.isFinite() && gsdCentimetersPerPixel > 0.0) {
            "La resolución debe ser mayor que 0 cm/px"
        }
        return gsdCentimetersPerPixel / 100.0 * camera.focalLengthMillimeters *
            camera.imageWidthPixels / camera.sensorWidthMillimeters
    }

    /** Ground footprint width in metres, measured across the flight direction. */
    fun footprintWidthMeters(heightMeters: Double, camera: SurveyCamera): Double {
        requireUsableCamera(camera)
        require(heightMeters.isFinite() && heightMeters > 0.0) { "La altura de vuelo debe ser mayor que 0 m" }
        return heightMeters * camera.sensorWidthMillimeters / camera.focalLengthMillimeters
    }

    /** Ground footprint height in metres, measured along the flight direction. */
    fun footprintDepthMeters(heightMeters: Double, camera: SurveyCamera): Double {
        requireUsableCamera(camera)
        require(heightMeters.isFinite() && heightMeters > 0.0) { "La altura de vuelo debe ser mayor que 0 m" }
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
        val overlap = requireOverlapFraction(forwardOverlapPercent, "longitudinal")
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
        val overlap = requireOverlapFraction(sideOverlapPercent, "transversal")
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
        require(areaSquareMeters.isFinite() && areaSquareMeters > 0.0) { "La superficie debe ser mayor que 0 m²" }
        val coverage = photoCoverageSquareMeters(heightMeters, camera)
        require(coverage > 0.0) { "La cámara no cubre suelo a esta altura" }
        val advance = (1.0 - forwardOverlapPercent / 100.0) * (1.0 - sideOverlapPercent / 100.0)
        require(advance > 0.0) { "Los solapes no dejan ningún avance de suelo utilizable" }
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
        require(areaSquareMeters.isFinite() && areaSquareMeters > 0.0) { "La superficie debe ser mayor que 0 m²" }
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
            notes += "La resolución es más fina que 1 cm/px. Espera archivos grandes, tiempos de proceso largos y una necesidad real de RTK/PPK."
            reliable = false
        }
        if (gsdCentimetersPerPixel > 5.0) {
            notes += "La resolución es más gruesa que 5 cm/px. El detalle fino, la vegetación y las estructuras delgadas no se resolverán."
            reliable = false
        }
        if (forwardOverlapPercent < 60) {
            notes += "Un solape longitudinal por debajo del 60 % reconstruye mal en terreno complejo. Lo habitual es 75-85 %."
        }
        if (sideOverlapPercent < 50) {
            notes += "Un solape transversal por debajo del 50 % deja huecos entre líneas de vuelo. Lo habitual es 60-70 %."
        }
        if (sideOverlapPercent < forwardOverlapPercent) {
            notes += "El solape transversal es menor que el longitudinal; en un bloque en nadir lo normal es lo contrario."
        }
        if (heightMeters > 120.0) {
            notes += "Volar por encima de 120 m aumenta el efecto del relieve, el viento y la distorsión de la lente."
        }
        if (!camera.isMechanicalOrFixedLens) {
            notes += "Esta cámara tiene objetivo variable. Confirma la focalidad realmente montada antes de volar."
        }
        // The reassuring line is about the resolution and the overlap, so it
        // is stated whenever those are inside the usual range, even if another
        // advisory note came first.
        if (reliable) {
            notes += "La resolución y el solape están dentro del rango habitual de levantamiento. Valida el resultado en un vuelo de prueba."
        }
        if (notes.isEmpty()) notes += "Revisa esta configuración antes de volar."
        return GsdAssessment(reliable, notes)
    }

    /** Devuelve el solape como fracción, después de comprobar el rango. */
    private fun requireOverlapFraction(percent: Int, kind: String): Double {
        require(percent in MIN_OVERLAP_PERCENT..MAX_OVERLAP_PERCENT) {
            "El solape $kind debe estar entre $MIN_OVERLAP_PERCENT % y $MAX_OVERLAP_PERCENT %"
        }
        return percent / 100.0
    }

    private fun requireUsableCamera(camera: SurveyCamera) {
        val validation = camera.validate()
        require(validation.isValid) { "La cámara no es utilizable: ${validation.errors.joinToString("; ")}" }
    }
}

data class GsdAssessment(
    val isReliable: Boolean,
    val notes: List<String>
)
