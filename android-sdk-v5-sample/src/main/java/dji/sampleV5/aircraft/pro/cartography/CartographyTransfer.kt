package dji.sampleV5.aircraft.pro.cartography

import android.content.Intent
import java.io.Serializable

/**
 * Moves a cartography profile and its control points between two screens.
 *
 * The two `Intent` extras are serialised objects, so the read is defensive:
 * anything that is not the expected type comes back as absent rather than as a
 * cast that throws, because a half-read profile would silently produce a route
 * with the wrong resolution.
 */
object CartographyTransfer {

    const val EXTRA_PROFILE = "com.djiflypro.cartography.PROFILE"
    const val EXTRA_CONTROL_POINTS = "com.djiflypro.cartography.CONTROL_POINTS"

    fun putProfile(intent: Intent, profile: CartographyProfile): Intent =
        intent.putExtra(EXTRA_PROFILE, profile as Serializable)

    fun readProfile(intent: Intent?): CartographyProfile? {
        val raw = read(intent, EXTRA_PROFILE) ?: return null
        return runCatching { raw as CartographyProfile }.getOrNull()
    }

    fun putControlPoints(intent: Intent, points: List<GroundControlPoint>): Intent =
        intent.putExtra(EXTRA_CONTROL_POINTS, ArrayList(points) as Serializable)

    fun readControlPoints(intent: Intent?): List<GroundControlPoint> {
        val raw = read(intent, EXTRA_CONTROL_POINTS) ?: return emptyList()
        val list = runCatching { raw as? List<*> }.getOrNull() ?: return emptyList()
        return list.filterIsInstance<GroundControlPoint>()
    }

    @Suppress("DEPRECATION")
    private fun read(intent: Intent?, key: String): Any? =
        intent?.let { runCatching { it.getSerializableExtra(key) }.getOrNull() }
}
