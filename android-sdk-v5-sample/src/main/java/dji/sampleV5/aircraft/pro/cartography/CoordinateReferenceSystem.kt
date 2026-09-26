package dji.sampleV5.aircraft.pro.cartography

import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A projected coordinate in a UTM zone, the working coordinate system of
 * almost every national mapping and cadastral workflow. A survey plan that
 * cannot be handed over in the projected system the client asked for is not
 * finished, so the app converts the planned block to UTM and reports both
 * representations.
 */
data class UtmCoordinate(
    val zone: Int,
    val hemisphere: Char,
    val eastingMeters: Double,
    val northingMeters: Double,
    val centralMeridianDegrees: Double,
    val scaleFactor: Double,
    val falseEastingMeters: Double = 500_000.0,
    val falseNorthingMeters: Double = 10_000_000.0
) {
    val epsgCode: Int
        get() = (if (hemisphere == 'S') 32700 else 32600) + zone

    val crsLabel: String
        get() = "EPSG:$epsgCode (WGS 84 / UTM zone $zone$hemisphere)"
}

/** North/east offset on a local tangent plane, in metres. */
data class LocalOffset(val northMeters: Double, val eastMeters: Double) {
    /** Distance from the plane origin. */
    val radiusMeters: Double
        get() = sqrt(northMeters * northMeters + eastMeters * eastMeters)
}

/**
 * WGS 84 geographic to UTM and back, using the standard series expansion
 * (Snyder, *Map Projections - A Working Manual* NOAA/NCES 165, equations 8-9
 * to 8-11 for the forward conversion and 3-21 for the inverse).
 *
 * The residual error is far below the ground sample distance the same plan
 * flies at, which is the accuracy that matters here. It is not a geodetic
 * library: it does not do datum shifts, projections other than UTM, or
 * geoid undulation.
 */
object CoordinateReferenceSystem {
    const val WGS84_SEMI_MAJOR_AXIS_METERS = 6_378_137.0
    const val WGS84_FLATTENING = 1.0 / 298.257_223_563
    const val WGS84_ECCENTRICITY_SQUARED = WGS84_FLATTENING * (2.0 - WGS84_FLATTENING)
    const val WGS84_SECOND_ECCENTRICITY_SQUARED =
        WGS84_ECCENTRICITY_SQUARED / (1.0 - WGS84_ECCENTRICITY_SQUARED)

    /**
     * Metres per degree of latitude on the WGS 84 ellipsoid. Derived from the
     * first eccentricity series so it agrees with [toUtm] instead of being an
     * independent approximation.
     */
    const val METERS_PER_DEGREE = 111_319.490_793_273_6

    const val SCALE_FACTOR_K0 = 0.9996
    const val MIN_UTM_LATITUDE = -80.0
    const val MAX_UTM_LATITUDE = 84.0

    /**
     * UTM zone containing the longitude. Zones are 6 degrees wide with zone 1
     * centred on 177°E, plus the deliberate widenings around Norway and
     * Svalbard.
     */
    fun zoneFor(longitudeDegrees: Double, latitudeDegrees: Double): Int {
        require(longitudeDegrees.isFinite() && longitudeDegrees in -180.0..180.0) {
            "Longitude is outside [-180, 180]"
        }
        require(latitudeDegrees.isFinite() && latitudeDegrees in -90.0..90.0) {
            "Latitude is outside [-90, 90]"
        }
        var zone = floor((longitudeDegrees + 180.0) / 6.0).toInt() + 1
        if (latitudeDegrees in 56.0..64.0 && longitudeDegrees in 3.0..12.0) zone = 32
        if (latitudeDegrees in 72.0..84.0) {
            zone = when (longitudeDegrees) {
                in 0.0..9.0 -> 31
                in 9.0..21.0 -> 33
                in 21.0..33.0 -> 35
                in 33.0..42.0 -> 37
                else -> zone
            }
        }
        return zone.coerceIn(1, 60)
    }

    fun centralMeridianDegrees(zone: Int): Double {
        require(zone in 1..60) { "UTM zone must be between 1 and 60" }
        return (zone - 1) * 6.0 - 180.0 + 3.0
    }

    @JvmOverloads
    fun toUtm(
        latitudeDegrees: Double,
        longitudeDegrees: Double,
        zone: Int = zoneFor(longitudeDegrees, latitudeDegrees)
    ): UtmCoordinate {
        require(latitudeDegrees.isFinite() && latitudeDegrees in MIN_UTM_LATITUDE..MAX_UTM_LATITUDE) {
            "UTM is only defined between $MIN_UTM_LATITUDE° and $MAX_UTM_LATITUDE° latitude"
        }
        require(longitudeDegrees.isFinite() && longitudeDegrees in -180.0..180.0) {
            "Longitude is outside [-180, 180]"
        }
        require(zone in 1..60) { "UTM zone must be between 1 and 60" }

        val a = WGS84_SEMI_MAJOR_AXIS_METERS
        val e2 = WGS84_ECCENTRICITY_SQUARED
        val ep2 = WGS84_SECOND_ECCENTRICITY_SQUARED
        val k0 = SCALE_FACTOR_K0

        val phi = Math.toRadians(latitudeDegrees)
        val lambda = Math.toRadians(longitudeDegrees)
        val lambda0 = Math.toRadians(centralMeridianDegrees(zone))

        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val tanPhi = tan(phi)

        val n = a / sqrt(1.0 - e2 * sinPhi * sinPhi)
        val t = tanPhi * tanPhi
        val c = ep2 * cosPhi * cosPhi
        val arc = cosPhi * (lambda - lambda0)
        val arc2 = arc * arc
        val arc3 = arc2 * arc
        val arc4 = arc3 * arc
        val arc5 = arc4 * arc
        val arc6 = arc5 * arc

        val m = meridionalArc(phi, a, e2)
        val e4 = e2 * e2
        val e6 = e4 * e2

        val easting = k0 * n * (
            arc +
                (1.0 - t + c) * arc3 / 6.0 +
                (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ep2) * arc5 / 120.0
            ) + 500_000.0

        val northing = k0 * (
            m + n * tanPhi * (
                arc2 / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c * c) * arc4 / 24.0 +
                    (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * ep2) * arc6 / 720.0
                )
            ) + if (latitudeDegrees < 0.0) 10_000_000.0 else 0.0

        return UtmCoordinate(
            zone = zone,
            hemisphere = if (latitudeDegrees < 0.0) 'S' else 'N',
            eastingMeters = easting,
            northingMeters = northing,
            centralMeridianDegrees = centralMeridianDegrees(zone),
            scaleFactor = k0
        )
    }

    /** Inverse of [toUtm], returning latitude and longitude in degrees. */
    fun fromUtm(coordinate: UtmCoordinate): Pair<Double, Double> {
        require(coordinate.zone in 1..60) { "UTM zone must be between 1 and 60" }
        val a = WGS84_SEMI_MAJOR_AXIS_METERS
        val e2 = WGS84_ECCENTRICITY_SQUARED
        val ep2 = WGS84_SECOND_ECCENTRICITY_SQUARED
        val k0 = SCALE_FACTOR_K0

        val x = coordinate.eastingMeters - coordinate.falseEastingMeters
        val y = coordinate.northingMeters -
            (if (coordinate.hemisphere == 'S') coordinate.falseNorthingMeters else 0.0)

        val e1 = (1.0 - sqrt(1.0 - e2)) / (1.0 + sqrt(1.0 - e2))
        val m = y / k0
        val mu = m / (a * (1.0 - e2 / 4.0 - 3.0 * e2 * e2 / 64.0 - 5.0 * e2 * e2 * e2 / 256.0))

        val phi1 = mu +
            (3.0 * e1 / 2.0 - 27.0 * e1 * e1 * e1 / 32.0) * sin(2.0 * mu) +
            (21.0 * e1 * e1 / 16.0 - 55.0 * e1 * e1 * e1 * e1 / 32.0) * sin(4.0 * mu) +
            (151.0 * e1 * e1 * e1 / 96.0) * sin(6.0 * mu) +
            (1097.0 * e1 * e1 * e1 * e1 / 512.0) * sin(8.0 * mu)

        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = tan(phi1)
        val c1 = ep2 * cosPhi1 * cosPhi1
        val t1 = tanPhi1 * tanPhi1
        val n1 = a / sqrt(1.0 - e2 * sinPhi1 * sinPhi1)
        val oneMinusESinSq = 1.0 - e2 * sinPhi1 * sinPhi1
        val m1 = a * (1.0 - e2) / (oneMinusESinSq * sqrt(oneMinusESinSq))
        val d = x / (n1 * k0)
        val d2 = d * d
        val d3 = d2 * d
        val d4 = d2 * d2
        val d5 = d4 * d
        val d6 = d3 * d3

        val latitude = phi1 - (n1 * tanPhi1 / m1) * (
            d2 / 2.0 -
                (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ep2) * d4 / 24.0 +
                (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ep2 - 3.0 * c1 * c1) * d6 / 720.0
            )

        val longitude = (d -
            (1.0 + 2.0 * t1 + c1) * d3 / 6.0 +
            (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ep2 + 24.0 * t1 * t1) * d5 / 120.0) / cosPhi1

        return Math.toDegrees(latitude) to normalizeLongitude(Math.toDegrees(longitude) + coordinate.centralMeridianDegrees)
    }

    /**
     * North and east offset of a target from an origin on a local tangent
     * plane. Survey work is done in local metric coordinates, not in degrees,
     * and this matches the projection the flight geometry uses.
     */
    fun localOffsetMeters(
        originLatitudeDegrees: Double,
        originLongitudeDegrees: Double,
        targetLatitudeDegrees: Double,
        targetLongitudeDegrees: Double
    ): LocalOffset {
        val meanLat = Math.toRadians((originLatitudeDegrees + targetLatitudeDegrees) / 2.0)
        return LocalOffset(
            northMeters = (targetLatitudeDegrees - originLatitudeDegrees) * METERS_PER_DEGREE,
            eastMeters = (targetLongitudeDegrees - originLongitudeDegrees) * METERS_PER_DEGREE * cos(meanLat)
        )
    }

    /**
     * Planar polygon area in square metres on the local tangent plane, by the
     * shoelace formula. Exact enough for a block of a few kilometres, and the
     * only way to get a usable hectare figure without a full geodesic polygon
     * area routine.
     */
    fun localAreaSquareMeters(
        centerLatitudeDegrees: Double,
        centerLongitudeDegrees: Double,
        points: List<Pair<Double, Double>>
    ): Double {
        if (points.size < 3) return 0.0
        val projected = points.map { (lat, lon) ->
            localOffsetMeters(centerLatitudeDegrees, centerLongitudeDegrees, lat, lon)
        }
        var sum = 0.0
        projected.forEachIndexed { index, current ->
            val next = projected[(index + 1) % projected.size]
            sum += current.northMeters * next.eastMeters - next.northMeters * current.eastMeters
        }
        return abs(sum) / 2.0
    }

    /** Bounding box of a set of WGS 84 points. */
    fun boundingBox(points: List<Pair<Double, Double>>): BoundingBox? {
        if (points.isEmpty()) return null
        var south = Double.MAX_VALUE
        var north = -Double.MAX_VALUE
        var west = Double.MAX_VALUE
        var east = -Double.MAX_VALUE
        points.forEach { (lat, lon) ->
            if (lat < south) south = lat
            if (lat > north) north = lat
            if (lon < west) west = lon
            if (lon > east) east = lon
        }
        return BoundingBox(south, west, north, east)
    }

    /** Polygon of the surveyed block, in WGS 84, from its centre and extent. */
    fun blockPolygon(
        centerLatitudeDegrees: Double,
        centerLongitudeDegrees: Double,
        lengthMeters: Double,
        widthMeters: Double,
        bearingDegrees: Double
    ): List<Pair<Double, Double>> {
        require(lengthMeters.isFinite() && lengthMeters > 0.0) { "Length must be greater than 0 m" }
        require(widthMeters.isFinite() && widthMeters > 0.0) { "Width must be greater than 0 m" }
        val halfLength = lengthMeters / 2.0
        val halfWidth = widthMeters / 2.0
        val bearing = Math.toRadians(bearingDegrees)
        val cosB = cos(bearing)
        val sinB = sin(bearing)
        return listOf(-halfLength to -halfWidth, halfLength to -halfWidth, halfLength to halfWidth, -halfLength to halfWidth)
            .map { (along, across) -> offset(centerLatitudeDegrees, centerLongitudeDegrees, along, across, cosB, sinB) }
    }

    private fun offset(
        latitudeDegrees: Double,
        longitudeDegrees: Double,
        alongMeters: Double,
        acrossMeters: Double,
        cosBearing: Double,
        sinBearing: Double
    ): Pair<Double, Double> {
        val north = cosBearing * alongMeters - sinBearing * acrossMeters
        val east = sinBearing * alongMeters + cosBearing * acrossMeters
        val latitude = latitudeDegrees + north / METERS_PER_DEGREE
        val longitudeScale = max(abs(cos(Math.toRadians(latitudeDegrees))), 1e-9)
        val longitude = longitudeDegrees + east / (METERS_PER_DEGREE * longitudeScale)
        require(latitude.isFinite() && latitude in -90.0..90.0) { "The block leaves the WGS 84 latitude range" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "The block leaves the WGS 84 longitude range" }
        return latitude to longitude
    }

    private fun meridionalArc(phi: Double, a: Double, e2: Double): Double {
        val e4 = e2 * e2
        val e6 = e4 * e2
        return a * (
            (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * phi -
                (3.0 * e2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * sin(2.0 * phi) +
                (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * sin(4.0 * phi) -
                (35.0 * e6 / 3072.0) * sin(6.0 * phi)
            )
    }

    private fun normalizeLongitude(value: Double): Double = (value + 540.0) % 360.0 - 180.0

    /**
     * Groups scalar values into clusters whose members are within [tolerance]
     * of each other, returning each cluster's mean.
     *
     * This exists because the obvious alternative, rounding each value onto a
     * grid of size `tolerance`, puts a value that lands exactly on a grid
     * boundary at the mercy of the last floating point digit: a flight line
     * 30 m from the origin on a 4 m grid sits exactly on the boundary and its
     * own waypoints then round to two different buckets, so one line counts as
     * two. Comparing against the cluster being built has no such boundary.
     */
    fun clusterByTolerance(values: List<Double>, tolerance: Double): List<Double> {
        require(tolerance > 0.0 && tolerance.isFinite()) { "The tolerance must be greater than 0" }
        if (values.isEmpty()) return emptyList()
        val sorted = values.filter { it.isFinite() }.sorted()
        if (sorted.isEmpty()) return emptyList()
        val clusters = mutableListOf<MutableList<Double>>()
        clusters += mutableListOf(sorted.first())
        sorted.drop(1).forEach { value ->
            val current = clusters.last()
            if (abs(value - current.first()) <= tolerance) {
                current += value
            } else {
                clusters += mutableListOf(value)
            }
        }
        return clusters.map { cluster -> cluster.average() }
    }
}

data class BoundingBox(
    val southLatitude: Double,
    val westLongitude: Double,
    val northLatitude: Double,
    val eastLongitude: Double
) {
    val centerLatitude: Double
        get() = (southLatitude + northLatitude) / 2.0

    val centerLongitude: Double
        get() = (westLongitude + eastLongitude) / 2.0
}

/** Format helpers so the same number is written the same way everywhere. */
object CoordinateFormat {
    fun degrees(value: Double, decimals: Int = 7): String =
        String.format(Locale.US, "%.${decimals}f°", value)

    fun degreesDirection(value: Double, decimals: Int = 7): String {
        val hemisphere = if (value < 0) "W" else "E"
        return String.format(Locale.US, "%.${decimals}f° %s", abs(value), hemisphere)
    }

    fun latitude(latitudeDegrees: Double): String {
        val hemisphere = if (latitudeDegrees < 0) "S" else "N"
        return String.format(Locale.US, "%.7f° %s", abs(latitudeDegrees), hemisphere)
    }

    fun longitude(longitudeDegrees: Double): String {
        val hemisphere = if (longitudeDegrees < 0) "W" else "E"
        return String.format(Locale.US, "%.7f° %s", abs(longitudeDegrees), hemisphere)
    }

    fun utm(coordinate: UtmCoordinate): String =
        String.format(Locale.US, "E %.3f  N %.3f", coordinate.eastingMeters, coordinate.northingMeters)
}
