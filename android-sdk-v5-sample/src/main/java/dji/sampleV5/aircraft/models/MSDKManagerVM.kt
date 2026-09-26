package dji.sampleV5.aircraft.models

import android.content.Context
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dji.sdk.keyvalue.key.DJIRemoteControllerKey
import dji.sdk.keyvalue.key.ProductKey
import dji.v5.common.error.IDJIError
import dji.v5.common.register.DJISDKInitEvent
import dji.v5.et.create
import dji.v5.et.get
import dji.v5.manager.SDKManager
import dji.v5.manager.interfaces.SDKManagerCallback
import dji.v5.network.DJINetworkManager
import java.util.concurrent.atomic.AtomicInteger

class MSDKManagerVM : ViewModel() {
    // The data is held in livedata mode, but you can also save the results of the sdk callbacks any way you like.
    val lvRegisterState = MutableLiveData<Pair<Boolean, IDJIError?>>()
    val lvProductConnectionState = MutableLiveData<Pair<Boolean, Int>>()
    val lvFirmwareVersion = MutableLiveData<String?>(null)
    val lvRemoteControllerType = MutableLiveData<String?>(null)
    val lvRemoteControllerFirmwareVersion = MutableLiveData<String?>(null)
    val lvProductChanges = MutableLiveData<Int>()
    val lvInitProcess = MutableLiveData<Pair<DJISDKInitEvent, Int>>()
    val lvDBDownloadProgress = MutableLiveData<Pair<Long, Long>>()

    private val identityQueryGeneration = AtomicInteger(0)

    @Volatile
    private var productConnected = false
    var isInit = false

    fun initMobileSDK(appContext: Context) {
        // Initialize and set the sdk callback, which is held internally by the sdk until destroy() is called
        SDKManager.getInstance().init(appContext, object : SDKManagerCallback {
            override fun onRegisterSuccess() {
                lvRegisterState.postValue(Pair(true, null))
                refreshDeviceIdentity()
            }

            override fun onRegisterFailure(error: IDJIError) {
                clearDeviceIdentity()
                lvRegisterState.postValue(Pair(false, error))
            }

            override fun onProductDisconnect(productId: Int) {
                productConnected = false
                clearDeviceIdentity()
                lvProductConnectionState.postValue(Pair(false, productId))
            }

            override fun onProductConnect(productId: Int) {
                productConnected = true
                lvProductConnectionState.postValue(Pair(true, productId))
                refreshDeviceIdentity()
            }

            override fun onProductChanged(productId: Int) {
                lvProductChanges.postValue(productId)
                refreshDeviceIdentity()
            }

            override fun onInitProcess(event: DJISDKInitEvent, totalProcess: Int) {
                lvInitProcess.postValue(Pair(event, totalProcess))
                // Don't forget to call the registerApp()
                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    isInit = true
                    SDKManager.getInstance().registerApp()
                }
            }

            override fun onDatabaseDownloadProgress(current: Long, total: Long) {
                lvDBDownloadProgress.postValue(Pair(current, total))
            }
        })

        DJINetworkManager.getInstance().addNetworkStatusListener { isAvailable ->
            if (isInit && isAvailable && !SDKManager.getInstance().isRegistered) {
                SDKManager.getInstance().registerApp()
            }
        }
    }

    private fun refreshDeviceIdentity() {
        val generation = identityQueryGeneration.incrementAndGet()
        if (!productConnected) {
            clearDeviceIdentity()
            return
        }

        runCatching {
            ProductKey.KeyFirmwareVersion.create().get(
                { version ->
                    if (generation == identityQueryGeneration.get()) {
                        lvFirmwareVersion.postValue(version?.trim()?.takeIf { it.isNotEmpty() })
                    }
                },
                {
                    if (generation == identityQueryGeneration.get()) {
                        lvFirmwareVersion.postValue(null)
                    }
                }
            )
        }.onFailure {
            if (generation == identityQueryGeneration.get()) {
                lvFirmwareVersion.postValue(null)
            }
        }

        runCatching {
            DJIRemoteControllerKey.KeyRemoteControllerType.create().get(
                { type ->
                    if (generation == identityQueryGeneration.get()) {
                        lvRemoteControllerType.postValue(type?.name)
                    }
                },
                {
                    if (generation == identityQueryGeneration.get()) {
                        lvRemoteControllerType.postValue(null)
                    }
                }
            )
        }.onFailure {
            if (generation == identityQueryGeneration.get()) {
                lvRemoteControllerType.postValue(null)
            }
        }

        runCatching {
            DJIRemoteControllerKey.KeyFirmwareVersion.create().get(
                { version ->
                    if (generation == identityQueryGeneration.get()) {
                        lvRemoteControllerFirmwareVersion.postValue(
                            version?.trim()?.takeIf { it.isNotEmpty() }
                        )
                    }
                },
                {
                    if (generation == identityQueryGeneration.get()) {
                        lvRemoteControllerFirmwareVersion.postValue(null)
                    }
                }
            )
        }.onFailure {
            if (generation == identityQueryGeneration.get()) {
                lvRemoteControllerFirmwareVersion.postValue(null)
            }
        }
    }

    private fun clearDeviceIdentity() {
        identityQueryGeneration.incrementAndGet()
        lvFirmwareVersion.postValue(null)
        lvRemoteControllerType.postValue(null)
        lvRemoteControllerFirmwareVersion.postValue(null)
    }

    fun destroyMobileSDK() {
        productConnected = false
        clearDeviceIdentity()
        SDKManager.getInstance().destroy()
    }
}
