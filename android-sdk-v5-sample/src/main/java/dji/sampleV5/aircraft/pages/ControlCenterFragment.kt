package dji.sampleV5.aircraft.pages

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import dji.sampleV5.aircraft.R
import dji.sampleV5.aircraft.databinding.FragControlCenterPageBinding
import dji.sampleV5.aircraft.models.CameraAutomationState
import dji.sampleV5.aircraft.models.CameraAutomationVM
import dji.sampleV5.aircraft.models.FlightCommand
import dji.sampleV5.aircraft.models.FlightControlVM
import dji.sampleV5.aircraft.models.MSDKManagerVM
import dji.sampleV5.aircraft.models.VirtualStickVM
import dji.sampleV5.aircraft.models.globalViewModels
import dji.v5.common.callback.CommonCallbacks
import dji.v5.common.error.IDJIError
import dji.v5.ux.core.util.ToastUtils

class ControlCenterFragment : DJIFragment() {
    private var binding: FragControlCenterPageBinding? = null
    private val flightControlVM: FlightControlVM by activityViewModels()
    private val virtualStickVM: VirtualStickVM by activityViewModels()
    private val cameraAutomationVM: CameraAutomationVM by activityViewModels()
    private val msdkManagerVM: MSDKManagerVM by globalViewModels()
    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraAutomationVM.startRecording(::showResult)
        } else {
            showResult(false, "Permiso de audio denegado; no se inicia la grabación")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val viewBinding = FragControlCenterPageBinding.inflate(inflater, container, false)
        binding = viewBinding
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        installCameraPreview()
        observeState()
        bindActions()
    }

    private fun installCameraPreview() {
        if (childFragmentManager.findFragmentById(R.id.camera_preview_container) != null) {
            return
        }
        childFragmentManager.beginTransaction()
            .replace(R.id.camera_preview_container, CameraStreamListFragment())
            .commitAllowingStateLoss()
    }

    private fun observeState() {
        msdkManagerVM.lvRegisterState.observe(viewLifecycleOwner) { result ->
            flightControlVM.setRegistered(result.first)
            updateActionState()
        }
        flightControlVM.telemetry.observe(viewLifecycleOwner) {
            renderTelemetry(it)
            updateActionState()
        }
        cameraAutomationVM.state.observe(viewLifecycleOwner) {
            renderCameraState()
        }
        cameraAutomationVM.status.observe(viewLifecycleOwner) {
            renderCameraState()
        }
    }

    private fun bindActions() {
        binding?.btnControlTakeoff?.setOnClickListener {
            confirmFlightAction(
                R.string.btn_control_takeoff,
                getString(R.string.control_confirm_takeoff)
            ) {
                flightControlVM.takeOff(::showResult)
            }
        }
        binding?.btnControlLand?.setOnClickListener {
            confirmFlightAction(
                R.string.btn_control_land,
                getString(R.string.control_confirm_land)
            ) {
                flightControlVM.land(::showResult)
            }
        }
        binding?.btnControlReturnHome?.setOnClickListener {
            confirmFlightAction(
                R.string.btn_control_return_home,
                getString(R.string.control_confirm_return_home)
            ) {
                flightControlVM.returnHome(::showResult)
            }
        }
        binding?.btnControlEnableVirtualStick?.setOnClickListener {
            if (!flightControlVM.canUseVirtualStick()) {
                showSafetyBlock()
                return@setOnClickListener
            }
            virtualStickVM.enableVirtualStick(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    showResult(true, "Joysticks virtuales activados")
                }

                override fun onFailure(error: IDJIError) {
                    showResult(false, error.description())
                }
            })
        }
        binding?.btnControlDisableVirtualStick?.setOnClickListener {
            virtualStickVM.disableVirtualStick(object : CommonCallbacks.CompletionCallback {
                override fun onSuccess() {
                    showResult(true, "Joysticks virtuales desactivados")
                }

                override fun onFailure(error: IDJIError) {
                    showResult(false, error.description())
                }
            })
        }
        binding?.btnControlTakePhoto?.setOnClickListener {
            cameraAutomationVM.takePhoto(::showResult)
        }
        binding?.btnControlStartRecording?.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                cameraAutomationVM.startRecording(::showResult)
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        binding?.btnControlStopRecording?.setOnClickListener {
            cameraAutomationVM.stopRecording(::showResult)
        }
        binding?.btnControlStartCapture?.setOnClickListener {
            cameraAutomationVM.startTimedCapture(5, 2, ::showResult)
        }
        binding?.btnControlStopCapture?.setOnClickListener {
            cameraAutomationVM.stopAutomation()
            showResult(true, "Automatización de cámara detenida")
        }
    }

    private fun confirmFlightAction(titleRes: Int, message: String, action: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> action() }
            .show()
    }

    private fun renderTelemetry(telemetry: dji.sampleV5.aircraft.models.FlightTelemetry) {
        val battery = if (telemetry.batteryPercent >= 0) "${telemetry.batteryPercent}%" else getString(R.string.control_not_available)
        binding?.controlTelemetryStatus?.text = getString(
            R.string.control_telemetry_format,
            if (telemetry.aircraftConnected) getString(R.string.control_state_connected) else getString(R.string.control_state_disconnected),
            if (telemetry.remoteControllerConnected) "connected" else "disconnected",
            battery,
            telemetry.gpsSignalLevel,
            String.format(java.util.Locale.US, "%.1f", telemetry.altitudeMeters)
        )
    }

    private fun updateActionState() {
        val takeoffDecision = flightControlVM.evaluate(FlightCommand.TAKEOFF)
        val landDecision = flightControlVM.evaluate(FlightCommand.LAND)
        val returnHomeDecision = flightControlVM.evaluate(FlightCommand.RETURN_HOME)
        val virtualStickDecision = flightControlVM.evaluate(FlightCommand.MANUAL_CONTROL)
        binding?.btnControlTakeoff?.isEnabled = takeoffDecision.allowed
        binding?.btnControlLand?.isEnabled = landDecision.allowed
        binding?.btnControlReturnHome?.isEnabled = returnHomeDecision.allowed
        binding?.btnControlEnableVirtualStick?.isEnabled = virtualStickDecision.allowed
        binding?.controlSafetyStatus?.text = getString(
            if (takeoffDecision.allowed) R.string.control_safety_ready else R.string.control_safety_blocked,
            takeoffDecision.reason.name
        )
    }

    private fun renderCameraState() {
        val state = cameraAutomationVM.state.value ?: CameraAutomationState.IDLE
        val status = cameraAutomationVM.status.value.orEmpty()
        binding?.controlCameraStatus?.text = if (status.isBlank()) state.name else "$state: $status"
    }

    private fun showSafetyBlock() {
        val decision = flightControlVM.evaluate(FlightCommand.MANUAL_CONTROL)
        showResult(false, getString(R.string.control_safety_blocked, decision.reason.name))
    }

    private fun showResult(success: Boolean, message: String) {
        ToastUtils.showToast(message)
    }

    override fun onDestroyView() {
        childFragmentManager.findFragmentById(R.id.camera_preview_container)?.let {
            childFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
        }
        binding = null
        super.onDestroyView()
    }
}
