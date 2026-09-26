package dji.sampleV5.aircraft.pro.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The allowlist is the single switch that can enable automatic WPML
 * execution, so every malformed-input path must resolve to an empty set.
 */
class ValidatedWpmlProfileRepositoryTest {

    private fun profileJson(
        product: String = "M30_SERIES",
        remote: String = "DJI_RC_PLUS_2",
        aircraftFirmware: String = "01.01.0000",
        remoteFirmware: String = "02.00.0100",
        reference: String = "physical-test-record-1"
    ) = """
        {
          "schema": "djiflypro.validated-wpml/v1",
          "profiles": [
            {
              "productName": "$product",
              "remoteControllerName": "$remote",
              "aircraftFirmwareVersion": "$aircraftFirmware",
              "remoteControllerFirmwareVersion": "$remoteFirmware",
              "validationReference": "$reference"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesAValidAllowlist() {
        val profiles = ValidatedWpmlProfileRepository.parse(profileJson())

        assertEquals(1, profiles.size)
        val profile = profiles.first()
        assertEquals("M30_SERIES", profile.productName)
        assertEquals("DJI_RC_PLUS_2", profile.remoteControllerName)
        assertEquals("01.01.0000", profile.aircraftFirmwareVersion)
        assertEquals("02.00.0100", profile.remoteControllerFirmwareVersion)
        assertEquals("physical-test-record-1", profile.validationReference)
    }

    @Test
    fun emptyProfileListIsAValidButDisabledAllowlist() {
        val raw = """{"schema":"djiflypro.validated-wpml/v1","profiles":[]}"""

        assertTrue(ValidatedWpmlProfileRepository.parse(raw).isEmpty())
    }

    @Test
    fun unreadableAssetFailsClosed() {
        assertTrue(ValidatedWpmlProfileRepository.parse(null).isEmpty())
    }

    @Test
    fun unknownOrMissingSchemaFailsClosed() {
        assertTrue(ValidatedWpmlProfileRepository.parse("""{"profiles":[]}""").isEmpty())
        assertTrue(
            ValidatedWpmlProfileRepository.parse("""{"schema":"other/v9","profiles":[]}""").isEmpty()
        )
        assertTrue(ValidatedWpmlProfileRepository.parse("null").isEmpty())
        assertTrue(ValidatedWpmlProfileRepository.parse("").isEmpty())
    }

    @Test
    fun malformedJsonFailsClosed() {
        listOf("{", "not json at all", "[]", """{"schema":123}""").forEach { raw ->
            assertTrue("Expected empty set for: $raw", ValidatedWpmlProfileRepository.parse(raw).isEmpty())
        }
    }

    @Test
    fun nullOrMissingProfilesArrayFailsClosed() {
        assertTrue(ValidatedWpmlProfileRepository.parse("""{"schema":"djiflypro.validated-wpml/v1"}""").isEmpty())
        assertTrue(
            ValidatedWpmlProfileRepository.parse("""{"schema":"djiflypro.validated-wpml/v1","profiles":null}""")
                .isEmpty()
        )
    }

    @Test
    fun oneInvalidProfileInvalidatesTheWholeFile() {
        val raw = """
            {
              "schema": "djiflypro.validated-wpml/v1",
              "profiles": [
                {
                  "productName": "M30_SERIES",
                  "remoteControllerName": "DJI_RC_PLUS_2",
                  "aircraftFirmwareVersion": "01.01.0000",
                  "remoteControllerFirmwareVersion": "02.00.0100",
                  "validationReference": "physical-test-record-1"
                },
                {
                  "productName": "UNKNOWN",
                  "remoteControllerName": "DJI_RC_PLUS_2",
                  "aircraftFirmwareVersion": "01.01.0000",
                  "remoteControllerFirmwareVersion": "02.00.0100",
                  "validationReference": "physical-test-record-2"
                }
              ]
            }
        """.trimIndent()

        assertTrue(ValidatedWpmlProfileRepository.parse(raw).isEmpty())
    }

    @Test
    fun placeholdersAndUnversionedValuesFailClosed() {
        listOf(
            profileJson(product = "REPLACE_WITH_EXACT_PRODUCT"),
            profileJson(remote = "REPLACE_WITH_EXACT_REMOTE"),
            profileJson(aircraftFirmware = "REPLACE_WITH_EXACT_AIRCRAFT_FIRMWARE"),
            profileJson(remoteFirmware = "REPLACE_WITH_EXACT_RC_FIRMWARE"),
            profileJson(reference = "physical-test-record-REQUIRED"),
            profileJson(remote = "NOT_SUPPORTED_0"),
            profileJson(product = "  ")
        ).forEach { raw ->
            assertTrue("Expected empty set for:\n$raw", ValidatedWpmlProfileRepository.parse(raw).isEmpty())
        }
    }

    /**
     * The shipped example file is the most likely source of a bad profile: a
     * developer copies it, fills in the product, and forgets the rest. It must
     * never validate.
     */
    @Test
    fun theShippedExampleFileIsNotAValidAllowlist() {
        val raw = """
            {
              "schema": "djiflypro.validated-wpml/v1",
              "profiles": [
                {
                  "productName": "DJI_M350_RTK",
                  "remoteControllerName": "DJI_RC_PLUS_2",
                  "aircraftFirmwareVersion": "REPLACE_WITH_EXACT_AIRCRAFT_FIRMWARE",
                  "remoteControllerFirmwareVersion": "REPLACE_WITH_EXACT_RC_FIRMWARE",
                  "validationReference": "physical-test-record-REQUIRED"
                }
              ]
            }
        """.trimIndent()

        assertTrue(ValidatedWpmlProfileRepository.parse(raw).isEmpty())
    }

    @Test
    fun aFilledProductWithAPlaceholderReferenceStaysBlocked() {
        val raw = """
            {
              "schema": "djiflypro.validated-wpml/v1",
              "profiles": [
                {
                  "productName": "DJI_M350_RTK",
                  "remoteControllerName": "DJI_RC_PLUS_2",
                  "aircraftFirmwareVersion": "01.01.0000",
                  "remoteControllerFirmwareVersion": "02.00.0100",
                  "validationReference": "physical-test-record-TBD"
                }
              ]
            }
        """.trimIndent()

        val profiles = ValidatedWpmlProfileRepository.parse(raw)
        assertTrue(profiles.isEmpty())

        val result = DroneCapabilities.evaluateProduct(
            productName = "DJI_M350_RTK",
            connected = true,
            registered = true,
            firmwareVersion = "01.01.0000",
            remoteControllerName = "DJI_RC_PLUS_2",
            remoteControllerFirmwareVersion = "02.00.0100",
            validatedProfiles = profiles
        )
        assertTrue(result.waypointExecutionSupported.not())
    }

    @Test
    fun oversizedAllowlistFailsClosed() {
        val padding = "x".repeat(70 * 1024)
        val raw = """{"schema":"djiflypro.validated-wpml/v1","profiles":[],"pad":"$padding"}"""

        assertTrue(ValidatedWpmlProfileRepository.parse(raw).isEmpty())
    }

    @Test
    fun aParsedProfileActuallyEnablesTheGate() {
        val profiles = ValidatedWpmlProfileRepository.parse(profileJson())
        val result = DroneCapabilities.evaluateProduct(
            productName = "M30_SERIES",
            connected = true,
            registered = true,
            firmwareVersion = "01.01.0000",
            remoteControllerName = "DJI_RC_PLUS_2",
            remoteControllerFirmwareVersion = "02.00.0100",
            validatedProfiles = profiles
        )

        assertTrue(result.waypointUploadSupported)
        assertTrue(result.waypointExecutionSupported)
    }

    @Test
    fun shippedDefaultAssetKeepsExecutionDisabled() {
        val raw = """{
  "schema": "djiflypro.validated-wpml/v1",
  "profiles": []
}"""

        val profiles = ValidatedWpmlProfileRepository.parse(raw)
        val result = DroneCapabilities.evaluateProduct(
            productName = "M30_SERIES",
            connected = true,
            registered = true,
            firmwareVersion = "01.01.0000",
            remoteControllerName = "DJI_RC_PLUS_2",
            remoteControllerFirmwareVersion = "02.00.0100",
            validatedProfiles = profiles
        )

        assertTrue(profiles.isEmpty())
        assertTrue(result.waypointUploadSupported.not())
        assertTrue(result.waypointExecutionSupported.not())
    }
}
