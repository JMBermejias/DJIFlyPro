package dji.sampleV5.aircraft.pro.mission

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.MSDKManagerVM
import dji.sampleV5.aircraft.models.globalViewModels
import dji.sampleV5.aircraft.pro.algorithm.AlgorithmRepository
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.manager.aircraft.waypoint3.WaypointMissionExecuteStateListener
import dji.v5.manager.aircraft.waypoint3.WaypointMissionManager
import dji.v5.manager.aircraft.waypoint3.model.WaypointMissionExecuteState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MissionPlannerActivity : AppCompatActivity() {
    private val msdkManagerVM: MSDKManagerVM by globalViewModels()
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var store: MissionStore
    private lateinit var audit: AuditLog
    private lateinit var algorithmRepository: AlgorithmRepository

    private lateinit var templateSpinner: Spinner
    private lateinit var routeSpinner: Spinner
    private lateinit var summary: TextView
    private lateinit var warnings: TextView
    private lateinit var capability: TextView
    private lateinit var progress: ProgressBar
    private lateinit var uploadButton: Button
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var stopButton: Button
    private lateinit var exportJsonButton: Button
    private lateinit var exportKmzButton: Button

    private var currentPlan: MissionPlan? = null
    private var currentKmz: File? = null
    private var uploadedMissionId: String? = null
    private var availableWaylineIds: List<Int> = emptyList()
    private var selectedWaylineIds: List<Int> = emptyList()
    private var registered = false
    private var connected = false
    private var firmwareVersion: String? = null
    private var remoteControllerName: String? = null
    private var remoteControllerFirmwareVersion: String? = null
    private var validatedProfiles: Set<ValidatedWpmlProfile> = emptySet()
    private var missionPaused = false
    private var missionState = WaypointMissionExecuteState.UNKNOWN
    private var selectedAlgorithmId: String? = null

    private val missionStateListener = WaypointMissionExecuteStateListener { state ->
        runOnUiThread { handleMissionState(state) }
    }

    private val createJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeText(it, currentPlan?.let { plan -> store.exportText(plan) }.orEmpty()) }
    }
    private val createKmz = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { copyFile(it, currentKmz) }
    }
    private val createValidationReport =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { writeText(it, pendingValidationReport.orEmpty()) }
            pendingValidationReport = null
        }
    private var pendingValidationReport: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mission_planner)
        store = MissionStore(this)
        audit = AuditLog(this)
        algorithmRepository = AlgorithmRepository(this)
        validatedProfiles = ValidatedWpmlProfileRepository(this).load()
        audit.append(
            "wpml.allowlist.loaded",
            null,
            mapOf(
                "profileCount" to validatedProfiles.size,
                "executionEnabled" to (validatedProfiles.isNotEmpty())
            )
        )
        bindViews()
        setDefaultFormValues()
        bindActions()
        observeSdk()
        selectedAlgorithmId = intent.getStringExtra(EXTRA_ALGORITHM_ID)
        selectedAlgorithmId?.let { applyAlgorithm(it) }
        refreshCapability()
    }

    override fun onStart() {
        super.onStart()
        runCatching {
            WaypointMissionManager.getInstance()
                .addWaypointMissionExecuteStateListener(missionStateListener)
        }
    }

    override fun onStop() {
        runCatching {
            WaypointMissionManager.getInstance()
                .removeWaypointMissionExecuteStateListener(missionStateListener)
        }
        super.onStop()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
        templateSpinner = findViewById(R.id.spinner_template)
        routeSpinner = findViewById(R.id.spinner_route_pattern)
        summary = findViewById(R.id.text_plan_summary)
        warnings = findViewById(R.id.text_plan_warnings)
        capability = findViewById(R.id.text_capability)
        progress = findViewById(R.id.progress_mission)
        uploadButton = findViewById(R.id.button_upload_mission)
        startButton = findViewById(R.id.button_start_mission)
        pauseButton = findViewById(R.id.button_pause_mission)
        stopButton = findViewById(R.id.button_stop_mission)
        exportJsonButton = findViewById(R.id.button_export_json)
        exportKmzButton = findViewById(R.id.button_export_kmz)

        templateSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            MissionTemplate.entries.map { it.displayName }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        routeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            RoutePattern.entries.map { it.displayName }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun setDefaultFormValues() {
        findViewById<EditText>(R.id.edit_latitude).setText("40.4168")
        findViewById<EditText>(R.id.edit_longitude).setText("-3.7038")
        findViewById<EditText>(R.id.edit_length).setText("80")
        findViewById<EditText>(R.id.edit_width).setText("50")
        findViewById<EditText>(R.id.edit_height).setText("30")
        findViewById<EditText>(R.id.edit_bearing).setText("0")
        findViewById<EditText>(R.id.edit_altitude).setText("40")
        findViewById<EditText>(R.id.edit_standoff).setText("10")
        findViewById<EditText>(R.id.edit_line_spacing).setText("12")
        findViewById<EditText>(R.id.edit_photo_spacing).setText("8")
        findViewById<EditText>(R.id.edit_overlap).setText("70")
        findViewById<EditText>(R.id.edit_speed).setText("4")
        findViewById<EditText>(R.id.edit_mission_name).setText("Misión ${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}")
    }

    private fun bindActions() {
        findViewById<Button>(R.id.button_generate_mission).setOnClickListener { generateMission() }
        exportJsonButton.setOnClickListener {
            currentPlan?.let { createJson.launch("${it.id}.json") }
        }
        exportKmzButton.setOnClickListener { exportKmz() }
        findViewById<Button>(R.id.button_export_validation_report).setOnClickListener {
            exportValidationReport()
        }
        uploadButton.setOnClickListener { uploadMission() }
        startButton.setOnClickListener { startMission() }
        pauseButton.setOnClickListener { pauseOrResumeMission() }
        stopButton.setOnClickListener { stopMission() }
    }

    private fun observeSdk() {
        msdkManagerVM.lvRegisterState.observe(this) { (value, _) ->
            registered = value
            refreshCapability()
        }
        msdkManagerVM.lvProductConnectionState.observe(this) { (value, _) ->
            connected = value
            refreshCapability()
        }
        msdkManagerVM.lvFirmwareVersion.observe(this) { value ->
            firmwareVersion = value
            refreshCapability()
        }
        msdkManagerVM.lvRemoteControllerType.observe(this) { value ->
            remoteControllerName = value
            refreshCapability()
        }
        msdkManagerVM.lvRemoteControllerFirmwareVersion.observe(this) { value ->
            remoteControllerFirmwareVersion = value
            refreshCapability()
        }
    }

    private fun currentCapability(): DroneCapabilities.Result = DroneCapabilities.current(
        connected = connected,
        registered = registered,
        firmwareVersion = firmwareVersion,
        remoteControllerName = remoteControllerName,
        remoteControllerFirmwareVersion = remoteControllerFirmwareVersion,
        validatedProfiles = validatedProfiles,
        firmwareWaypointSupport = firmwareWaypointSupport()
    )

    /**
     * Translates the live `WaypointMissionExecuteState` into the capability
     * signal the gate understands. `NOT_SUPPORTED` is the aircraft itself
     * declaring that its firmware cannot run waypoint missions, which is
     * stronger evidence than any product table.
     */
    private fun firmwareWaypointSupport(): FirmwareWaypointSupport =
        when (missionState) {
            WaypointMissionExecuteState.NOT_SUPPORTED -> FirmwareWaypointSupport.NOT_SUPPORTED
            WaypointMissionExecuteState.DISCONNECTED,
            WaypointMissionExecuteState.UNKNOWN,
            WaypointMissionExecuteState.IDLE -> FirmwareWaypointSupport.UNKNOWN
            else -> FirmwareWaypointSupport.SUPPORTED
        }

    private fun refreshCapability() {
        val result = currentCapability()
        val firmwareLabel = result.firmwareVersion?.let { " (firmware $it)" } ?: " (firmware desconocido)"
        val remoteLabel = result.remoteControllerName?.let { " / RC $it" } ?: " / RC desconocido"
        capability.text = "Compatibilidad: ${result.productName}$firmwareLabel$remoteLabel\n${result.reason}"
        val hasPlan = currentPlan != null
        val hasUploadedMission = uploadedMissionId != null
        val missionBusy = missionState in setOf(
            WaypointMissionExecuteState.UPLOADING,
            WaypointMissionExecuteState.PREPARING,
            WaypointMissionExecuteState.ENTER_WAYLINE,
            WaypointMissionExecuteState.EXECUTING,
            WaypointMissionExecuteState.RECOVERING,
            WaypointMissionExecuteState.RETURN_TO_START_POINT,
            WaypointMissionExecuteState.INTERRUPTED
        )
        val canStart = missionState == WaypointMissionExecuteState.READY ||
            missionState == WaypointMissionExecuteState.IDLE
        val canControl = missionState in setOf(
            WaypointMissionExecuteState.PREPARING,
            WaypointMissionExecuteState.ENTER_WAYLINE,
            WaypointMissionExecuteState.EXECUTING,
            WaypointMissionExecuteState.RECOVERING,
            WaypointMissionExecuteState.RETURN_TO_START_POINT,
            WaypointMissionExecuteState.INTERRUPTED
        )
        val missionControlAvailable = connected && registered
        uploadButton.isEnabled = hasPlan && result.waypointUploadSupported && !missionBusy
        startButton.isEnabled = hasPlan && hasUploadedMission && selectedWaylineIds.isNotEmpty() &&
            result.waypointExecutionSupported && canStart
        pauseButton.isEnabled = hasUploadedMission && missionControlAvailable && canControl
        stopButton.isEnabled = hasUploadedMission && missionControlAvailable && canControl
    }

    private fun handleMissionState(state: WaypointMissionExecuteState) {
        missionState = state
        missionPaused = state == WaypointMissionExecuteState.INTERRUPTED
        refreshCapability()
    }

    private fun generateMission() {
        val request = runCatching { readRequest() }.getOrElse { error ->
            showError("Revisa los parámetros", error.message ?: "Parámetros inválidos")
            return
        }
        progress.visibility = View.VISIBLE
        progress.progress = 0
        executor.execute {
            try {
                val plan = MissionGeometry.plan(request, selectedAlgorithmId)
                val validation = MissionValidator.validate(request, plan)
                if (!validation.isValid) {
                    runOnUiThread {
                        progress.visibility = View.GONE
                        showError("La misión no es válida", validation.errors.joinToString("\n"))
                    }
                    return@execute
                }
                store.save(plan)
                audit.append("mission.created", plan.id, mapOf("template" to request.template.key, "waypoints" to plan.waypoints.size))
                runOnUiThread {
                    currentPlan = plan
                    currentKmz = null
                    uploadedMissionId = null
                    availableWaylineIds = emptyList()
                    selectedWaylineIds = emptyList()
                    missionState = WaypointMissionExecuteState.UNKNOWN
                    missionPaused = false
                    progress.visibility = View.GONE
                    renderPlan(plan)
                    refreshCapability()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    progress.visibility = View.GONE
                    showError("No se pudo generar la ruta", error.message ?: "Error de planificación")
                }
            }
        }
    }

    private fun readRequest(): MissionRequest {
        val template = MissionTemplate.entries[templateSpinner.selectedItemPosition]
        val route = RoutePattern.entries[routeSpinner.selectedItemPosition]
        return MissionRequest(
            name = text(R.id.edit_mission_name, "Nombre"),
            template = template,
            centerLatitude = number(R.id.edit_latitude, "Latitud"),
            centerLongitude = number(R.id.edit_longitude, "Longitud"),
            lengthMeters = number(R.id.edit_length, "Longitud de la zona"),
            widthMeters = number(R.id.edit_width, "Ancho"),
            heightMeters = number(R.id.edit_height, "Altura"),
            bearingDegrees = number(R.id.edit_bearing, "Rumbo"),
            altitudeMeters = number(R.id.edit_altitude, "Altura de vuelo"),
            standoffMeters = number(R.id.edit_standoff, "Distancia de seguridad"),
            lineSpacingMeters = number(R.id.edit_line_spacing, "Separación entre líneas"),
            photoSpacingMeters = number(R.id.edit_photo_spacing, "Separación entre fotos"),
            overlapPercent = number(R.id.edit_overlap, "Solape").toInt(),
            speedMps = number(R.id.edit_speed, "Velocidad"),
            routePattern = route,
            finishAction = FinishAction.RETURN_HOME,
            gimbalPitchDegrees = if (template == MissionTemplate.FACADE) -45.0 else -90.0
        )
    }

    private fun renderPlan(plan: MissionPlan) {
        summary.text = buildString {
            append("Misión: ${plan.request.name}\n")
            append("Puntos: ${plan.waypoints.size}\n")
            append("Distancia: ${format("%.1f", plan.totalDistanceMeters / 1000.0)} km\n")
            append("Duración estimada: ${format("%.1f", plan.estimatedDurationSeconds / 60.0)} min\n")
            append("Fotos: ${plan.waypoints.count { it.takePhoto }}\n")
            append("ID: ${plan.id}")
        }
        warnings.text = plan.warnings.joinToString("\n\n") { "• $it" }
        exportJsonButton.isEnabled = true
        exportKmzButton.isEnabled = true
    }

    private fun exportKmz(onGenerated: ((File) -> Unit)? = null) {
        val plan = currentPlan ?: return
        progress.visibility = View.VISIBLE
        progress.progress = 0
        executor.execute {
            runCatching { WpmlMissionExporter.export(this, plan) }
                .onSuccess { file ->
                    runOnUiThread {
                        currentKmz = file
                        progress.visibility = View.GONE
                        if (onGenerated != null) {
                            onGenerated(file)
                        } else {
                            createKmz.launch("${plan.id}.kmz")
                        }
                    }
                }
                .onFailure { error ->
                    runOnUiThread {
                        progress.visibility = View.GONE
                        showError("No se pudo crear el KMZ", error.message ?: "Error WPML")
                    }
                }
        }
    }

    private fun uploadMission() {
        val plan = currentPlan ?: return
        val validation = MissionValidator.validate(plan.request, plan)
        if (!validation.isValid) {
            showError("Misión no válida", validation.errors.joinToString("\n"))
            return
        }
        val capability = currentCapability()
        if (!capability.waypointUploadSupported) {
            auditBlocked("upload.request", capability)
            showError("Ejecución bloqueada", capability.reason)
            return
        }
        if (missionState in setOf(
                WaypointMissionExecuteState.UPLOADING,
                WaypointMissionExecuteState.PREPARING,
                WaypointMissionExecuteState.ENTER_WAYLINE,
                WaypointMissionExecuteState.EXECUTING,
                WaypointMissionExecuteState.RECOVERING,
                WaypointMissionExecuteState.RETURN_TO_START_POINT,
                WaypointMissionExecuteState.INTERRUPTED
            )
        ) {
            showError("Misión ocupada", "Espera a que la misión actual termine o deténla antes de subir otra.")
            return
        }
        val file = currentKmz
        if (file == null || !file.exists()) {
            exportKmz { generated ->
                if (currentPlan?.id == plan.id) uploadKmz(plan, generated)
            }
            return
        }
        uploadKmz(plan, file)
    }

    private fun uploadKmz(plan: MissionPlan, file: File) {
        val validation = MissionValidator.validate(plan.request, plan)
        if (!validation.isValid) {
            showError("Misión no válida", validation.errors.joinToString("\n"))
            return
        }
        val capability = currentCapability()
        if (!capability.waypointUploadSupported) {
            auditBlocked("upload.send", capability)
            showError("Ejecución bloqueada", capability.reason)
            return
        }
        if (!file.exists() || file.length() <= 0L) {
            showError("Misión no disponible", "El KMZ no existe o está vacío.")
            return
        }

        uploadedMissionId = null
        availableWaylineIds = emptyList()
        selectedWaylineIds = emptyList()
        missionState = WaypointMissionExecuteState.UPLOADING
        progress.visibility = View.VISIBLE
        progress.progress = 0
        val manager = WaypointMissionManager.getInstance()
        runCatching {
            manager.pushKMZFileToAircraft(
                file.absolutePath,
                object : CommonCallbacks.CompletionCallbackWithProgress<Double> {
                    override fun onProgressUpdate(progress: Double) {
                        runOnUiThread {
                            this@MissionPlannerActivity.progress.progress = (progress * 100).toInt().coerceIn(0, 100)
                        }
                    }

                    override fun onSuccess() {
                        val ids = runCatching {
                            manager.getAvailableWaylineIDs(file.absolutePath)
                        }.getOrDefault(emptyList()).distinct().sorted()
                        runOnUiThread {
                            progress.visibility = View.GONE
                            val latestCapability = currentCapability()
                            if (currentPlan?.id != plan.id || !latestCapability.waypointUploadSupported) {
                                missionState = WaypointMissionExecuteState.UNKNOWN
                                refreshCapability()
                                showError(
                                    "Misión no habilitada",
                                    "La conexión, el registro o el firmware cambiaron durante la subida; no se habilita el inicio."
                                )
                            } else if (ids.isEmpty()) {
                                missionState = WaypointMissionExecuteState.UNKNOWN
                                refreshCapability()
                                showError("Misión sin waylines", "El aircraft no devolvió waylines válidas.")
                            } else {
                                val missionId = file.nameWithoutExtension
                                uploadedMissionId = missionId
                                availableWaylineIds = ids
                                selectedWaylineIds = ids
                                missionState = WaypointMissionExecuteState.READY
                                audit.append(
                                    "mission.uploaded",
                                    plan.id,
                                    mapOf("missionId" to missionId, "waylines" to ids.size)
                                )
                                refreshCapability()
                                showInfo("Misión subida", "Se han encontrado ${ids.size} wayline(s). Selecciona cuáles ejecutar.")
                            }
                        }
                    }

                    override fun onFailure(error: IDJIError) {
                        runOnUiThread {
                            missionState = WaypointMissionExecuteState.UNKNOWN
                            progress.visibility = View.GONE
                            refreshCapability()
                            showError("Fallo al subir", error.description())
                        }
                    }
                }
            )
        }.onFailure { error ->
            runOnUiThread {
                missionState = WaypointMissionExecuteState.UNKNOWN
                progress.visibility = View.GONE
                refreshCapability()
                showError("Fallo al iniciar la subida", error.message ?: "Error de MSDK")
            }
        }
    }

    private fun startMission() {
        val plan = currentPlan ?: return
        val missionId = uploadedMissionId ?: return
        val capability = currentCapability()
        if (!capability.waypointExecutionSupported) {
            auditBlocked("mission.start.request", capability)
            showError("Ejecución bloqueada", capability.reason)
            return
        }
        if (missionState !in setOf(WaypointMissionExecuteState.READY, WaypointMissionExecuteState.IDLE)) {
            showError("Misión no disponible", "El aircraft no está en un estado listo para iniciar.")
            return
        }
        val validation = MissionValidator.validate(plan.request, plan)
        if (!validation.isValid) {
            showError("Misión no válida", validation.errors.joinToString("\n"))
            return
        }
        val file = currentKmz
        val ids = availableWaylineIds.ifEmpty {
            if (file == null || !file.exists()) {
                emptyList()
            } else {
                runCatching {
                    WaypointMissionManager.getInstance().getAvailableWaylineIDs(file.absolutePath)
                }.getOrDefault(emptyList())
            }
        }.distinct().sorted()
        if (ids.isEmpty()) {
            showError("Misión sin waylines", "El aircraft no ha devuelto waylines válidas.")
            return
        }
        availableWaylineIds = ids
        val initialSelection = selectedWaylineIds.filter { it in ids }.ifEmpty { ids }
        showWaylineSelection(plan, missionId, ids, initialSelection)
    }

    private fun showWaylineSelection(
        plan: MissionPlan,
        missionId: String,
        ids: List<Int>,
        initialSelection: List<Int>
    ) {
        val checked = BooleanArray(ids.size) { ids[it] in initialSelection }
        val labels = ids.map { "Wayline $it" as CharSequence }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Seleccionar waylines")
            .setMessage("Selecciona explícitamente las waylines que forman parte de esta misión.")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Revisar inicio") { _, _ ->
                val selected = ids.filterIndexed { index, _ -> checked[index] }
                if (selected.isEmpty()) {
                    showError("Waylines obligatorias", "Selecciona al menos una wayline.")
                } else {
                    selectedWaylineIds = selected
                    confirmMissionStart(plan, missionId, selected)
                }
            }
            .show()
    }

    private fun confirmMissionStart(plan: MissionPlan, missionId: String, ids: List<Int>) {
        val capability = currentCapability()
        if (currentPlan?.id != plan.id || uploadedMissionId != missionId ||
            !capability.waypointExecutionSupported ||
            missionState !in setOf(WaypointMissionExecuteState.READY, WaypointMissionExecuteState.IDLE)
        ) {
            showError("Inicio no permitido", "La conexión, el firmware o el estado de la misión cambiaron.")
            return
        }
        val validation = MissionValidator.validate(plan.request, plan)
        if (!validation.isValid) {
            showError("Misión no válida", validation.errors.joinToString("\n"))
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Confirmar inicio de misión")
            .setMessage(
                "Comprueba en el aircraft y en el área de vuelo: obstáculos, personas, espacio aéreo, " +
                    "batería, GPS, control remoto y comportamiento ante pérdida de RC.\n\n" +
                    "Waylines seleccionadas: ${ids.joinToString(", ")}\n" +
                    "Esta confirmación es una ayuda, no una certificación de seguridad."
            )
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Aceptar y enviar") { _, _ -> startMissionNow(plan, missionId, ids) }
            .show()
    }

    private fun startMissionNow(plan: MissionPlan, missionId: String, ids: List<Int>) {
        val capability = currentCapability()
        if (currentPlan?.id != plan.id || uploadedMissionId != missionId ||
            !capability.waypointExecutionSupported ||
            missionState !in setOf(WaypointMissionExecuteState.READY, WaypointMissionExecuteState.IDLE)
        ) {
            if (!capability.waypointExecutionSupported) auditBlocked("mission.start.send", capability)
            showError("Inicio no permitido", "La conexión, el firmware o el estado de la misión cambiaron.")
            return
        }
        val validation = MissionValidator.validate(plan.request, plan)
        if (!validation.isValid) {
            showError("Misión no válida", validation.errors.joinToString("\n"))
            return
        }
        val file = currentKmz
        if (file == null || !file.exists()) {
            showError("Misión no disponible", "El KMZ local ya no existe.")
            return
        }
        val aircraftIds = runCatching {
            WaypointMissionManager.getInstance().getAvailableWaylineIDs(file.absolutePath)
        }.getOrDefault(emptyList()).toSet()
        val verifiedIds = ids.filter { it in aircraftIds }
        if (ids.isEmpty() || verifiedIds.size != ids.size) {
            showError(
                "Waylines cambiaron",
                "El aircraft no confirmó todas las waylines seleccionadas; vuelve a revisar la selección."
            )
            return
        }
        selectedWaylineIds = verifiedIds
        missionState = WaypointMissionExecuteState.PREPARING
        missionPaused = false
        progress.visibility = View.VISIBLE
        audit.append(
            "mission.start_confirmed",
            plan.id,
            mapOf("missionId" to missionId, "waylines" to verifiedIds)
        )
        val manager = WaypointMissionManager.getInstance()
        runCatching {
            manager.startMission(missionId, verifiedIds, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    runOnUiThread {
                        progress.visibility = View.GONE
                        missionState = WaypointMissionExecuteState.EXECUTING
                        missionPaused = false
                        pauseButton.text = "Pausar / reanudar"
                        audit.append("mission.started", plan.id, mapOf("missionId" to missionId))
                        refreshCapability()
                        showInfo("Misión iniciada", "Waylines ${verifiedIds.joinToString(", ")}")
                    }
                }

                override fun onFailure(error: IDJIError) {
                    runOnUiThread {
                        progress.visibility = View.GONE
                        missionState = WaypointMissionExecuteState.READY
                        refreshCapability()
                        showError("No se pudo iniciar", error.description())
                    }
                }
            })
        }.onFailure { error ->
            runOnUiThread {
                progress.visibility = View.GONE
                missionState = WaypointMissionExecuteState.READY
                refreshCapability()
                showError("No se pudo iniciar", error.message ?: "Error de MSDK")
            }
        }
    }

    private fun pauseOrResumeMission() {
        if (uploadedMissionId == null) return
        if (!connected || !registered) {
            showError("Control bloqueado", "El aircraft o el registro SDK no están disponibles.")
            return
        }
        if (missionState !in setOf(
                WaypointMissionExecuteState.PREPARING,
                WaypointMissionExecuteState.ENTER_WAYLINE,
                WaypointMissionExecuteState.EXECUTING,
                WaypointMissionExecuteState.RECOVERING,
                WaypointMissionExecuteState.RETURN_TO_START_POINT,
                WaypointMissionExecuteState.INTERRUPTED
            )
        ) {
            showError("Misión no activa", "El aircraft no está ejecutando una misión controlable.")
            return
        }
        val manager = WaypointMissionManager.getInstance()
        val callback = object : CommonCallbacks.CompletionCallback {
            override fun onSuccess() {
                runOnUiThread {
                    missionPaused = !missionPaused
                    missionState = if (missionPaused) {
                        WaypointMissionExecuteState.INTERRUPTED
                    } else {
                        WaypointMissionExecuteState.EXECUTING
                    }
                    pauseButton.text = if (missionPaused) "Reanudar misión" else "Pausar / reanudar"
                    refreshCapability()
                }
            }

            override fun onFailure(error: IDJIError) {
                runOnUiThread { showError("No se pudo cambiar el estado", error.description()) }
            }
        }
        runCatching {
            if (missionPaused) manager.resumeMission(callback) else manager.pauseMission(callback)
        }.onFailure { error ->
            showError("No se pudo cambiar el estado", error.message ?: "Error de MSDK")
        }
    }

    private fun stopMission() {
        val missionId = uploadedMissionId ?: return
        if (!connected || !registered) {
            showError("Control bloqueado", "El aircraft o el registro SDK no están disponibles.")
            return
        }
        if (missionState !in setOf(
                WaypointMissionExecuteState.PREPARING,
                WaypointMissionExecuteState.ENTER_WAYLINE,
                WaypointMissionExecuteState.EXECUTING,
                WaypointMissionExecuteState.RECOVERING,
                WaypointMissionExecuteState.RETURN_TO_START_POINT,
                WaypointMissionExecuteState.INTERRUPTED
            )
        ) {
            showError("Misión no activa", "No hay una misión activa que detener.")
            return
        }
        val manager = WaypointMissionManager.getInstance()
        runCatching {
            manager.stopMission(missionId, object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    runOnUiThread {
                        missionPaused = false
                        missionState = WaypointMissionExecuteState.FINISHED
                        pauseButton.text = "Pausar / reanudar"
                        audit.append("mission.stopped", currentPlan?.id, mapOf("missionId" to missionId))
                        refreshCapability()
                        showInfo("Misión detenida", "El aircraft ha aceptado la orden de detención.")
                    }
                }

                override fun onFailure(error: IDJIError) {
                    runOnUiThread { showError("No se pudo detener", error.description()) }
                }
            })
        }.onFailure { error ->
            showError("No se pudo detener", error.message ?: "Error de MSDK")
        }
    }

    private fun applyAlgorithm(id: String) {
        val recipe = algorithmRepository.find(id) ?: return
        val d = recipe.defaults
        templateSpinner.setSelection(MissionTemplate.entries.indexOf(recipe.template).coerceAtLeast(0))
        routeSpinner.setSelection(RoutePattern.entries.indexOf(recipe.routePattern).coerceAtLeast(0))
        findViewById<EditText>(R.id.edit_mission_name).setText(recipe.name)
        findViewById<EditText>(R.id.edit_length).setText(formatPlain(d.lengthMeters))
        findViewById<EditText>(R.id.edit_width).setText(formatPlain(d.widthMeters))
        findViewById<EditText>(R.id.edit_height).setText(formatPlain(d.heightMeters))
        findViewById<EditText>(R.id.edit_bearing).setText(formatPlain(d.bearingDegrees))
        findViewById<EditText>(R.id.edit_altitude).setText(formatPlain(d.altitudeMeters))
        findViewById<EditText>(R.id.edit_standoff).setText(formatPlain(d.standoffMeters))
        findViewById<EditText>(R.id.edit_line_spacing).setText(formatPlain(d.lineSpacingMeters))
        findViewById<EditText>(R.id.edit_photo_spacing).setText(formatPlain(d.photoSpacingMeters))
        findViewById<EditText>(R.id.edit_overlap).setText(d.overlapPercent.toString())
        findViewById<EditText>(R.id.edit_speed).setText(formatPlain(d.speedMps))
    }

    private fun text(id: Int, label: String): String = findViewById<EditText>(id).text.toString().trim().also {
        if (it.isBlank()) error("$label es obligatorio")
    }

    private fun number(id: Int, label: String): Double = text(id, label).replace(',', '.').toDoubleOrNull()
        ?: error("$label debe ser un número")

    private fun writeText(uri: Uri, value: String) {
        runCatching { contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(value) } }
            .onFailure { showError("No se pudo exportar", it.message ?: "Error de E/S") }
    }

    private fun copyFile(uri: Uri, file: File?) {
        if (file == null || !file.exists()) return
        runCatching {
            contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
        }.onFailure { showError("No se pudo exportar", it.message ?: "Error de E/S") }
    }

    /**
     * Emits the exact product/remote-controller/firmware identity MSDK reports,
     * so a physical-validation record can be written from evidence instead of
     * from memory. The report is a candidate: its validation reference is a
     * placeholder that the allowlist loader rejects, so it cannot be pasted
     * back as an approval.
     */
    private fun exportValidationReport() {
        val result = currentCapability()
        pendingValidationReport = ValidationReportBuilder.build(
            capability = result,
            deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.SDK_INT})"
        )
        audit.append(
            "wpml.validation_report.exported",
            null,
            mapOf(
                "product" to result.productName,
                "firmware" to result.firmwareVersion,
                "remoteController" to result.remoteControllerName,
                "remoteFirmware" to result.remoteControllerFirmwareVersion,
                "gate" to result.reason
            )
        )
        createValidationReport.launch("wpml-validation-evidence.txt")
    }

    private fun showError(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    /**
     * Records a denied waypoint operation. A denial is a safety-relevant event
     * and must be auditable without exposing the mission plan itself.
     */
    private fun auditBlocked(stage: String, capability: DroneCapabilities.Result) {
        audit.append(
            "wpml.blocked",
            currentPlan?.id,
            mapOf(
                "stage" to stage,
                "product" to capability.productName,
                "firmware" to capability.firmwareVersion,
                "remoteController" to capability.remoteControllerName,
                "remoteFirmware" to capability.remoteControllerFirmwareVersion,
                "connected" to capability.connected,
                "registered" to capability.registered,
                "reason" to capability.reason
            )
        )
    }

    private fun showInfo(title: String, message: String) {
        AlertDialog.Builder(this).setTitle(title).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun format(pattern: String, value: Double): String = String.format(Locale.US, pattern, value)
    private fun formatPlain(value: Double): String = format("%.2f", value).trimEnd('0').trimEnd('.')

    companion object {
        const val EXTRA_ALGORITHM_ID = "com.djiflypro.extra.ALGORITHM_ID"
    }
}
