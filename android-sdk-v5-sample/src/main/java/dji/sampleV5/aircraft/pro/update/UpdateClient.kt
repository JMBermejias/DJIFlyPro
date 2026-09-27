package dji.sampleV5.aircraft.pro.update

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the public GitHub releases API to find out whether a newer build
 * exists, and to download it.
 *
 * Deliberately plain `HttpURLConnection`: the update path should not depend on
 * the same third-party HTTP stack the rest of the app uses, so a fault in that
 * stack cannot also block the update that would fix it.
 */
class UpdateClient(
    private val repository: String = DEFAULT_REPOSITORY,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000
) {

    sealed class Result {
        data class Available(val manifest: UpdateManifest) : Result()
        object UpToDate : Result()
        data class Failed(val reason: String) : Result()
    }

    /**
     * Reads the manifest of the newest release.
     *
     * `releases/latest` is not usable here: it excludes prereleases, and every
     * release of this project is one, so it would always report that there is
     * nothing. The list endpoint includes them.
     *
     * The list is **sorted here, by the timestamp the response carries**, and the
     * order of the array is not trusted. That is not defensive paranoia: with
     * v1.1.0-alpha.10 the API returned it *last*, with the newest `created_at` of
     * all, while v1.1.0-alpha.9 came first. Taking the first element with a
     * manifest therefore found alpha.9 and reported "nothing new" while alpha.10
     * was already published. The asset it had downloaded 400 MB of and checked
     * the hash of was the wrong one.
     *
     * Timestamps are ISO 8601 in UTC with a fixed layout, so they compare
     * correctly as plain strings. `published_at` is preferred over `created_at`
     * because a release can be created as a draft and published later.
     */
    fun check(installedVersionCode: Int): Result = try {
        val releases = getJsonArray("$API/repos/$repository/releases?per_page=10")
        val newest = releases.asSequence()
            .filter { it.isJsonObject }
            .map { it.asJsonObject }
            .sortedByDescending { releaseTimestamp(it) }
            .firstOrNull { hasUpdateAsset(it) }
            ?: return Result.Failed("No hay ninguna release con manifiesto de actualización")

        val assetUrl = assetDownloadUrl(newest, MANIFEST_ASSET)
            ?: return Result.Failed("La release no publica $MANIFEST_ASSET")
        val manifest = UpdateManifest.parse(getText(assetUrl))
        val validation = manifest.validate()
        if (!validation.isValid) {
            Result.Failed("El manifiesto no es válido: ${validation.errors.joinToString("; ")}")
        } else if (!manifest.isNewerThan(installedVersionCode)) {
            Result.UpToDate
        } else {
            Result.Available(manifest)
        }
    } catch (error: Exception) {
        Result.Failed(error.message ?: "No se ha podido consultar si hay actualizaciones")
    }

    /**
     * Downloads the APK to [target] and verifies it. The file is deleted on any
     * failure, so a partial or tampered download is never left where a later
     * step could pick it up.
     *
     * The certificate is not checked here: it is read from the running app by
     * the caller, which needs a `PackageManager` that this class does not have.
     */
    fun download(manifest: UpdateManifest, target: File, onProgress: (Long, Long) -> Unit): ApkVerifier.Result {
        target.parentFile?.mkdirs()
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("El servidor respondió ${connection.responseCode}")
            }
            val announced = connection.getContentLengthLong().takeIf { it > 0 } ?: manifest.apkSize
            val limit = manifest.apkSize + manifest.apkSize / 50f + 64f
            BufferedInputStream(connection.inputStream, 64 * 1024).use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        total += read
                        if (total > limit) {
                            throw IllegalStateException("La descarga es más grande de lo que anuncia la release")
                        }
                        onProgress(total, announced)
                    }
                }
            }
            val result = ApkVerifier.verify(target, manifest, null)
            if (result !is ApkVerifier.Result.Valid) {
                target.delete()
            }
            result
        } catch (error: Exception) {
            target.delete()
            ApkVerifier.Result.Unreadable(error.message ?: "No se ha podido descargar la actualización")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * The instant a release became visible, as an ISO 8601 string.
     *
     * `published_at` first, `created_at` as the fallback for a release that is
     * not published yet. An absent timestamp yields an empty string, which sorts
     * last: a release the API renders oddly is treated as the least recent rather
     * than silently winning. ISO 8601 in UTC has a fixed layout, so these compare
     * correctly as plain strings.
     */
    private fun releaseTimestamp(release: JsonObject): String {
        val published = release.stringOrEmpty("published_at")
        if (published.isNotBlank()) return published
        return release.stringOrEmpty("created_at")
    }

    private fun JsonObject.stringOrEmpty(name: String): String =
        get(name)?.takeIf { !it.isJsonNull }?.asString.orEmpty()

    private fun hasUpdateAsset(release: JsonObject): Boolean =
        release.getAsJsonArray("assets")?.asSequence()
            ?.filter { it.isJsonObject }
            ?.map { it.asJsonObject.get("name").asString }
            ?.any { it == MANIFEST_ASSET } == true

    private fun assetDownloadUrl(release: JsonObject, name: String): String? =
        release.getAsJsonArray("assets")
            ?.asSequence()
            ?.filter { it.isJsonObject }
            ?.map { it.asJsonObject }
            ?.firstOrNull { it.get("name").asString == name }
            ?.get("browser_download_url")?.asString

    private fun getJsonArray(url: String): JsonArray = JsonParser.parseString(getText(url)).asJsonArray

    private fun getText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("El servidor respondió ${connection.responseCode}")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val DEFAULT_REPOSITORY = "JMBermejias/DJIFlyPro"
        const val MANIFEST_ASSET = "update.json"

        /**
         * GitHub rejects API requests without a User-Agent, and a bare one
         * would be indistinguishable from a scraper. The app identifies itself
         * so a rate limit or a block is attributable.
         */
        const val USER_AGENT = "DJIFlyPro-Android"
        private const val API = "https://api.github.com"
    }
}
