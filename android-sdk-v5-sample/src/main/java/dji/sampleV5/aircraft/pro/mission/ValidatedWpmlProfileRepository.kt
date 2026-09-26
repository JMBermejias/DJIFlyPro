package dji.sampleV5.aircraft.pro.mission

import android.content.Context
import com.google.gson.GsonBuilder

/**
 * Loads the build-time, reviewable validation allowlist. Failure to load or
 * validate the allowlist fails closed: the app can still plan/export, but
 * automatic WPML upload/execution remains disabled.
 */
class ValidatedWpmlProfileRepository(private val context: Context) {

    /** Reads the allowlist from the packaged asset. */
    fun load(): Set<ValidatedWpmlProfile> = parse(readAsset())

    private fun readAsset(): String? = runCatching {
        context.assets.open(ASSET_NAME).use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= MAX_BYTES) { "Validation allowlist is too large" }
            bytes.toString(Charsets.UTF_8)
        }
    }.getOrNull()

    companion object {
        private const val ASSET_NAME = "validated_wpml_profiles.json"
        private const val MAX_BYTES = 64 * 1024
        private val gson = GsonBuilder().create()

        /**
         * Parses allowlist content. Every failure path returns an empty set, so
         * a malformed, oversized, unknown-schema or partially invalid file can
         * never enable a mission. One bad profile invalidates the whole file
         * instead of being skipped, so a broken entry cannot be mistaken for a
         * reviewed one.
         *
         * [raw] is nullable to model an unreadable asset.
         */
        fun parse(raw: String?): Set<ValidatedWpmlProfile> = runCatching {
            requireNotNull(raw) { "Validation allowlist could not be read" }
            require(raw.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) {
                "Validation allowlist is too large"
            }
            val file = gson.fromJson(raw, ValidatedWpmlProfileFile::class.java)
                ?: error("Validation allowlist is empty")
            require(file.schema == ValidatedWpmlProfile.FILE_SCHEMA) {
                "Unsupported validation allowlist schema"
            }
            val profiles = requireNotNull(file.profiles) {
                "Validation allowlist has no profiles array"
            }
            profiles.onEach { it.validate() }.toSet()
        }.getOrDefault(emptySet())
    }
}
