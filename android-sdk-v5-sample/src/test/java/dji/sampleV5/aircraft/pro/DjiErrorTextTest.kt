package dji.sampleV5.aircraft.pro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The translation table for the DJI SDK's errors.
 *
 * Nothing here names a DJI class on purpose. `ErrorType` is a Java enum whose
 * constructor bytecode does not pass the JVM verifier, so any test that
 * instantiates it dies with a VerifyError before reaching an assertion. The
 * table is therefore addressed by the type and code *names* the SDK reports,
 * which is also the form the lookup uses at runtime, and which keeps this test
 * from depending on the SDK being on the test classpath at all.
 *
 * What is worth checking is not the wording, which lives in `strings_pro.xml`
 * and is reviewed there, but the three ways this table can quietly be wrong:
 *
 * 1. A code pointing at the wrong string. A `when` would not catch it; a test
 *    that pins specific codes would.
 * 2. Losing the type. `UNKNOWN` exists as both a generic SDK error and a
 *    waypoint error and means different things, so type and code must travel
 *    together. If they were separated, one of the two would show the other's
 *    text.
 * 3. An unknown code getting a confident answer instead of null. Null falls
 *    back to naming the area of the fault, which is true; a guess is not.
 */
class DjiErrorTextTest {

    @Test
    fun elCodigoSeNormaliza() {
        val alto = DjiErrorText.labelForCode("SDK", "INVALID_METADATA")
        // DJI does not always hand over the code in the same shape, so the lookup
        // cannot assume the exact spelling it happens to use today.
        assertEquals(alto, DjiErrorText.labelForCode("SDK", "invalid_metadata"))
        assertEquals(alto, DjiErrorText.labelForCode(" sdk ", "  Invalid_Metadata "))
    }

    @Test
    fun elTipoYElCodigoVanJuntos() {
        val comun = DjiErrorText.labelForCode("COMMON", "UNKNOWN")
        val waypoint = DjiErrorText.labelForCode("WAYPOINT", "UNKNOWN")
        assertNotNull("COMMON:UNKNOWN deberia estar traducido", comun)
        assertNotNull("WAYPOINT:UNKNOWN deberia estar traducido", waypoint)
        assertTrue("UNKNOWN debe significar cosas distintas segun el tipo", comun != waypoint)
    }

    @Test
    fun unCodigoDesconocidoNoInventaNada() {
        assertNull(DjiErrorText.labelForCode("WAYPOINT", "UN_CODIGO_QUE_NO_EXISTE"))
        assertNull(DjiErrorText.labelForCode("CORE", "TRAJ_WP_YAW_OUT_OF_RANGE"))
        assertNull(DjiErrorText.labelForCode("SDK", ""))
    }

    @Test
    fun losCodigosCriticosDeDjEstanTraducidos() {
        // The ones actually seen in the field, one by one. If DJI adds a new kind
        // of key, or changes what one of these means, this list is what forces
        // the translation to be looked at again.
        listOf(
            "SDK:INVALID_METADATA" to "la clave que rechazaba el registro",
            "SDK:INVALID_APP_KEY" to "una clave invalida",
            "SDK:APP_KEY_NOT_EXIST" to "una clave que no existe",
            "SDK:BUNDLE_NOT_MATCH" to "un package name distinto",
            "SDK:COULD_NOT_CONNECT_TO_INTERNET" to "sin red en el primer registro",
            "NETWORK:NO_NETWORK" to "sin red",
            "COMMON:TIMEOUT" to "un tiempo de espera agotado",
            "COMMON:DISCONNECTED" to "una desconexion",
            "WAYPOINT:HEIGHT_LIMIT" to "altura por encima del limite",
            "WAYPOINT:LOWER_BATTERY" to "bateria baja",
            "WAYPOINT:WPMZ_FILE_LOAD_ERROR" to "un KMZ incorrecto",
            "WAYPOINT:WAYPOINT_METHOD_NOT_SUPPORT" to "un aircraft sin waylines"
        ).forEach { (key, why) ->
            val (type, code) = key.split(':', limit = 2)
            assertNotNull("$key ($why) deberia estar traducido", DjiErrorText.labelForCode(type, code))
        }
    }

    @Test
    fun losTiposUsadosPorLosCodigosTienenCadenaPropia() {
        // Every type the code table mentions must also be resolvable, otherwise a
        // known code whose type lacks a label would render an empty line where
        // the area of the fault should be named.
        listOf("COMMON", "SDK", "NETWORK", "WAYPOINT", "CORE", "VIDEO", "RTK", "PAYLOAD")
            .forEach { type ->
                assertNotNull("sin cadena para el tipo $type", DjiErrorText.TYPE_LABELS[type])
            }
    }
}
