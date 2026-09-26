package dji.sampleV5.aircraft.pro.map

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.pro.cartography.CartographicSummary
import dji.sampleV5.aircraft.pro.cartography.CartographicSummaryBuilder
import dji.sampleV5.aircraft.pro.cartography.GroundControlPoint
import dji.sampleV5.aircraft.pro.cartography.MissionMapRenderer
import dji.sampleV5.aircraft.pro.mission.AuditLog
import dji.sampleV5.aircraft.pro.mission.MissionStore
import dji.v5.ux.map.MapWidget
import dji.v5.ux.mapkit.core.maps.DJIMap
import dji.v5.ux.mapkit.core.models.DJILatLng
import dji.v5.ux.mapkit.core.models.DJILatLngBounds
import dji.v5.ux.mapkit.core.camera.DJICameraUpdateFactory
import dji.v5.ux.mapkit.core.camera.DJICameraUpdate

/**
 * A review map of a planned block: the footprint, the flight lines and the
 * ground control.
 *
 * This is a view, not a control surface. It never moves the aircraft, it does
 * not verify anything, and it says so on screen. Its job is to let an operator
 * and a client look at the same block on a globe before anyone flies it, and
 * to let a control point be dropped where it is actually painted instead of
 * typed from memory.
 */
class MissionMapActivity : AppCompatActivity() {

    private lateinit var mapWidget: MapWidget
    private lateinit var title: TextView
    private lateinit var summaryText: TextView
    private lateinit var statusText: TextView
    private lateinit var pickButton: Button
    private lateinit var centerButton: Button
    private lateinit var audit: AuditLog

    private var mapReady = false
    private var mapInitialised = false
    private var center: DJILatLng? = null
    private var pending: Pending? = null
    private var mode: String = MODE_VIEW

    private data class Pending(
        val summary: CartographicSummary,
        val controlPoints: List<GroundControlPoint>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mission_map)
        audit = AuditLog(this)
        title = findViewById(R.id.text_map_title)
        summaryText = findViewById(R.id.text_map_summary)
        statusText = findViewById(R.id.text_map_status)
        pickButton = findViewById(R.id.button_pick_here)
        centerButton = findViewById(R.id.button_center_block)
        mapWidget = findViewById(R.id.map_widget)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VIEW

        val store = MissionStore(this)
        val plan = store.latest()
        if (plan == null) {
            title.text = "Sin bloque que mostrar"
            summaryText.text = "Genera primero una ruta en el planificador."
            statusText.text = "No hay ninguna ruta guardada en este dispositivo."
            pickButton.isEnabled = false
            centerButton.isEnabled = false
            return
        }
        pending = Pending(
            summary = CartographicSummaryBuilder.build(plan, plan.cartography, plan.controlPoints()),
            controlPoints = plan.controlPoints()
        )
        title.text = plan.request.name
        summaryText.text = pending!!.summary.format()
        pickButton.isVisibleIf(mode == MODE_PICK)
        pickButton.setOnClickListener { useCentreAsControlPoint() }
        centerButton.setOnClickListener { centreOnBlock() }

        initMap(savedInstanceState)
    }

    /**
     * The map lifecycle only runs once [initMap] succeeded. Forwarding these
     * calls to a MapLibre view that never initialised would turn a missing map
     * key into a crash, and a missing map key is not a crash.
     */
    override fun onResume() {
        super.onResume()
        if (mapInitialised) runCatching { mapWidget.onResume() }
    }

    override fun onPause() {
        if (mapInitialised) runCatching { mapWidget.onPause() }
        super.onPause()
    }

    override fun onDestroy() {
        if (mapInitialised) runCatching { mapWidget.onDestroy() }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mapInitialised) runCatching { mapWidget.onSaveInstanceState(outState) }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (mapInitialised) runCatching { mapWidget.onLowMemory() }
    }

    private fun initMap(savedInstanceState: Bundle?) {
        runCatching {
            mapWidget.initMapLibreMap(this) { map ->
                map.setMapType(DJIMap.MapType.NORMAL)
                mapReady = true
                drawPending()
            }
            mapWidget.onCreate(savedInstanceState)
            mapInitialised = true
        }.onFailure { error ->
            mapReady = false
            mapInitialised = false
            statusText.text = "El mapa no se ha podido iniciar: ${error.message ?: "error de MapLibre"}. " +
                "La ruta y la ficha de vuelo siguen siendo la referencia."
        }
    }

    private fun drawPending() {
        val map = mapWidget.map ?: return
        val current = pending ?: return
        val drawing = MissionMapRenderer.build(
            summary = current.summary,
            controlPoints = current.controlPoints,
            startLatitude = current.summary.startLatitude,
            startLongitude = current.summary.startLongitude
        )
        MissionMapRenderer.draw(map, drawing)
        val points = drawing.allPoints()
        if (points.isEmpty()) return
        val bounds = DJILatLngBounds.fromLatLngs(points.toList())
        center = points.first()
        moveCamera(
            DJICameraUpdateFactory.newLatLngBounds(
                bounds,
                mapWidget.width.coerceAtLeast(1),
                mapWidget.height.coerceAtLeast(1),
                17,
                64
            )
        )
        statusText.text = "Parcela en azul, líneas de vuelo en verde, control en rojo. " +
            "El mapa es una vista de apoyo: no verifica nada."
        pickButton.isEnabled = mode == MODE_PICK
        centerButton.isEnabled = true
    }

    private fun centreOnBlock() {
        val map = mapWidget.map ?: return
        val current = pending ?: return
        val points = MissionMapRenderer.build(current.summary, current.controlPoints).allPoints()
        if (points.isEmpty()) return
        val bounds = DJILatLngBounds.fromLatLngs(points.toList())
        moveCamera(
            DJICameraUpdateFactory.newLatLngBounds(
                bounds,
                mapWidget.width.coerceAtLeast(1),
                mapWidget.height.coerceAtLeast(1),
                17,
                64
            )
        )
    }

    private fun moveCamera(update: DJICameraUpdate) {
        runCatching { mapWidget.map?.moveCamera(update) }
    }

    private fun useCentreAsControlPoint() {
        val point = center ?: run {
            AlertDialog.Builder(this)
                .setTitle("Sin posición")
                .setMessage("El mapa no ha devuelto el centro. Mueve el mapa e inténtalo de nuevo.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        audit.append("map.control_point_picked", null, mapOf("latitude" to point.latitude, "longitude" to point.longitude))
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(RESULT_LATITUDE, point.latitude)
                .putExtra(RESULT_LONGITUDE, point.longitude)
        )
        finish()
    }

    private fun Button.isVisibleIf(condition: Boolean) {
        visibility = if (condition) View.VISIBLE else View.GONE
    }

    companion object {
        const val EXTRA_MODE = "com.djiflypro.map.MODE"
        const val MODE_VIEW = "view"
        const val MODE_PICK = "pick"
        const val RESULT_LATITUDE = "com.djiflypro.map.LATITUDE"
        const val RESULT_LONGITUDE = "com.djiflypro.map.LONGITUDE"

        fun viewIntent(context: Context): Intent =
            Intent(context, MissionMapActivity::class.java).putExtra(EXTRA_MODE, MODE_VIEW)

        fun pickIntent(context: Context): Intent =
            Intent(context, MissionMapActivity::class.java).putExtra(EXTRA_MODE, MODE_PICK)
    }
}
