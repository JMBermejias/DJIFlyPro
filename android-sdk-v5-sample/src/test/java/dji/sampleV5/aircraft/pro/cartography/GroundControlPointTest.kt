package dji.sampleV5.aircraft.pro.cartography

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundControlPointTest {

    private fun point(
        id: String = "gcp-1",
        code: String = "CP01",
        latitude: Double = 40.4168,
        longitude: Double = -3.7038,
        role: ControlPointRole = ControlPointRole.CONTROL,
        target: ControlPointTarget = ControlPointTarget.CROSS,
        accuracy: Double? = 15.0,
        height: Double? = 650.0
    ) = GroundControlPoint(
        id = id,
        code = code,
        latitude = latitude,
        longitude = longitude,
        heightMeters = height,
        role = role,
        target = target,
        horizontalAccuracyMillimeters = accuracy,
        source = "topográfico 1/5000"
    )

    @Test
    fun aWellFormedControlPointIsValid() {
        assertTrue(point().validate().isValid)
    }

    @Test
    fun identityIsRequired() {
        val validation = point(id = " ", code = "").validate()
        assertTrue(validation.errors.any { it.contains("identificador") })
        assertTrue(validation.errors.any { it.contains("código") })
    }

    @Test
    fun coordinatesOutsideWgs84AreRejected() {
        assertTrue(!point(latitude = 91.0).validate().isValid)
        assertTrue(!point(latitude = -91.0).validate().isValid)
        assertTrue(!point(longitude = 181.0).validate().isValid)
        assertTrue(!point(latitude = Double.NaN).validate().isValid)
    }

    @Test
    fun aNonPositiveAccuracyIsRejected() {
        assertTrue(!point(accuracy = 0.0).validate().isValid)
        assertTrue(!point(accuracy = -5.0).validate().isValid)
        assertTrue(point(accuracy = null).validate().isValid)
    }

    @Test
    fun aTerrainFeatureCannotBeControl() {
        val validation = point(target = ControlPointTarget.FEATURE).validate()
        assertTrue(validation.errors.any { it.contains("verificación") })
        // As a check point it is allowed, with a warning about precision.
        val asCheck = point(target = ControlPointTarget.FEATURE, role = ControlPointRole.CHECK).validate()
        assertTrue(asCheck.errors.any { it.contains("no se puede medir con precisión") })
    }

    @Test
    fun rolesAndTargetsParseFromTheirKeys() {
        assertEquals(ControlPointRole.CHECK, ControlPointRole.fromKey("check"))
        assertEquals(ControlPointRole.CONTROL, ControlPointRole.fromKey("nope"))
        assertEquals(ControlPointRole.CONTROL, ControlPointRole.fromKey(null))
        assertEquals(ControlPointTarget.CHECKERBOARD, ControlPointTarget.fromKey("checkerboard"))
        assertEquals(ControlPointTarget.CROSS, ControlPointTarget.fromKey(null))
    }

    @Test
    fun theDistanceBetweenTwoPointsIsSymmetricAndMetric() {
        val a = point(latitude = 40.0, longitude = -3.0)
        val b = point(latitude = 40.01, longitude = -3.0)
        assertEquals(1_113.19, a.distanceTo(b), 2.0)
        assertEquals(a.distanceTo(b), b.distanceTo(a), 1e-9)
        assertEquals(0.0, a.distanceTo(a), 1e-9)
    }

    @Test
    fun aControlPointProjectsIntoTheZoneItSitsIn() {
        val utm = point().utm()
        assertEquals(30, utm.zone)
        assertEquals(32630, utm.epsgCode)
    }

    @Test
    fun aBlockWithNoControlIsNotGeoreferenceable() {
        val assessment = ControlNetwork.assess(emptyList(), 40.0, -3.0)
        assertTrue(!assessment.isValid)
        assertTrue(assessment.errors.any { it.contains("no tiene control terrestre utilizable") })
    }

    @Test
    fun fourSpreadControlPointsMakeAUsableNetwork() {
        val network = listOf(
            point(id = "1", code = "A", latitude = 40.410, longitude = -3.710),
            point(id = "2", code = "B", latitude = 40.410, longitude = -3.700),
            point(id = "3", code = "C", latitude = 40.425, longitude = -3.700),
            point(id = "4", code = "D", latitude = 40.425, longitude = -3.710)
        )
        val assessment = ControlNetwork.assess(network, 40.4168, -3.7038)
        assertTrue(assessment.errors.toString(), assessment.isValid)
        assertEquals(4, assessment.metrics.controlCount)
        assertEquals(0, assessment.metrics.checkCount)
        assertTrue(assessment.metrics.maxControlRadiusMeters > 500.0)
    }

    @Test
    fun tooFewControlPointsIsAnErrorNotAWarning() {
        val assessment = ControlNetwork.assess(
            listOf(
                point(id = "1", code = "A", latitude = 40.410, longitude = -3.710),
                point(id = "2", code = "B", latitude = 40.425, longitude = -3.700)
            ),
            40.4168,
            -3.7038
        )
        assertTrue(!assessment.isValid)
        assertTrue(assessment.errors.any { it.contains("Hacen falta al menos 4") })
    }

    @Test
    fun aBlockWithoutACheckPointIsFlagged() {
        val network = listOf(
            point(id = "1", code = "A", latitude = 40.405, longitude = -3.715),
            point(id = "2", code = "B", latitude = 40.405, longitude = -3.692),
            point(id = "3", code = "C", latitude = 40.430, longitude = -3.692),
            point(id = "4", code = "D", latitude = 40.430, longitude = -3.715)
        )
        val assessment = ControlNetwork.assess(network, 40.4168, -3.7038)
        assertTrue(assessment.isValid)
        assertTrue(assessment.warnings.any { it.contains("verificación independiente") })
    }

    @Test
    fun clusteredControlIsCalledOut() {
        val network = listOf(
            point(id = "1", code = "A", latitude = 40.41680, longitude = -3.70380),
            point(id = "2", code = "B", latitude = 40.41682, longitude = -3.70382),
            point(id = "3", code = "C", latitude = 40.41670, longitude = -3.70370),
            point(id = "4", code = "D", latitude = 40.41690, longitude = -3.70390)
        )
        val assessment = ControlNetwork.assess(
            points = network,
            blockCenterLatitude = 40.4168,
            blockCenterLongitude = -3.7038,
            blockRadiusMeters = ControlNetwork.blockRadiusMeters(200.0, 120.0)
        )
        assertTrue(assessment.warnings.any { it.contains("5 m") })
        assertTrue(assessment.warnings.any { it.contains("del radio del bloque") })
        assertTrue(assessment.metrics.blockCoverageRatio < ControlNetwork.MIN_BLOCK_COVERAGE)
    }

    @Test
    fun collinearControlIsCalledOut() {
        // Four points along one edge of a 200 x 120 block: they cannot resolve
        // the rotation, so the reconstruction stays tilted.
        val network = listOf(
            point(id = "1", code = "A", latitude = 40.4160, longitude = -3.7100),
            point(id = "2", code = "B", latitude = 40.4160, longitude = -3.7060),
            point(id = "3", code = "C", latitude = 40.4160, longitude = -3.7020),
            point(id = "4", code = "D", latitude = 40.4160, longitude = -3.6980)
        )
        val assessment = ControlNetwork.assess(
            points = network,
            blockCenterLatitude = 40.4168,
            blockCenterLongitude = -3.7038,
            blockRadiusMeters = ControlNetwork.blockRadiusMeters(200.0, 120.0)
        )
        assertTrue(assessment.warnings.any { it.contains("una línea") })
    }

    @Test
    fun controlReachingTheBlockEdgesIsAccepted() {
        val assessment = ControlNetwork.assess(
            points = listOf(
                point(id = "1", code = "A", latitude = 40.4050, longitude = -3.7150),
                point(id = "2", code = "B", latitude = 40.4050, longitude = -3.6925),
                point(id = "3", code = "C", latitude = 40.4285, longitude = -3.6925),
                point(id = "4", code = "D", latitude = 40.4285, longitude = -3.7150),
                point(id = "5", code = "E", latitude = 40.4168, longitude = -3.7038, role = ControlPointRole.CHECK)
            ),
            blockCenterLatitude = 40.4168,
            blockCenterLongitude = -3.7038,
            blockRadiusMeters = ControlNetwork.blockRadiusMeters(200.0, 120.0)
        )
        assertTrue(assessment.isValid)
        assertTrue(assessment.warnings.none { it.contains("una línea") })
        assertTrue(assessment.warnings.none { it.contains("block radius") })
        assertTrue(assessment.metrics.blockCoverageRatio >= ControlNetwork.MIN_BLOCK_COVERAGE)
        assertEquals(5, assessment.metrics.totalCount)
        assertEquals(4, assessment.metrics.controlCount)
        assertEquals(1, assessment.metrics.checkCount)
    }

    @Test
    fun controlWithoutHeightsMeansATwoDimensionalBlock() {
        val network = listOf(
            point(id = "1", code = "A", latitude = 40.405, longitude = -3.715, height = null),
            point(id = "2", code = "B", latitude = 40.405, longitude = -3.692, height = null),
            point(id = "3", code = "C", latitude = 40.430, longitude = -3.692, height = null),
            point(id = "4", code = "D", latitude = 40.430, longitude = -3.715, height = null)
        )
        val assessment = ControlNetwork.assess(network, 40.4168, -3.7038)
        assertTrue(assessment.warnings.any { it.contains("georreferenciar en 2D") })
    }

    @Test
    fun poorControlAccuracyLimitsTheProduct() {
        val network = listOf(
            point(id = "1", code = "A", latitude = 40.405, longitude = -3.715, accuracy = 120.0),
            point(id = "2", code = "B", latitude = 40.405, longitude = -3.692, accuracy = 120.0),
            point(id = "3", code = "C", latitude = 40.430, longitude = -3.692, accuracy = 120.0),
            point(id = "4", code = "D", latitude = 40.430, longitude = -3.715, accuracy = 120.0)
        )
        val assessment = ControlNetwork.assess(network, 40.4168, -3.7038)
        assertTrue(assessment.warnings.any { it.contains("más exacto que su control") })
    }

    @Test
    fun anUnusablePointIsCountedAndReported() {
        val network = listOf(
            point(id = "1", code = "A", latitude = 40.405, longitude = -3.715),
            point(id = "2", code = "B", latitude = 40.405, longitude = -3.692),
            point(id = "3", code = "C", latitude = 40.430, longitude = -3.692),
            point(id = "4", code = "D", latitude = 40.430, longitude = -3.715),
            point(id = "5", code = "E", latitude = 999.0, longitude = -3.715)
        )
        val assessment = ControlNetwork.assess(network, 40.4168, -3.7038)
        assertTrue(assessment.errors.any { it.contains("no son utilizables") })
        assertEquals(4, assessment.metrics.totalCount)
    }

    @Test
    fun duplicateControlIsDetected() {
        val a = point(id = "1", code = "A", latitude = 40.4168, longitude = -3.7038)
        val b = point(id = "2", code = "B", latitude = 40.4168, longitude = -3.7038)
        assertTrue(!ControlNetwork.isDistinctEnough(b, listOf(a)))
        assertTrue(ControlNetwork.isDistinctEnough(b, emptyList()))
    }

    @Test
    fun targetAccuracyIsOrderedTheWayRealTargetsAre() {
        assertTrue(
            ControlPointTarget.CHECKERBOARD.typicalAccuracyMillimeters <
                ControlPointTarget.CROSS.typicalAccuracyMillimeters
        )
        assertTrue(
            ControlPointTarget.CROSS.typicalAccuracyMillimeters <
                ControlPointTarget.PERMANENT_MARK.typicalAccuracyMillimeters
        )
        assertTrue(
            ControlPointTarget.PERMANENT_MARK.typicalAccuracyMillimeters <
                ControlPointTarget.FEATURE.typicalAccuracyMillimeters
        )
    }

    @Test
    fun onlyATerrainFeatureIsUnusableAsControl() {
        ControlPointTarget.entries.forEach { target ->
            assertEquals(
                target.displayName,
                target != ControlPointTarget.FEATURE,
                target.isSuitableAsControl
            )
        }
    }
}
