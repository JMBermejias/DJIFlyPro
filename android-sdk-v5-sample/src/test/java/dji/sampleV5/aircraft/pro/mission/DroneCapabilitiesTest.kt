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
        profiles: Set<ValidatedWpmlProfile> = setOf(validatedProfile)
    ) = DroneCapabilities.evaluateProduct(
        productName = product,
        connected = connected,
        registered = registered,
        firmwareVersion = firmware,
        remoteControllerName = remoteController,
        remoteControllerFirmwareVersion = remoteFirmware,
        validatedProfiles = profiles
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
        assertTrue(result.reason.contains("profile"))
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
}
