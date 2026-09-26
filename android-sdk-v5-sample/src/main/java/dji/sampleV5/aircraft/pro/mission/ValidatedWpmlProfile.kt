package dji.sampleV5.aircraft.pro.mission

import java.util.Locale

/**
 * A physical-validation record required before automatic WPML execution is
 * enabled for a product/remote-controller/firmware combination.
 *
 * Firmware values are exact on purpose. A broad family match could silently
 * enable a new firmware that was never tested.
 */
data class ValidatedWpmlProfile(
    val productName: String,
    val remoteControllerName: String,
    val aircraftFirmwareVersion: String,
    val remoteControllerFirmwareVersion: String,
    val validationReference: String
) {
    fun matches(
        product: String?,
        remoteController: String?,
        aircraftFirmware: String?,
        remoteFirmware: String?
    ): Boolean =
        product.equals(productName, ignoreCase = true) &&
            remoteController.equals(remoteControllerName, ignoreCase = true) &&
            aircraftFirmware.equals(aircraftFirmwareVersion, ignoreCase = true) &&
            remoteFirmware.equals(remoteControllerFirmwareVersion, ignoreCase = true)

    fun validate() {
        requireText(productName, 120, "Profile product")
        requireText(remoteControllerName, 120, "Profile remote controller")
        requireText(aircraftFirmwareVersion, 80, "Profile aircraft firmware")
        requireText(remoteControllerFirmwareVersion, 80, "Profile remote-controller firmware")
        requireText(validationReference, 200, "Profile validation reference")
        requireNoPlaceholder(productName, "Profile product")
        requireNoPlaceholder(remoteControllerName, "Profile remote controller")
        requireNoPlaceholder(aircraftFirmwareVersion, "Profile aircraft firmware")
        requireNoPlaceholder(remoteControllerFirmwareVersion, "Profile remote-controller firmware")
        requireNoPlaceholder(validationReference, "Profile validation reference")
        val upperProduct = productName.uppercase(Locale.ROOT)
        val upperRemote = remoteControllerName.uppercase(Locale.ROOT)
        require(upperProduct !in setOf("UNKNOWN", "NONE")) { "Profile product is not an identified model" }
        require(upperRemote !in setOf("UNKNOWN", "NONE") && !upperRemote.startsWith("NOT_SUPPORTED")
        ) { "Profile remote controller is not identified" }
    }

    private fun requireText(value: String, maxLength: Int, label: String) {
        require(value.isNotBlank() && value.length <= maxLength && value == value.trim()) {
            "$label is invalid"
        }
        require(value.none(Char::isISOControl)) { "$label contains control characters" }
    }

    /**
     * Rejects unfilled template markers. Without this, a profile copied from
     * `validated-wpml-profiles.example.json` and only partially edited would
     * pass validation and could enable automatic flight with no physical test
     * actually recorded.
     */
    private fun requireNoPlaceholder(value: String, label: String) {
        val upper = value.uppercase(Locale.ROOT)
        require(upper !in PLACEHOLDER_EXACT) { "$label is a bare placeholder" }
        PLACEHOLDER_MARKERS.forEach { marker ->
            require(!upper.contains(marker)) { "$label still contains the placeholder $marker" }
        }
    }

    companion object {
        const val FILE_SCHEMA = "djiflypro.validated-wpml/v1"

        /** Rejected as a whole value. */
        private val PLACEHOLDER_EXACT = setOf(
            "TBD", "TODO", "FIXME", "PENDING", "REQUIRED", "N/A", "NA", "-", "--",
            "XXX", "XXXX", "CHANGEME", "CHANGE_ME", "CHANGE ME", "EXAMPLE",
            "PLACEHOLDER", "NONE", "UNKNOWN", "FILL", "FILLME", "FILL_ME", "DUMMY", "TEST"
        )

        /** Rejected when found anywhere in a value. */
        private val PLACEHOLDER_MARKERS = listOf(
            "REPLACE_",
            "REPLACE WITH",
            "TODO",
            "FIXME",
            "TBD",
            "XXX",
            "CHANGEME",
            "CHANGE_ME",
            "FILL_",
            "FILL IN",
            "FILLME",
            "EXAMPLE",
            "PLACEHOLDER",
            "REQUIRED",
            "PENDING",
            "INSERT_",
            "<",
            ">",
            "???"
        )
    }
}

/**
 * On-disk shape of the allowlist asset. Both fields are nullable because Gson
 * bypasses Kotlin default values: a missing or null field is a real condition
 * the loader must reject, not a default to fall back on.
 */
data class ValidatedWpmlProfileFile(
    val schema: String? = null,
    val profiles: List<ValidatedWpmlProfile>? = null
)
