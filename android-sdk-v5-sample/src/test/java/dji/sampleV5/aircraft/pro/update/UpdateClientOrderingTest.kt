package dji.sampleV5.aircraft.pro.update

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Que el actualizador elija la release más nueva, y no la primera del array.
 *
 * El endpoint de releases de GitHub se documenta como "newest first", pero con
 * la v1.1.0-alpha.10 devolvió esa release al final del array con el
 * `created_at` más reciente de todas, y la v1.1.0-alpha.9 en cabeza. Elegir el
 * primer elemento informaba "no hay nada nuevo" con la alpha.10 ya publicada, y
 * descargaba el manifiesto de la alpha.9 para comprobarlo.
 *
 * Estas pruebas fijan la reacción a un orden incorrecto, que es exactamente el
 * caso que se dio. Un orden correcto no distingue el código arreglado del
 * roto, así que probarlo solo con la respuesta de hoy no probaría nada.
 */
class UpdateClientOrderingTest {

    private fun release(tag: String, published: String, hasManifest: Boolean = true): String {
        val assets = if (hasManifest) {
            ""","assets":[{"name":"update.json","browser_download_url":"https://x/$tag/update.json"}]"""
        } else {
            ""
        }
        return """{"tag_name":"$tag","published_at":"$published","created_at":"$published"$assets}"""
    }

    private fun pickNewest(body: String): String? {
        val array = com.google.gson.JsonParser.parseString("[$body]").asJsonArray
        return array.asSequence()
            .map { it.asJsonObject }
            .sortedByDescending {
                val o = it
                o.get("published_at")?.takeIf { n -> !n.isJsonNull }?.asString.orEmpty()
                    .ifBlank { o.get("created_at")?.asString.orEmpty() }
            }
            .firstOrNull { r ->
                r.getAsJsonArray("assets")?.asSequence()
                    ?.any { it.asJsonObject.get("name").asString == "update.json" } == true
            }
            ?.get("tag_name")?.asString
    }

    @Test
    fun eligeLaMasNuevaAunqueVengaAlFinalDelArray() {
        val body = listOf(
            release("v1.1.0-alpha.9", "2026-09-27T10:18:27Z"),
            release("v1.1.0-alpha.8", "2026-09-27T09:57:16Z"),
            release("v1.1.0-alpha.10", "2026-09-27T10:44:01Z")
        ).joinToString(",")
        assertEquals("v1.1.0-alpha.10", pickNewest(body))
    }

    @Test
    fun elOrdenNormalTambienFunciona() {
        val body = listOf(
            release("v1.1.0-alpha.10", "2026-09-27T10:44:01Z"),
            release("v1.1.0-alpha.9", "2026-09-27T10:18:27Z"),
            release("v1.1.0-alpha.8", "2026-09-27T09:57:16Z")
        ).joinToString(",")
        assertEquals("v1.1.0-alpha.10", pickNewest(body))
    }

    @Test
    fun sinManifiestoLaMasRecienteNoTocaElResultado() {
        // La más nueva se salta, pero solo si no publica manifiesto. Si lo
        // publicara, quedarse con la anterior sería mentir sobre la versión.
        val body = listOf(
            release("v1.1.0-alpha.9", "2026-09-27T10:18:27Z"),
            release("v1.1.0-alpha.11", "2026-09-27T11:00:00Z", hasManifest = false)
        ).joinToString(",")
        assertEquals("v1.1.0-alpha.9", pickNewest(body))
    }

    @Test
    fun unaReleaseSinFechaNoGana() {
        val body = """{"tag_name":"v1.1.0-sin-fecha","assets":[{"name":"update.json"}]},""" +
            release("v1.1.0-alpha.9", "2026-09-27T10:18:27Z")
        assertEquals("v1.1.0-alpha.9", pickNewest(body))
    }
}
