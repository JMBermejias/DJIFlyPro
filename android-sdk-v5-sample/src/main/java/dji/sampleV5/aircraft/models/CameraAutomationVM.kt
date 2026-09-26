package dji.sampleV5.aircraft.models

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dji.sdk.keyvalue.key.CameraKey
import dji.sdk.keyvalue.key.KeyTools.createKey
import dji.sdk.keyvalue.value.camera.CameraMode
import dji.sdk.keyvalue.value.common.ComponentIndexType
import dji.v5.common.utils.RxUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable

enum class CameraAutomationState {
    IDLE,
    CAPTURING,
    RECORDING,
    ERROR
}

class CameraAutomationVM : DJIViewModel() {
    private val cameraIndex = ComponentIndexType.LEFT_OR_MAIN
    private val handler = Handler(Looper.getMainLooper())
    private val _state = MutableLiveData(CameraAutomationState.IDLE)
    private val _status = MutableLiveData("")
    private var remainingPhotos = 0
    private var intervalMillis = 0L
    private var recording = false
    private val disposables = CompositeDisposable()

    val state: LiveData<CameraAutomationState> = _state
    val status: LiveData<String> = _status

    fun takePhoto(callback: (Boolean, String) -> Unit) {
        RxUtil.setValue(
            createKey<CameraMode>(CameraKey.KeyCameraMode, cameraIndex),
            CameraMode.PHOTO_NORMAL
        ).andThen(
            RxUtil.performActionWithOutResult(
                createKey(CameraKey.KeyStartShootPhoto, cameraIndex)
            )
        ).subscribe(
            { callback(true, "Orden de foto aceptada") },
            { callback(false, it.message ?: "La orden de foto falló") }
        ).also { disposables.add(it) }
    }

    fun startRecording(callback: (Boolean, String) -> Unit) {
        RxUtil.setValue(
            createKey<CameraMode>(CameraKey.KeyCameraMode, cameraIndex),
            CameraMode.VIDEO_NORMAL
        ).andThen(
            RxUtil.performActionWithOutResult(
                createKey(CameraKey.KeyStartRecord, cameraIndex)
            )
        ).subscribe(
            {
                recording = true
                _state.postValue(CameraAutomationState.RECORDING)
                callback(true, "Grabación iniciada")
            },
            { callback(false, it.message ?: "No se pudo iniciar la grabación") }
        ).also { disposables.add(it) }
    }

    fun stopRecording(callback: (Boolean, String) -> Unit) {
        RxUtil.performActionWithOutResult(
            createKey(CameraKey.KeyStopRecord, cameraIndex)
        ).subscribe(
            {
                recording = false
                _state.postValue(CameraAutomationState.IDLE)
                callback(true, "Grabación detenida")
            },
            { callback(false, it.message ?: "No se pudo detener la grabación") }
        ).also { disposables.add(it) }
    }

    fun startTimedCapture(
        photoCount: Int,
        intervalSeconds: Int,
        callback: (Boolean, String) -> Unit
    ) {
        val plan = try {
            CameraAutomationPlanner.createCapturePlan(photoCount, intervalSeconds)
        } catch (error: IllegalArgumentException) {
            callback(false, error.message ?: "Plan de captura no válido")
            return
        }
        stopScheduledWork()
        remainingPhotos = plan.photoCount
        intervalMillis = plan.intervalSeconds * 1000L
        _state.postValue(CameraAutomationState.CAPTURING)
        _status.postValue("Capturando $remainingPhotos fotos")
        callback(true, "Captura temporizada iniciada")
        captureNext()
    }

    fun startTimedRecording(
        durationSeconds: Int,
        callback: (Boolean, String) -> Unit
    ) {
        val plan = try {
            CameraAutomationPlanner.createRecordingPlan(durationSeconds)
        } catch (error: IllegalArgumentException) {
            callback(false, error.message ?: "Plan de grabación no válido")
            return
        }
        stopScheduledWork()
        startRecording { success, message ->
            if (!success) {
                _state.postValue(CameraAutomationState.ERROR)
                callback(false, message)
                return@startRecording
            }
            _status.postValue("Grabando ${plan.durationSeconds} segundos")
            handler.postDelayed({
                stopRecording { stopped, stopMessage ->
                    if (stopped) {
                        _state.postValue(CameraAutomationState.IDLE)
                        callback(true, "Grabación temporizada completada")
                    } else {
                        _state.postValue(CameraAutomationState.ERROR)
                        callback(false, stopMessage)
                    }
                }
            }, plan.durationSeconds * 1000L)
        }
    }

    fun stopAutomation() {
        stopScheduledWork()
        if (recording) {
            stopRecording { _, _ -> }
        }
        _state.postValue(CameraAutomationState.IDLE)
        _status.postValue("")
    }

    private fun captureNext() {
        if (remainingPhotos <= 0) {
            _state.postValue(CameraAutomationState.IDLE)
            _status.postValue("Captura temporizada completada")
            return
        }
        takePhoto { success, message ->
            if (!success) {
                _state.postValue(CameraAutomationState.ERROR)
                _status.postValue(message)
                stopScheduledWork()
                return@takePhoto
            }
            remainingPhotos -= 1
            _status.postValue("Capturando $remainingPhotos fotos")
            if (remainingPhotos > 0) {
                handler.postDelayed({ captureNext() }, intervalMillis)
            } else {
                _state.postValue(CameraAutomationState.IDLE)
                _status.postValue("Captura temporizada completada")
            }
        }
    }

    private fun stopScheduledWork() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun onCleared() {
        stopScheduledWork()
        disposables.clear()
        super.onCleared()
    }
}
