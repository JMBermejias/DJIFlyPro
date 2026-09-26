package dji.sampleV5.aircraft.pro.cartography

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a cartographic intent into the numbers a route needs: how high to
 * fly, how far apart the lines go and how far apart the photographs go.
 *
 * The derivation is deliberately one-directional. A GSD, a payload and two
 * overlap figures are things an operator can state from a specification; the
 * height and the spacings are consequences. The planner fills those
 * consequences back into the route, so the route and the cartography report
 * can never disagree.
 */
object CartographySolution {

    /**
     * @param areaLengthMeters extent along the flight direction
     * @param areaWidthMeters extent across the flight direction
     */
    fun solve(
        profile: CartographyProfile,
        areaLengthMeters: Double,
        areaWidthMeters: Double
    ): CartographySolutionResult {
        val validation = profile.validate()
        require(validation.isValid) { "The cartography profile is not valid: ${validation.errors.joinToString("; ")}" }
        require(areaLengthMeters.isFinite() && areaLengthMeters > 0.0) { "The length must be greater than 0 m" }
        require(areaWidthMeters.isFinite() && areaWidthMeters > 0.0) { "The width must be greater than 0 m" }

        val camera = profile.camera
        val heightMeters = GroundSampleDistance.heightForGsd(profile.targetGsdCentimetersPerPixel, camera)
        val footprintWidth = GroundSampleDistance.footprintWidthMeters(heightMeters, camera)
        val footprintDepth = GroundSampleDistance.footprintDepthMeters(heightMeters, camera)
        val photoSpacing = GroundSampleDistance.photoSpacingMeters(
            heightMeters = heightMeters,
            camera = camera,
            forwardOverlapPercent = profile.forwardOverlapPercent
        )
        val lineSpacing = GroundSampleDistance.lineSpacingMeters(
            heightMeters = heightMeters,
            camera = camera,
            sideOverlapPercent = profile.sideOverlapPercent
        )
        val photoCoverage = GroundSampleDistance.photoCoverageSquareMeters(heightMeters, camera)

        // A line has to start half a footprint before the boundary and end
        // half a footprint after it. Without that margin the border is only
        // covered by the very edge of a photograph and holes open along every
        // edge of the block.
        val edgeMargin = footprintWidth * (1.0 - profile.forwardOverlapPercent / 100.0) / 2.0
        val extendedLength = areaLengthMeters + 2.0 * edgeMargin
        val lineCount = max(1, ceil(areaWidthMeters / lineSpacing).toInt())
        val photosPerLine = max(2, ceil(extendedLength / photoSpacing).toInt() + 1)
        val areaSquareMeters = areaLengthMeters * areaWidthMeters
        val estimatedPhotos = lineCount * photosPerLine
        val redundancy = estimatedPhotos * photoCoverage / areaSquareMeters

        val assessment = GroundSampleDistance.assess(
            gsdCentimetersPerPixel = profile.targetGsdCentimetersPerPixel,
            heightMeters = heightMeters,
            camera = camera,
            forwardOverlapPercent = profile.forwardOverlapPercent,
            sideOverlapPercent = profile.sideOverlapPercent
        )

        val notes = buildList {
            addAll(assessment.notes)
            if (lineCount < 2) {
                add("A single flight line cannot satisfy a ${profile.sideOverlapPercent}% side overlap. " +
                    "Widen the area or accept a lower overlap.")
            }
            if (heightMeters > CartographyLimits.MAX_SURVEY_HEIGHT_METERS) {
                add("This GSD needs ${heightMeters.toInt()} m, past the ${CartographyLimits.MAX_SURVEY_HEIGHT_METERS} m " +
                    "the planner will build. The resolution you asked for and the height you can reach do not agree.")
            }
            if (heightMeters > dji.sampleV5.aircraft.pro.mission.MissionValidator.MAX_ALTITUDE_METERS) {
                add("At ${heightMeters.toInt()} m this block is above the " +
                    "${dji.sampleV5.aircraft.pro.mission.MissionValidator.MAX_ALTITUDE_METERS.toInt()} m ceiling of an " +
                    "automatic waypoint mission. It can be planned and flown with manual guidance, " +
                    "or flown automatically at a coarser resolution.")
            }
            if (profile.altitudeReference == AltitudeReference.RELATIVE_TO_TAKEOFF) {
                add("Heights are measured from the take-off point. On sloping ground the effective GSD will drift.")
            }
            if (profile.altitudeReference == AltitudeReference.AMSL) {
                add("Heights are over the ellipsoid. A geoid model is needed to relate them to the terrain.")
            }
            if (profile.crossTrack) {
                add("Cross-track pass enabled: the block is flown twice, in perpendicular directions.")
            }
        }

        return CartographySolutionResult(
            altitudeMeters = heightMeters,
            lineSpacingMeters = lineSpacing,
            photoSpacingMeters = photoSpacing,
            footprintWidthMeters = footprintWidth,
            footprintDepthMeters = footprintDepth,
            photoCoverageSquareMeters = photoCoverage,
            achievedGsdCentimetersPerPixel = GroundSampleDistance.gsdCentimetersPerPixel(heightMeters, camera),
            lineCount = lineCount,
            photosPerLine = photosPerLine,
            estimatedPhotoCount = estimatedPhotos * if (profile.crossTrack) 2 else 1,
            areaSquareMeters = areaSquareMeters,
            redundancyFactor = redundancy,
            isReliable = assessment.isReliable,
            notes = notes
        )
    }
}

/** Named bounds so the limits are documented values rather than magic numbers. */
object CartographyLimits {
    /** Above this the GSD target is almost always a mistake or a blocked airspace. */
    const val MAX_SURVEY_HEIGHT_METERS = 500.0
}

data class CartographySolutionResult(
    val altitudeMeters: Double,
    val lineSpacingMeters: Double,
    val photoSpacingMeters: Double,
    val footprintWidthMeters: Double,
    val footprintDepthMeters: Double,
    val photoCoverageSquareMeters: Double,
    val achievedGsdCentimetersPerPixel: Double,
    val lineCount: Int,
    val photosPerLine: Int,
    val estimatedPhotoCount: Int,
    val areaSquareMeters: Double,
    val redundancyFactor: Double,
    val isReliable: Boolean,
    val notes: List<String>
) {
    val areaHectares: Double
        get() = areaSquareMeters / 10_000.0

    /**
     * True when the height this resolution needs stays inside DJI's automatic
     * waypoint mission ceiling, so the block may be uploaded and started
     * without a human flying it.
     */
    val isFlyableAutomatically: Boolean
        get() = altitudeMeters <= dji.sampleV5.aircraft.pro.mission.MissionValidator.MAX_ALTITUDE_METERS
}
