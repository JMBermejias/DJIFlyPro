package dji.sampleV5.aircraft.pro.cartography

import dji.sampleV5.aircraft.pro.mission.FinishAction
import dji.sampleV5.aircraft.pro.mission.MissionGeometry
import dji.sampleV5.aircraft.pro.mission.MissionPlan
import dji.sampleV5.aircraft.pro.mission.MissionRequest
import dji.sampleV5.aircraft.pro.mission.MissionTemplate
import dji.sampleV5.aircraft.pro.mission.RoutePattern
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class KmlExporterTest {

    private val profile = CartographyProfile(
        cameraId = "zenmuse-p1",
        targetGsdCentimetersPerPixel = 2.0,
        forwardOverlapPercent = 80,
        sideOverlapPercent = 70
    )

    private fun plan(name: String = "Bloque \"norte\" & <test>"): MissionPlan = MissionGeometry.plan(
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
            source = "topografico & тогда"
        ),
        GroundControlPoint(
            id = "gcp-2",
            code = "CP02",
            latitude = 40.4250,
            longitude = -3.6950,
            role = ControlPointRole.CHECK,
            target = ControlPointTarget.CHECKERBOARD
        )
    )

    private fun document(kml: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        return factory.newDocumentBuilder().parse(kml.byteInputStream()).documentElement
    }

    private fun Element.placemarks(): List<Element> {
        val found = mutableListOf<Element>()
        val nodes = getElementsByTagName("Placemark")
        for (i in 0 until nodes.length) {
            (nodes.item(i) as Element).let(found::add)
        }
        return found
    }

    private fun Element.tag(name: String): List<Element> {
        val found = mutableListOf<Element>()
        val nodes = getElementsByTagName(name)
        for (i in 0 until nodes.length) {
            (nodes.item(i) as Element).let(found::add)
        }
        return found
    }

    private fun Element.textOf(name: String): String? = tag(name).firstOrNull()?.textContent

    @Test
    fun theExportIsWellFormedKml() {
        val root = document(KmlExporter.export(plan(), profile, control))
        assertEquals("kml", root.tagName)
        assertEquals(1, root.tag("Document").size)
        assertTrue(root.tag("Style").isNotEmpty())
    }

    @Test
    fun theFootprintIsAPolygonClampedToTheGround() {
        val root = document(KmlExporter.export(plan(), profile))
        val footprint = root.placemarks().first { it.textOf("name") == "Zona de vuelo" }
        val polygon = footprint.tag("Polygon").single()
        assertEquals("clampToGround", polygon.textOf("altitudeMode"))
        val ring = polygon.tag("outerBoundaryIs").single().tag("LinearRing").single()
        val coordinates = ring.tag("coordinates").single().textContent.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        assertEquals(5, coordinates.size)
        assertEquals(coordinates.first(), coordinates.last())
    }

    @Test
    fun everyFlightLineIsAPlacemarkWithAltitude() {
        val p = plan()
        val root = document(KmlExporter.export(p, profile))
        val lines = root.placemarks().filter { it.textOf("name")?.startsWith("Línea ") == true }
        assertEquals(10, lines.size)
        assertEquals(
            p.waypoints.size,
            lines.sumOf { it.tag("coordinates").single().textContent.lines().count { l -> l.isNotBlank() } }
        )
        lines.forEach { line ->
            val lineString = line.tag("LineString").single()
            assertEquals("relativeToGround", lineString.textOf("altitudeMode"))
            val first = lineString.tag("coordinates").single().textContent.lines()
                .map { it.trim() }
                .first { it.isNotEmpty() }
            val parts = first.split(",")
            assertEquals(3, parts.size)
            assertEquals(159.7, parts[2].toDouble(), 0.1)
        }
    }

    @Test
    fun controlPointsAreSeparatePlacemarksWithTheirStyle() {
        val root = document(KmlExporter.export(plan(), profile, control))
        val points = root.placemarks().filter { it.textOf("name") == "CP01" || it.textOf("name") == "CP02" }
        assertEquals(2, points.size)
        val cp01 = points.first { it.textOf("name") == "CP01" }
        assertEquals("#control", cp01.textOf("styleUrl"))
        val description = cp01.textOf("description")!!
        assertTrue(description.contains("topografico & тогда"))
        assertTrue(description.contains("E "))
        val point = cp01.tag("Point").single()
        // The height is known, so the point sits relative to the ground.
        assertEquals("relativeToGround", point.textOf("altitudeMode"))
        assertTrue(point.textOf("coordinates")!!.endsWith(",650.00"))

        // The check point has no height, so it clamps to the terrain.
        val cp02 = points.first { it.textOf("name") == "CP02" }
        assertEquals("#check", cp02.textOf("styleUrl"))
        assertEquals("clampToGround", cp02.tag("Point").single().textOf("altitudeMode"))
    }

    @Test
    fun theAltitudeModeFollowsTheProfileReference() {
        fun altitudeModeFor(reference: AltitudeReference): String? {
            val root = document(
                KmlExporter.export(
                    plan(),
                    profile.copy(altitudeReference = reference)
                )
            )
            return root.placemarks()
                .first { it.textOf("name")?.startsWith("Línea ") == true }
                .tag("LineString")
                .single()
                .textOf("altitudeMode")
        }
        assertEquals("relativeToGround", altitudeModeFor(AltitudeReference.ABOVE_GROUND))
        assertEquals("absolute", altitudeModeFor(AltitudeReference.AMSL))
        assertEquals("relativeToSeaLevel", altitudeModeFor(AltitudeReference.RELATIVE_TO_TAKEOFF))
    }

    @Test
    fun theMetadataPlacemarkCarriesTheFlightSheet() {
        val root = document(KmlExporter.export(plan(), profile, control))
        val sheet = root.placemarks().first { it.textOf("name") == "Ficha del vuelo" }
        val facts = sheet.textOf("description")!!
        assertTrue(facts.contains("EPSG:32630"))
        assertTrue(facts.contains("2.00 cm/px"))
        assertTrue(facts.contains("Zenmuse P1"))
        assertTrue(facts.contains("ha"))
        assertTrue(facts.contains("No certifica vuelo seguro"))
    }

    @Test
    fun theDocumentDescriptionRepeatsTheSummary() {
        val root = document(KmlExporter.export(plan(), profile))
        val description = root.tag("Document").single().textOf("description")!!
        assertTrue(description.contains("Superficie"))
        assertTrue(description.contains("EPSG"))
    }

    @Test
    fun awkwardTextIsEscapedSoTheDocumentStaysWellFormed() {
        val raw = KmlExporter.export(plan("Bloque \"norte\" & <test>"), profile)
        assertTrue(raw.contains("&amp;"))
        assertTrue(raw.contains("&lt;test&gt;"))
        assertTrue(raw.contains("&quot;"))
        // The hostile text does not appear raw anywhere.
        assertTrue(!raw.contains("<test>"))
        val root = document(raw)
        assertEquals("Bloque \"norte\" & <test>", root.tag("Document").single().textOf("name"))
    }

    @Test
    fun theXmlEscaperCoversEveryDangerousCharacter() {
        assertEquals("&amp;&lt;&gt;&quot;&apos;", KmlExporter.xml("&<>\"'"))
        assertEquals("plain", KmlExporter.xml("plain"))
        assertEquals("keeps\nnewlines\tand tabs", KmlExporter.xml("keeps\nnewlines\tand tabs"))
        assertEquals("strips ", KmlExporter.xml("strips\u0001"))
    }

    @Test
    fun theExportWorksWithoutAControlNetwork() {
        val root = document(KmlExporter.export(plan(), profile))
        val names = root.placemarks().mapNotNull { it.textOf("name") }
        assertTrue(names.contains("Zona de vuelo"))
        assertTrue(names.none { it.startsWith("CP") })
    }

    @Test
    fun theExportWorksWithoutACartographyProfile() {
        val root = document(KmlExporter.export(plan(), null))
        val facts = root.placemarks().first { it.textOf("name") == "Ficha del vuelo" }.textOf("description")!!
        assertTrue(facts.contains(SurveyCamera.DEFAULT.displayName))
    }

    @Test
    fun everyStyleReferencedExists() {
        val raw = KmlExporter.export(plan(), profile, control)
        val root = document(raw)
        val defined = root.tag("Style").map { it.getAttribute("id") }.toSet()
        val referenced = root.placemarks().mapNotNull { it.textOf("styleUrl") }
            .map { it.removePrefix("#") }
            .toSet()
        assertTrue(referenced.isNotEmpty())
        assertTrue("undefined styles: ${referenced - defined}", defined.containsAll(referenced))
    }

    @Test
    fun theXmlEscaperIsAppliedToNamesOnlyOnce() {
        // A double-escaped name would show "&amp;amp;" and break the round trip.
        val raw = KmlExporter.export(plan("A & B"), profile)
        assertTrue(!raw.contains("&amp;amp;"))
        assertEquals("A & B", document(raw).tag("Document").single().textOf("name"))
    }
}
