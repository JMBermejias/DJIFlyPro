package dji.sampleV5.aircraft.pro.guidance

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.pro.mission.AuditLog
import dji.sampleV5.aircraft.pro.mission.MissionStore
import dji.sampleV5.aircraft.pro.mission.MissionPlan
import java.util.Locale

/**
 * Walks an operator through a plan one waypoint at a time, flown by hand.
 *
 * This exists because the Mini 3 family cannot run waypoint missions at all: its
 * firmware does not implement them, so no amount of app-side configuration
 * helps. What the app can do is show where the next point is, how far it is, and
 * which way to turn, while the pilot flies on the remote controller.
 *
 * Nothing in this screen or [GuidanceTelemetryVM] can command the aircraft.
 */
class ManualGuidanceActivity : AppCompatActivity() {

    private val telemetry = GuidanceTelemetryVM()
    private lateinit var store: MissionStore
    private lateinit var audit: AuditLog

    private var session: GuidanceSession? = null
    private var plan: MissionPlan? = null

    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var target: TextView
    private lateinit var turn: TextView
    private lateinit var metrics: TextView
    private lateinit var safety: TextView
    private lateinit var reachedButton: Button
    private lateinit var previousButton: Button
    private lateinit var skipButton: Button
    private lateinit var restartButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_guidance)
        store = MissionStore(this)
        audit = AuditLog(this)

        status = findViewById(R.id.guidance_status)
        progress = findViewById(R.id.guidance_progress)
        target = findViewById(R.id.guidance_target)
        turn = findViewById(R.id.guidance_turn)
        metrics = findViewById(R.id.guidance_metrics)
        safety = findViewById(R.id.guidance_safety)
        reachedButton = findViewById(R.id.button_guidance_reached)
        previousButton = findViewById(R.id.button_guidance_previous)
        skipButton = findViewById(R.id.button_guidance_skip)
        restartButton = findViewById(R.id.button_guidance_restart)

        loadPlan()
        bindActions()
        observeTelemetry()
        render()
    }

    override fun onStart() {
        super.onStart()
        telemetry.start()
    }

    private fun loadPlan() {
        val requestedId = intent.getStringExtra(EXTRA_MISSION_ID)
        val loaded = requestedId?.let { store.load(it) } ?: store.latest()
        if (loaded == null) {
            status.text = "No hay ninguna misión guardada. Genera una en el planificador."
            session = null
            plan = null
            setButtonsEnabled(false)
            return
        }
        plan = loaded
        val built = GuidanceSession(
            loaded.waypoints.map {
                GuidanceTarget(index = it.index, latitude = it.latitude, longitude = it.longitude, heightMeters = it.heightMeters)
            }
        )
        session = built
        audit.append(
            "guidance.opened",
            loaded.id,
            mapOf("waypoints" to built.total, "skipped" to built.skippedCount)
        )
        if (built.skippedCount > 0) {
            status.text = "Misión ${loaded.request.name}: ${built.total} puntos válidos, ${built.skippedCount} descartados por coordenadas no utilizables."
        }
    }

    private fun bindActions() {
        reachedButton.setOnClickListener {
            if (session?.markReached() == true) {
                audit.append("guidance.markedReached", plan?.id, mapOf("position" to session?.position))
                render()
            }
        }
        previousButton.setOnClickListener {
            if (session?.back() == true) {
                audit.append("guidance.previous", plan?.id, mapOf("position" to session?.position))
                render()
            }
        }
        skipButton.setOnClickListener {
            if (session?.skip() == true) {
                audit.append("guidance.skipped", plan?.id, mapOf("position" to session?.position))
                render()
            }
        }
        restartButton.setOnClickListener {
            session?.restart()
            audit.append("guidance.restarted", plan?.id)
            render()
        }
    }

    private fun observeTelemetry() {
        val redraw = { render() }
        telemetry.listening.observe(this) { ok ->
            if (ok == true) return@observe
            status.text = "No se han podido registrar las claves de telemetría. " +
                "La guía no mostrará datos hasta que se resuelva."
        }
        telemetry.aircraftConnected.observe(this) { redraw() }
        telemetry.position.observe(this) {
            val advanced = session?.advanceIfReached(
                from = telemetry.position.value,
                currentAltitudeMeters = telemetry.altitudeMeters.value,
                headingDegrees = telemetry.compassHeadingDegrees.value
            ) == true
            if (advanced) {
                audit.append("guidance.arrived", plan?.id, mapOf("position" to session?.position))
            }
            redraw()
        }
        telemetry.altitudeMeters.observe(this) { redraw() }
        telemetry.compassHeadingDegrees.observe(this) { redraw() }
        telemetry.batteryPercent.observe(this) { redraw() }
        telemetry.gpsSignalLevel.observe(this) { redraw() }
        telemetry.homePosition.observe(this) { redraw() }
    }

    private fun render() {
        val current = session
        if (current == null) {
            setButtonsEnabled(false)
            return
        }
        if (current.isEmpty) {
            progress.text = "La misión no tiene puntos de vuelo utilizables."
            setButtonsEnabled(false)
            return
        }
        if (current.isFinished) {
            progress.text = "Guía completada: ${current.total} puntos."
            target.text = ""
            turn.text = "LISTO"
            metrics.text = ""
            setButtonsEnabled(false)
            return
        }

        val connected = telemetry.aircraftConnected.value == true
        val leg = current.leg(
            from = telemetry.position.value,
            currentAltitudeMeters = telemetry.altitudeMeters.value,
            headingDegrees = telemetry.compassHeadingDegrees.value
        )
        val position = current.current!!

        progress.text = "Punto ${current.position} de ${current.total}  ·  ${(current.progressFraction * 100).toInt()}%"

        val targetLine = buildString {
            append(String.format(Locale.US, "%.6f, %.6f", position.latitude, position.longitude))
            append(String.format(Locale.US, "\nAltitud objetivo: %.1f m", position.heightMeters))
        }
        target.text = targetLine

        turn.text = GuidanceLabels.turn(leg, connected)

        val altitude = telemetry.altitudeMeters.value
        val distance = leg?.distanceMeters
        val heading = telemetry.compassHeadingDegrees.value
        metrics.text = buildString {
            append("Distancia: ").append(GuidanceLabels.meters(distance))
            append("   Rumbo: ").append(GuidanceLabels.degrees(leg?.bearingDegrees))
            append("   Nose: ").append(GuidanceLabels.degrees(heading))
            append("   Altitud: ").append(GuidanceLabels.meters(altitude))
            val home = telemetry.distanceToHomeMeters()
            if (home != null) append("   A casa: ").append(GuidanceLabels.meters(home))
        }

        val battery = telemetry.batteryPercent.value ?: -1
        val gps = telemetry.gpsSignalLevel.value ?: 0
        safety.text = buildString {
            append("Batería: ").append(GuidanceLabels.battery(battery))
            append("   GPS: ").append(if (gps > 0) gps.toString() else "N/A")
            GuidanceLabels.altitudeAdvice(leg)?.let { append("\n").append(it) }
        }

        setButtonsEnabled(true)
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        reachedButton.isEnabled = enabled
        skipButton.isEnabled = enabled
        previousButton.isEnabled = enabled
        restartButton.isEnabled = enabled
    }

    companion object {
        const val EXTRA_MISSION_ID = "mission_id"
    }
}
