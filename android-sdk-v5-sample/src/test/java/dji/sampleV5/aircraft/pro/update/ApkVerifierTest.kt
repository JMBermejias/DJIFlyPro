package dji.sampleV5.aircraft.pro.update

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate that decides whether a downloaded APK is allowed anywhere near the
 * system installer. Every case here is something that could turn an update
 * button into a way to install somebody else's app, so each one is checked
 * explicitly rather than assumed.
 */
class ApkVerifierTest {

    private val payload = "contenido de prueba".toByteArray()
    private val realHash = ApkVerifier.sha256Of(ByteArrayInputStream(payload))

    private val certificate = "8a3aac2e456093f76dc0a2ccd717538b1732da3cecf470231feb48de187def43"

    private fun manifest(
        sha256: String = realHash,
        apkSize: Long = payload.size.toLong(),
        signer: String = certificate
    ) = UpdateManifest(
        versionCode = 9,
        versionName = "1.1.0-alpha.6",
        tag = "v1.1.0-alpha.6",
        apkUrl = "https://github.com/JMBermejias/DJIFlyPro/releases/download/v1.1.0-alpha.6/a.apk",
        apkSize = apkSize,
        sha256 = sha256,
        signingCertificateSha256 = signer,
        releaseUrl = "https://github.com/JMBermejias/DJIFlyPro/releases/tag/v1.1.0-alpha.6",
        notes = "",
        publishedAt = ""
    )

    private fun tempFile(bytes: ByteArray = payload): File {
        val file = File.createTempFile("djiflypro-update", ".apk")
        file.deleteOnExit()
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun aFileThatMatchesBothDigestsIsAccepted() {
        val result = ApkVerifier.verify(tempFile(), manifest(), certificate)
        assertTrue(result is ApkVerifier.Result.Valid)
        assertEquals(payload.size.toLong(), (result as ApkVerifier.Result.Valid).sizeBytes)
    }

    @Test
    fun aFileWithTheRightSizeAndTheWrongContentIsRefused() {
        val other = "otro contenido".toByteArray()
        val file = tempFile(other)
        val result = ApkVerifier.verify(file, manifest(sha256 = realHash, apkSize = other.size.toLong()), certificate)
        assertTrue(result is ApkVerifier.Result.HashMismatch)
    }

    @Test
    fun aTruncatedDownloadIsRefused() {
        val file = tempFile()
        val result = ApkVerifier.verify(file, manifest(apkSize = payload.size.toLong() + 10), certificate)
        assertTrue(result is ApkVerifier.Result.Unreadable)
        assertTrue((result as ApkVerifier.Result.Unreadable).reason.contains("bytes"))
    }

    @Test
    fun aMissingFileIsRefused() {
        val result = ApkVerifier.verify(File("/no/existe.apk"), manifest(), certificate)
        assertTrue(result is ApkVerifier.Result.Unreadable)
    }

    @Test
    fun anApkSignedBySomebodyElseIsRefused() {
        // Right content, right size, wrong signer. This is the case that matters
        // most: it is what a hijacked release or a hostile mirror would produce.
        val other = "b".repeat(64)
        val result = ApkVerifier.verify(tempFile(), manifest(signer = other), certificate)
        assertTrue(result is ApkVerifier.Result.WrongSigner)
    }

    @Test
    fun anUnknownInstalledCertificateOnlyChecksTheHash() {
        // The certificate of the running app could not be read, so the signer
        // cannot be compared. The activity refuses to install in that case; the
        // verifier's job is just to not claim a mismatch it did not find.
        val result = ApkVerifier.verify(tempFile(), manifest(), null)
        assertTrue(result is ApkVerifier.Result.Valid)
    }

    @Test
    fun aDownloadLargerThanAnnouncedIsStopped() {
        val outcome = ApkVerifier.hashWhileDownloading(
            stream = ByteArrayInputStream(ByteArray(4096)),
            expectedSize = 100
        )
        assertTrue(outcome is ApkVerifier.HashOutcome.Failed)
        assertTrue((outcome as ApkVerifier.HashOutcome.Failed).reason.contains("más grande"))
    }

    @Test
    fun aDownloadShorterThanAnnouncedIsStopped() {
        val outcome = ApkVerifier.hashWhileDownloading(
            stream = ByteArrayInputStream(ByteArray(10)),
            expectedSize = 100
        )
        assertTrue(outcome is ApkVerifier.HashOutcome.Failed)
    }

    @Test
    fun aDownloadOfTheAnnouncedSizeIsAccepted() {
        val outcome = ApkVerifier.hashWhileDownloading(ByteArrayInputStream(payload), payload.size.toLong())
        assertTrue(outcome is ApkVerifier.HashOutcome.Hashed)
        assertEquals(realHash, (outcome as ApkVerifier.HashOutcome.Hashed).sha256)
    }

    @Test
    fun theDigestIsLowercaseHexOfTheRightLength() {
        assertEquals(64, realHash.length)
        assertTrue(realHash.all { it in "0123456789abcdef" })
    }

    @Test
    fun digestsAreComparedWithoutRegardToCase() {
        val upper = realHash.uppercase()
        val result = ApkVerifier.verify(tempFile(), manifest(sha256 = upper), certificate.uppercase())
        assertTrue(result is ApkVerifier.Result.Valid)
    }

    @Test
    fun toHexPadsSingleDigitBytes() {
        assertEquals("0f", ApkVerifier.toHex(byteArrayOf(0x0f)))
        assertEquals("00ff10", ApkVerifier.toHex(byteArrayOf(0x00, 0xff.toByte(), 0x10)))
    }
}
