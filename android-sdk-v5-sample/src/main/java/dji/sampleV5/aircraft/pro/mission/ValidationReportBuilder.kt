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
            appendLine("# DJIFlyPro: evidencia de validacion WPML")
            appendLine("# generado: ${generatedAt()}")
            if (!deviceLabel.isNullOrBlank()) {
                appendLine("# dispositivo: $deviceLabel")
            }
            appendLine("# conectado: ${capability.connected}")
            appendLine("# registrado: ${capability.registered}")
            appendLine("# barrera actual: ${gateLabel(capability)}")
            appendLine("#")
            appendLine("# Los valores de arriba los ha informado MSDK para el hardware conectado.")
            appendLine("# Copialos en android-sdk-v5-sample/src/main/assets/validated_wpml_profiles.json")
            appendLine("# SOLO despues de una prueba de vuelo real, y sustituye validationReference por")
            appendLine("# la referencia real del registro. Hasta entonces este fichero es un candidato,")
            appendLine("# no un permiso para volar, y la app mantiene desactivada la ejecucion WPML automatica.")
        }
    }

    private fun gateLabel(capability: DroneCapabilities.Result): String = when {
        capability.waypointExecutionSupported -> "perfil-coincidente"
        !capability.connected -> "sin-conexion"
        !capability.registered -> "sin-registro"
        else -> "sin-perfil-validado"
    }

    private fun generatedAt(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(java.util.Date())
}
