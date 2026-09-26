package dji.sampleV5.aircraft.pro.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The updater decides whether a device will install a new version of the app.
 * These tests cover the part that can be checked without a device: what counts
 * as newer, what a manifest is allowed to say, and when a downloaded file is
 * allowed to reach the system installer.
 */
class UpdateManifestTest {

    private fun manifest(
        schema: String = UpdateManifest.SCHEMA,
        versionCode: Int = 7,
        versionName: String = "1.1.0-alpha.5",
        tag: String = "v1.1.0-alpha.5",
        apkUrl: String = "https://github.com/JMBermejias/DJIFlyPro/releases/download/v1.1.0-alpha.5/a.apk",
        apkSize: Long = 205_113_194,
        sha256: String = "4f95403da5aca91af9dcaa1ae45ec671912e441e7b395938eed7ad48c1a860cd",
        certificate: String = "8a3aac2e456093f76dc0a2ccd717538b1732da3cecf470231feb48de187def43",
        releaseUrl: String = "https://github.com/JMBermejias/DJIFlyPro/releases/tag/v1.1.0-alpha.5",
        notes: String = "Novedades",
        publishedAt: String = "2026-09-26T15:28:24Z"
    ) = """
        {
          "schema": "$schema",
          "versionCode": $versionCode,
          "versionName": "$versionName",
          "tag": "$tag",
          "apkUrl": "$apkUrl",
          "apkSize": $apkSize,
          "sha256": "$sha256",
          "signingCertificateSha256": "$certificate",
          "releaseUrl": "$releaseUrl",
          "notes": "$notes",
          "publishedAt": "$publishedAt"
        }
    """.trimIndent()

    @Test
    fun aWellFormedManifestParses() {
        val parsed = UpdateManifest.parse(manifest())
        assertEquals(7, parsed.versionCode)
        assertEquals("1.1.0-alpha.5", parsed.versionName)
        assertEquals(205_113_194L, parsed.apkSize)
        assertTrue(parsed.validate().isValid)
    }

    @Test
    fun aNewerVersionCodeIsAnUpdate() {
        val parsed = UpdateManifest.parse(manifest(versionCode = 7))
        assertTrue(parsed.isNewerThan(6))
        assertFalse(parsed.isNewerThan(7))
        assertFalse(parsed.isNewerThan(8))
    }

    @Test
    fun aPrereleaseOfTheSameCodeIsNotAnUpdate() {
        // The installed app is already 1.1.0-alpha.5 and the release offers the
        // same build again. Showing "there is an update" for the build you are
        // running is the fastest way to make people stop trusting the notice.
        val parsed = UpdateManifest.parse(manifest(versionCode = 7))
        assertFalse(parsed.isNewerThan(7))
    }

    @Test
    fun anUnknownSchemaIsRefused() {
        val error = runCatching { UpdateManifest.parse(manifest(schema = "otro/v9")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun anInsecureApkUrlIsRefused() {
        val parsed = UpdateManifest.parse(manifest(apkUrl = "http://example.com/djiflypro.apk"))
        val errors = parsed.validate().errors
        assertTrue(errors.any { it.contains("HTTPS") })
    }

    @Test
    fun anInsecureReleaseUrlIsRefused() {
        val parsed = UpdateManifest.parse(manifest(releaseUrl = "http://example.com/release"))
        assertTrue(parsed.validate().errors.any { it.contains("HTTPS") })
    }

    @Test
    fun aShortDigestIsRefused() {
        val parsed = UpdateManifest.parse(manifest(sha256 = "abc123"))
        assertTrue(parsed.validate().errors.any { it.contains("sha256") })
    }

    @Test
    fun anUppercaseDigestIsAccepted() {
        // A manifest written by hand and one written by a script differ in case.
        // That is not a security event and must not block an update.
        val upper = manifest(sha256 = "4F95403DA5ACA91AF9DCAA1AE45EC671912E441E7B395938EED7AD48C1A860CD")
        assertTrue(UpdateManifest.parse(upper).validate().isValid)
    }

    @Test
    fun aMissingCertificateIsRefused() {
        val parsed = UpdateManifest.parse(manifest(certificate = ""))
        assertTrue(parsed.validate().errors.any { it.contains("signingCertificateSha256") })
    }

    @Test
    fun anImplausibleSizeIsRefused() {
        assertTrue(UpdateManifest.parse(manifest(apkSize = 0)).validate().errors.any { it.contains("apkSize") })
        assertTrue(UpdateManifest.parse(manifest(versionCode = 0)).validate().errors.any { it.contains("versionCode") })
        assertTrue(UpdateManifest.parse(manifest(tag = "1.1.0")).validate().errors.any { it.contains("tag") })
    }

    @Test
    fun aManifestSurvivesTheRoundTrip() {
        // Caching and the activity hand-off both re-parse what they were given,
        // so a manifest that cannot survive its own serialisation would either
        // be trusted unread or break on the way back in. The notes are built
        // directly rather than through the template above, which does not
        // escape: the point is to exercise toJson, not the test's own quoting.
        val original = UpdateManifest(
            versionCode = 7,
            versionName = "1.1.0-alpha.5",
            tag = "v1.1.0-alpha.5",
            apkUrl = "https://github.com/o/r/releases/download/v1.1.0-alpha.5/a.apk",
            apkSize = 205_113_194,
            sha256 = "4f95403da5aca91af9dcaa1ae45ec671912e441e7b395938eed7ad48c1a860cd",
            signingCertificateSha256 = "8a3aac2e456093f76dc0a2ccd717538b1732da3cecf470231feb48de187def43",
            releaseUrl = "https://github.com/o/r/releases/tag/v1.1.0-alpha.5",
            notes = "Comillas \"y\" barra \\ tabulador\t y\nsalto",
            publishedAt = "2026-09-26T15:28:24Z"
        )
        val round = UpdateManifest.parse(original.toJson())
        assertEquals(original, round)
        assertTrue(round.validate().isValid)
    }

    @Test
    fun notesAndTimestampAreOptional() {
        val parsed = UpdateManifest.parse(
            """
            {
              "schema": "${UpdateManifest.SCHEMA}",
              "versionCode": 2,
              "versionName": "1.0.0",
              "tag": "v1.0.0",
              "apkUrl": "https://example.com/a.apk",
              "apkSize": 10,
              "sha256": "${"a".repeat(64)}",
              "signingCertificateSha256": "${"b".repeat(64)}",
              "releaseUrl": "https://example.com/r"
            }
            """.trimIndent()
        )
        assertEquals("", parsed.notes)
        assertEquals("", parsed.publishedAt)
        assertTrue(parsed.validate().isValid)
    }
}
