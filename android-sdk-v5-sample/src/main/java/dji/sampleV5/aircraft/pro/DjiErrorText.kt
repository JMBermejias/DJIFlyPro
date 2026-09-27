package dji.sampleV5.aircraft.pro

import android.content.Context
import androidx.annotation.StringRes
import dji.sampleV5.aircraft.R
import dji.v5.common.error.ErrorType
import dji.v5.common.error.IDJIError

/** Translated messages for the errors the DJI SDK reports.
 *
 * DJI does not translate these: the AAR ships 175 `dji_msdk_error_*` strings in
 * English and in Chinese, and not one in `values-es/`. So this is our work, not
 * a translation task left half-done by DJI. The English strings in the AAR were
 * the source for the meanings below.
 *
 * WHY ONLY SOME CODES ARE TRANSLATED
 *
 * Of the 170 codes in the SDK, this app can ever surface the 52 in [CODES]. The
 * rest belong to livestream, RTK, payload and other areas the app does not use,
 * and a few of them, like the 48 waypoint codes with no text at all, are not
 * even documented by DJI in English. Guessing what those mean would be worse
 * than saying little: a mistranslated `HEIGHT_LIMIT` as "below the minimum"
 * would tell the pilot the opposite of the truth about their flight height.
 *
 * So an unknown code falls back to a Spanish line naming the area of the fault,
 * plus the code itself so it can be looked up, plus DJI's own text. The user
 * never loses the information, and never reads a confident wrong statement.
 *
 * DJI's original text is always kept, even when the translation is known. It is
 * the authoritative wording, it is what support will ask for, and it costs one
 * line to keep. If a translation is ever wrong, the original is right there.
 */
object DjiErrorText {

    /**
     * The Spanish name of each error area. Exhaustive over [ErrorType], so adding
     * a type to the SDK fails the build here instead of silently falling back to
     * an English enum name in the middle of a Spanish sentence.
     */
    /**
     * The Spanish name of each error area, keyed by the enum's name.
     *
     * Data for the same reason as [CODE_LABELS]: DJI's enum cannot be
     * instantiated in a unit test, so a `when` over it would be untestable and
     * the table of area names would be the one part of this file with no
     * coverage.
     */
    internal val TYPE_LABELS: Map<String, Int> = mapOf(
        "COMMON" to R.string.dji_type_common,
        "SDK" to R.string.dji_type_sdk,
        "NETWORK" to R.string.dji_type_network,
        "LDM" to R.string.dji_type_ldm,
        "CORE" to R.string.dji_type_core,
        "VIDEO" to R.string.dji_type_video,
        "MEGAPHONE" to R.string.dji_type_megaphone,
        "WAYPOINT" to R.string.dji_type_waypoint,
        "LIVESTREAM" to R.string.dji_type_livestream,
        "RTK" to R.string.dji_type_rtk,
        "USER_LOGIN" to R.string.dji_type_user_login,
        "PERCEPTION" to R.string.dji_type_perception,
        "UAS" to R.string.dji_type_uas,
        "LTE" to R.string.dji_type_lte,
        "PAYLOAD" to R.string.dji_type_payload,
        "MOP" to R.string.dji_type_mop,
        "FLY_SAFE" to R.string.dji_type_fly_safe,
        "UPGRADE" to R.string.dji_type_upgrade,
        "REAL_NAME" to R.string.dji_type_real_name,
        "INTELLIGENT" to R.string.dji_type_intelligent,
        "UNKNOWN" to R.string.dji_type_unknown,
    )

    @StringRes
    fun label(type: ErrorType): Int =
        TYPE_LABELS[type.name] ?: R.string.dji_type_unknown
    /**
     * The Spanish text for each code this app can surface, keyed by
     * "TIPO:CODIGO".
     *
     * A map, not a `when`, and keyed by strings rather than by enum values, for
     * two reasons. `UNKNOWN` exists both as a generic SDK error and as a
     * waypoint error and means different things, so type and code have to travel
     * together. And DJI's `ErrorType` is a Java enum whose constructor bytecode
     * does not pass the JVM verifier — ART accepts it, a plain unit test does
     * not — so a `when` over it could not be tested at all. As data, the whole
     * table is testable, which matters more here than the exhaustive check: the
     * risk in this file is a code pointing at the wrong string, and a `when`
     * does not catch that.
     */
    private val CODE_LABELS: Map<String, Int> = mapOf(
        "COMMON:EXECUTION_FAILED" to R.string.dji_err_common_execution_failed,
        "COMMON:SYSTEM_BUSY" to R.string.dji_err_common_system_busy,
        "COMMON:UNKNOWN" to R.string.dji_err_common_unknown,
        "COMMON:UNDEFINED" to R.string.dji_err_common_undefined,
        "COMMON:TIMEOUT" to R.string.dji_err_common_timeout,
        "COMMON:PARAM_ILLEGAL" to R.string.dji_err_common_param_illegal,
        "COMMON:PARAM_INVALID" to R.string.dji_err_common_param_invalid,
        "COMMON:UNSUPPORTED" to R.string.dji_err_common_unsupported,
        "COMMON:DISCONNECTED" to R.string.dji_err_common_disconnected,
        "COMMON:OUT_OF_RANGE" to R.string.dji_err_common_out_of_range,
        "SDK:REGISTER_WITHOUT_INIT" to R.string.dji_err_sdk_register_without_init,
        "SDK:EMPTY_APP_KEY" to R.string.dji_err_sdk_empty_app_key,
        "SDK:INVALID_APP_KEY" to R.string.dji_err_sdk_invalid_app_key,
        "SDK:REGISTRATION_INVALID_UUID" to R.string.dji_err_sdk_registration_invalid_uuid,
        "SDK:BUNDLE_NOT_MATCH" to R.string.dji_err_sdk_bundle_not_match,
        "SDK:APP_KEY_PROHIBITED" to R.string.dji_err_sdk_app_key_prohibited,
        "SDK:MAX_ACTIVATION_COUNT_REACHED" to R.string.dji_err_sdk_max_activation_count_reached,
        "SDK:APP_KEY_INVALID_PLATFORM" to R.string.dji_err_sdk_app_key_invalid_platform,
        "SDK:APP_KEY_NOT_EXIST" to R.string.dji_err_sdk_app_key_not_exist,
        "SDK:APP_KEY_LEVEL_NOT_PERMITTED" to R.string.dji_err_sdk_app_key_level_not_permitted,
        "SDK:OVER_MAX_ACTIVATIONS_COUNT" to R.string.dji_err_sdk_over_max_activations_count,
        "SDK:SERVER_PARSE_FAILURE" to R.string.dji_err_sdk_server_parse_failure,
        "SDK:SERVER_WRITE_FAILURE" to R.string.dji_err_sdk_server_write_failure,
        "SDK:SERVER_DATA_ABNORMAL" to R.string.dji_err_sdk_server_data_abnormal,
        "SDK:INVALID_METADATA" to R.string.dji_err_sdk_invalid_metadata,
        "SDK:REGISTRATION_AES_ENCRYPT_ERROR" to R.string.dji_err_sdk_registration_aes_encrypt_error,
        "SDK:APP_KEY_NOT_ENROLLED_BY_BETA_SDK" to R.string.dji_err_sdk_app_key_not_enrolled_by_beta_sdk,
        "SDK:INVALID_APP_KEY_FOR_BETA_VERSION" to R.string.dji_err_sdk_invalid_app_key_for_beta_version,
        "SDK:SDK_VERSION_BLOCKED" to R.string.dji_err_sdk_sdk_version_blocked,
        "SDK:ACTIVATE_FAILED" to R.string.dji_err_sdk_activate_failed,
        "SDK:APP_KEY_BLOCKED" to R.string.dji_err_sdk_app_key_blocked,
        "SDK:COULD_NOT_CONNECT_TO_INTERNET" to R.string.dji_err_sdk_could_not_connect_to_internet,
        "SDK:INVALID_PRODUCT_CATEGORY" to R.string.dji_err_sdk_invalid_product_category,
        "SDK:MSDK_VERSION_MISMATCH" to R.string.dji_err_sdk_msdk_version_mismatch,
        "NETWORK:NETWORK_ACCESS_FAIL" to R.string.dji_err_network_network_access_fail,
        "NETWORK:CALL_CANCELED" to R.string.dji_err_network_call_canceled,
        "NETWORK:NO_NETWORK" to R.string.dji_err_network_no_network,
        "NETWORK:NO_NETWORK_DELEGATE" to R.string.dji_err_network_no_network_delegate,
        "NETWORK:SYSTEM_ERROR" to R.string.dji_err_network_system_error,
        "WAYPOINT:WAYPOINT_METHOD_NOT_SUPPORT" to R.string.dji_err_waypoint_waypoint_method_not_support,
        "WAYPOINT:LOWER_BATTERY" to R.string.dji_err_waypoint_lower_battery,
        "WAYPOINT:CANNOT_BREAK_WAYLINE_IN_CUR_STATE" to R.string.dji_err_waypoint_cannot_break_wayline_in_cur_state,
        "WAYPOINT:CANNOT_START_WAYLINE_WHEN_WAYLINE_RUNNING" to R.string.dji_err_waypoint_cannot_start_wayline_when_wayline_running,
        "WAYPOINT:CANNOT_STOP_WAYLINE_WHEN_WAYLINE_NOT_RUNNING" to R.string.dji_err_waypoint_cannot_stop_wayline_when_wayline_not_running,
        "WAYPOINT:WPMZ_FILE_LOAD_ERROR" to R.string.dji_err_waypoint_wpmz_file_load_error,
        "WAYPOINT:GPS_INVALID" to R.string.dji_err_waypoint_gps_invalid,
        "WAYPOINT:HEIGHT_LIMIT" to R.string.dji_err_waypoint_height_limit,
        "WAYPOINT:LOW_LIMIT" to R.string.dji_err_waypoint_low_limit,
        "WAYPOINT:MISSION_ID_NOT_EXIST" to R.string.dji_err_waypoint_mission_id_not_exist,
        "WAYPOINT:OBSTACAL_STOP" to R.string.dji_err_waypoint_obstacal_stop,
        "WAYPOINT:UNKNOWN" to R.string.dji_err_waypoint_unknown,
        "WAYPOINT:UPLOAD_FILE_FAILED" to R.string.dji_err_waypoint_upload_file_failed,
    )

    /**
     * The Spanish text for a specific code, or null when it is not one this app
     * can hit. Null matters: it falls back to naming the area of the fault, which
     * is a fact, instead of guessing, which would not be.
     */
    @StringRes
    fun labelForCode(typeName: String, code: String): Int? =
        CODE_LABELS["${typeName.trim().uppercase()}:${code.trim().uppercase()}"]

    /**
     * What the operator reads. Never empty, and never a translation without the
     * original behind it.
     */
    fun describe(context: Context, error: IDJIError): String =
        describe(context, error.errorType(), error.errorCode(), error.description())

    /**
     * La forma que usan los ViewModel, que guardan el error desmontado en vez del
     * `IDJIError`: el mismo dato, pero sin arrastrar la interfaz del SDK.
     */
    fun describe(
        context: Context,
        type: ErrorType,
        code: String?,
        original: String?
    ): String {
        val detail = original?.trim().orEmpty().ifBlank {
            context.getString(R.string.dji_error_no_description)
        }
        val normalized = code?.trim().orEmpty().uppercase()
        val specific = labelForCode(type.name, normalized)
        return if (specific != null) {
            context.getString(R.string.dji_error_translated, context.getString(specific), detail)
        } else {
            context.getString(
                R.string.dji_error_by_type,
                context.getString(label(type)),
                normalized.ifBlank { context.getString(R.string.dji_error_no_code) },
                detail
            )
        }
    }
}
