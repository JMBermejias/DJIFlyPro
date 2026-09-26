package dji.sampleV5.aircraft.pro.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DroneCapabilitiesTest {
    private val validatedProfile = ValidatedWpmlProfile(
        productName = "M30_SERIES",
        remoteControllerName = "DJI_RC_PLUS_2",
        aircraftFirmwareVersion = "01.01.0000",
        remoteControllerFirmwareVersion = "02.00.0100",
        validationReference = "physical-test-record-1"
    )

    private fun evaluate(
        product: String,
        firmware: String?,
        connected: Boolean = true,
        registered: Boolean = true,
        remoteController: String? = "DJI_RC_PLUS_2",
        remoteFirmware: String? = "02.00.0100",
        profiles: Set<ValidatedWpmlProfile> = setOf(validatedProfile),
        firmwareWaypointSupport: FirmwareWaypointSupport = FirmwareWaypointSupport.UNKNOWN
    ) = DroneCapabilities.evaluateProduct(
        productName = product,
        connected = connected,
        registered = registered,
        firmwareVersion = firmware,
        remoteControllerName = remoteController,
        remoteControllerFirmwareVersion = remoteFirmware,
        validatedProfiles = profiles,
        firmwareWaypointSupport = firmwareWaypointSupport
    )

    @Test
    fun blocksEnterpriseProductWhenFirmwareIsMissing() {
        val result = evaluate("M350_RTK", null)

        assertFalse(result.waypointUploadSupported)
        assertFalse(result.waypointExecutionSupported)
        assertTrue(result.reason.contains("firmware"))
    }

    @Test
    fun rejectsNonVersionedFirmwareLabels() {
        listOf("unknown", "N/A", "not-a-version", "   ").forEach { firmware ->
            val result = evaluate("M350_RTK", firmware)
            assertFalse("Expected $firmware to be rejected", result.waypointExecutionSupported)
        }
    }

    @Test
    fun blocksPhantomAndMini3EvenWithReviewedProfile() {
        assertFalse(evaluate("P4P", "01.01.0000").waypointExecutionSupported)
        assertFalse(evaluate("DJI_MINI_3_PRO", "01.01.0000").waypointExecutionSupported)
    }

    @Test
    fun doesNotEnableEnterpriseProductWithoutReviewedProfile() {
        val result = evaluate("M30_SERIES", "01.01.0000", profiles = emptySet())

        assertFalse(result.waypointUploadSupported)
        assertFalse(result.waypointExecutionSupported)
        assertTrue(result.reason.contains("perfil revisado"))
    }

    @Test
    fun enablesOnlyConnectedRegisteredExactReviewedCombination() {
        val allowed = evaluate("M30_SERIES", "01.01.0000")
        assertTrue(allowed.waypointUploadSupported)
        assertTrue(allowed.waypointExecutionSupported)

        assertFalse(evaluate("M30_SERIES", "01.01.0000", connected = false).waypointUploadSupported)
        assertFalse(evaluate("M30_SERIES", "01.01.0000", registered = false).waypointExecutionSupported)
        assertFalse(
            evaluate("M30_SERIES", "01.01.0000", remoteController = "DJI_RC_N3")
                .waypointExecutionSupported
        )
        assertFalse(
            evaluate("M30_SERIES", "01.01.0000", remoteFirmware = "02.00.0101")
                .waypointExecutionSupported
        )
    }

    @Test
    fun normalizesProductAndFirmwareForDisplay() {
        val result = evaluate(" m30_series ", " 01.01.0000 ")

        assertEquals("m30_series", result.productName)
        assertEquals("01.01.0000", result.firmwareVersion)
        assertEquals("DJI_RC_PLUS_2", result.remoteControllerName)
        assertEquals("02.00.0100", result.remoteControllerFirmwareVersion)
    }

    /**
     * DJI states that Mini 3 and Mini 3 Pro firmware do not implement waypoint
     * missions, so DJI Fly has no route feature on them either. Neither variant
     * may run a WPML mission, and the operator deserves to be told why.
     */
    @Test
    fun bothMini3VariantsAreBlockedWithTheFirmwareReason() {
        listOf("DJI_MINI_3", "DJI_MINI_3_PRO").forEach { product ->
            val result = evaluate(
                product = product,
                firmware = "01.01.0000",
                remoteController = "DJI_RC_N3",
                remoteFirmware = "02.00.0100"
            )

            assertFalse(product, result.waypointUploadSupported)
            assertFalse(product, result.waypointExecutionSupported)
            assertTrue(product, result.reason.contains("no admite misiones wayline"))
        }
    }

    /**
     * The Mini 3 family is matched on the exact ProductType name. A substring
     * test would also accept names this gate has never seen, such as a future
     * DJI_MINI_3X, and would then assert something about it that nobody checked.
     */
    @Test
    fun anUnknownProductContainingMini3IsNotGivenTheMini3Verdict() {
        val result = evaluate("DJI_MINI_3X", "01.01.0000")

        assertFalse(result.waypointExecutionSupported)
        assertFalse(
            "must fall through to the generic non-enterprise reason",
            result.reason.contains("Mini 3 and Mini 3 Pro")
        )
    }

    /**
     * The aircraft reporting NOT_SUPPORTED is stronger evidence than the
     * product table, and it has to beat the "no reviewed profile" message,
     * which would otherwise send the operator off to register a profile that
     * can never help.
     */
    @Test
    fun aFirmwareNotSupportedReportOutranksTheMissingProfile() {
        val result = evaluate(
            product = "M30_SERIES",
            firmware = "01.01.0000",
            profiles = emptySet(),
            firmwareWaypointSupport = FirmwareWaypointSupport.NOT_SUPPORTED
        )

        assertFalse(result.waypointUploadSupported)
        assertFalse(result.waypointExecutionSupported)
        assertTrue(result.reason, result.reason.contains("no admite misiones wayline"))
        assertFalse(result.reason, result.reason.contains("perfil revisado"))
        assertEquals(FirmwareWaypointSupport.NOT_SUPPORTED, result.firmwareWaypointSupport)
    }

    @Test
    fun aFirmwareNotSupportedReportClosesAGateThatWouldOtherwiseBeOpen() {
        val allowed = evaluate("M30_SERIES", "01.01.0000")
        assertTrue("precondition: a reviewed profile opens the gate", allowed.waypointExecutionSupported)

        val blocked = evaluate(
            product = "M30_SERIES",
            firmware = "01.01.0000",
            firmwareWaypointSupport = FirmwareWaypointSupport.NOT_SUPPORTED
        )
        assertFalse(blocked.waypointUploadSupported)
        assertFalse(blocked.waypointExecutionSupported)
    }

    @Test
    fun anUnknownFirmwareReportChangesNothing() {
        val result = evaluate("M30_SERIES", "01.01.0000", firmwareWaypointSupport = FirmwareWaypointSupport.UNKNOWN)

        assertTrue(result.waypointExecutionSupported)
        assertEquals(FirmwareWaypointSupport.UNKNOWN, result.firmwareWaypointSupport)
    }

    /** The RC-N3 is a real RemoteControllerType, so it must survive normalization. */
    @Test
    fun theRcN3IsAUsableRemoteControllerIdentity() {
        val result = evaluate("M30_SERIES", "01.01.0000", remoteController = "DJI_RC_N3")

        assertEquals("DJI_RC_N3", result.remoteControllerName)
    }
}
