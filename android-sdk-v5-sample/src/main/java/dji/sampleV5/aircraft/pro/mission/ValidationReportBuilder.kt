package dji.sampleV5.aircraft.pro.mission

import java.util.Locale

/**
 * Builds the evidence needed to register a physical-validation record.
 *
 * Registering a product in the allowlist requires the exact product, remote
 * controller and both firmware strings that MSDK reports on the real hardware.
 * Reading them off a dialog by hand is error prone, so the app emits a
 * ready-to-paste profile skeleton instead.
 *
 * This produces a *candidate*, never an approval: the emitted
 * `validationReference` is a placeholder on purpose, and [ValidatedWpmlProfile]
 * rejects it, so the report cannot be pasted back into the allowlist as-is.
 */
object ValidationReportBuilder {

    fun build(capability: DroneCapabilities.Result, deviceLabel: String? = null): String {
        val product = capability.productName.takeIf { it.isNotBlank() } ?: "UNKNOWN_PRODUCT"
        val remote = capability.remoteControllerName?.takeIf { it.isNotBlank() }
            ?: "UNKNOWN_REMOTE_CONTROLLER"
        val aircraftFirmware = capability.firmwareVersion?.takeIf { it.isNotBlank() }
            ?: "UNKNOWN_AIRCRAFT_FIRMWARE"
        val remoteFirmware = capability.remoteControllerFirmwareVersion?.takeIf { it.isNotBlank() }
            ?: "UNKNOWN_RC_FIRMWARE"

        return buildString {
            appendLine("{")
            appendLine("  \"schema\": \"${ValidatedWpmlProfile.FILE_SCHEMA}\",")
            appendLine("  \"profiles\": [")
            appendLine("    {")
            appendLine("      \"productName\": \"$product\",")
            appendLine("      \"remoteControllerName\": \"$remote\",")
            appendLine("      \"aircraftFirmwareVersion\": \"$aircraftFirmware\",")
            appendLine("      \"remoteControllerFirmwareVersion\": \"$remoteFirmware\",")
            appendLine("      \"validationReference\": \"REPLACE_WITH_YOUR_TEST_RECORD_REFERENCE\"")
            appendLine("    }")
            appendLine("  ]")
            appendLine("}")
            appendLine()
            appendLine("# DJIFlyPro WPML validation evidence")
            appendLine("# generated: ${generatedAt()}")
            if (!deviceLabel.isNullOrBlank()) {
                appendLine("# device: $deviceLabel")
            }
            appendLine("# connected: ${capability.connected}")
            appendLine("# registered: ${capability.registered}")
            appendLine("# current gate: ${gateLabel(capability)}")
            appendLine("#")
            appendLine("# Values above were reported by MSDK for the connected hardware.")
            appendLine("# Copy them into android-sdk-v5-sample/src/main/assets/validated_wpml_profiles.json")
            appendLine("# ONLY after a real flight test, and replace the validationReference with")
            appendLine("# the actual record reference. Until then this file is a candidate, not a")
            appendLine("# permission to fly, and the app keeps automatic WPML execution disabled.")
        }
    }

    private fun gateLabel(capability: DroneCapabilities.Result): String = when {
        capability.waypointExecutionSupported -> "profile-matched"
        !capability.connected -> "not-connected"
        !capability.registered -> "not-registered"
        else -> "no-validated-profile"
    }

    private fun generatedAt(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(java.util.Date())
}
