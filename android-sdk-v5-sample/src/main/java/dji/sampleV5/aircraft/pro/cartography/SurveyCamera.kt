package dji.sampleV5.aircraft.pro.cartography

/**
 * A mapping payload described by the physical parameters that decide how much
 * ground one photograph covers: sensor size, focal length and output
 * resolution. Nothing here is DJI specific, so a camera the app has never seen
 * can be described with [SurveyCamera.custom].
 *
 * GSD is derived from these values, not from a table of "DJI cameras", so a
 * wrong or unknown model degrades into a wrong number that the operator can
 * see and correct instead of a silently wrong route.
 */
data class SurveyCamera(
    val id: String,
    val displayName: String,
    val sensorWidthMillimeters: Double,
    val sensorHeightMillimeters: Double,
    val focalLengthMillimeters: Double,
    val imageWidthPixels: Int,
    val imageHeightPixels: Int,
    val isMechanicalOrFixedLens: Boolean = true
) {
    val pixelPitchMillimeters: Double
        get() = sensorWidthMillimeters / imageWidthPixels

    /** Horizontal field of view in degrees, from the thin lens relation. */
    val horizontalFovDegrees: Double
        get() = 2.0 * Math.toDegrees(Math.atan((sensorWidthMillimeters / 2.0) / focalLengthMillimeters))

    /** Vertical field of view in degrees. */
    val verticalFovDegrees: Double
        get() = 2.0 * Math.toDegrees(Math.atan((sensorHeightMillimeters / 2.0) / focalLengthMillimeters))

    /** Nominal aspect ratio, useful as a sanity check for custom cameras. */
    val aspectRatio: Double
        get() = if (imageHeightPixels <= 0) 0.0 else imageWidthPixels.toDouble() / imageHeightPixels

    fun validate(): CameraValidation {
        val errors = mutableListOf<String>()
        if (id.isBlank()) errors += "El identificador de la cámara es obligatorio"
        if (displayName.isBlank()) errors += "El nombre de la cámara es obligatorio"
        if (!sensorWidthMillimeters.isFinite() || sensorWidthMillimeters <= 0.0) {
            errors += "El ancho del sensor debe ser mayor que 0 mm"
        }
        if (!sensorHeightMillimeters.isFinite() || sensorHeightMillimeters <= 0.0) {
            errors += "El alto del sensor debe ser mayor que 0 mm"
        }
        if (!focalLengthMillimeters.isFinite() || focalLengthMillimeters <= 0.0) {
            errors += "La focal debe ser mayor que 0 mm"
        }
        if (imageWidthPixels <= 0) errors += "El ancho de la imagen debe ser mayor que 0 px"
        if (imageHeightPixels <= 0) errors += "El alto de la imagen debe ser mayor que 0 px"
        return CameraValidation(errors)
    }

    companion object {
        /**
         * Payloads DJI markets for mapping. Values are the published sensor and
         * lens figures; they are documented in `docs/CARTOGRAFIA.md` so an
         * operator can check them against the payload in front of them.
         */
        val ZENMUSE_P1 = SurveyCamera(
            id = "zenmuse-p1",
            displayName = "Zenmuse P1 (45 MP, 35 mm)",
            sensorWidthMillimeters = 35.9,
            sensorHeightMillimeters = 24.0,
            focalLengthMillimeters = 35.0,
            imageWidthPixels = 8192,
            imageHeightPixels = 5460
        )

        val ZENMUSE_H20T = SurveyCamera(
            id = "zenmuse-h20t",
            displayName = "Zenmuse H20T (angular 23 MP / tele 48 MP)",
            sensorWidthMillimeters = 18.4,
            sensorHeightMillimeters = 12.3,
            focalLengthMillimeters = 11.0,
            imageWidthPixels = 5280,
            imageHeightPixels = 3956
        )

        val ZENMUSE_L2 = SurveyCamera(
            id = "zenmuse-l2",
            displayName = "Zenmuse L2 (LiDAR + RGB 4/3)",
            sensorWidthMillimeters = 17.4,
            sensorHeightMillimeters = 13.1,
            focalLengthMillimeters = 7.0,
            imageWidthPixels = 4000,
            imageHeightPixels = 3000
        )

        val MAVIC_3E = SurveyCamera(
            id = "mavic-3e",
            displayName = "Mavic 3 Enterprise (4/3 CMOS, 24 mm)",
            sensorWidthMillimeters = 17.3,
            sensorHeightMillimeters = 13.0,
            focalLengthMillimeters = 12.0,
            imageWidthPixels = 5280,
            imageHeightPixels = 3956
        )

        val MATRICE_4E = SurveyCamera(
            id = "matrice-4e",
            displayName = "Matrice 4E (angular 20 MP)",
            sensorWidthMillimeters = 17.3,
            sensorHeightMillimeters = 13.0,
            focalLengthMillimeters = 10.5,
            imageWidthPixels = 5280,
            imageHeightPixels = 3956
        )

        val PHANTOM_4_PRO = SurveyCamera(
            id = "phantom-4-pro",
            displayName = "Phantom 4 Pro (CMOS 1\", 24 mm)",
            sensorWidthMillimeters = 13.2,
            sensorHeightMillimeters = 8.8,
            focalLengthMillimeters = 10.0,
            imageWidthPixels = 5472,
            imageHeightPixels = 3648
        )

        val IPHONE_15_PRO = SurveyCamera(
            id = "iphone-15-pro",
            displayName = "iPhone 15 Pro (referencia para levantamientos a mano)",
            sensorWidthMillimeters = 9.52,
            sensorHeightMillimeters = 6.35,
            focalLengthMillimeters = 6.86,
            imageWidthPixels = 8064,
            imageHeightPixels = 6048,
            isMechanicalOrFixedLens = false
        )

        /** Every built-in payload, in the order the cartography screen lists them. */
        val BUILT_INS: List<SurveyCamera> = listOf(
            ZENMUSE_P1,
            ZENMUSE_H20T,
            ZENMUSE_L2,
            MATRICE_4E,
            MAVIC_3E,
            PHANTOM_4_PRO,
            IPHONE_15_PRO
        )

        val DEFAULT: SurveyCamera = ZENMUSE_P1

        fun find(id: String?): SurveyCamera? =
            BUILT_INS.firstOrNull { it.id.equals(id, ignoreCase = true) }

        fun require(id: String?): SurveyCamera = find(id) ?: DEFAULT

        fun custom(
            id: String,
            displayName: String,
            sensorWidthMillimeters: Double,
            sensorHeightMillimeters: Double,
            focalLengthMillimeters: Double,
            imageWidthPixels: Int,
            imageHeightPixels: Int
        ): SurveyCamera = SurveyCamera(
            id = id.trim(),
            displayName = displayName.trim().ifBlank { "Cámara personalizada" },
            sensorWidthMillimeters = sensorWidthMillimeters,
            sensorHeightMillimeters = sensorHeightMillimeters,
            focalLengthMillimeters = focalLengthMillimeters,
            imageWidthPixels = imageWidthPixels,
            imageHeightPixels = imageHeightPixels,
            isMechanicalOrFixedLens = false
        )
    }
}

data class CameraValidation(val errors: List<String>) {
    val isValid: Boolean
        get() = errors.isEmpty()
}
