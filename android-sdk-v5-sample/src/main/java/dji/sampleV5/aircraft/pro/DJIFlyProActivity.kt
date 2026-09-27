package dji.sampleV5.aircraft.pro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.models.MSDKManagerVM
import dji.sampleV5.aircraft.models.globalViewModels
import dji.sampleV5.aircraft.pro.algorithm.AlgorithmLibraryActivity
import dji.sampleV5.aircraft.pro.mission.MissionStore
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.common.utils.GeoidManager
import dji.v5.ux.core.communication.DefaultGlobalPreferences
import dji.v5.ux.core.communication.GlobalPreferencesManager
import dji.v5.ux.core.util.UxSharedPreferencesUtil
import dji.sampleV5.aircraft.pro.update.UpdateActivity
import dji.sampleV5.aircraft.pro.update.UpdateClient
import dji.sampleV5.aircraft.pro.update.UpdateManifest
import dji.sampleV5.aircraft.pro.update.UpdatePrefs
import java.util.concurrent.Executors

class DJIFlyProActivity : AppCompatActivity() {
    private val msdkManagerVM: MSDKManagerVM by globalViewModels()
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { !it }) {
            connectionStatus.text = "Some permissions were denied; aircraft control may be unavailable"
        }
    }

    private lateinit var connectionStatus: TextView
    private lateinit var productStatus: TextView
    private lateinit var progress: ProgressBar
    private lateinit var lastMission: TextView
    private lateinit var updateNotice: TextView
    private lateinit var applyUpdateButton: Button

    private val updateExecutor = Executors.newSingleThreadExecutor()
    private val updatePrefs by lazy { UpdatePrefs(this) }
    private val updateClient = UpdateClient()

    /** The newest release seen, held so the button can act without re-checking. */
    private var pendingUpdate: UpdateManifest? = null

    /** App Key as baked into the manifest. Null when it was not configured. */
    private var apiKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeUxSdk()
        setContentView(R.layout.activity_djiflypro_main)

        connectionStatus = findViewById(R.id.dashboard_connection_status)
        productStatus = findViewById(R.id.dashboard_product_status)
        progress = findViewById(R.id.dashboard_progress)
        lastMission = findViewById(R.id.dashboard_last_mission)
        updateNotice = findViewById(R.id.dashboard_update_notice)
        applyUpdateButton = findViewById(R.id.button_apply_update)
        applyUpdateButton.setOnClickListener { offerUpdate(automatic = false) }
        findViewById<Button>(R.id.button_check_update).setOnClickListener { checkForUpdate(force = true) }

        findViewById<Button>(R.id.button_control_center).setOnClickListener {
            startActivity(Intent(this, ControlCenterActivity::class.java))
        }
        findViewById<Button>(R.id.button_cartography).setOnClickListener {
            startActivity(Intent(this, dji.sampleV5.aircraft.pro.cartography.CartographyActivity::class.java))
        }
        findViewById<Button>(R.id.button_mission_planner).setOnClickListener {
            startActivity(Intent(this, dji.sampleV5.aircraft.pro.mission.MissionPlannerActivity::class.java))
        }
        findViewById<Button>(R.id.button_algorithm_library).setOnClickListener {
            startActivity(Intent(this, AlgorithmLibraryActivity::class.java))
        }
        findViewById<Button>(R.id.button_documentation).setOnClickListener {
            startActivity(Intent(this, DocumentationActivity::class.java))
        }

        // Runs at most every six hours and never blocks the dashboard.
        updateExecutor.execute {
            Thread.sleep(1500)
            runOnUiThread { checkForUpdate(force = false) }
        }


        apiKey = runCatching {
            packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData?.getString("com.dji.sdk.API_KEY")
        }.getOrNull()
        connectionStatus.text = ConnectionStatusMessage.initial(apiKey)

        requestRuntimePermissions()
        observeSdk()
        refreshLastMission()
    }

    override fun onResume() {
        super.onResume()
        if (::lastMission.isInitialized) refreshLastMission()
    }

    private fun observeSdk() {
        msdkManagerVM.lvInitProcess.observe(this) { (event, total) ->
            val percent = if (total > 0) (100 * event.ordinal / total.coerceAtLeast(1)).coerceIn(0, 100) else 0
            progress.progress = percent
            progress.visibility = if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) android.view.View.GONE else android.view.View.VISIBLE
        }
        msdkManagerVM.lvRegisterState.observe(this) { (registered, error) ->
            connectionStatus.text = ConnectionStatusMessage.registration(
                registered = registered,
                // Se traduce aquí y no dentro de ConnectionStatusMessage porque
                // ese objeto no lleva Context a propósito: se prueba sin el SDK.
                errorDescription = error?.let { DjiErrorText.describe(this, it) },
                apiKey = apiKey
            )
        }
        msdkManagerVM.lvProductConnectionState.observe(this) { (connected, productId) ->
            productStatus.text = if (connected) {
                getString(R.string.dash_product_connected, productId)
            } else {
                "Producto: no conectado"
            }
        }
    }

    private fun requestRuntimePermissions() {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }.distinct().filter { permission ->
            ContextCompat.checkSelfPermission(this@DJIFlyProActivity, permission) !=
                PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions)
        }
    }

    private fun refreshLastMission() {
        val mission = MissionStore(this).latest()
        lastMission.text = if (mission == null) {
            getString(R.string.dash_no_missions)
        } else {
            getString(
                R.string.dash_last_mission_value,
                mission.request.name,
                mission.waypoints.size,
                String.format(java.util.Locale.US, "%.1f", mission.totalDistanceMeters / 1000.0)
            )
        }
    }

    private fun initializeUxSdk() {
        UxSharedPreferencesUtil.initialize(this)
        GlobalPreferencesManager.initialize(DefaultGlobalPreferences(this))
        GeoidManager.getInstance().init(this)
    }

    /**
     * An update check is a request to a third party, so it happens at most every
     * six hours and never blocks the dashboard. A failure is a line of text and
     * nothing else: "no update available" must not be indistinguishable from a
     * broken network, and a broken network must not look like something the
     * operator has to act on.
     */
    private fun checkForUpdate(force: Boolean) {
        if (!force && updatePrefs.isFresh()) {
            updatePrefs.cachedManifest()?.let { renderUpdate(it) }
            return
        }
        updateNotice.text = getString(R.string.dash_update_checking)
        applyUpdateButton.isEnabled = false
        val installed = installedVersionCode()
        updateExecutor.execute {
            val result = updateClient.check(installed)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                when (result) {
                    is UpdateClient.Result.Available -> {
                        updatePrefs.cacheManifest(result.manifest)
                        renderUpdate(result.manifest)
                        offerUpdate(automatic = true)
                    }
                    UpdateClient.Result.UpToDate -> {
                        updatePrefs.clear()
                        updatePrefs.markCheckedNow()
                        pendingUpdate = null
                        applyUpdateButton.isEnabled = false
                        updateNotice.text = getString(R.string.dash_update_up_to_date, currentVersionName())
                    }
                    is UpdateClient.Result.Failed -> {
                        updatePrefs.markCheckedNow()
                        applyUpdateButton.isEnabled = false
                        updateNotice.text = getString(R.string.dash_update_check_failed, result.reason)
                    }
                }
            }
        }
    }

    private fun renderUpdate(manifest: UpdateManifest) {
        pendingUpdate = manifest
        updateNotice.text = getString(R.string.dash_update_available, manifest.versionName)
        applyUpdateButton.isEnabled = true
    }

    private fun offerUpdate(automatic: Boolean) {
        val manifest = pendingUpdate
        if (manifest == null) {
            checkForUpdate(force = true)
            return
        }
        startActivity(UpdateActivity.launchIntent(this, manifest, automatic))
    }

    private fun installedVersionCode(): Int = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionCode
    }.getOrDefault(0)

    private fun currentVersionName(): String = runCatching {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    override fun onDestroy() {
        updateExecutor.shutdownNow()
        super.onDestroy()
    }
}
