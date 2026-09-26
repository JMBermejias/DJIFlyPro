package dji.sampleV5.aircraft.pro.mission

import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.product.ProductType
import dji.v5.et.create
import dji.v5.et.get
import java.util.Locale

/**
 * What the aircraft itself reports about waypoint missions, read from
 * `WaypointMissionExecuteState`. This is the only capability signal that comes
 * from the aircraft rather than from a table in this file.
 */
enum class FirmwareWaypointSupport {
    /** No waypoint capability state has been reported yet. */
    UNKNOWN,

    /** The aircraft reported a live waypoint mission state, so it implements them. */
    SUPPORTED,

    /**
     * The aircraft reported `NOT_SUPPORTED`. No App Key, no allowlist profile
     * and no build flag can change this: the firmware does not implement
     * waypoint missions. Reported by DJI for the Mini 3 / Mini 3 Pro.
     */
    NOT_SUPPORTED
}

/** Conservative capability gate for the V5 SDK. */
object DroneCapabilities {
    data class Result(
        val productName: String,
        val firmwareVersion: String?,
        val connected: Boolean,
        val registered: Boolean,
        val waypointUploadSupported: Boolean,
        val waypointExecutionSupported: Boolean,
        val reason: String,
        val remoteControllerName: String? = null,
        val remoteControllerFirmwareVersion: String? = null,
        val firmwareWaypointSupport: FirmwareWaypointSupport = FirmwareWaypointSupport.UNKNOWN
    )

    /**
     * ProductType names for the Mini 3 family, matched exactly. A substring
     * test would also accept future names such as DJI_MINI_3X, which would make
     * this gate silently mis-classify a product it knows nothing about.
     */
    private val MINI_3_PRODUCTS = setOf("DJI_MINI_3", "DJI_MINI_3_PRO")

    /**
     * Reads the current product identity and evaluates the gate with the
     * aircraft/remote-controller identity already reported by MSDK. The
     * default allowlist is empty by design: a firmware string by itself is not
     * evidence of physical validation.
     */
    fun current(
        connected: Boolean,
        registered: Boolean,
        firmwareVersion: String? = null,
        remoteControllerName: String? = null,
        remoteControllerFirmwareVersion: String? = null,
        validatedProfiles: Set<ValidatedWpmlProfile> = emptySet(),
        firmwareWaypointSupport: FirmwareWaypointSupport = FirmwareWaypointSupport.UNKNOWN
    ): Result {
        val product = runCatching {
            ProductKey.KeyProductType.create().get(ProductType.UNKNOWN)
        }.getOrNull() ?: ProductType.UNKNOWN
        return evaluateProduct(
            productName = product.name,
            connected = connected,
            registered = registered,
            firmwareVersion = firmwareVersion,
            remoteControllerName = remoteControllerName,
            remoteControllerFirmwareVersion = remoteControllerFirmwareVersion,
            validatedProfiles = validatedProfiles,
            firmwareWaypointSupport = firmwareWaypointSupport
        )
    }

    /**
     * Evaluates a product/controller identity supplied by MSDK (or a test
     * fixture). A profile is required for upload/execution; generation and
     * export remain available when no profile matches.
     */
    fun evaluateProduct(
        productName: String,
        connected: Boolean,
        registered: Boolean,
        firmwareVersion: String?,
        remoteControllerName: String? = null,
        remoteControllerFirmwareVersion: String? = null,
        validatedProfiles: Set<ValidatedWpmlProfile> = emptySet(),
        firmwareWaypointSupport: FirmwareWaypointSupport = FirmwareWaypointSupport.UNKNOWN
    ): Result {
        val name = productName.trim().ifBlank { "UNKNOWN" }
        val upper = name.uppercase(Locale.ROOT)
        val firmware = normalizeFirmwareIdentifier(firmwareVersion)
        val remoteName = normalizeDeviceIdentifier(remoteControllerName)
        val remoteFirmware = normalizeFirmwareIdentifier(remoteControllerFirmwareVersion)
        val enterprise = upper.contains("M350") || upper.contains("M300") ||
            upper.contains("M30_SERIES") || upper.contains("MAVIC_3_ENTERPRISE") ||
            upper.contains("MATRICE_400") || upper.contains("MATRICE_4")
        val mini3 = upper in MINI_3_PRODUCTS
        val phantom = upper.contains("PHANTOM_4") || upper == "P4" ||
            upper.startsWith("P4P") || upper.startsWith("P4A") || upper.startsWith("P4R") ||
            upper.startsWith("P4_")
        val matchingProfile = validatedProfiles.firstOrNull { profile ->
            runCatching {
                profile.validate()
                profile.matches(name, remoteName, firmware, remoteFirmware)
            }.getOrDefault(false)
        }
        val supported = connected && registered && enterprise && firmware != null &&
            remoteName != null && remoteFirmware != null && matchingProfile != null &&
            firmwareWaypointSupport != FirmwareWaypointSupport.NOT_SUPPORTED
        val reason = when {
            !connected -> "No hay ningún aircraft conectado"
            !registered -> "El registro del SDK de DJI está pendiente o ha fallado"
            phantom -> "El Phantom 4 necesita el SDK heredado de DJI; esta compilación V5 no puede conectarse a él"
            // What the aircraft says about itself outranks anything in this file.
            firmwareWaypointSupport == FirmwareWaypointSupport.NOT_SUPPORTED ->
                "El aircraft informa de que su firmware no admite misiones wayline; " +
                    "la aplicación no puede activarlo"
            mini3 -> "El firmware del Mini 3 y del Mini 3 Pro no admite misiones wayline " +
                "(DJI confirma que DJI Fly no tiene función de rutas en estos modelos); " +
                "usa la aplicación solo para telemetría, cámara y control manual"
            !enterprise -> "La ejecución automática de waylines no está habilitada para este producto"
            firmware == null -> "El firmware del aircraft es desconocido; la ejecución WPML queda bloqueada hasta identificarlo y validarlo"
            remoteName == null -> "El modelo del control remoto es desconocido; la ejecución WPML sigue bloqueada"
            remoteFirmware == null -> "El firmware del control remoto es desconocido; la ejecución WPML sigue bloqueada"
            matchingProfile == null -> "Ningún perfil revisado de modelo, control remoto y firmware coincide; la ejecución WPML automática está desactivada"
            else -> "La subida y la ejecución de waylines coinciden con un perfil de validación física revisado; verifica el espacio aéreo y el estado del aircraft"
        }
        return Result(
            productName = name,
            firmwareVersion = firmware,
            connected = connected,
            registered = registered,
            waypointUploadSupported = supported,
            waypointExecutionSupported = supported,
            reason = reason,
            remoteControllerName = remoteName,
            remoteControllerFirmwareVersion = remoteFirmware,
            firmwareWaypointSupport = firmwareWaypointSupport
        )
    }

    private fun normalizeFirmwareIdentifier(value: String?): String? {
        val candidate = value?.trim() ?: return null
        if (candidate.isEmpty() || candidate.length > 80) return null
        if (candidate.equals("unknown", ignoreCase = true) ||
            candidate.equals("n/a", ignoreCase = true) ||
            candidate.equals("null", ignoreCase = true)
        ) return null
        // DJI firmware labels are versioned strings. Reject control characters
        // and labels with no version digit, while allowing spaces/build labels.
        if (candidate.any(Char::isISOControl) || candidate.none(Char::isDigit)) return null
        return candidate
    }

    private fun normalizeDeviceIdentifier(value: String?): String? {
        val candidate = value?.trim() ?: return null
        if (candidate.isEmpty() || candidate.length > 120) return null
        val upper = candidate.uppercase(Locale.ROOT)
        if (upper in setOf("UNKNOWN", "NONE") || upper.startsWith("NOT_SUPPORTED")) return null
        return candidate
    }
}
