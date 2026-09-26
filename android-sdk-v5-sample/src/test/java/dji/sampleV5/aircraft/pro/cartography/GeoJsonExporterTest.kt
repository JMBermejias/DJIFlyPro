package dji.sampleV5.aircraft.pro.cartography

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dji.sampleV5.aircraft.pro.mission.FinishAction
import dji.sampleV5.aircraft.pro.mission.MissionGeometry
import dji.sampleV5.aircraft.pro.mission.MissionPlan
import dji.sampleV5.aircraft.pro.mission.MissionRequest
import dji.sampleV5.aircraft.pro.mission.MissionTemplate
import dji.sampleV5.aircraft.pro.mission.PlannedWaypoint
import dji.sampleV5.aircraft.pro.mission.RoutePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoJsonExporterTest {

    private val profile = CartographyProfile(
        cameraId = "zenmuse-p1",
        targetGsdCentimetersPerPixel = 2.0,
        forwardOverlapPercent = 80,
        sideOverlapPercent = 70
    )

    private fun plan(name: String = "Bloque norte"): MissionPlan = MissionGeometry.plan(
        MissionRequest(
            name = name,
            template = MissionTemplate.FIELD,
            centerLatitude = 40.4168,
            centerLongitude = -3.7038,
            lengthMeters = 200.0,
            widthMeters = 120.0,
            heightMeters = 30.0,
            bearingDegrees = 0.0,
            altitudeMeters = GroundSampleDistance.heightForGsd(2.0, SurveyCamera.ZENMUSE_P1),
            standoffMeters = 12.0,
            lineSpacingMeters = 12.0,
            photoSpacingMeters = 8.0,
            overlapPercent = 80,
            speedMps = 4.0,
            routePattern = RoutePattern.PARALLEL,
            finishAction = FinishAction.RETURN_HOME
        )
    )

    private val control = listOf(
        GroundControlPoint(
            id = "gcp-1",
            code = "CP01",
            latitude = 40.4100,
            longitude = -3.7100,
            heightMeters = 650.0,
            role = ControlPointRole.CONTROL,
            target = ControlPointTarget.CROSS,
            horizontalAccuracyMillimeters = 12.0,
            source = "topografico"
        ),
        GroundControlPoint(
            id = "gcp-2",
            code = "CP02",
            latitude = 40.4250,
            longitude = -3.6950,
            role = ControlPointRole.CHECK,
            target = ControlPointTarget.CHECKERBOARD,
            source = ""
        )
    )

    private fun root(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

    private fun features(json: JsonObject): List<JsonObject> =
        json.getAsJsonArray("features").map { it.asJsonObject }

    private fun JsonObject.byRole(role: String): List<JsonObject> =
        features(this).filter { it.role() == role }

    private fun JsonObject.role(): String =
        getAsJsonObject("properties").get("role").asString

    private fun JsonObject.str(key: String): String = get(key).asString

    private fun JsonObject.int(key: String): Int = get(key).asInt

    private fun JsonObject.dbl(key: String): Double = get(key).asDouble

    private fun JsonObject.bool(key: String): Boolean = get(key).asBoolean

    private fun JsonObject.isJsonNullAt(key: String): Boolean = get(key).isJsonNull

    private fun JsonObject.obj(key: String): JsonObject = getAsJsonObject(key)

    private fun JsonObject.arr(key: String): JsonArray = getAsJsonArray(key)

    private fun JsonArray.dblAt(index: Int): Double = get(index).asDouble

    @Test
    fun theExportIsAValidFeatureCollection() {
        val json = root(GeoJsonExporter.export(plan(), profile, control))
        assertEquals("FeatureCollection", json.str("type"))
        assertTrue(features(json).size >= 5)
        features(json).forEach { feature ->
            assertEquals("Feature", feature.str("type"))
            assertTrue(feature.has("geometry"))
            assertTrue(feature.has("properties"))
        }
    }

    @Test
    fun theFootprintIsAClosedPolygon() {
        val json = root(GeoJsonExporter.export(plan(), profile))
        val footprint = json.byRole("footprint").single()
        assertEquals("Polygon", footprint.obj("geometry").str("type"))
        val ring = footprint.obj("geometry").arr("coordinates")[0].asJsonArray
        assertEquals(5, ring.size())
        assertEquals(ring[0].toString(), ring[4].toString())
        assertEquals(24_000.0, footprint.obj("properties").dbl("areaSquareMeters"), 40.0)
    }

    @Test
    fun coordinatesAreLongitudeThenLatitude() {
        val json = root(GeoJsonExporter.export(plan(), profile))
        val footprint = json.byRole("footprint").single()
        val ring = footprint.obj("geometry").arr("coordinates")[0].asJsonArray
        val first = ring[0].asJsonArray
        val longitude = first.dblAt(0)
        val latitude = first.dblAt(1)
        assertTrue("longitude $longitude", longitude in -3.8..-3.6)
        assertTrue("latitude $latitude", latitude in 40.3..40.5)
    }

    @Test
    fun everyFlightLineIsItsOwnFeature() {
        val p = plan()
        val lines = root(GeoJsonExporter.export(p, profile)).byRole("flightline")
        assertEquals(10, lines.size)
        assertEquals(p.waypoints.size, lines.sumOf { it.obj("properties").int("waypointCount") })
        assertEquals("LineString", lines.first().obj("geometry").str("type"))
        assertTrue(lines.all { it.obj("properties").dbl("lengthMeters") > 0.0 })
    }

    @Test
    fun flightLinesAreIndexedFromOne() {
        val lines = root(GeoJsonExporter.export(plan(), profile)).byRole("flightline")
        assertEquals((1..10).toList(), lines.map { it.obj("properties").int("index") })
    }

    @Test
    fun controlPointsCarryTheirUtmAndTheirProvenance() {
        val json = root(GeoJsonExporter.export(plan(), profile, control))
        val points = json.byRole("ground_control")
        assertEquals(2, points.size)
        val first = points.first().obj("properties")
        assertEquals("CP01", first.str("code"))
        assertEquals("control", first.str("kind"))
        assertEquals("cross", first.str("target"))
        assertEquals(5.0, first.dbl("targetAccuracyMm"), 0.01)
        assertEquals(650.0, first.dbl("heightMeters"), 0.001)
        assertEquals(12.0, first.dbl("horizontalAccuracyMm"), 0.001)
        assertEquals("topografico", first.str("source"))
        assertEquals(32630, first.int("epsg"))
        assertTrue(first.dbl("utmEastingMeters") in 400_000.0..450_000.0)
        assertTrue(first.dbl("utmNorthingMeters") in 4_470_000.0..4_490_000.0)

        val second = points.last().obj("properties")
        assertEquals("check", second.str("kind"))
        assertTrue(second.isJsonNullAt("horizontalAccuracyMm"))
        assertTrue(second.isJsonNullAt("heightMeters"))
    }

    @Test
    fun theTopLevelPropertiesCarryTheCartography() {
        val properties = root(GeoJsonExporter.export(plan(), profile, control)).obj("properties")
        assertEquals(2.0, properties.dbl("groundSampleDistanceCmPerPx"), 0.01)
        assertEquals(2.0, properties.dbl("targetGroundSampleDistanceCmPerPx"), 0.01)
        assertEquals(80, properties.int("forwardOverlapPercent"))
        assertEquals(70, properties.int("sideOverlapPercent"))
        assertEquals(2.4, properties.dbl("areaHectares"), 0.02)
        assertEquals(10, properties.int("flightLineCount"))
        assertEquals("above_ground", properties.str("altitudeReference"))
        assertTrue(properties.bool("terrainFollowing"))
        assertTrue(properties.str("coordinateReferenceSystem").contains("32630"))
        assertEquals(30, properties.obj("utm").int("zone"))
        assertTrue(properties.obj("utm").str("crs").startsWith("EPSG:32630"))
        assertEquals(35.9, properties.obj("camera").dbl("sensorWidthMm"), 0.001)
        assertEquals(8192, properties.obj("camera").int("imageWidthPx"))
        assertEquals(plan().waypoints.size, properties.int("waypointCount"))
    }

    @Test
    fun theDeclaredOverlapIsKeptSeparatelyFromTheMeasuredOne() {
        val properties = root(GeoJsonExporter.export(plan(), profile)).obj("properties")
        assertEquals(80, properties.int("declaredOverlapPercent"))
        assertEquals(80, properties.int("forwardOverlapPercent"))
    }

    @Test
    fun withoutAProfileTheOverlapIsDerivedFromTheLegacyField() {
        val properties = root(GeoJsonExporter.export(plan(), null)).obj("properties")
        assertEquals(SurveyCamera.DEFAULT.id, properties.obj("camera").str("id"))
        assertEquals(20, properties.int("forwardOverlapPercent"))
        assertEquals("above_ground", properties.str("altitudeReference"))
    }

    @Test
    fun awkwardNamesAreEscapedNotDropped() {
        val raw = GeoJsonExporter.export(plan("Bloque \"norte\" & test\nline 2"), profile)
        assertTrue(raw.contains("\\\"norte\\\""))
        assertTrue(raw.contains("\\n"))
        assertEquals("Bloque \"norte\" & test\nline 2", root(raw).str("name"))
    }

    @Test
    fun theExportWorksWithoutAControlNetwork() {
        val json = root(GeoJsonExporter.export(plan(), profile))
        val roles = features(json).map { it.role() }
        assertTrue(roles.contains("footprint"))
        assertTrue(roles.contains("flightline"))
        assertTrue(roles.contains("start"))
        assertTrue(!roles.contains("ground_control"))
    }

    @Test
    fun theStartPointIsTheFirstWaypoint() {
        val p = plan()
        val start = root(GeoJsonExporter.export(p, profile)).byRole("start").single()
        val coordinates = start.obj("geometry").arr("coordinates")
        assertEquals(p.waypoints.first().longitude, coordinates.dblAt(0), 1e-7)
        assertEquals(p.waypoints.first().latitude, coordinates.dblAt(1), 1e-7)
    }

    @Test
    fun theFlightLineSplitIsReversible() {
        val p = plan()
        val lines = GeoJsonExporter.splitIntoFlightLines(p)
        assertEquals(10, lines.size)
        assertEquals(p.waypoints.size, lines.sumOf { it.size })
        assertEquals(p.waypoints.map { it.index }, lines.flatten().map { it.index })
    }

    @Test
    fun aSingleWaypointIsOneFlightLineOfOnePoint() {
        val single = plan().copy(waypoints = listOf(PlannedWaypoint(0, 40.0, -3.0, 100.0, 4.0)))
        val lines = GeoJsonExporter.splitIntoFlightLines(single)
        assertEquals(1, lines.size)
        assertEquals(1, lines.single().size)
    }

    @Test
    fun aPlanWithNoWaypointsHasNoFlightLines() {
        val empty = plan().copy(waypoints = emptyList())
        assertTrue(GeoJsonExporter.splitIntoFlightLines(empty).isEmpty())
    }

    @Test
    fun theSchemaIsStamped() {
        assertEquals(GeoJsonExporter.SCHEMA, root(GeoJsonExporter.export(plan(), profile)).str("djiflypro_schema"))
    }

    @Test
    fun numbersAreFormattedForEveryLocale() {
        assertEquals("0.0000", GeoJsonExporter.number(0.0))
        assertEquals("0.0000", GeoJsonExporter.number(-0.00001))
        assertEquals("1.5000", GeoJsonExporter.number(1.5))
        assertEquals("null", GeoJsonExporter.number(Double.NaN))
        assertEquals("null", GeoJsonExporter.number(Double.POSITIVE_INFINITY))
        assertEquals("-3.7038000", GeoJsonExporter.number(-3.7038, 7))
    }

    @Test
    fun stringsAreEscapedForJson() {
        assertEquals("\"\"", GeoJsonExporter.json(""))
        assertEquals("null", GeoJsonExporter.json(null))
        assertEquals("\"a\\u0001b\"", GeoJsonExporter.json("a\u0001b"))
        assertEquals("\"tab\\there\"", GeoJsonExporter.json("tab\there"))
        assertEquals("\"quote\\\"\"", GeoJsonExporter.json("quote\""))
    }

    @Test
    fun theFeatureCountIsStable() {
        val p = plan()
        val withControl = features(root(GeoJsonExporter.export(p, profile, control))).size
        val withoutControl = features(root(GeoJsonExporter.export(p, profile))).size
        assertEquals(withoutControl + 2, withControl)
    }

    @Test
    fun anEmptyArrayIsStillAnArray() {
        val json = root(GeoJsonExporter.export(plan(), profile))
        assertTrue(json.get("features") is JsonArray)
        val element: JsonElement = json.get("features")
        assertTrue(element.asJsonArray.size() > 0)
    }
}
