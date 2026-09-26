package dji.sampleV5.aircraft.pro.cartography

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dji.sampleV5.aircraft.pro.mission.FinishAction
import dji.sampleV5.aircraft.pro.mission.MissionGeometry
import dji.sampleV5.aircraft.pro.mission.MissionPlan
import dji.sampleV5.aircraft.pro.mission.MissionRequest
import dji.sampleV5.aircraft.pro.mission.MissionTemplate
import dji.sampleV5.aircraft.pro.mission.RoutePattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CartographyReportBuilderTest {

    private val profile = CartographyProfile(
        cameraId = "zenmuse-p1",
        targetGsdCentimetersPerPixel = 2.0,
        forwardOverlapPercent = 80,
        sideOverlapPercent = 70,
        terrainFollowing = true,
        crossTrack = true,
        altitudeReference = AltitudeReference.ABOVE_GROUND
    )

    private fun plan(): MissionPlan = MissionGeometry.plan(
        MissionRequest(
            name = "Bloque centro",
            template = MissionTemplate.FIELD,
            centerLatitude = 40.4168,
            centerLongitude = -3.7038,
            lengthMeters = 200.0,
            widthMeters = 120.0,
            heightMeters = 30.0,
            bearingDegrees = 12.0,
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
            latitude = 40.4050,
            longitude = -3.7150,
            heightMeters = 650.0,
            horizontalAccuracyMillimeters = 12.0,
            source = "topografico"
        ),
        GroundControlPoint(
            id = "gcp-2",
            code = "CP02",
            latitude = 40.4050,
            longitude = -3.6925,
            heightMeters = 651.0,
            horizontalAccuracyMillimeters = 12.0,
            source = "topografico"
        ),
        GroundControlPoint(
            id = "gcp-3",
            code = "CP03",
            latitude = 40.4285,
            longitude = -3.6925,
            heightMeters = 652.0,
            horizontalAccuracyMillimeters = 12.0,
            source = "topografico"
        ),
        GroundControlPoint(
            id = "gcp-4",
            code = "CP04",
            latitude = 40.4285,
            longitude = -3.7150,
            heightMeters = 653.0,
            horizontalAccuracyMillimeters = 12.0,
            source = "topografico"
        ),
        GroundControlPoint(
            id = "gcp-5",
            code = "CP05",
            latitude = 40.4168,
            longitude = -3.7038,
            role = ControlPointRole.CHECK,
            target = ControlPointTarget.CHECKERBOARD
        )
    )

    private fun report(json: String): JsonObject = JsonParser.parseString(json).asJsonObject

    private fun JsonObject.obj(key: String): JsonObject = getAsJsonObject(key)

    @Test
    fun theReportDeclaresItselfAPlan() {
        val root = report(CartographyReportBuilder.build(plan(), profile, control))
        assertEquals(CartographyReportBuilder.SCHEMA, root.get("schema").asString)
        assertEquals("flight_plan_report", root.get("documentType").asString)
        // "planned", not "flown": nothing has been flown when this is written.
        assertEquals("planned", root.get("status").asString)
    }

    @Test
    fun theMissionSectionIsComplete() {
        val p = plan()
        val mission = report(CartographyReportBuilder.build(p, profile, control)).obj("mission")
        assertEquals(p.id, mission.get("id").asString)
        assertEquals("Bloque centro", mission.get("name").asString)
        assertEquals("field", mission.get("template").asString)
        assertEquals("parallel", mission.get("routePattern").asString)
        assertEquals("return_home", mission.get("finishAction").asString)
        assertEquals(200.0, mission.get("blockLengthMeters").asDouble, 0.01)
        assertEquals(120.0, mission.get("blockWidthMeters").asDouble, 0.01)
        assertEquals(12.0, mission.get("bearingDegrees").asDouble, 0.01)
        assertEquals(40.4168, mission.get("centerLatitude").asDouble, 1e-6)
    }

    @Test
    fun theCartographySectionDescribesThePayload() {
        val cartography = report(CartographyReportBuilder.build(plan(), profile, control)).obj("cartography")
        assertEquals("zenmuse-p1", cartography.get("cameraId").asString)
        assertEquals(35.9, cartography.get("sensorWidthMm").asDouble, 0.01)
        assertEquals(35.0, cartography.get("focalLengthMm").asDouble, 0.01)
        assertEquals(8192, cartography.get("imageWidthPx").asInt)
        assertEquals(54.3, cartography.get("horizontalFovDegrees").asDouble, 0.05)
        assertEquals(2.0, cartography.get("targetGsdCmPerPx").asDouble, 0.01)
        assertEquals(2.0, cartography.get("achievedGsdCmPerPx").asDouble, 0.01)
        assertEquals(80, cartography.get("forwardOverlapPercent").asInt)
        assertEquals(70, cartography.get("sideOverlapPercent").asInt)
        assertEquals("above_ground", cartography.get("altitudeReference").asString)
        assertTrue(cartography.get("terrainFollowing").asBoolean)
        assertTrue(cartography.get("crossTrack").asBoolean)
    }

    @Test
    fun theCoverageSectionIsInSurveyUnits() {
        val p = plan()
        val coverage = report(CartographyReportBuilder.build(p, profile, control)).obj("coverage")
        assertEquals(2.4, coverage.get("areaHectares").asDouble, 0.02)
        assertEquals(24_000.0, coverage.get("areaSquareMeters").asDouble, 40.0)
        assertEquals(p.waypoints.size, coverage.get("waypointCount").asInt)
        assertEquals(10, coverage.get("flightLineCount").asInt)
        assertTrue(coverage.get("photoCount").asInt > 0)
        assertTrue(coverage.get("totalDistanceMeters").asDouble > 0.0)
        assertTrue(coverage.get("estimatedDurationSeconds").asDouble > 0.0)
        assertTrue(coverage.get("estimatedStorageGigabytesJpeg").asDouble > 0.0)
        assertTrue(
            coverage.get("estimatedStorageGigabytesRaw").asDouble >
                coverage.get("estimatedStorageGigabytesJpeg").asDouble
        )
    }

    @Test
    fun theReferenceSystemGivesBothRepresentations() {
        val reference = report(CartographyReportBuilder.build(plan(), profile, control)).obj("referenceSystem")
        assertEquals("EPSG:4326", reference.get("geographic").asString)
        assertEquals("EPSG:32630 (WGS 84 / UTM zone 30N)", reference.get("projected").asString)
        assertEquals(30, reference.get("utmZone").asInt)
        assertEquals(-3.0, reference.get("centralMeridianDegrees").asDouble, 1e-9)
        assertEquals(0.9996, reference.get("scaleFactor").asDouble, 1e-9)
        assertTrue(reference.get("utmEastingMeters").asDouble in 400_000.0..450_000.0)
    }

    @Test
    fun theGroundControlSectionCarriesEveryPoint() {
        val groundControl = report(CartographyReportBuilder.build(plan(), profile, control)).obj("groundControl")
        assertEquals(5, groundControl.get("pointCount").asInt)
        assertEquals(4, groundControl.get("controlCount").asInt)
        assertEquals(1, groundControl.get("checkCount").asInt)
        assertTrue(groundControl.get("isUsable").asBoolean)
        assertTrue(groundControl.get("blockCoverageRatio").asDouble > 0.5)
        val points = groundControl.getAsJsonArray("points")
        assertEquals(5, points.size())
        val first = points[0].asJsonObject
        assertEquals("CP01", first.get("code").asString)
        assertEquals("control", first.get("role").asString)
        assertEquals(12.0, first.get("horizontalAccuracyMm").asDouble, 0.01)
        assertEquals(5.0, first.get("targetAccuracyMm").asDouble, 0.01)
        assertEquals(32630, first.get("epsg").asInt)
        assertTrue(points[4].asJsonObject.get("role").asString == "check")
    }

    @Test
    fun aBlockWithNoControlSaysSoAndIsNotUsable() {
        val groundControl = report(CartographyReportBuilder.build(plan(), profile)).obj("groundControl")
        assertEquals(0, groundControl.get("pointCount").asInt)
        assertFalse(groundControl.get("isUsable").asBoolean)
        assertTrue(groundControl.getAsJsonArray("points").size() == 1)
        assertTrue(groundControl.getAsJsonArray("points")[0].isJsonNull)
    }

    @Test
    fun theChecksSectionSurfacesProblems() {
        val checks = report(CartographyReportBuilder.build(plan(), profile)).obj("checks")
        assertTrue(checks.get("profileValid").asBoolean)
        assertTrue(checks.get("profileErrors").asJsonArray.size() == 0)
        assertTrue(checks.get("controlErrors").asJsonArray.size() > 0)
        assertTrue(checks.getAsJsonArray("planWarnings").size() > 0)
    }

    @Test
    fun aPlanWithoutAProfileIsMarkedInvalid() {
        val checks = report(CartographyReportBuilder.build(plan(), null)).obj("checks")
        assertFalse(checks.get("profileValid").asBoolean)
        assertTrue(checks.getAsJsonArray("profileErrors")[0].asString.contains("no cartography profile"))
    }

    @Test
    fun theLimitationsSayTheBlockHasNotBeenFlown() {
        val limitations = report(CartographyReportBuilder.build(plan(), profile, control))
            .getAsJsonArray("limitations")
        assertTrue(limitations.size() >= 4)
        val text = (0 until limitations.size()).joinToString(" ") { limitations[it].asString }
        assertTrue(text.contains("not been flown"))
        assertTrue(text.contains("Ground sample distance holds over ground of known height"))
        assertTrue(text.contains("bounded by its ground control"))
        assertTrue(text.contains("Airspace"))
    }

    @Test
    fun theReportIsReproducible() {
        val p = plan()
        assertEquals(
            CartographyReportBuilder.build(p, profile, control),
            CartographyReportBuilder.build(p, profile, control)
        )
    }

    @Test
    fun awkwardNamesSurviveTheRoundTrip() {
        val p = plan().copy(request = plan().request.copy(name = "Sector \"A\" & B <2026>"))
        val root = report(CartographyReportBuilder.build(p, profile))
        assertEquals("Sector \"A\" & B <2026>", root.obj("mission").get("name").asString)
    }
}
