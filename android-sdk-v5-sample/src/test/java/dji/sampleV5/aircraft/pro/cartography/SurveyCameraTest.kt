package dji.sampleV5.aircraft.pro.cartography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyCameraTest {

    @Test
    fun pixelPitchIsSensorWidthOverResolution() {
        assertEquals(35.9 / 8192, SurveyCamera.ZENMUSE_P1.pixelPitchMillimeters, 1e-12)
    }

    @Test
    fun horizontalFovMatchesTheLensRelation() {
        // 2 * atan(17.95 / 35.0) = 54.3 degrees for the P1.
        assertEquals(54.3, SurveyCamera.ZENMUSE_P1.horizontalFovDegrees, 0.05)
    }

    @Test
    fun verticalFovIsSmallerThanHorizontalForALandscapeSensor() {
        val camera = SurveyCamera.ZENMUSE_P1
        assertTrue(camera.verticalFovDegrees < camera.horizontalFovDegrees)
        assertTrue(camera.aspectRatio > 1.0)
    }

    @Test
    fun everyBuiltInCameraIsUsable() {
        SurveyCamera.BUILT_INS.forEach { camera ->
            val validation = camera.validate()
            assertTrue("${camera.id}: ${validation.errors}", validation.isValid)
        }
    }

    @Test
    fun builtInIdsAreUnique() {
        val ids = SurveyCamera.BUILT_INS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun everyBuiltInCameraIsResolvableById() {
        SurveyCamera.BUILT_INS.forEach { camera ->
            assertEquals(camera.id, SurveyCamera.find(camera.id)?.id)
            assertEquals(camera.id, SurveyCamera.find(camera.id.uppercase())?.id)
        }
    }

    @Test
    fun anUnknownIdFallsBackToTheDefaultPayload() {
        assertNull(SurveyCamera.find("nope"))
        assertEquals(SurveyCamera.DEFAULT.id, SurveyCamera.require("nope").id)
        assertEquals(SurveyCamera.DEFAULT.id, SurveyCamera.require(null).id)
    }

    @Test
    fun validationRejectsNonPhysicalValues() {
        val broken = listOf(
            SurveyCamera.custom("a", "A", 0.0, 10.0, 10.0, 100, 100),
            SurveyCamera.custom("b", "B", 10.0, 10.0, -1.0, 100, 100),
            SurveyCamera.custom("c", "C", 10.0, 10.0, 10.0, 0, 100),
            SurveyCamera.custom("d", "D", Double.NaN, 10.0, 10.0, 100, 100)
        )
        broken.forEach { assertTrue(!it.validate().isValid) }
    }

    @Test
    fun validationRequiresAnIdentifier() {
        val anonymous = SurveyCamera.custom("   ", "Cámara", 10.0, 10.0, 10.0, 100, 100)
        val validation = anonymous.validate()
        assertTrue(validation.errors.any { it.contains("id") })
    }

    @Test
    fun customCamerasAreNotMarkedAsFixedLens() {
        val custom = SurveyCamera.custom("c", "C", 17.3, 13.0, 12.0, 5280, 3956)
        assertTrue(!custom.isMechanicalOrFixedLens)
        assertEquals("C", custom.displayName)
    }

    @Test
    fun aCustomCameraWithABlankNameGetsAReadableDefault() {
        val custom = SurveyCamera.custom("c", "   ", 17.3, 13.0, 12.0, 5280, 3956)
        assertEquals("Cámara personalizada", custom.displayName)
    }

    @Test
    fun aCustomCameraIsValidatedForPhotogrammetry() {
        // A Mavic 3E sized sensor and lens at 100 m gives 2.73 cm/px.
        val custom = SurveyCamera.custom("c", "C", 17.3, 13.0, 12.0, 5280, 3956)
        val gsd = GroundSampleDistance.gsdCentimetersPerPixel(100.0, custom)
        assertEquals(2.7304, gsd, 0.001)
        assertEquals(100.0, GroundSampleDistance.heightForGsd(gsd, custom), 1e-9)
    }

    @Test
    fun everyBuiltInCameraProducesAUsableMappingHeight() {
        // None of the shipped payloads should need an absurd height for 2 cm/px.
        SurveyCamera.BUILT_INS.forEach { camera ->
            val height = GroundSampleDistance.heightForGsd(2.0, camera)
            assertTrue("${camera.id} needs $height m", height in 10.0..1_000.0)
        }
    }

    @Test
    fun builtInCameraOrderIsStable() {
        assertEquals(SurveyCamera.ZENMUSE_P1.id, SurveyCamera.BUILT_INS.first().id)
        assertNotNull(SurveyCamera.BUILT_INS.find { it.id == "zenmuse-l2" })
        assertTrue(SurveyCamera.BUILT_INS.any { it.id == "matrice-4e" })
    }

    @Test
    fun heightAndFootprintAgreeInBothDirections() {
        // heightForGsd and footprintWidthMeters are the same relation written
        // twice, so solving one and feeding the other has to come back.
        val camera = SurveyCamera.ZENMUSE_P1
        val height = GroundSampleDistance.heightForGsd(2.5, camera)
        val width = GroundSampleDistance.footprintWidthMeters(height, camera)
        val backAgain = width * camera.focalLengthMillimeters / camera.sensorWidthMillimeters
        assertEquals(height, backAgain, 1e-9)
    }
}
