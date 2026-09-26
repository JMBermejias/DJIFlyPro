package dji.sampleV5.aircraft.pro.cartography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateReferenceSystemTest {

    @Test
    fun madridMapsToUtmZone30() {
        val utm = CoordinateReferenceSystem.toUtm(40.416800, -3.703800)
        assertEquals(30, utm.zone)
        assertEquals('N', utm.hemisphere)
        assertEquals(-3.0, utm.centralMeridianDegrees, 1e-9)
        assertEquals(0.9996, utm.scaleFactor, 1e-9)
        assertTrue(utm.crsLabel.contains("32630"))
        // West of the central meridian, so west of the false easting.
        assertTrue("easting ${utm.eastingMeters}", utm.eastingMeters < 500_000.0)
        assertTrue("easting ${utm.eastingMeters}", utm.eastingMeters > 430_000.0)
        assertTrue("northing ${utm.northingMeters}", utm.northingMeters in 4_460_000.0..4_490_000.0)
    }

    @Test
    fun theEastingOffsetIsTheArcDistanceAlongTheParallel() {
        // The transverse Mercator series is, to the millimetre inside a zone,
        // the distance along the parallel from the central meridian, scaled by
        // k0. That is an independent check on the easting.
        val latitude = 40.0
        val longitude = -3.0
        val utm = CoordinateReferenceSystem.toUtm(latitude, longitude)
        val lambda = Math.toRadians(longitude)
        val lambda0 = Math.toRadians(utm.centralMeridianDegrees)
        val phi = Math.toRadians(latitude)
        val n = CoordinateReferenceSystem.WGS84_SEMI_MAJOR_AXIS_METERS /
            kotlin.math.sqrt(1.0 - CoordinateReferenceSystem.WGS84_ECCENTRICITY_SQUARED * kotlin.math.sin(phi) * kotlin.math.sin(phi))
        val arcMeters = (lambda - lambda0) * kotlin.math.cos(phi) * n
        assertEquals(500_000.0 + 0.9996 * arcMeters, utm.eastingMeters, 0.01)
    }

    @Test
    fun theEastingMatchesTheSnyderSeriesWrittenIndependently() {
        // The forward series, written out again here from the textbook rather
        // than called, so the check is against the definition of UTM and not
        // against the implementation.
        val a = CoordinateReferenceSystem.WGS84_SEMI_MAJOR_AXIS_METERS
        val f = CoordinateReferenceSystem.WGS84_FLATTENING
        val e2 = f * (2.0 - f)
        val ep2 = e2 / (1.0 - e2)

        fun expectedEasting(latitude: Double, longitude: Double, centralMeridian: Double): Double {
            val phi = Math.toRadians(latitude)
            val n = a / kotlin.math.sqrt(1.0 - e2 * kotlin.math.sin(phi) * kotlin.math.sin(phi))
            val t = kotlin.math.tan(phi) * kotlin.math.tan(phi)
            val c = ep2 * kotlin.math.cos(phi) * kotlin.math.cos(phi)
            val bigA = kotlin.math.cos(phi) * Math.toRadians(longitude - centralMeridian)
            return 0.9996 * n * (
                bigA +
                    (1.0 - t + c) * bigA * bigA * bigA / 6.0 +
                    (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ep2) *
                    bigA * bigA * bigA * bigA * bigA / 120.0
                ) + 500_000.0
        }

        val cases = listOf(
            Triple(40.0, -3.7038, 30),
            Triple(40.0, -3.0, 30),
            Triple(60.0, 2.9, 31),
            Triple(0.0, 0.5, 31),
            Triple(-33.8688, 151.2093, 56),
            Triple(78.2232, 15.6469, 33),
            Triple(12.0, -0.5, 30),
            Triple(-60.0, -70.0, 19)
        )
        cases.forEach { (latitude, longitude, zone) ->
            val utm = CoordinateReferenceSystem.toUtm(latitude, longitude, zone)
            assertEquals(
                "$latitude,$longitude zone $zone",
                expectedEasting(latitude, longitude, CoordinateReferenceSystem.centralMeridianDegrees(zone)),
                utm.eastingMeters,
                1e-6
            )
        }
    }

    @Test
    fun theEastingIsLinearEnoughToMeasureWith() {
        // On the central meridian the scale is exactly k0, so a short east-west
        // baseline is k0 times its true length. This is the accuracy a survey
        // can expect to take off a UTM coordinate.
        val latitude = 52.0
        val sin = kotlin.math.sin(Math.toRadians(latitude))
        val primeVerticalRadius = CoordinateReferenceSystem.WGS84_SEMI_MAJOR_AXIS_METERS /
            kotlin.math.sqrt(1.0 - CoordinateReferenceSystem.WGS84_ECCENTRICITY_SQUARED * sin * sin)
        val deltaLongitude = 0.001
        val a = CoordinateReferenceSystem.toUtm(latitude, 3.0)
        val b = CoordinateReferenceSystem.toUtm(latitude, 3.0 + deltaLongitude)
        val measured = b.eastingMeters - a.eastingMeters
        val trueLength = primeVerticalRadius * kotlin.math.cos(Math.toRadians(latitude)) * Math.toRadians(deltaLongitude)
        assertEquals(0.9996 * trueLength, measured, 0.01)
        // Better than 1 part in 1e8 over 57 km: far below any survey tolerance.
        assertTrue(measured / trueLength > 0.99959999 && measured / trueLength < 0.99960001)
        // The northing barely moves: off the meridian it picks up the
        // k0 * N * tan(phi) * A^2 / 2 term, which over a 0.001 degree step is
        // a few tens of micrometres.
        assertTrue(kotlin.math.abs(b.northingMeters - a.northingMeters) < 0.001)
    }

    @Test
    fun theNorthingIsTheMeridianArcScaled() {
        // The northing on the central meridian is k0 times the meridian arc
        // from the equator, which is the second textbook definition of UTM.
        val latitude = 45.0
        val utm = CoordinateReferenceSystem.toUtm(latitude, 3.0)
        val a = CoordinateReferenceSystem.WGS84_SEMI_MAJOR_AXIS_METERS
        val f = CoordinateReferenceSystem.WGS84_FLATTENING
        val e2 = f * (2.0 - f)
        val phi = Math.toRadians(latitude)
        val e4 = e2 * e2
        val e6 = e4 * e2
        val m = a * (
            (1.0 - e2 / 4.0 - 3.0 * e4 / 64.0 - 5.0 * e6 / 256.0) * phi -
                (3.0 * e2 / 8.0 + 3.0 * e4 / 32.0 + 45.0 * e6 / 1024.0) * kotlin.math.sin(2.0 * phi) +
                (15.0 * e4 / 256.0 + 45.0 * e6 / 1024.0) * kotlin.math.sin(4.0 * phi) -
                (35.0 * e6 / 3072.0) * kotlin.math.sin(6.0 * phi)
            )
        assertEquals(0.9996 * m, utm.northingMeters, 1e-6)
    }

    @Test
    fun theEpsgCodeFollowsTheHemisphere() {
        assertEquals(32630, CoordinateReferenceSystem.toUtm(40.0, -3.0).epsgCode)
        assertEquals(32719, CoordinateReferenceSystem.toUtm(-33.0, -70.0).epsgCode)
        assertEquals(32660, CoordinateReferenceSystem.toUtm(10.0, 177.0).epsgCode)
        assertEquals(32760, CoordinateReferenceSystem.toUtm(-10.0, 179.0).epsgCode)
        assertEquals(32701, CoordinateReferenceSystem.toUtm(-10.0, -179.0).epsgCode)
    }

    @Test
    fun theOriginSitsAtTheFalseEasting() {
        val utm = CoordinateReferenceSystem.toUtm(0.0, 3.0)
        assertEquals(500_000.0, utm.eastingMeters, 0.01)
        assertEquals(0.0, utm.northingMeters, 0.01)
    }

    @Test
    fun theEquatorHemisphereBoundaryIsClean() {
        val north = CoordinateReferenceSystem.toUtm(0.001, 10.0)
        val south = CoordinateReferenceSystem.toUtm(-0.001, 10.0)
        assertEquals('N', north.hemisphere)
        assertEquals('S', south.hemisphere)
        // Just south of the equator the false northing puts the value near
        // 10 000 km; just north of it the value is the real distance.
        assertEquals(9_999_889.0, south.northingMeters, 12.0)
        assertEquals(111.0, north.northingMeters, 12.0)
    }

    @Test
    fun theCentralMeridianScalesWithoutDistortion() {
        listOf(1, 15, 30, 45, 60).forEach { zone ->
            val meridian = CoordinateReferenceSystem.centralMeridianDegrees(zone)
            val utm = CoordinateReferenceSystem.toUtm(0.0, meridian, zone)
            assertEquals("$zone", 500_000.0, utm.eastingMeters, 0.01)
        }
    }

    @Test
    fun utmRoundTripsWithinAMillimetre() {
        val samples = listOf(
            40.4168 to -3.7038,
            48.8584 to 2.2945,
            -33.8688 to 151.2093,
            64.1466 to -21.9426,
            0.0 to 0.0,
            -54.8019 to -68.3030,
            78.2232 to 15.6469
        )
        samples.forEach { (latitude, longitude) ->
            val utm = CoordinateReferenceSystem.toUtm(latitude, longitude)
            val (backLat, backLon) = CoordinateReferenceSystem.fromUtm(utm)
            val back = CoordinateReferenceSystem.toUtm(backLat, backLon)
            assertEquals("$latitude,$longitude easting", utm.eastingMeters, back.eastingMeters, 0.001)
            assertEquals("$latitude,$longitude northing", utm.northingMeters, back.northingMeters, 0.001)
        }
    }

    @Test
    fun zonesAreSixDegreesWide() {
        assertEquals(30, CoordinateReferenceSystem.zoneFor(-3.0, 40.0))
        // The meridian 0 belongs to zone 31, whose range starts there.
        assertEquals(31, CoordinateReferenceSystem.zoneFor(0.0, 40.0))
        assertEquals(31, CoordinateReferenceSystem.zoneFor(0.5, 40.0))
        assertEquals(31, CoordinateReferenceSystem.zoneFor(3.5, 40.0))
        assertEquals(32, CoordinateReferenceSystem.zoneFor(6.0, 40.0))
        assertEquals(1, CoordinateReferenceSystem.zoneFor(-177.0, 0.0))
        assertEquals(60, CoordinateReferenceSystem.zoneFor(177.0, 0.0))
    }

    @Test
    fun norwayAndSvalbardUseTheirWidenedZones() {
        // Zone 32 is widened west over southern Norway.
        assertEquals(32, CoordinateReferenceSystem.zoneFor(5.0, 60.0))
        // Svalbard jumps 32 to 31 west of 9 degrees E.
        assertEquals(31, CoordinateReferenceSystem.zoneFor(5.0, 78.0))
        assertEquals(33, CoordinateReferenceSystem.zoneFor(15.0, 78.0))
        assertEquals(35, CoordinateReferenceSystem.zoneFor(25.0, 78.0))
        assertEquals(37, CoordinateReferenceSystem.zoneFor(35.0, 78.0))
    }

    @Test
    fun latitudesOutsideUtmAreRejected() {
        listOf(-85.0, 86.0, -90.0, 90.0).forEach { latitude ->
            val error = runCatching { CoordinateReferenceSystem.toUtm(latitude, 0.0) }.exceptionOrNull()
            assertTrue("latitude $latitude must be rejected", error is IllegalArgumentException)
        }
    }

    @Test
    fun anInvalidZoneIsRejected() {
        listOf(0, 61, -1).forEach { zone ->
            val error = runCatching { CoordinateReferenceSystem.toUtm(0.0, 0.0, zone) }.exceptionOrNull()
            assertTrue("zone $zone must be rejected", error is IllegalArgumentException)
        }
    }

    @Test
    fun localOffsetsAreMetricAndSigned() {
        val north = CoordinateReferenceSystem.localOffsetMeters(40.0, -3.0, 40.01, -3.0)
        assertEquals(1_113.19, north.northMeters, 1.0)
        assertEquals(0.0, north.eastMeters, 1.0)

        val east = CoordinateReferenceSystem.localOffsetMeters(40.0, -3.0, 40.0, -2.99)
        assertEquals(0.0, east.northMeters, 1.0)
        assertTrue(east.eastMeters > 850.0)
        assertTrue(east.eastMeters < 900.0)
    }

    @Test
    fun theAreaOfAKnownBlockIsClose() {
        val area = CoordinateReferenceSystem.localAreaSquareMeters(
            centerLatitudeDegrees = 40.0,
            centerLongitudeDegrees = -3.0,
            points = CoordinateReferenceSystem.blockPolygon(40.0, -3.0, 200.0, 100.0, 0.0)
        )
        assertEquals(20_000.0, area, 60.0)
    }

    @Test
    fun theAreaIsIndependentOfVertexOrder() {
        val polygon = CoordinateReferenceSystem.blockPolygon(40.0, -3.0, 120.0, 80.0, 37.0)
        val forwards = CoordinateReferenceSystem.localAreaSquareMeters(40.0, -3.0, polygon)
        val backwards = CoordinateReferenceSystem.localAreaSquareMeters(40.0, -3.0, polygon.reversed())
        assertEquals(forwards, backwards, 1e-6)
        assertEquals(9_600.0, forwards, 100.0)
    }

    @Test
    fun aDegeneratePolygonHasNoArea() {
        assertEquals(0.0, CoordinateReferenceSystem.localAreaSquareMeters(40.0, -3.0, listOf(40.0 to -3.0)), 0.0)
        assertEquals(
            0.0,
            CoordinateReferenceSystem.localAreaSquareMeters(40.0, -3.0, listOf(40.0 to -3.0, 40.0 to -2.9)),
            0.0
        )
    }

    @Test
    fun theBlockPolygonIsFourDistinctCorners() {
        val polygon = CoordinateReferenceSystem.blockPolygon(40.0, -3.0, 100.0, 50.0, 0.0)
        assertEquals(4, polygon.size)
        assertEquals(4, polygon.toSet().size)
        val corners = polygon.map { (lat, lon) ->
            CoordinateReferenceSystem.localOffsetMeters(40.0, -3.0, lat, lon)
        }
        // South-west, north-west, north-east, south-east: the block is walked
        // counterclockwise, as RFC 7946 asks for an exterior ring.
        assertTrue(corners[0].northMeters < 0 && corners[0].eastMeters < 0)
        assertTrue(corners[1].northMeters > 0 && corners[1].eastMeters < 0)
        assertTrue(corners[2].northMeters > 0 && corners[2].eastMeters > 0)
        assertTrue(corners[3].northMeters < 0 && corners[3].eastMeters > 0)
        val signed = corners.indices.sumOf { i ->
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            a.northMeters * b.eastMeters - b.northMeters * a.eastMeters
        }
        assertTrue("exterior ring must wind counterclockwise", signed > 0.0)
    }

    @Test
    fun rotatingTheBearingRotatesTheBlock() {
        val rotated = CoordinateReferenceSystem.blockPolygon(0.0, 0.0, 100.0, 10.0, 90.0)
        val offsets = rotated.map { CoordinateReferenceSystem.localOffsetMeters(0.0, 0.0, it.first, it.second) }
        // The block keeps its own 100 m x 10 m shape; the bearing decides which
        // world axis the long side lies on.
        assertEquals(10.0, offsets.maxOf { it.northMeters } - offsets.minOf { it.northMeters }, 2.0)
        assertEquals(100.0, offsets.maxOf { it.eastMeters } - offsets.minOf { it.eastMeters }, 2.0)
    }

    @Test
    fun aBlockCrossingTheAntimeridianIsNotDistorted() {
        val polygon = CoordinateReferenceSystem.blockPolygon(0.0, 179.999, 200.0, 100.0, 90.0)
        val area = CoordinateReferenceSystem.localAreaSquareMeters(0.0, 179.999, polygon)
        assertEquals(20_000.0, area, 500.0)
    }

    @Test
    fun boundingBoxCoversEveryPoint() {
        val points = listOf(40.0 to -3.0, 41.0 to -2.0, 39.0 to -4.0)
        val box = CoordinateReferenceSystem.boundingBox(points)!!
        assertEquals(39.0, box.southLatitude, 1e-9)
        assertEquals(41.0, box.northLatitude, 1e-9)
        assertEquals(-4.0, box.westLongitude, 1e-9)
        assertEquals(-2.0, box.eastLongitude, 1e-9)
        assertEquals(40.0, box.centerLatitude, 1e-9)
        assertEquals(-3.0, box.centerLongitude, 1e-9)
    }

    @Test
    fun anEmptyPointListHasNoBoundingBox() {
        assertEquals(null, CoordinateReferenceSystem.boundingBox(emptyList()))
    }

    @Test
    fun aDegenerateBlockIsRejected() {
        listOf(0.0, -1.0, Double.NaN).forEach { length ->
            val error = runCatching {
                CoordinateReferenceSystem.blockPolygon(40.0, -3.0, length, 10.0, 0.0)
            }.exceptionOrNull()
            assertTrue("length $length must be rejected", error is IllegalArgumentException)
        }
    }

    @Test
    fun coordinatesAreFormattedTheSameWayEverywhere() {
        assertEquals("40.4168000° N", CoordinateFormat.latitude(40.4168))
        assertEquals("3.7038000° W", CoordinateFormat.longitude(-3.7038))
        assertEquals("3.7038000° E", CoordinateFormat.longitude(3.7038))
        assertEquals("40.4168000°", CoordinateFormat.degrees(40.4168))
        val utm = CoordinateReferenceSystem.toUtm(40.4168, -3.7038)
        assertTrue(
            CoordinateFormat.utm(utm),
            Regex("^E \\d+\\.\\d{3}  N \\d+\\.\\d{3}$").matches(CoordinateFormat.utm(utm))
        )
    }
}
