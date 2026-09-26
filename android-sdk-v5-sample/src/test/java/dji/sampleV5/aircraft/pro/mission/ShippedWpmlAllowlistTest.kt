package dji.sampleV5.aircraft.pro.mission

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates the allowlist that is actually shipped, reading it from the source
 * tree so the rules are not duplicated in a second implementation. This runs in
 * the normal unit-test task, so an invalid or placeholder-filled allowlist
 * breaks the build instead of reaching a device.
 */
class ShippedWpmlAllowlistTest {

    private fun assetFile(): File {
        val candidates = listOf(
            File("src/main/assets/validated_wpml_profiles.json"),
            File("android-sdk-v5-sample/src/main/assets/validated_wpml_profiles.json")
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Cannot locate validated_wpml_profiles.json from ${File(".").absolutePath}")
    }

    @Test
    fun shippedAllowlistIsWithinTheSizeLimit() {
        val size = assetFile().length()
        assertTrue("Allowlist is $size bytes", size in 1..(64 * 1024))
    }

    @Test
    fun shippedAllowlistParsesWithoutFallingBackToEmpty() {
        val raw = assetFile().readText(Charsets.UTF_8)

        // Parsed with the production rules: a well-formed file must not fail
        // closed, otherwise a genuine validation record would be silently
        // ignored and the app would look permanently disabled.
        val file = com.google.gson.GsonBuilder().create()
            .fromJson(raw, ValidatedWpmlProfileFile::class.java)
        assertTrue("Unparseable allowlist asset", file != null)
        assertTrue(
            "Unexpected schema: ${file?.schema}",
            file?.schema == ValidatedWpmlProfile.FILE_SCHEMA
        )
        assertTrue("Missing profiles array", file?.profiles != null)
    }

    @Test
    fun everyShippedProfileIsReviewable() {
        val raw = assetFile().readText(Charsets.UTF_8)
        val profiles = ValidatedWpmlProfileRepository.parse(raw)
        val declared = com.google.gson.GsonBuilder().create()
            .fromJson(raw, ValidatedWpmlProfileFile::class.java)
            ?.profiles
            .orEmpty()

        assertTrue(
            "Declared ${declared.size} profile(s) but ${profiles.size} survived validation. " +
                "An invalid entry would silently disable automatic WPML execution.",
            declared.size == profiles.size
        )
    }

    @Test
    fun shippedDefaultDeliveryKeepsAutomaticExecutionDisabled() {
        val raw = assetFile().readText(Charsets.UTF_8)
        val profiles = ValidatedWpmlProfileRepository.parse(raw)

        if (profiles.isNotEmpty()) {
            // A populated allowlist is only legitimate when each entry carries a
            // real reference; the placeholder rules in validate() enforce that.
            profiles.forEach { profile ->
                runCatching { profile.validate() }
                    .getOrElse { error("Shipped profile is not reviewable: $it") }
            }
            return
        }

        val result = DroneCapabilities.evaluateProduct(
            productName = "M30_SERIES",
            connected = true,
            registered = true,
            firmwareVersion = "01.01.0000",
            remoteControllerName = "DJI_RC_PLUS_2",
            remoteControllerFirmwareVersion = "02.00.0100",
            validatedProfiles = profiles
        )
        assertTrue(
            "Empty allowlist must keep automatic WPML execution blocked",
            !result.waypointExecutionSupported
        )
    }
}
