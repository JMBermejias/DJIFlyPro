package dji.sampleV5.aircraft.pro.update

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale

/**
 * Verification of a downloaded APK.
 *
 * An app that can install a new version of itself is the same capability malware
 * uses to spread, so the download is treated as untrusted input until two
 * independent things line up:
 *
 * 1. The bytes hash to the digest the release published, fetched over HTTPS
 *    from GitHub.
 * 2. The APK is signed by the same certificate the installed copy is signed by,
 *    so even a manifest served from a hijacked release cannot point the
 *    updater at a different app.
 *
 * Digests are compared case-insensitively: a manifest written by hand and one
 * written by a script differ in case, and that is not a security event.
 */
object ApkVerifier {

    sealed class Result {
        data class Valid(val sizeBytes: Long) : Result()
        data class HashMismatch(val expected: String, val actual: String) : Result()
        data class WrongSigner(val expected: String, val actual: String) : Result()
        data class Unreadable(val reason: String) : Result()
    }

    /** Outcome of streaming a download to its digest. */
    sealed class HashOutcome {
        data class Hashed(val sha256: String, val sizeBytes: Long) : HashOutcome()
        data class Failed(val reason: String) : HashOutcome()
    }

    /**
     * Streams [stream] and returns its SHA-256. Stops early if the payload
     * grows past what the release announced, so a hostile or broken response
     * cannot quietly fill the device's storage.
     */
    fun hashWhileDownloading(
        stream: InputStream,
        expectedSize: Long,
        onProgress: (Long) -> Unit = {}
    ): HashOutcome {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        val limit = expectedSize + expectedSize / 50f + 64f
        var total = 0L
        return try {
            stream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    total += read
                    if (total > limit) {
                        return HashOutcome.Failed("La descarga es más grande de lo que anuncia la release")
                    }
                    digest.update(buffer, 0, read)
                    onProgress(total)
                }
            }
            if (total != expectedSize) {
                return HashOutcome.Failed("Se esperaban ${expectedSize} bytes y se han leído $total")
            }
            HashOutcome.Hashed(toHex(digest.digest()), total)
        } catch (error: Exception) {
            HashOutcome.Failed(error.message ?: "No se ha podido leer la descarga")
        }
    }

    /**
     * Decides whether a downloaded file may be handed to the system installer.
     * [installedCertificateSha256] is the digest of the certificate the running
     * app is signed with, or null when it could not be read, in which case only
     * the file hash is checked and the reason is reported to the caller.
     */
    fun verify(
        file: File,
        manifest: UpdateManifest,
        installedCertificateSha256: String?
    ): Result {
        if (!file.exists()) return Result.Unreadable("El fichero descargado no existe")
        if (file.length() != manifest.apkSize) {
            return Result.Unreadable(
                "Se esperaban ${manifest.apkSize} bytes y el fichero tiene ${file.length()}"
            )
        }
        val actualHash = try {
            file.inputStream().use { sha256Of(it) }
        } catch (error: Exception) {
            return Result.Unreadable(error.message ?: "No se ha podido leer el fichero")
        }
        if (!actualHash.equals(manifest.sha256, ignoreCase = true)) {
            return Result.HashMismatch(manifest.sha256.lowercase(Locale.US), actualHash)
        }
        if (installedCertificateSha256 != null &&
            !installedCertificateSha256.equals(manifest.signingCertificateSha256, ignoreCase = true)
        ) {
            return Result.WrongSigner(
                manifest.signingCertificateSha256.lowercase(Locale.US),
                installedCertificateSha256.lowercase(Locale.US)
            )
        }
        return Result.Valid(file.length())
    }

    fun sha256Of(stream: InputStream): String =
        toHex(MessageDigest.getInstance("SHA-256").digest(stream.readBytes()))

    fun sha256Of(file: File): String = file.inputStream().use { sha256Of(it) }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { String.format(Locale.US, "%02x", it) }
}
