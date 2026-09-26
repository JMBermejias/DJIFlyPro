package dji.sampleV5.aircraft.pro.mission

import dji.sdk.keyvalue.key.ProductKey
import dji.sdk.keyvalue.value.product.ProductType
import dji.v5.et.create
import dji.v5.et.get
import java.util.Locale

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
        val remoteControllerFirmwareVersion: String? = null
    )

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
        validatedProfiles: Set<ValidatedWpmlProfile> = emptySet()
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
            validatedProfiles = validatedProfiles
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
        validatedProfiles: Set<ValidatedWpmlProfile> = emptySet()
    ): Result {
        val name = productName.trim().ifBlank { "UNKNOWN" }
        val upper = name.uppercase(Locale.ROOT)
        val firmware = normalizeFirmwareIdentifier(firmwareVersion)
        val remoteName = normalizeDeviceIdentifier(remoteControllerName)
        val remoteFirmware = normalizeFirmwareIdentifier(remoteControllerFirmwareVersion)
        val enterprise = upper.contains("M350") || upper.contains("M300") ||
            upper.contains("M30_SERIES") || upper.contains("MAVIC_3_ENTERPRISE") ||
            upper.contains("MATRICE_400") || upper.contains("MATRICE_4")
        val mini3 = upper.contains("MINI_3")
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
            remoteName != null && remoteFirmware != null && matchingProfile != null
        val reason = when {
            !connected -> "No aircraft connected"
            !registered -> "DJI SDK registration is pending or failed"
            phantom -> "Phantom 4 requires the legacy DJI SDK; this V5 build cannot connect to it"
            mini3 -> "Mini 3 is exposed by MSDK V5, but this build does not claim onboard WPML execution for it"
            !enterprise -> "Automatic waypoint execution is not enabled for this product"
            firmware == null -> "Aircraft firmware is unknown; WPML execution is blocked until the firmware is identified and validated"
            remoteName == null -> "Remote-controller model is unknown; WPML execution remains blocked"
            remoteFirmware == null -> "Remote-controller firmware is unknown; WPML execution remains blocked"
            matchingProfile == null -> "No reviewed model/remote-controller/firmware profile matches; automatic WPML is disabled"
            else -> "Waypoint upload and execution match a reviewed physical-validation profile; verify airspace and aircraft state"
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
            remoteControllerFirmwareVersion = remoteFirmware
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
