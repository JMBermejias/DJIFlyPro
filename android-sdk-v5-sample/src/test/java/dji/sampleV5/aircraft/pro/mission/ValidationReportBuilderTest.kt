package dji.sampleV5.aircraft.pro.mission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationReportBuilderTest {

    private fun result(
        product: String = "M350_RTK",
        firmware: String? = "01.01.0000",
        remote: String? = "DJI_RC_PLUS_2",
        remoteFirmware: String? = "02.00.0100",
        connected: Boolean = true,
        registered: Boolean = true,
        profiles: Set<ValidatedWpmlProfile> = emptySet()
    ) = DroneCapabilities.evaluateProduct(
        productName = product,
        connected = connected,
        registered = registered,
        firmwareVersion = firmware,
        remoteControllerName = remote,
        remoteControllerFirmwareVersion = remoteFirmware,
        validatedProfiles = profiles
    )

    @Test
    fun reportsTheExactStringsMsdkReturned() {
        val report = ValidationReportBuilder.build(result())

        assertTrue(report.contains("\"productName\": \"M350_RTK\""))
        assertTrue(report.contains("\"remoteControllerName\": \"DJI_RC_PLUS_2\""))
        assertTrue(report.contains("\"aircraftFirmwareVersion\": \"01.01.0000\""))
        assertTrue(report.contains("\"remoteControllerFirmwareVersion\": \"02.00.0100\""))
    }

    @Test
    fun theReportItselfCanNeverBeUsedAsAnApproval() {
        val report = ValidationReportBuilder.build(result())

        assertTrue(
            "An exported report must not parse as a usable allowlist",
            ValidatedWpmlProfileRepository.parse(report).isEmpty()
        )
    }

    @Test
    fun missingIdentityIsMarkedUnknownInsteadOfGuessed() {
        val report = ValidationReportBuilder.build(
            result(connected = false, registered = false, firmware = null, remote = null, remoteFirmware = null)
        )

        assertTrue(report.contains("UNKNOWN_PRODUCT") || report.contains("UNKNOWN_AIRCRAFT_FIRMWARE"))
        assertTrue(report.contains("# connected: false"))
        assertTrue(report.contains("# registered: false"))
        assertTrue(ValidatedWpmlProfileRepository.parse(report).isEmpty())
    }

    @Test
    fun matchedProfileIsStillNotSelfApproving() {
        val profile = ValidatedWpmlProfile(
            productName = "M350_RTK",
            remoteControllerName = "DJI_RC_PLUS_2",
            aircraftFirmwareVersion = "01.01.0000",
            remoteControllerFirmwareVersion = "02.00.0100",
            validationReference = "lab-record-42"
        )
        val report = ValidationReportBuilder.build(result(profiles = setOf(profile)))

        assertTrue(report.contains("# current gate: profile-matched"))
        assertTrue(report.contains("REPLACE_WITH_YOUR_TEST_RECORD_REFERENCE"))
        assertTrue(ValidatedWpmlProfileRepository.parse(report).isEmpty())
    }

    @Test
    fun gateLabelReflectsWhyExecutionIsBlocked() {
        assertTrue(
            ValidationReportBuilder.build(result(connected = false))
                .contains("# current gate: not-connected")
        )
        assertTrue(
            ValidationReportBuilder.build(result(registered = false))
                .contains("# current gate: not-registered")
        )
        assertTrue(
            ValidationReportBuilder.build(result())
                .contains("# current gate: no-validated-profile")
        )
    }

    @Test
    fun reportDoesNotClaimCompatibility() {
        val report = ValidationReportBuilder.build(result())

        assertFalse(report.contains("approved", ignoreCase = true))
        assertFalse(report.contains("certified", ignoreCase = true))
        assertTrue(report.contains("candidate, not a"))
    }
}
