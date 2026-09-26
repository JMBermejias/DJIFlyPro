package dji.sampleV5.aircraft.pro

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionStatusMessageTest {

    @Test
    fun anEmptyApiKeyMeansTheAppCannotRegister() {
        assertFalse(ConnectionStatusMessage.isApiKeyConfigured(null))
        assertFalse(ConnectionStatusMessage.isApiKeyConfigured(""))
        assertFalse(ConnectionStatusMessage.isApiKeyConfigured("   "))
    }

    @Test
    fun theSamplePlaceholderIsNotAConfiguredKey() {
        assertFalse(ConnectionStatusMessage.isApiKeyConfigured("Please add your App Key here"))
    }

    @Test
    fun aRealKeyIsRecognised() {
        assertTrue(ConnectionStatusMessage.isApiKeyConfigured("0123456789abcdef0123456789abcdef"))
    }

    /**
     * The regression this guards: the registration observer fires and used to
     * overwrite the missing-Key warning with a generic error, leaving the
     * operator with no idea what to do.
     */
    @Test
    fun aMissingKeySurvivesTheRegistrationFailure() {
        val text = ConnectionStatusMessage.registration(
            registered = false,
            errorDescription = "DJI SDK registration failed (code -1)",
            apiKey = ""
        )

        assertTrue(text, text.contains("API Key"))
        assertTrue(text, text.contains("com.djiflypro.app"))
        assertFalse("must not be masked by the generic error", text.contains("code -1"))
    }

    @Test
    fun aMissingKeyAlsoSurvivesTheInitialState() {
        val text = ConnectionStatusMessage.initial("")

        assertTrue(text, text.contains("API Key"))
        assertTrue(text, text.contains("DJI Developer"))
    }

    @Test
    fun withAKeyTheRegistrationErrorIsShown() {
        val text = ConnectionStatusMessage.registration(
            registered = false,
            errorDescription = "timeout",
            apiKey = "0123456789abcdef0123456789abcdef"
        )

        assertTrue(text, text.contains("timeout"))
        assertFalse(text, text.contains("API Key"))
    }

    @Test
    fun aBlankErrorFallsBackToWaiting() {
        val text = ConnectionStatusMessage.registration(
            registered = false,
            errorDescription = null,
            apiKey = "0123456789abcdef0123456789abcdef"
        )

        assertTrue(text, text.contains("Esperando registro"))
    }

    @Test
    fun successIsReportedWhenRegistered() {
        val text = ConnectionStatusMessage.registration(
            registered = true,
            errorDescription = null,
            apiKey = "0123456789abcdef0123456789abcdef"
        )

        assertTrue(text, text.contains("registrado"))
    }
}
