package dji.sampleV5.aircraft.pro.update

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dji.sampleV5.aircraft.pro.cartography.GeoJsonExporter

/**
 * The description of an available update, published as an `update.json` asset
 * on every release.
 *
 * A manifest exists so the app can decide *before* downloading anything, and so
 * it can prove afterwards that what it downloaded is what the release intended.
 * The GitHub release API says nothing about the version of an APK, and a tag
 * name is a string that two different builds could share.
 */
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val tag: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
    val signingCertificateSha256: String,
    val releaseUrl: String,
    val notes: String,
    val publishedAt: String
) {
    fun isNewerThan(installedVersionCode: Int): Boolean = versionCode > installedVersionCode

    /**
     * Round trip back to the published shape, so a manifest that was cached or
     * handed to an activity can be re-validated on the way out instead of
     * being trusted because it came from inside the app.
     */
    fun toJson(): String = buildString {
        fun q(value: String) = GeoJsonExporter.json(value)
        append('{')
        append("\"schema\":").append(q(SCHEMA)).append(',')
        append("\"versionCode\":").append(versionCode).append(',')
        append("\"versionName\":").append(q(versionName)).append(',')
        append("\"tag\":").append(q(tag)).append(',')
        append("\"apkUrl\":").append(q(apkUrl)).append(',')
        append("\"apkSize\":").append(apkSize).append(',')
        append("\"sha256\":").append(q(sha256)).append(',')
        append("\"signingCertificateSha256\":").append(q(signingCertificateSha256)).append(',')
        append("\"releaseUrl\":").append(q(releaseUrl)).append(',')
        append("\"notes\":").append(q(notes)).append(',')
        append("\"publishedAt\":").append(q(publishedAt))
        append('}')
    }

    /** Parsing. Anything missing, malformed or implausible is rejected, not guessed. */
    fun validate(): UpdateValidation {
        val errors = mutableListOf<String>()
        if (versionCode <= 0) errors += "versionCode debe ser mayor que 0"
        if (versionName.isBlank()) errors += "versionName está vacío"
        if (!tag.startsWith("v")) errors += "tag debe empezar por v"
        if (!apkUrl.startsWith("https://")) errors += "apkUrl debe ser HTTPS"
        if (!releaseUrl.startsWith("https://")) errors += "releaseUrl debe ser HTTPS"
        if (apkSize <= 0) errors += "apkSize debe ser mayor que 0"
        if (!SHA256_PATTERN.matches(sha256)) errors += "sha256 no es un resumen SHA-256"
        if (!SHA256_PATTERN.matches(signingCertificateSha256)) {
            errors += "signingCertificateSha256 no es un resumen SHA-256"
        }
        return UpdateValidation(errors)
    }

    companion object {
        const val SCHEMA = "djiflypro.update/v1"
        /**
         * Case-insensitive on purpose. A manifest written by hand and one
         * written by a script differ in case, and rejecting the uppercase form
         * would block a legitimate update over something that is not a security
         * event. The comparison in [ApkVerifier] is case-insensitive for the
         * same reason.
         */
        val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")

        val MAX_REASONABLE_APK_BYTES = 2L * 1024 * 1024 * 1024

        fun parse(raw: String): UpdateManifest {
            val root = JsonParser.parseString(raw).asJsonObject
            require(root.get("schema").asString == SCHEMA) { "Esquema de actualización no admitido" }
            return UpdateManifest(
                versionCode = root.get("versionCode").asInt,
                versionName = root.get("versionName").asString,
                tag = root.get("tag").asString,
                apkUrl = root.get("apkUrl").asString,
                apkSize = root.get("apkSize").asLong,
                sha256 = root.get("sha256").asString,
                signingCertificateSha256 = root.get("signingCertificateSha256").asString,
                releaseUrl = root.get("releaseUrl").asString,
                notes = root.optString("notes"),
                publishedAt = root.optString("publishedAt")
            )
        }

        private fun JsonObject.optString(key: String): String =
            if (has(key) && !get(key).isJsonNull) get(key).asString else ""
    }
}

data class UpdateValidation(val errors: List<String>) {
    val isValid: Boolean
        get() = errors.isEmpty()
}
