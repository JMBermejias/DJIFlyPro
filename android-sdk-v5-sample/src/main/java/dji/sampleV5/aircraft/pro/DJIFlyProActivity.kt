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
                errorDescription = error?.description(),
                apiKey = apiKey
            )
        }
        msdkManagerVM.lvProductConnectionState.observe(this) { (connected, productId) ->
            productStatus.text = if (connected) {
                "Producto conectado: DJI product $productId"
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
            "No hay misiones guardadas"
        } else {
            "${mission.request.name}\n${mission.waypoints.size} puntos · ${"%.1f".format(mission.totalDistanceMeters / 1000.0)} km"
        }
    }

    private fun initializeUxSdk() {
        UxSharedPreferencesUtil.initialize(this)
        GlobalPreferencesManager.initialize(DefaultGlobalPreferences(this))
        GeoidManager.getInstance().init(this)
    }
}
