package dji.sampleV5.aircraft.pro.mission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatedWpmlProfileTest {
    private val profile = ValidatedWpmlProfile(
        productName = "M350_RTK",
        remoteControllerName = "DJI_RC_PLUS_2",
        aircraftFirmwareVersion = "01.01.0000",
        remoteControllerFirmwareVersion = "02.00.0100",
        validationReference = "lab-record-42"
    )

    @Test
    fun exactIdentityMatches() {
        assertTrue(
            profile.matches(
                product = "m350_rtk",
                remoteController = "dji_rc_plus_2",
                aircraftFirmware = "01.01.0000",
                remoteFirmware = "02.00.0100"
            )
        )
    }

    @Test
    fun firmwareOrRemoteChangeDoesNotMatch() {
        assertFalse(profile.matches("M350_RTK", "DJI_RC_PLUS_2", "01.01.0001", "02.00.0100"))
        assertFalse(profile.matches("M350_RTK", "DJI_RC_N3", "01.01.0000", "02.00.0100"))
    }

    @Test
    fun placeholderAndUnidentifiedValuesAreRejected() {
        assertTrue(runCatching { profile.copy(productName = "UNKNOWN").validate() }.isFailure)
        assertTrue(runCatching { profile.copy(remoteControllerName = "NOT_SUPPORTED_0").validate() }.isFailure)
        assertTrue(runCatching { profile.copy(validationReference = " ").validate() }.isFailure)
    }

    @Test
    fun unfilledTemplateMarkersAreRejected() {
        listOf(
            profile.copy(productName = "REPLACE_WITH_EXACT_PRODUCT"),
            profile.copy(remoteControllerName = "REPLACE_WITH_EXACT_REMOTE"),
            profile.copy(aircraftFirmwareVersion = "REPLACE_WITH_EXACT_AIRCRAFT_FIRMWARE"),
            profile.copy(remoteControllerFirmwareVersion = "REPLACE_WITH_EXACT_RC_FIRMWARE"),
            profile.copy(validationReference = "physical-test-record-REQUIRED"),
            profile.copy(validationReference = "TBD"),
            profile.copy(validationReference = "TODO-record"),
            profile.copy(productName = "DJI_M350_RTK_EXAMPLE"),
            profile.copy(validationReference = "FIXME-add-record"),
            profile.copy(aircraftFirmwareVersion = "01.01.???"),
            profile.copy(validationReference = "PENDING"),
            profile.copy(remoteControllerName = "<remote>"),
            profile.copy(aircraftFirmwareVersion = "INSERT_VERSION")
        ).forEach { candidate ->
            assertTrue(
                "Expected rejection of ${candidate.productName}/${candidate.validationReference}",
                runCatching { candidate.validate() }.isFailure
            )
        }
    }

    @Test
    fun realisticValuesAreAccepted() {
        runCatching { profile.validate() }.getOrElse { error("Valid profile was rejected: $it") }
    }
}
