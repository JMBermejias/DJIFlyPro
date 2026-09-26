package dji.sampleV5.aircraft.pro.update

import android.content.Context
import androidx.core.content.edit

/**
 * Caches the last update check so opening the app does not talk to GitHub on
 * every launch.
 *
 * An update check is a network request to a third party on every app open, and
 * that is a cost worth paying rarely rather than constantly. The window is
 * short enough that a new release is picked up the same day.
 */
class UpdatePrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("djiflypro.update", Context.MODE_PRIVATE)

    /** The manifest of the newest known release, or null if never seen. */
    fun cachedManifest(): UpdateManifest? {
        val raw = prefs.getString(KEY_MANIFEST, null) ?: return null
        return runCatching { UpdateManifest.parse(raw) }.getOrNull()
    }

    fun cacheManifest(manifest: UpdateManifest) {
        prefs.edit {
            putString(KEY_MANIFEST, manifest.toJson())
            putLong(KEY_CHECKED_AT, System.currentTimeMillis())
        }
    }

    fun lastCheckedAtMillis(): Long = prefs.getLong(KEY_CHECKED_AT, 0L)

    /** True when the last check is recent enough that another adds nothing. */
    fun isFresh(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val last = lastCheckedAtMillis()
        return last > 0L && nowMillis - last < FRESH_WINDOW_MILLIS
    }

    /** Records a check that found nothing, so it is not repeated for hours. */
    fun markCheckedNow() {
        prefs.edit { putLong(KEY_CHECKED_AT, System.currentTimeMillis()) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val KEY_MANIFEST = "manifest"
        private const val KEY_CHECKED_AT = "checked_at"

        /** Six hours. Long enough to be quiet, short enough to be useful. */
        const val FRESH_WINDOW_MILLIS = 6L * 60 * 60 * 1000
    }
}
