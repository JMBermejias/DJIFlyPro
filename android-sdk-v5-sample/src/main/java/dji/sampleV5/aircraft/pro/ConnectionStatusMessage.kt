package dji.sampleV5.aircraft.pro

/**
 * Connection banner text for the main dashboard.
 *
 * Kept separate from the Activity so the wording is unit tested, and it takes
 * plain strings rather than DJI SDK types so it can be tested without the SDK
 * runtime. The order of the cases matters: when the App Key is missing,
 * registration cannot succeed, and the generic registration error would
 * otherwise overwrite the only message that tells the operator what to do.
 */
object ConnectionStatusMessage {

    const val API_KEY_MISSING =
        "Falta la API Key de DJI: registra com.djiflypro.app en DJI Developer"

    /**
     * Reads the App Key the way DJI reads it: from the manifest metadata that
     * the build fills from `AIRCRAFT_API_KEY`.
     */
    fun isApiKeyConfigured(value: String?): Boolean {
        val key = value?.trim().orEmpty()
        if (key.isEmpty()) return false
        // The unmodified DJI sample ships a placeholder in this slot.
        if (key.contains("Please add", ignoreCase = true)) return false
        return true
    }

    /** Banner text before the SDK reports a registration state. */
    fun initial(apiKey: String?): String = if (isApiKeyConfigured(apiKey)) {
        "SDK iniciado"
    } else {
        API_KEY_MISSING
    }

    /**
     * Banner text once the SDK reports a registration state. A missing App Key
     * wins over the registration error, because it is the root cause and the
     * only step the operator can take.
     */
    fun registration(registered: Boolean, errorDescription: String?, apiKey: String?): String = when {
        registered -> "DJI Mobile SDK registrado correctamente"
        !isApiKeyConfigured(apiKey) -> "$API_KEY_MISSING. Sin ella el SDK no se puede registrar."
        else -> "Registro DJI: ${errorDescription?.takeIf { it.isNotBlank() } ?: "Esperando registro"}"
    }
}
