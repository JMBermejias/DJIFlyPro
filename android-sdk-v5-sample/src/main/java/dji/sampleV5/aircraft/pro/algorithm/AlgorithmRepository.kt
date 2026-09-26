package dji.sampleV5.aircraft.pro.algorithm

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import dji.sampleV5.aircraft.pro.mission.AuditLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

class AlgorithmRepository(private val context: Context) {
    companion object {
        const val MAX_RECIPE_BYTES = 1_048_576
        const val ASSET_DIRECTORY = "algorithms"
        private val ALLOWED_OUTPUTS = setOf("KMZ", "mission.json", "audit.jsonl")
    }

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val importedDirectory = File(context.filesDir, "algorithms").apply { mkdirs() }
    private val audit = AuditLog(context)

    /**
     * Recipes the app offers, in one list: the ones shipped as assets, then the
     * ones the operator imported. The assets are the single source of truth, so
     * a recipe can be reviewed, corrected or extended in the repository without
     * a recompile; [builtIns] is only the fallback for the case where the assets
     * cannot be read at all.
     */
    fun list(): List<AlgorithmRecipe> {
        val shipped = shippedRecipes()
        val base = if (shipped.isEmpty()) builtIns() else shipped
        val imported = importedDirectory.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        }.orEmpty().mapNotNull { file ->
            if (file.length() > MAX_RECIPE_BYTES) return@mapNotNull null
            runCatching { parseAndValidate(file.readText(Charsets.UTF_8)) }.getOrNull()
        }
        return (base + imported).distinctBy { it.id }.sortedBy { it.name }
    }

    private fun shippedRecipes(): List<AlgorithmRecipe> =
        runCatching {
            context.assets.list(ASSET_DIRECTORY).orEmpty()
                .filter { it.endsWith(".json", ignoreCase = true) }
                .sorted()
                .mapNotNull { name ->
                    runCatching {
                        parseAndValidate(context.assets.open("$ASSET_DIRECTORY/$name").bufferedReader().use { it.readText() })
                    }.getOrNull()
                }
        }.getOrDefault(emptyList())

    fun find(id: String): AlgorithmRecipe? = list().firstOrNull { it.id == id }

    fun import(uri: Uri): AlgorithmRecipe {
        val bytes = readRecipeBytes(uri)
        if (bytes.size > MAX_RECIPE_BYTES) {
            error("Algorithm recipes are limited to 1 MiB")
        }
        val raw = bytes.toString(Charsets.UTF_8)
        val recipe = parseAndValidate(raw)
        val target = File(importedDirectory, "${recipe.id}.json")
        target.writeBytes(bytes)
        audit.append(
            event = "algorithm.imported",
            missionId = null,
            details = mapOf("id" to recipe.id, "sha256" to sha256(bytes))
        )
        return recipe
    }

    fun validate(recipe: AlgorithmRecipe) {
        require(recipe.schema == AlgorithmRecipe.SCHEMA) {
            "Unsupported schema; expected ${AlgorithmRecipe.SCHEMA}"
        }
        require(recipe.id.matches(Regex("[a-z0-9][a-z0-9._-]{2,63}"))) {
            "Algorithm id must use lowercase letters, digits, dot, underscore or hyphen"
        }
        require(recipe.name.isNotBlank() && recipe.name.length <= 100) { "Algorithm name is invalid" }
        require(recipe.version.matches(Regex("[A-Za-z0-9][A-Za-z0-9.+_-]{0,31}"))) {
            "Algorithm version is invalid"
        }
        require(recipe.jobType.matches(Regex("[a-z0-9][a-z0-9._-]{1,31}"))) {
            "Algorithm jobType is invalid"
        }
        require(recipe.description.length <= 500) { "Algorithm description is too long" }
        require(recipe.outputs.all { it in ALLOWED_OUTPUTS }) { "Algorithm outputs contain an unsupported value" }
        val d = recipe.defaults
        require(d.lengthMeters in 1.0..5_000.0) { "Algorithm length is outside the allowed range" }
        require(d.widthMeters in 1.0..5_000.0) { "Algorithm width is outside the allowed range" }
        require(d.heightMeters in 1.0..300.0) { "Algorithm height is outside the allowed range" }
        require(d.bearingDegrees.isFinite()) { "Algorithm bearing is invalid" }
        require(d.altitudeMeters in 2.0..dji.sampleV5.aircraft.pro.mission.MissionValidator.MAX_GUIDED_ALTITUDE_METERS) {
            "Algorithm altitude is outside the allowed range"
        }
        require(d.standoffMeters in 0.5..500.0) { "Algorithm stand-off is outside the allowed range" }
        require(d.lineSpacingMeters in 0.5..500.0) { "Algorithm line spacing is outside the allowed range" }
        require(d.photoSpacingMeters in 0.5..100.0) { "Algorithm photo spacing is outside the allowed range" }
        require(d.overlapPercent in 10..90) { "Algorithm overlap is outside the allowed range" }
        require(d.speedMps in 1.0..15.0) { "Algorithm speed is outside the allowed range" }
        require(d.gimbalPitchDegrees in -90.0..30.0) { "Algorithm gimbal pitch is outside the allowed range" }
    }

    private fun parseAndValidate(raw: String): AlgorithmRecipe {
        val recipe = try {
            gson.fromJson(raw, AlgorithmRecipe::class.java)
        } catch (error: JsonParseException) {
            error("Invalid JSON recipe: ${error.message}")
        } catch (error: Exception) {
            error("Invalid JSON recipe: ${error.message}")
        } ?: error("The recipe is empty")
        try {
            validate(recipe)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid algorithm recipe: ${error.message}", error)
        }
        return recipe
    }

    private fun builtIns(): List<AlgorithmRecipe> = listOf(
        AlgorithmRecipe(
            id = "facade.vertical.v1",
            name = "Fachada vertical",
            jobType = "facade",
            description = "Barrido vertical con solape configurable y fotografías en cada punto.",
            template = dji.sampleV5.aircraft.pro.mission.MissionTemplate.FACADE,
            defaults = AlgorithmDefaults(lengthMeters = 60.0, widthMeters = 1.0, heightMeters = 30.0, altitudeMeters = 8.0, standoffMeters = 10.0, lineSpacingMeters = 8.0, photoSpacingMeters = 6.0, overlapPercent = 75, speedMps = 3.0, gimbalPitchDegrees = -45.0)
        ),
        AlgorithmRecipe(
            id = "roof.grid.v1",
            name = "Cubierta en malla",
            jobType = "roof",
            description = "Malla double para inspección de cubiertas; requiere validación de obstáculos.",
            template = dji.sampleV5.aircraft.pro.mission.MissionTemplate.ROOF,
            routePattern = dji.sampleV5.aircraft.pro.mission.RoutePattern.GRID,
            defaults = AlgorithmDefaults(lengthMeters = 80.0, widthMeters = 50.0, heightMeters = 20.0, altitudeMeters = 35.0, standoffMeters = 10.0, lineSpacingMeters = 10.0, photoSpacingMeters = 7.0, overlapPercent = 75, speedMps = 4.0, gimbalPitchDegrees = -90.0)
        ),
        AlgorithmRecipe(
            id = "solar.parallel.v1",
            name = "Planta solar paralela",
            jobType = "solar",
            description = "Líneas paralelas solapadas para revisión visual de una planta solar.",
            template = dji.sampleV5.aircraft.pro.mission.MissionTemplate.SOLAR,
            defaults = AlgorithmDefaults(lengthMeters = 180.0, widthMeters = 100.0, heightMeters = 10.0, altitudeMeters = 45.0, standoffMeters = 10.0, lineSpacingMeters = 12.0, photoSpacingMeters = 8.0, overlapPercent = 75, speedMps = 4.0, gimbalPitchDegrees = -90.0)
        ),
        AlgorithmRecipe(
            id = "field.parallel.v1",
            name = "Campo en líneas",
            jobType = "field",
            description = "Cobertura de un campo con líneas paralelas y puntos de captura.",
            template = dji.sampleV5.aircraft.pro.mission.MissionTemplate.FIELD,
            defaults = AlgorithmDefaults(lengthMeters = 250.0, widthMeters = 150.0, heightMeters = 10.0, altitudeMeters = 60.0, standoffMeters = 10.0, lineSpacingMeters = 18.0, photoSpacingMeters = 12.0, overlapPercent = 65, speedMps = 6.0, gimbalPitchDegrees = -90.0)
        )
    )

    private fun readRecipeBytes(uri: Uri): ByteArray {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not read the selected file")
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        input.use { stream ->
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                if (total > MAX_RECIPE_BYTES) {
                    error("Algorithm recipes are limited to 1 MiB")
                }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun sha256(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }
}
