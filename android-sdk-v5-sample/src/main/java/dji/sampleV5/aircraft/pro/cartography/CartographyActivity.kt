package dji.sampleV5.aircraft.pro.cartography

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.pro.cartography.ui.ControlPointEditorDialog
import dji.sampleV5.aircraft.pro.mission.AuditLog
import dji.sampleV5.aircraft.pro.mission.MissionStore
import java.util.Locale
import java.util.concurrent.Executors

/** Route pattern keys the cartography screen asks the planner for. */
private object RoutePatternKeys {
    const val PARALLEL = "parallel"
    const val GRID = "grid"
}

/**
 * The cartography screen: state a resolution and an overlap, get a flight
 * height and two spacings back.
 *
 * This is the direction a survey actually works in. A GSD and a payload are
 * facts from a specification; the height and the line spacing are consequences,
 * and the route is built from the consequences so the plan and the deliverable
 * cannot disagree.
 */
class CartographyActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: MissionStore
    private lateinit var audit: AuditLog

    private lateinit var cameraSpinner: Spinner
    private lateinit var cameraDetail: TextView
    private lateinit var gsdInput: EditText
    private lateinit var forwardOverlapInput: EditText
    private lateinit var sideOverlapInput: EditText
    private lateinit var altitudeReferenceSpinner: Spinner
    private lateinit var terrainFollowing: CheckBox
    private lateinit var crossTrack: CheckBox
    private lateinit var lengthInput: EditText
    private lateinit var widthInput: EditText
    private lateinit var latitudeInput: EditText
    private lateinit var longitudeInput: EditText
    private lateinit var solutionText: TextView
    private lateinit var applyButton: Button
    private lateinit var controlNetworkText: TextView
    private lateinit var controlPointsLayout: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var exportGeoJsonButton: Button
    private lateinit var exportKmlButton: Button
    private lateinit var exportReportButton: Button
    private lateinit var openMapButton: Button

    private val controlPoints = mutableListOf<GroundControlPoint>()

    private var solution: CartographySolutionResult? = null
    private var profile: CartographyProfile = CartographyProfile.DEFAULT

    private val exportGeoJson =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/geo+json")) { uri ->
            uri?.let { writeText(it, pendingExport.orEmpty()) }
            pendingExport = null
        }
    private val exportKml =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.google-earth.kml+xml")) { uri ->
            uri?.let { writeText(it, pendingExport.orEmpty()) }
            pendingExport = null
        }
    private val exportReport =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { writeText(it, pendingExport.orEmpty()) }
            pendingExport = null
        }
    private var pendingExport: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cartography)
        store = MissionStore(this)
        audit = AuditLog(this)
        bindViews()
        setDefaults()
        bindActions()
        controlPoints.addAll(store.latest()?.controlPoints().orEmpty())
        renderControlPoints()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
        cameraSpinner = findViewById(R.id.spinner_camera)
        cameraDetail = findViewById(R.id.text_camera_detail)
        gsdInput = findViewById(R.id.edit_gsd)
        forwardOverlapInput = findViewById(R.id.edit_forward_overlap)
        sideOverlapInput = findViewById(R.id.edit_side_overlap)
        altitudeReferenceSpinner = findViewById(R.id.spinner_altitude_reference)
        terrainFollowing = findViewById(R.id.check_terrain_following)
        crossTrack = findViewById(R.id.check_cross_track)
        lengthInput = findViewById(R.id.edit_area_length)
        widthInput = findViewById(R.id.edit_area_width)
        latitudeInput = findViewById(R.id.edit_area_latitude)
        longitudeInput = findViewById(R.id.edit_area_longitude)
        solutionText = findViewById(R.id.text_solution)
        applyButton = findViewById(R.id.button_apply_to_planner)
        controlNetworkText = findViewById(R.id.text_control_network)
        controlPointsLayout = findViewById(R.id.layout_control_points)
        progress = findViewById(R.id.progress_cartography)
        exportGeoJsonButton = findViewById(R.id.button_export_geojson)
        exportKmlButton = findViewById(R.id.button_export_kml)
        exportReportButton = findViewById(R.id.button_export_report)
        openMapButton = findViewById(R.id.button_open_map)

        cameraSpinner.adapter = spinner(
            SurveyCamera.BUILT_INS.map { "${it.displayName}" }
        )
        altitudeReferenceSpinner.adapter = spinner(
            AltitudeReference.entries.map { it.displayName }
        )
        cameraSpinner.onItemSelectedListener = SimpleSelection { renderCameraDetail() }
        terrainFollowing.isChecked = true
        crossTrack.isChecked = false
    }

    private fun setDefaults() {
        gsdInput.setText("2")
        forwardOverlapInput.setText("80")
        sideOverlapInput.setText("70")
        lengthInput.setText("200")
        widthInput.setText("120")
        latitudeInput.setText("40.4168")
        longitudeInput.setText("-3.7038")
    }

    private fun bindActions() {
        findViewById<Button>(R.id.button_calculate).setOnClickListener { calculate() }
        applyButton.setOnClickListener { applyToPlanner() }
        findViewById<Button>(R.id.button_add_control_point).setOnClickListener {
            ControlPointEditorDialog.show(this, null) { point -> upsertControlPoint(point) }
        }
        findViewById<Button>(R.id.button_pick_control_point).setOnClickListener {
            startActivity(
                Intent(this, dji.sampleV5.aircraft.pro.map.MissionMapActivity::class.java)
                    .putExtra(dji.sampleV5.aircraft.pro.map.MissionMapActivity.EXTRA_MODE, "pick")
            )
        }
        exportGeoJsonButton.setOnClickListener { export(ExportFormat.GEOJSON) }
        exportKmlButton.setOnClickListener { export(ExportFormat.KML) }
        exportReportButton.setOnClickListener { export(ExportFormat.REPORT) }
        openMapButton.setOnClickListener {
            startActivity(
                Intent(this, dji.sampleV5.aircraft.pro.map.MissionMapActivity::class.java)
                    .putExtra(dji.sampleV5.aircraft.pro.map.MissionMapActivity.EXTRA_MODE, "view")
            )
        }
    }

    private fun currentCamera(): SurveyCamera =
        SurveyCamera.BUILT_INS.getOrNull(cameraSpinner.selectedItemPosition) ?: SurveyCamera.DEFAULT

    private fun renderCameraDetail() {
        val camera = currentCamera()
        cameraDetail.text = buildString {
            appendLine("Sensor: ${format("%.1f", camera.sensorWidthMillimeters)} × ${format("%.1f", camera.sensorHeightMillimeters)} mm")
            appendLine("Objetivo: ${format("%.1f", camera.focalLengthMillimeters)} mm")
            appendLine("Resolución: ${camera.imageWidthPixels} × ${camera.imageHeightPixels} px")
            append("Campo de visión: ${format("%.1f", camera.horizontalFovDegrees)}° × ${format("%.1f", camera.verticalFovDegrees)}°")
        }
    }

    private fun readProfile(): CartographyProfile {
        val cameraIndex = cameraSpinner.selectedItemPosition
        return CartographyProfile(
            cameraId = SurveyCamera.BUILT_INS.getOrNull(cameraIndex)?.id ?: SurveyCamera.DEFAULT.id,
            targetGsdCentimetersPerPixel = number(gsdInput, "Resolución"),
            forwardOverlapPercent = number(forwardOverlapInput, "Solape longitudinal").toInt(),
            sideOverlapPercent = number(sideOverlapInput, "Solape transversal").toInt(),
            altitudeReference = AltitudeReference.entries[
                altitudeReferenceSpinner.selectedItemPosition.coerceIn(0, AltitudeReference.entries.lastIndex)
            ],
            terrainFollowing = terrainFollowing.isChecked,
            crossTrack = crossTrack.isChecked
        )
    }

    private fun calculate() {
        val candidate = runCatching { readProfile() }.getOrElse { error ->
            showError("Revisa los parámetros", error.message ?: "Parámetros inválidos")
            return
        }
        val length = runCatching { number(lengthInput, "Longitud de la parcela") }.getOrElse { error ->
            showError("Revisa la parcela", error.message ?: "Valor inválido")
            return
        }
        val width = runCatching { number(widthInput, "Ancho de la parcela") }.getOrElse { error ->
            showError("Revisa la parcela", error.message ?: "Valor inválido")
            return
        }
        val result = runCatching { CartographySolution.solve(candidate, length, width) }.getOrElse { error ->
            showError("No se pudo calcular", error.message ?: "Configuración no válida")
            return
        }
        profile = candidate
        solution = result
        audit.append(
            "cartography.solved",
            null,
            mapOf(
                "camera" to candidate.cameraId,
                "targetGsd" to candidate.targetGsdCentimetersPerPixel,
                "height" to result.altitudeMeters,
                "lineSpacing" to result.lineSpacingMeters,
                "photoSpacing" to result.photoSpacingMeters,
                "automatic" to result.isFlyableAutomatically
            )
        )
        renderSolution(result)
    }

    private fun renderSolution(result: CartographySolutionResult) {
        solutionText.text = buildString {
            appendLine("Altura de vuelo: ${format("%.1f", result.altitudeMeters)} m")
            appendLine("Resolución prevista: ${format("%.2f", result.achievedGsdCentimetersPerPixel)} cm/px")
            appendLine("Huella de la foto: ${format("%.1f", result.footprintWidthMeters)} × ${format("%.1f", result.footprintDepthMeters)} m")
            appendLine("Separación entre líneas: ${format("%.2f", result.lineSpacingMeters)} m")
            appendLine("Separación entre fotos: ${format("%.2f", result.photoSpacingMeters)} m")
            appendLine("Líneas de vuelo: ${result.lineCount}")
            appendLine("Fotos por línea: ${result.photosPerLine}")
            appendLine("Fotos estimadas: ${result.estimatedPhotoCount}")
            appendLine("Superficie: ${format("%.3f", result.areaHectares)} ha")
            appendLine("Solape efectivo: ${format("%.2f", result.redundancyFactor)}×")
            if (result.isFlyableAutomatically) {
                appendLine("Ejecución automática: disponible hasta 120 m")
            } else {
                appendLine("Ejecución automática: bloqueada por altura, se vuela con guía manual")
            }
            if (result.notes.isNotEmpty()) {
                appendLine()
                result.notes.forEach { appendLine("• $it") }
            }
        }
        renderControlNetwork()
        applyButton.isEnabled = true
        exportGeoJsonButton.isEnabled = true
        exportKmlButton.isEnabled = true
        exportReportButton.isEnabled = true
        openMapButton.isEnabled = true
    }

    private fun applyToPlanner() {
        val result = solution ?: return
        val plan = store.latest()
        val latitude = runCatching { number(latitudeInput, "Latitud") }.getOrElse { plan?.request?.centerLatitude ?: return }
        val longitude = runCatching { number(longitudeInput, "Longitud") }.getOrElse { plan?.request?.centerLongitude ?: return }
        val intent = CartographyTransfer.putControlPoints(
            CartographyTransfer.putProfile(Intent(), profile),
            controlPoints
        ).apply {
            putExtra(RESULT_ALTITUDE, result.altitudeMeters)
            putExtra(RESULT_LINE_SPACING, result.lineSpacingMeters)
            putExtra(RESULT_PHOTO_SPACING, result.photoSpacingMeters)
            putExtra(RESULT_LATITUDE, latitude)
            putExtra(RESULT_LONGITUDE, longitude)
            putExtra(RESULT_ROUTE_PATTERN, if (profile.crossTrack) RoutePatternKeys.GRID else RoutePatternKeys.PARALLEL)
        }
        audit.append("cartography.applied", plan?.id, mapOf("camera" to profile.cameraId, "gsd" to profile.targetGsdCentimetersPerPixel))
        setResult(RESULT_OK, intent)
        finish()
    }

    private fun upsertControlPoint(point: GroundControlPoint) {
        val validation = point.validate()
        if (!validation.isValid) {
            showError("Punto de control no válido", validation.errors.joinToString("\n"))
            return
        }
        if (!ControlNetwork.isDistinctEnough(point, controlPoints)) {
            showError("Punto duplicado", "Ya hay otro punto a menos de 5 m de este.")
            return
        }
        controlPoints.removeAll { it.id == point.id }
        controlPoints += point
        audit.append(
            "cartography.control_point",
            null,
            mapOf("code" to point.code, "role" to point.role.key, "target" to point.target.key)
        )
        renderControlPoints()
    }

    private fun renderControlPoints() {
        controlPointsLayout.removeAllViews()
        controlPoints.forEach { point ->
            val card = LayoutInflater.from(this)
                .inflate(R.layout.pro_control_point_card, controlPointsLayout, false)
            card.findViewById<TextView>(R.id.text_control_point_title).text = buildString {
                append(point.code)
                append(" · ")
                append(point.role.displayName)
            }
            card.findViewById<TextView>(R.id.text_control_point_detail).text = buildString {
                append(CoordinateFormat.latitude(point.latitude))
                append(", ")
                append(CoordinateFormat.longitude(point.longitude))
                append('\n')
                append("${point.target.displayName} (±${point.target.typicalAccuracyMillimeters.toInt()} mm)")
                if (point.heightMeters != null) append(" · ${format("%.2f", point.heightMeters)} m")
                point.horizontalAccuracyMillimeters?.let { append(" · medida ±${it.toInt()} mm") }
                if (point.source.isNotBlank()) append("\nOrigen: ${point.source}")
            }
            card.findViewById<Button>(R.id.button_edit_control_point).setOnClickListener {
                ControlPointEditorDialog.show(this, point) { updated -> upsertControlPoint(updated) }
            }
            card.findViewById<Button>(R.id.button_delete_control_point).setOnClickListener {
                controlPoints.removeAll { it.id == point.id }
                audit.append("cartography.control_point_removed", null, mapOf("code" to point.code))
                renderControlPoints()
            }
            controlPointsLayout.addView(card)
        }
        renderControlNetwork()
    }

    private fun renderControlNetwork() {
        val latitude = runCatching { number(latitudeInput, "Latitud") }.getOrNull()
        val longitude = runCatching { number(longitudeInput, "Longitud") }.getOrNull()
        val length = runCatching { number(lengthInput, "Longitud") }.getOrNull() ?: 0.0
        val width = runCatching { number(widthInput, "Ancho") }.getOrNull() ?: 0.0
        if (latitude == null || longitude == null) {
            controlNetworkText.text = "Introduce el centro de la parcela para comprobar la red de control."
            return
        }
        val assessment = ControlNetwork.assess(
            points = controlPoints,
            blockCenterLatitude = latitude,
            blockCenterLongitude = longitude,
            blockRadiusMeters = if (length > 0.0 && width > 0.0) {
                ControlNetwork.blockRadiusMeters(length, width)
            } else {
                0.0
            }
        )
        controlNetworkText.text = buildString {
            appendLine(
                "${assessment.metrics.controlCount} de control, ${assessment.metrics.checkCount} de verificación"
            )
            if (assessment.metrics.totalCount > 0) {
                val percent = (assessment.metrics.blockCoverageRatio * 100).toInt()
                appendLine("Cobertura de la parcela: $percent% del radio")
            }
            assessment.errors.forEach { appendLine("✗ $it") }
            assessment.warnings.forEach { appendLine("• $it") }
            if (assessment.isValid && assessment.warnings.isEmpty()) {
                append("Red de control utilizable para georreferenciar el bloque.")
            }
        }
    }

    private enum class ExportFormat { GEOJSON, KML, REPORT }

    private fun export(format: ExportFormat) {
        val plan = store.latest()
        if (plan == null) {
            showError("No hay bloque", "Genera primero una ruta en el planificador para poder exportar.")
            return
        }
        val current = runCatching { readProfile() }.getOrElse { error ->
            showError("Revisa los parámetros", error.message ?: "Perfil no válido")
            return
        }
        progress.visibility = View.VISIBLE
        progress.progress = 0
        executor.execute {
            runCatching {
                when (format) {
                    ExportFormat.GEOJSON -> GeoJsonExporter.export(plan, current, controlPoints)
                    ExportFormat.KML -> KmlExporter.export(plan, current, controlPoints)
                    ExportFormat.REPORT -> CartographyReportBuilder.build(plan, current, controlPoints)
                }
            }.onSuccess { text ->
                runOnUiThread {
                    progress.visibility = View.GONE
                    pendingExport = text
                    audit.append(
                        "cartography.exported",
                        plan.id,
                        mapOf("format" to format.name.lowercase(), "bytes" to text.length)
                    )
                    when (format) {
                        ExportFormat.GEOJSON -> exportGeoJson.launch("${plan.id}.geojson")
                        ExportFormat.KML -> exportKml.launch("${plan.id}.kml")
                        ExportFormat.REPORT -> exportReport.launch("${plan.id}-ficha.json")
                    }
                }
            }.onFailure { error ->
                runOnUiThread {
                    progress.visibility = View.GONE
                    showError("No se pudo exportar", error.message ?: "Error de generación")
                }
            }
        }
    }

    private fun spinner(values: List<String>): ArrayAdapter<String> =
        ArrayAdapter(this, android.R.layout.simple_spinner_item, values)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

    private fun number(input: EditText, label: String): Double =
        input.text.toString().trim().replace(',', '.').toDoubleOrNull()
            ?: error("$label debe ser un número")

    private fun writeText(uri: Uri, value: String) {
        runCatching {
            contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(value) }
        }.onFailure { showError("No se pudo exportar", it.message ?: "Error de E/S") }
    }

    private fun showError(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun format(pattern: String, value: Double): String = String.format(Locale.US, pattern, value)

    /** Minimal selection listener so a spinner change has a readable name. */
    private class SimpleSelection(
        private val onChange: (Int) -> Unit
    ) : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onChange(position)
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    companion object {
        const val RESULT_ALTITUDE = "com.djiflypro.cartography.ALTITUDE"
        const val RESULT_LINE_SPACING = "com.djiflypro.cartography.LINE_SPACING"
        const val RESULT_PHOTO_SPACING = "com.djiflypro.cartography.PHOTO_SPACING"
        const val RESULT_LATITUDE = "com.djiflypro.cartography.LATITUDE"
        const val RESULT_LONGITUDE = "com.djiflypro.cartography.LONGITUDE"
        const val RESULT_ROUTE_PATTERN = "com.djiflypro.cartography.ROUTE_PATTERN"
    }
}
